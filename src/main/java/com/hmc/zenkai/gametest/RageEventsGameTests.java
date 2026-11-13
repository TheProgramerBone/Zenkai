package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.api.event.PartyMemberDiedEvent;
import com.hmc.zenkai.api.event.PlayerBodyInteractEvent;
import com.hmc.zenkai.feature.player.death.PlayerBodyManager;
import com.hmc.zenkai.registry.ModEntities;
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
 * Eventos para las rage forms (#6): {@link PartyMemberDiedEvent} y {@link PlayerBodyInteractEvent}, con dos jugadores
 * simulados en la misma party. Los jugadores de prueba NO están en la {@code PlayerList}, y el cálculo de testigos los busca
 * ahí por UUID, así que cada test los registra a mano (reflexión sobre {@code playersByUUID}) y los quita al acabar.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RageEventsGameTests {
    private RageEventsGameTests() {}

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> byUuid(MinecraftServer server) throws ReflectiveOperationException {
        Field f = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        f.setAccessible(true);
        return (Map<UUID, ServerPlayer>) f.get(server.getPlayerList());
    }

    private static PlayerBodyEntity bodyOf(GameTestHelper helper, ServerPlayer owner) {
        PlayerBodyEntity b = ModEntities.PLAYER_BODY.get().create(helper.getLevel());
        b.configure(owner.getUUID(), "test-owner", "human", "", "", 1f, helper.getLevel().getGameTime() - 1000);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.getLevel().addFreshEntity(b);
        return b;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void witnessesSeePartyMemberDie(GameTestHelper helper) throws ReflectiveOperationException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer dead = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer near = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer far = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer stranger = ZenkaiGameTestHelpers.raceChosenPlayer(helper);   // sin party: nunca testigo
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        dead.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        near.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5);
        stranger.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5);
        far.moveTo(at.getX() + 200.5, at.getY(), at.getZ() + 0.5);

        Map<UUID, ServerPlayer> map = byUuid(server);
        PartyManager pm = PartyManager.get(server);
        PartyManager.Party party = pm.createFor(dead.getUUID());
        party.members.add(dead.getUUID());
        pm.addMember(party, near.getUUID());
        pm.addMember(party, far.getUUID());
        int prevRadius = ServerConfig.PERMADEATH_WITNESS_RADIUS.get();
        PlayerBodyEntity body = bodyOf(helper, dead);
        List<PartyMemberDiedEvent> seen = new ArrayList<>();
        Consumer<PartyMemberDiedEvent> listener = seen::add;
        NeoForge.EVENT_BUS.addListener(PartyMemberDiedEvent.class, listener);
        try {
            for (ServerPlayer p : List.of(dead, near, far, stranger)) map.put(p.getUUID(), p);

            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(24);
            PlayerBodyManager.registerCreated(dead, body);
            helper.assertTrue(seen.size() == 1, "Un evento con radio 24; se vieron " + seen.size());
            List<ServerPlayer> w = seen.get(0).witnesses();
            helper.assertTrue(w.size() == 1 && w.get(0) == near,
                    "Solo el compañero cercano es testigo (no el lejano, ni el que no es de la party, ni el muerto); testigos=" + w.size());
            helper.assertTrue(seen.get(0).dead() == dead, "Conserva al muerto");

            seen.clear();
            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(0);
            PlayerBodyManager.registerCreated(dead, body);
            helper.assertTrue(seen.isEmpty(), "Con radio 0 no hay testigos ni evento");

            seen.clear();
            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(512);
            PlayerBodyManager.registerCreated(dead, body);
            helper.assertTrue(seen.size() == 1 && seen.get(0).witnesses().size() == 2,
                    "Con radio 512 ven los dos compañeros de la party");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            ServerConfig.PERMADEATH_WITNESS_RADIUS.set(prevRadius);
            for (ServerPlayer p : List.of(dead, near, far, stranger)) map.remove(p.getUUID());
            pm.disband(party);
            body.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void bodyInteractKnowsAllyOwnAndNobody(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer ally = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer stranger = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PartyManager pm = PartyManager.get(server);
        PartyManager.Party party = pm.createFor(owner.getUUID());
        party.members.add(owner.getUUID());
        pm.addMember(party, ally.getUUID());
        PlayerBodyEntity body = bodyOf(helper, owner);
        List<PlayerBodyInteractEvent> seen = new ArrayList<>();
        Consumer<PlayerBodyInteractEvent> listener = seen::add;
        NeoForge.EVENT_BUS.addListener(PlayerBodyInteractEvent.class, listener);
        try {
            PlayerBodyInteractEvent.fire(ally, body, PlayerBodyInteractEvent.Kind.OPEN);
            PlayerBodyInteractEvent.fire(ally, body, PlayerBodyInteractEvent.Kind.CARRY);
            PlayerBodyInteractEvent.fire(ally, body, PlayerBodyInteractEvent.Kind.BURY);
            PlayerBodyInteractEvent.fire(owner, body, PlayerBodyInteractEvent.Kind.OPEN);
            PlayerBodyInteractEvent.fire(stranger, body, PlayerBodyInteractEvent.Kind.DISINTEGRATE);
            PlayerBodyInteractEvent.fire(null, body, PlayerBodyInteractEvent.Kind.DISINTEGRATE);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            pm.disband(party);
            body.discard();
        }
        helper.assertTrue(seen.size() == 6, "Seis eventos; se vieron " + seen.size());
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(seen.get(i).ally() && !seen.get(i).own() && seen.get(i).who() == ally,
                    "Aliado " + seen.get(i).kind() + ": ally=true, own=false");
        }
        helper.assertTrue(seen.get(0).kind() == PlayerBodyInteractEvent.Kind.OPEN
                && seen.get(1).kind() == PlayerBodyInteractEvent.Kind.CARRY
                && seen.get(2).kind() == PlayerBodyInteractEvent.Kind.BURY, "Conservan el tipo (OPEN, CARRY, BURY)");
        helper.assertTrue(seen.get(3).own() && !seen.get(3).ally(), "El dueño: own=true, ally=false");
        helper.assertTrue(!seen.get(4).own() && !seen.get(4).ally(), "Un desconocido: ni propio ni aliado");
        helper.assertTrue(seen.get(5).who() == null && !seen.get(5).own() && !seen.get(5).ally(),
                "Sin jugador (ki de una entidad): who=null, ni propio ni aliado");
        helper.succeed();
    }
}
