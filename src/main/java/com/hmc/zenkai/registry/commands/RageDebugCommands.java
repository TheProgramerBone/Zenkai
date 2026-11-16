package com.hmc.zenkai.registry.commands;

import com.hmc.zenkai.api.event.CriticalSurvivalEvent;
import com.hmc.zenkai.api.event.PlayerDownedEvent;
import com.hmc.zenkai.api.event.PlayerRescuedEvent;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.event.CombatZenkaiHooks;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.death.PlayerBodyManager;
import com.hmc.zenkai.feature.rage.RageChronicle;
import com.hmc.zenkai.feature.rage.RageListeners;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * SOLO PARA PRUEBAS (se borra después): {@code /zenkai debug rage <escena>} monta la situación REAL de cada hecho de Rage Forms para
 * un jugador en solitario. Crea un aliado falso ({@link FakePlayerFactory}) en tu party, a 2 bloques, y recorre el DETECTOR REAL
 * ({@link RageListeners}) con el evento que ocurriría de verdad: party, testigos, radio, quién mata, enfriamientos y despertar. Al acabar
 * lo saca de la party y del registro, sin dejar rastro en los datos guardados. Cada escena dice si el detector llegó a anotar algo.
 */
public final class RageDebugCommands {
    private RageDebugCommands() {}

    private static final UUID ALLY_ID = UUID.fromString("00000000-0000-4000-8000-0000000a11e5");

