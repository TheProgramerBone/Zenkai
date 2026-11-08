package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.event.tick.DownedSystem;
import com.hmc.zenkai.event.tick.TickCtx;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.death.PlayerDownedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Rage Forms, fase 0: {@link PlayerDownedEvent}. Cae UNA vez por caída (el orquestador del tick corta en handleDowned
 * mientras dura el derribo), lleva a los testigos de la party con el mismo radio que la muerte, y no existe en el Otherworld.
 * Los jugadores de prueba no están en la PlayerList: se registran a mano como en {@link RageEventsGameTests}.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerDownedEventGameTests {
    private PlayerDownedEventGameTests() {}

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> byUuid(MinecraftServer server) throws ReflectiveOperationException {
        Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        f.setAccessible(true);
        return (Map<UUID, ServerPlayer>) f.get(server.getPlayerList());
    }

    private static TickCtx ctx(ServerPlayer sp) {
        return new TickCtx(sp, PlayerStatsAttachment.get(sp),
                sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()),
                sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_VISUAL.get()));
    }

    /** handleBodyDepleted termina sincronizando al cliente; el jugador simulado no tiene conexión y eso lanza. Lo que se
     *  comprueba (estado y evento) ocurre antes, así que se tolera ese fallo de red. */
    private static boolean depleted(ServerPlayer sp) {
        try {
            return DownedSystem.handleBodyDepleted(ctx(sp));
        } catch (UnsupportedOperationException noConnection) {
            return true;
        }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void downedFiresOnceWithPartyWitnesses(GameTestHelper helper) throws ReflectiveOperationException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer downed = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer near = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer far = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        downed.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        near.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5);
        far.moveTo(at.getX() + 200.5, at.getY(), at.getZ() + 0.5);

        Map<UUID, ServerPlayer> map = byUuid(server);
        PartyManager pm = PartyManager.get(server);
        PartyManager.Party party = pm.createFor(downed.getUUID());
        party.members.add(downed.getUUID());
        pm.addMember(party, near.getUUID());
        pm.addMember(party, far.getUUID());
        int prevRadius = ServerConfig.PERMADEATH_WITNESS_RADIUS.get();
        List<PlayerDownedEvent> seen = new ArrayList<>();
        Consumer<PlayerDownedEvent> listener = seen::add;
        NeoForge.EVENT_BUS.addListener(PlayerDownedEvent.class, listener);
        try {
            for (ServerPlayer p : List.of(downed, near, far)) map.put(p.getUUID(), p);
            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(24);

            PlayerStatsAttachment att = PlayerStatsAttachment.get(downed);
            att.setInOtherworld(false);
            att.setBody(0);
            // Mismo orden que ZenkaiTickHandlers: handleDowned primero, handleBodyDepleted solo si no está derribado.
            helper.assertTrue(!DownedSystem.handleDowned(ctx(downed)), "Antes de caer no está derribado");
            helper.assertTrue(depleted(downed), "Body 0 debe cortar el tick");
            helper.assertTrue(att.flags().isDowned(), "Debe quedar derribado");
            helper.assertTrue(seen.size() == 1, "Un evento al caer; se vieron " + seen.size());
            helper.assertTrue(seen.get(0).downed() == downed, "Conserva al derribado");
            helper.assertTrue(seen.get(0).downedUntil() == att.flags().getDownedUntil(), "Lleva el fin de la ventana");
            List<ServerPlayer> w = seen.get(0).witnesses();
            helper.assertTrue(w.size() == 1 && w.get(0) == near,
                    "Solo el compañero cercano es testigo; testigos=" + w.size());

            // Ticks siguientes: handleDowned corta y handleBodyDepleted no vuelve a correr -> sin eventos nuevos.
            helper.assertTrue(DownedSystem.handleDowned(ctx(downed)), "Derribado: handleDowned corta el tick");
            helper.assertTrue(seen.size() == 1, "No se repite durante el derribo; se vieron " + seen.size());

            // Sin party: el evento se publica igual, con la lista vacía.
            pm.disband(party);
            seen.clear();
            att.flags().setDowned(false);
            att.setBody(0);
            depleted(downed);
            helper.assertTrue(seen.size() == 1 && seen.get(0).witnesses().isEmpty(),
                    "Sin party: evento con testigos vacíos; eventos=" + seen.size());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(prevRadius);
            for (ServerPlayer p : List.of(downed, near, far)) map.remove(p.getUUID());
            try { pm.disband(party); } catch (RuntimeException ignored) { /* ya disuelta */ }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void noDownedEventInOtherworld(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        List<PlayerDownedEvent> seen = new ArrayList<>();
        Consumer<PlayerDownedEvent> listener = seen::add;
        NeoForge.EVENT_BUS.addListener(PlayerDownedEvent.class, listener);
        try {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.setInOtherworld(true);
            att.setBody(0);
            depleted(sp);
            helper.assertTrue(seen.isEmpty(), "En el Otherworld no hay derribo ni evento; eventos=" + seen.size());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            PlayerStatsAttachment.get(sp).setInOtherworld(false);
        }
        helper.succeed();
    }
}
