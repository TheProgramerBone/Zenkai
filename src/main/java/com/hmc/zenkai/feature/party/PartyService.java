package com.hmc.zenkai.feature.party;

import com.hmc.zenkai.Zenkai;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REGLAS y EFECTOS del sistema de party: quién puede hacer qué, y qué le llega a quién
 * cuando lo hace (mensajes de chat + {@link PartySyncPacket}). {@link PartyManager} solo
 * guarda datos; esta clase es la ÚNICA que lo muta desde fuera — así, {@link PartyCommands}
 * hoy y una futura GUI que mute la party en v2 llaman aquí y no directamente al manager,
 * y las dos vías aplican exactamente las mismas reglas.
 * SIN GameProfileCache: los nombres de miembros offline caen al UUID en texto. Es una
 * degradación aceptable para v1 (kick/list siguen funcionando, solo se ve menos bonito);
 * mejorarlo no toca ninguna otra pieza del sistema.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PartyService {
    private PartyService() {}

    /** Prefijo visual del chat de party, reutilizado por chat() y por las claves de traducción. */
    private static final ChatFormatting PARTY_COLOR = ChatFormatting.AQUA;

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        var mgr = PartyManager.get(sp.server);
        var party = mgr.partyOf(sp.getUUID());
        if (party != null) sendSyncTo(sp, party);
    }

    // ── Invitar / responder ──────────────────────────────────────────────────

    public static boolean invite(MinecraftServer server, ServerPlayer inviter, String targetName) {
        var mgr = PartyManager.get(server);
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            inviter.sendSystemMessage(Component.translatable(
                    "command.zenkai.party.invite.offline", targetName));
            return false;
        }
        if (target == inviter) {
            inviter.sendSystemMessage(Component.translatable("command.zenkai.party.invite.self"));
            return false;
        }
        if (mgr.partyOf(target.getUUID()) != null) {
            inviter.sendSystemMessage(Component.translatable(
                    "command.zenkai.party.invite.already_grouped", target.getGameProfile().getName()));
            return false;
        }

        PartyManager.Party party = mgr.partyOf(inviter.getUUID());
        if (party == null) {
            party = mgr.createFor(inviter.getUUID());
        } else if (!party.leaderId.equals(inviter.getUUID())) {
            inviter.sendSystemMessage(Component.translatable("command.zenkai.party.not_leader"));
            return false;
        }
        if (party.members.size() >= PartyManager.MAX_SIZE) {
            inviter.sendSystemMessage(Component.translatable(
                    "command.zenkai.party.invite.full", PartyManager.MAX_SIZE));
            return false;
        }

        mgr.invite(target.getUUID(), party.id);
        inviter.sendSystemMessage(Component.translatable(
                "command.zenkai.party.invite.sent", target.getGameProfile().getName()));
        // ClickEvent sugiere el comando en vez de ejecutarlo solo: aceptar una invitación
        // que no pediste con un solo clic accidental sería demasiado fácil.
        target.sendSystemMessage(Component.translatable(
                        "command.zenkai.party.invite.received", inviter.getGameProfile().getName())
                .copy().withStyle(style -> style
                        .withColor(PARTY_COLOR)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/zparty accept"))));
        return true;
    }

    public static boolean accept(MinecraftServer server, ServerPlayer sp) {
        var mgr = PartyManager.get(server);
        UUID partyId = mgr.pendingInviteOf(sp.getUUID());
        if (partyId == null) {
            sp.sendSystemMessage(Component.translatable("command.zenkai.party.accept.none"));
            return false;
        }
        mgr.clearInvite(sp.getUUID());

        if (mgr.partyOf(sp.getUUID()) != null) {
            sp.sendSystemMessage(Component.translatable("command.zenkai.party.invite.already_grouped_self"));
            return false;
        }
        PartyManager.Party party = mgr.party(partyId);
        if (party == null || party.members.size() >= PartyManager.MAX_SIZE) {
            sp.sendSystemMessage(Component.translatable("command.zenkai.party.accept.gone"));
            return false;
        }

        mgr.addMember(party, sp.getUUID());
        broadcast(server, party, Component.translatable(
                "command.zenkai.party.joined", sp.getGameProfile().getName()));
        syncAll(server, party);
        return true;
    }

    public static boolean deny(MinecraftServer server, ServerPlayer sp) {
        var mgr = PartyManager.get(server);
        UUID partyId = mgr.pendingInviteOf(sp.getUUID());
        if (partyId == null) {
            sp.sendSystemMessage(Component.translatable("command.zenkai.party.accept.none"));
            return false;
        }
        mgr.clearInvite(sp.getUUID());
        sp.sendSystemMessage(Component.translatable("command.zenkai.party.deny.done"));

        PartyManager.Party party = mgr.party(partyId);
        ServerPlayer leader = party == null ? null : server.getPlayerList().getPlayer(party.leaderId);
        if (leader != null) {
            leader.sendSystemMessage(Component.translatable(
                    "command.zenkai.party.deny.notify", sp.getGameProfile().getName()));
        }
        return true;
    }

    // ── Salir / expulsar / disolver ─────────────────────────────────────────

    public static boolean leave(MinecraftServer server, ServerPlayer sp) {
        var mgr = PartyManager.get(server);
        PartyManager.Party party = mgr.partyOf(sp.getUUID());
        if (party == null) {
            sp.sendSystemMessage(Component.translatable("command.zenkai.party.none"));
            return false;
        }
        if (party.leaderId.equals(sp.getUUID())) {
            // Irse siendo líder disuelve el grupo entero: no hay sucesión en v1 (ver plan
            // del feature). Se avisa explícito para que no sea una sorpresa.
            sp.sendSystemMessage(Component.translatable("command.zenkai.party.leave.as_leader"));
            return disband(server, sp);
        }
        mgr.removeMember(party, sp.getUUID());
        sp.sendSystemMessage(Component.translatable("command.zenkai.party.leave.done"));
        broadcast(server, party, Component.translatable(
                "command.zenkai.party.left", sp.getGameProfile().getName()));
        syncAll(server, party);
        sendEmptySync(sp);
        return true;
    }

    public static boolean kick(MinecraftServer server, ServerPlayer leader, String targetName) {
        var mgr = PartyManager.get(server);
        PartyManager.Party party = mgr.partyOf(leader.getUUID());
        if (party == null || !party.leaderId.equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.translatable("command.zenkai.party.not_leader"));
            return false;
        }
        UUID targetId = resolveMemberByName(server, party, targetName);
        if (targetId == null) {
            leader.sendSystemMessage(Component.translatable("command.zenkai.party.kick.not_found", targetName));
            return false;
        }
        if (targetId.equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.translatable("command.zenkai.party.kick.self"));
            return false;
        }

        String targetDisplay = resolveName(server, targetId);
        mgr.removeMember(party, targetId);
        leader.sendSystemMessage(Component.translatable("command.zenkai.party.kick.done", targetDisplay));

        ServerPlayer targetSp = server.getPlayerList().getPlayer(targetId);
        if (targetSp != null) {
            targetSp.sendSystemMessage(Component.translatable("command.zenkai.party.kick.notify"));
            sendEmptySync(targetSp);
        }
        broadcast(server, party, Component.translatable("command.zenkai.party.kicked_announce", targetDisplay));
        syncAll(server, party);
        return true;
    }

    public static boolean disband(MinecraftServer server, ServerPlayer leader) {
        var mgr = PartyManager.get(server);
        PartyManager.Party party = mgr.partyOf(leader.getUUID());
        if (party == null || !party.leaderId.equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.translatable("command.zenkai.party.not_leader"));
            return false;
        }
        broadcast(server, party, Component.translatable("command.zenkai.party.disbanded"));
        for (UUID m : party.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(m);
            if (sp != null) sendEmptySync(sp);
        }
        mgr.disband(party);
        return true;
    }

    // ── Chat y listado ───────────────────────────────────────────────────────

    /**
     * Broadcast de sistema a los miembros online, no chat firmado: no pasa por el pipeline
     * de reporte de chat de vanilla, igual que otros mensajes de sistema del mod. Para un
     * chat de grupo de cuatro personas como mucho, es proporcionado.
     */
    public static boolean chat(MinecraftServer server, ServerPlayer sender, String message) {
        var mgr = PartyManager.get(server);
        PartyManager.Party party = mgr.partyOf(sender.getUUID());
        if (party == null) {
            sender.sendSystemMessage(Component.translatable("command.zenkai.party.none"));
            return false;
        }
        Component line = Component.literal("[Party] ").withStyle(PARTY_COLOR)
                .append(Component.literal(sender.getGameProfile().getName() + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        for (UUID m : party.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(m);
            if (sp != null) sp.sendSystemMessage(line);
        }
        return true;
    }

    public static boolean list(MinecraftServer server, ServerPlayer sp) {
        var mgr = PartyManager.get(server);
        PartyManager.Party party = mgr.partyOf(sp.getUUID());
        if (party == null) {
            sp.sendSystemMessage(Component.translatable("command.zenkai.party.none"));
            return false;
        }
        sp.sendSystemMessage(Component.translatable(
                "command.zenkai.party.list.header", party.members.size(), PartyManager.MAX_SIZE)
                .withStyle(ChatFormatting.GOLD));
        for (UUID m : party.members) {
            String name = resolveName(server, m);
            boolean isLeader = m.equals(party.leaderId);
            Component tag = isLeader
                    ? Component.literal("★ ").withStyle(ChatFormatting.GOLD)
                    : Component.literal("- ").withStyle(ChatFormatting.GRAY);
            sp.sendSystemMessage(tag.copy().append(Component.literal(name)));
        }
        return true;
    }

    /** Nombres de los compañeros de party de {@code sp} (líder incluido), para el
     *  autocompletado de {@code /zparty kick} en PartyCommands. Lista vacía sin party. */
    public static List<String> memberNames(MinecraftServer server, ServerPlayer sp) {
        PartyManager.Party party = PartyManager.get(server).partyOf(sp.getUUID());
        if (party == null) return List.of();
        List<String> names = new ArrayList<>(party.members.size());
        for (UUID m : party.members) names.add(resolveName(server, m));
        return names;
    }

    // ── Ayudantes ────────────────────────────────────────────────────────────

    /** Busca por nombre SOLO entre los miembros actuales de esa party (online u offline):
     *  a diferencia de invite(), kick() no necesita que el objetivo esté conectado. Sin
     *  GameProfileCache (ver javadoc de la clase), un miembro offline solo se encuentra
     *  tecleando su UUID completo — degradación aceptable para v1. */
    @Nullable
    private static UUID resolveMemberByName(MinecraftServer server, PartyManager.Party party, String name) {
        for (UUID m : party.members) {
            if (resolveName(server, m).equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    private static String resolveName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        return id.toString();
    }

    private static void broadcast(MinecraftServer server, PartyManager.Party party, Component msg) {
        for (UUID m : party.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(m);
            if (sp != null) sp.sendSystemMessage(msg);
        }
    }

    private static void syncAll(MinecraftServer server, PartyManager.Party party) {
        for (UUID m : party.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(m);
            if (sp != null) sendSyncTo(sp, party);
        }
    }

    private static void sendSyncTo(ServerPlayer sp, PartyManager.Party party) {
        List<PartySyncPacket.Member> members = new ArrayList<>();
        for (UUID m : party.members) {
            members.add(new PartySyncPacket.Member(m, resolveName(sp.server, m)));
        }
        PacketDistributor.sendToPlayer(sp, new PartySyncPacket(true, party.leaderId, members));
    }

    private static void sendEmptySync(ServerPlayer sp) {
        PacketDistributor.sendToPlayer(sp, new PartySyncPacket(false, null, List.of()));
    }
}
