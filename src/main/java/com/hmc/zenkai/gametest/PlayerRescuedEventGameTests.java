package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.SenzuBean;
import com.hmc.zenkai.event.CombatZenkaiHooks;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.api.event.PlayerRescuedEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Rage Forms, fase 0: {@link PlayerRescuedEvent}, el rescate de un derribado dentro de su ventana. Se prueban las dos
 * vías reales (mano vacía y senzu sobre otro jugador) y lo que NO cuenta: levantar a quien no está derribado, rescatarse a
 * uno mismo y llegar con la ventana ya agotada. Los jugadores simulados no tienen conexión: el sync final lanza y se tolera
 * (el evento se publica antes de curar).
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerRescuedEventGameTests {
    private PlayerRescuedEventGameTests() {}

    private static void down(ServerPlayer sp, long ticksLeft) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setBody(0);
        att.flags().setDowned(true);
        att.flags().setDownedUntil(sp.level().getGameTime() + ticksLeft);
    }

    private static void quiet(Runnable r) {
        try { r.run(); } catch (UnsupportedOperationException noConnection) { /* sync sin conexión */ }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void emptyHandReviveFiresRescue(GameTestHelper helper) {
        ServerPlayer healer = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer downed = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        List<PlayerRescuedEvent> seen = new ArrayList<>();
        Consumer<PlayerRescuedEvent> l = seen::add;
        NeoForge.EVENT_BUS.addListener(PlayerRescuedEvent.class, l);
        try {
            healer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            down(downed, 60);
            quiet(() -> CombatZenkaiHooks.onDownedAllyInteract(
                    new PlayerInteractEvent.EntityInteract(healer, InteractionHand.MAIN_HAND, downed)));
            helper.assertTrue(seen.size() == 1, "Revivir con la mano vacía publica 1 rescate; se vieron " + seen.size());
            PlayerRescuedEvent e = seen.get(0);
            helper.assertTrue(e.rescuer() == healer && e.rescued() == downed
                    && e.method() == PlayerRescuedEvent.Method.REVIVE, "Quién, a quién y cómo");
            helper.assertTrue(e.ticksLeft() > 0 && e.ticksLeft() <= 60, "Tiempo restante dentro de la ventana: " + e.ticksLeft());

            // Quien no está derribado no es un rescate.
            seen.clear();
            PlayerStatsAttachment.get(downed).flags().setDowned(false);
            quiet(() -> CombatZenkaiHooks.onDownedAllyInteract(
                    new PlayerInteractEvent.EntityInteract(healer, InteractionHand.MAIN_HAND, downed)));
            helper.assertTrue(seen.isEmpty(), "Levantar a quien no está derribado no cuenta; eventos=" + seen.size());
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void senzuOnDownedPlayerFiresRescue(GameTestHelper helper) {
        ServerPlayer healer = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer downed = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        List<PlayerRescuedEvent> seen = new ArrayList<>();
        Consumer<PlayerRescuedEvent> l = seen::add;
        NeoForge.EVENT_BUS.addListener(PlayerRescuedEvent.class, l);
        try {
            // El ítem REGISTRADO: crear uno nuevo con el registro congelado falla.
            SenzuBean bean = (SenzuBean) com.hmc.zenkai.registry.ModItems.SENZU_BEAN.get();
            ItemStack stack = new ItemStack(bean);
            down(downed, 80);
            quiet(() -> bean.interactLivingEntity(stack, healer, downed, InteractionHand.MAIN_HAND));
            helper.assertTrue(seen.size() == 1 && seen.get(0).method() == PlayerRescuedEvent.Method.SENZU
                            && seen.get(0).rescuer() == healer && seen.get(0).rescued() == downed,
                    "Senzu sobre un derribado publica 1 rescate SENZU; eventos=" + seen.size());

            // Senzu a alguien herido pero NO derribado: cura, pero no es un rescate.
            seen.clear();
            PlayerStatsAttachment att = PlayerStatsAttachment.get(downed);
            att.flags().setDowned(false);
            att.setBody(1);
            quiet(() -> bean.interactLivingEntity(stack, healer, downed, InteractionHand.MAIN_HAND));
            helper.assertTrue(seen.isEmpty(), "Curar a un herido no derribado no es un rescate; eventos=" + seen.size());
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void selfAndExpiredWindowDoNotCount(GameTestHelper helper) {
        ServerPlayer a = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ServerPlayer b = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        List<PlayerRescuedEvent> seen = new ArrayList<>();
        Consumer<PlayerRescuedEvent> l = seen::add;
        NeoForge.EVENT_BUS.addListener(PlayerRescuedEvent.class, l);
        try {
            down(a, 60);
            helper.assertTrue(!PlayerRescuedEvent.fire(a, a, PlayerRescuedEvent.Method.SENZU), "Uno mismo no se rescata");
            down(b, 0);   // ventana ya agotada
            helper.assertTrue(!PlayerRescuedEvent.fire(a, b, PlayerRescuedEvent.Method.REVIVE), "Sin ventana no hay rescate a tiempo");
            helper.assertTrue(!PlayerRescuedEvent.fire(null, b, PlayerRescuedEvent.Method.REVIVE), "Sin rescatador no hay evento");
            helper.assertTrue(seen.isEmpty(), "Ninguno de los casos publica; eventos=" + seen.size());
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
        helper.succeed();
    }
}
