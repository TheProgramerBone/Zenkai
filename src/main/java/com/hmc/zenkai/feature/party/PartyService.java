package com.hmc.zenkai.feature.party;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
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
        if (party.members.size() >= party.maxSize) {
            inviter.sendSystemMessage(Component.translatable(
                    "command.zenkai.party.invite.full", party.maxSize));
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
        if (party == null || party.members.size() >= party.maxSize) {
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

    // ── Debug (temporal) ─────────────────────────────────────────────────────

    /**
     * SOLO PARA PRUEBAS: añade un miembro con un UUID aleatorio (no un jugador real) a la
     * party de quien ejecuta el comando, creándola si hace falta. Bypasa invite()/accept() a
     * propósito — sirve para probar PartyScreen en singleplayer, donde no hay con quién formar
     * grupo de verdad. Ver el javadoc de {@link PartyDebug} para qué más borrar junto a esto.
     * Comando: {@code /zenkai debug party add [nombre]}, gated por el permiso 2 del root
     * /zenkai (ver ModCommands) — no expuesto en /zparty, que es de cualquier jugador.
     */
    public static boolean debugAddFakeMember(MinecraftServer server, ServerPlayer leader,
                                             @Nullable String fakeName) {
        var mgr = PartyManager.get(server);
        PartyManager.Party party = mgr.partyOf(leader.getUUID());
        if (party == null) {
            party = mgr.createFor(leader.getUUID());
        } else if (!party.leaderId.equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.translatable("command.zenkai.party.not_leader"));
            return false;
        }
        if (party.members.size() >= party.maxSize) {
            leader.sendSystemMessage(Component.translatable(
                    "command.zenkai.party.invite.full", party.maxSize));
            return false;
        }

        String name = (fakeName == null || fakeName.isBlank())
                ? "TestBot" + party.members.size() : fakeName;
        // Recortado al mismo tope que PartySyncPacket acepta (ver su MAX_NAME_LEN): un
        // nombre de prueba más largo que eso reventaba el encoder en CADA login mientras la
        // party siguiera guardada — justo el bug que dejó esto aquí, no algo hipotético.
        if (name.length() > 32) name = name.substring(0, 32);
        UUID fakeId = UUID.randomUUID();
        PartyDebug.register(fakeId, name);
        mgr.addMember(party, fakeId);
        leader.sendSystemMessage(Component.literal("[Zenkai DEBUG] Added fake party member: " + name));
        syncAll(server, party);
        return true;
    }

    // ── Fuego amigo ──────────────────────────────────────────────────────────

    /**
     * Único punto de decisión de si un golpe entre dos jugadores debe anularse por ir entre
     * miembros de la misma party. Lo consulta CombatZenkaiHooks.onDamage ANTES de calcular
     * nada — mismo hueco que los i-frames — así que cubre daño cuerpo a cuerpo, ki (blasts,
     * proyectiles, Kiai) y técnicas por igual: pasa por ese mismo LivingDamageEvent.Pre,
     * sin importar el origen.
     */
    public static boolean friendlyFireBlocked(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == victim) return false;
        var mgr = PartyManager.get(attacker.server);
        PartyManager.Party party = mgr.partyOf(attacker.getUUID());
        if (party == null || !party.members.contains(victim.getUUID())) return false;
        return !party.friendlyFire;
    }

    /** Solo el líder. Por defecto una party nace con friendlyFire=false (protegidos); esto
     *  es lo único que lo cambia. */
    public static boolean setFriendlyFire(MinecraftServer server, ServerPlayer leader, boolean on) {
        var mgr = PartyManager.get(server);
        PartyManager.Party party = mgr.partyOf(leader.getUUID());
        if (party == null || !party.leaderId.equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.translatable("command.zenkai.party.not_leader"));
            return false;
        }
        if (party.friendlyFire == on) {
            leader.sendSystemMessage(Component.translatable(on
                    ? "command.zenkai.party.ff.already_on"
                    : "command.zenkai.party.ff.already_off"));
            return false;
        }
        mgr.setFriendlyFire(party, on);
        broadcast(server, party, Component.translatable(on
                ? "command.zenkai.party.ff.on"
                : "command.zenkai.party.ff.off", leader.getGameProfile().getName()));
        syncAll(server, party);
        return true;
    }

    // ── Tamaño máximo ────────────────────────────────────────────────────────

    /**
     * Solo el líder. Acotado a [members.size() actual, CommonConfig.partyMaxSizeCeiling()]:
     * ni por debajo de la gente que ya está dentro (los expulsaría en silencio) ni por encima
     * del tope que el admin del servidor haya fijado. El icono PartyConfig de PartyScreen ya
     * lee ese mismo tope del último PartySyncPacket para no dejar arrastrar el picker más allá
     * (ver PartySyncPacket.maxSizeCeiling), pero el servidor es quien de verdad decide: un
     * cliente desincronizado o modificado no puede colarse por encima.
     */
    public static boolean setMaxSize(MinecraftServer server, ServerPlayer leader, int size) {
        var mgr = PartyManager.get(server);
        PartyManager.Party party = mgr.partyOf(leader.getUUID());
        if (party == null || !party.leaderId.equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.translatable("command.zenkai.party.not_leader"));
            return false;
        }
        int ceiling = CommonConfig.partyMaxSizeCeiling();
        if (size < 1 || size > ceiling) {
            leader.sendSystemMessage(Component.translatable(
                    "command.zenkai.party.maxsize.out_of_range", 1, ceiling));
            return false;
        }
        if (size < party.members.size()) {
            leader.sendSystemMessage(Component.translatable(
                    "command.zenkai.party.maxsize.too_small", party.members.size()));
            return false;
        }
        if (size == party.maxSize) {
            leader.sendSystemMessage(Component.translatable(
                    "command.zenkai.party.maxsize.same", size));
            return false;
        }

        mgr.setMaxSize(party, size);
        broadcast(server, party, Component.translatable(
                "command.zenkai.party.maxsize.done", leader.getGameProfile().getName(), size));
        syncAll(server, party);
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
                "command.zenkai.party.list.header", party.members.size(), party.maxSize)
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
        String debugName = PartyDebug.nameOf(id);   // SOLO PRUEBAS, ver PartyDebug
        if (debugName != null) return debugName;
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
        PacketDistributor.sendToPlayer(sp,
                new PartySyncPacket(true, party.leaderId, party.friendlyFire,
                        party.maxSize, CommonConfig.partyMaxSizeCeiling(), members));
    }

    private static void sendEmptySync(ServerPlayer sp) {
        PacketDistributor.sendToPlayer(sp, new PartySyncPacket(false, null, false, 0, 0, List.of()));
    }
}