    private enum Scene { ALLY_DEATH, ALLY_DIES_WHILE_DOWNED, ALLY_DOWNED, ALLY_RESCUED, CRITICAL_SURVIVAL }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rage");
        // Un aliado de tu party muere a manos de un zombi, a 2 bloques.
        root.then(Commands.literal("ally_death").executes(c -> run(c, Scene.ALLY_DEATH)));
        // Estás derribado (ventana real de 5 s) y tu aliado muere: el despertar tiene que levantarte.
        root.then(Commands.literal("ally_dies_while_downed").executes(c -> run(c, Scene.ALLY_DIES_WHILE_DOWNED)));
        // Tu aliado cae derribado a 2 bloques.
        root.then(Commands.literal("ally_downed").executes(c -> run(c, Scene.ALLY_DOWNED)));
        // Tu aliado está derribado y tú lo revives con la mano vacía dentro de la ventana.
        root.then(Commands.literal("ally_rescued").executes(c -> run(c, Scene.ALLY_RESCUED)));
        // Tu aliado está derribado y tú sobrevives al borde (bajar del 20 % y volver al 60 %).
        root.then(Commands.literal("critical_survival").executes(c -> run(c, Scene.CRITICAL_SURVIVAL)));
        // Un lobo tuyo muere: con «env» por lava (sin causante, no cuenta); sin él por un zombi (cuenta).
        root.then(Commands.literal("pet")
                .executes(c -> pet(c, false))
                .then(Commands.literal("env").executes(c -> pet(c, true))));
        root.then(Commands.literal("reset").executes(RageDebugCommands::reset));
        root.then(Commands.literal("status").executes(RageDebugCommands::status));
        return root;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Scene scene) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        if (!(me.level() instanceof ServerLevel sl)) return 0;
        var server = sl.getServer();
        ServerPlayer ally = FakePlayerFactory.get(sl, new GameProfile(ALLY_ID, "Aliado-Prueba"));
        Vec3 look = me.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 at = me.position().add(flat.scale(2.0));
        ally.moveTo(at.x, at.y, at.z, me.getYRot() + 180f, 0f);
        PlayerStatsAttachment allyAtt = PlayerStatsAttachment.get(ally);
        allyAtt.setRace(Race.HUMAN);
        allyAtt.setRaceChosen(true);

        PartyManager pm = PartyManager.get(server);
        PartyManager.Party party = pm.partyOf(me.getUUID());
        boolean created = false;
        if (party == null) {
            party = pm.createFor(me.getUUID());
            party.members.add(me.getUUID());
            created = true;
        }
        pm.addMember(party, ally.getUUID());
        Map<UUID, ServerPlayer> online;
        try {
            online = playersByUuid(server);
        } catch (ReflectiveOperationException ex) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] No pude registrar al aliado de prueba: " + ex));
            return 0;
        }
        online.put(ally.getUUID(), ally);

        int before = RageChronicle.get(me).entries().size();
        PlayerStatsAttachment meAtt = PlayerStatsAttachment.get(me);
        long now = sl.getGameTime();
        try {
            switch (scene) {
                case ALLY_DEATH -> kill(sl, ally);
                case ALLY_DIES_WHILE_DOWNED -> {
                    meAtt.setBody(0);
                    meAtt.flags().setDowned(true);
                    meAtt.flags().setDownedUntil(now + CombatZenkaiHooks.DOWNED_TICKS);
                    kill(sl, ally);
                }
                case ALLY_DOWNED -> {
                    allyAtt.flags().setDowned(true);
                    allyAtt.flags().setDownedUntil(now + CombatZenkaiHooks.DOWNED_TICKS);
                    RageListeners.onDowned(new PlayerDownedEvent(ally, PlayerBodyManager.partyWitnesses(ally), now + CombatZenkaiHooks.DOWNED_TICKS));
                }
                case ALLY_RESCUED -> {
                    allyAtt.flags().setDowned(true);
                    allyAtt.flags().setDownedUntil(now + CombatZenkaiHooks.DOWNED_TICKS);
                    RageListeners.onRescued(new PlayerRescuedEvent(me, ally, PlayerRescuedEvent.Method.REVIVE, CombatZenkaiHooks.DOWNED_TICKS - 20));
                }
                case CRITICAL_SURVIVAL -> {
                    allyAtt.flags().setDowned(true);
                    allyAtt.flags().setDownedUntil(now + CombatZenkaiHooks.DOWNED_TICKS);
                    RageListeners.onCriticalSurvival(new CriticalSurvivalEvent(me, true));
                }
            }
        } finally {
            allyAtt.flags().setDowned(false);
            allyAtt.flags().setDownedUntil(0L);
            online.remove(ally.getUUID());
            pm.removeMember(party, ally.getUUID());
            if (created) pm.disband(party);
        }
        report(ctx, me, before, scene.name().toLowerCase(java.util.Locale.ROOT));
        return 1;
    }

    /** Muerte real del aliado a manos de un zombi (que no es nadie de tu party): el mismo oyente que el juego. */
    private static void kill(ServerLevel sl, ServerPlayer ally) {
        DamageSource src = ally.damageSources().mobAttack(EntityType.ZOMBIE.create(sl));
        RageListeners.onDeath(new LivingDeathEvent(ally, src));
    }

    private static int pet(CommandContext<CommandSourceStack> ctx, boolean env) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        Wolf wolf = EntityType.WOLF.create(sl);
        if (wolf == null) return 0;
        Vec3 look = sp.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 pos = sp.position().add(flat.scale(2.0));
        wolf.moveTo(pos.x, pos.y, pos.z, sp.getYRot() + 180f, 0f);
        wolf.tame(sp);
        sl.addFreshEntity(wolf);
        int before = RageChronicle.get(sp).entries().size();
        // Sin causante (lava) NO cuenta; con un enemigo como causante sí. Va por el oyente real (LivingDeathEvent).
        wolf.die(env ? wolf.damageSources().lava() : wolf.damageSources().mobAttack(EntityType.ZOMBIE.create(sl)));
        report(ctx, sp, before, env ? "pet (por lava: no debe contar)" : "pet (por un zombi: debe contar)");
        return 1;
    }

    private static void report(CommandContext<CommandSourceStack> ctx, ServerPlayer me, int before, String name) {
        var list = RageChronicle.get(me).entries();
        String msg = list.size() > before
                ? "→ " + fmt(list.get(list.size() - 1))
                : "→ el detector NO anotó nada (condición no cumplida o hecho repetido)";
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] " + name + " " + msg), false);
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(ctx.getSource().getPlayerOrException());
        att.flags().setRageAllyEventTick(-1L);
        att.flags().setRageMinorEventTick(-1L);
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Enfriamientos de furia borrados (aliados y hechos menores)"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        long now = sp.level().getGameTime();
        String ally = left(now, att.flags().getRageAllyEventTick(), ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.get());
        String minor = left(now, att.flags().getRageMinorEventTick(), ServerConfig.RAGE_MINOR_EVENT_COOLDOWN.get());
        var list = RageChronicle.get(sp).entries();
        String last = list.isEmpty() ? "(crónica vacía)" : fmt(list.get(list.size() - 1));
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Enfriamiento de aliados: " + ally
                + " | de hechos menores: " + minor + "\n  Último hecho: " + last), false);
        return 1;
    }

    private static String left(long now, long last, int cooldown) {
        if (last < 0) return "libre";
        long rest = cooldown - (now - last);
        return rest <= 0 ? "libre" : "faltan " + rest / 20 + " s";
    }

    private static String fmt(RageChronicle.Entry e) {
        return e.trigger() + " → " + e.result() + (e.targetForm().isEmpty() ? "" : " " + e.targetForm()
                + String.format(" (maestría %.1f→%.1f)", e.masteryBefore(), e.masteryAfter()))
                + (e.ignoredByCooldown() ? " [ignorado por enfriamiento]" : "");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playersByUuid(net.minecraft.server.MinecraftServer server) throws ReflectiveOperationException {
        Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        f.setAccessible(true);
        return (Map<UUID, ServerPlayer>) f.get(server.getPlayerList());
    }
}
