package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.tick.DownedSystem;
import com.hmc.zenkai.event.tick.TickCtx;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.rage.CriticalSurvival;
import com.hmc.zenkai.api.event.CriticalSurvivalEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Rage Forms, fase 0: {@link CriticalSurvival}. Arma al bajar del 20 % (o al caer derribado), dispara al volver al 60 %, una
 * sola vez por ciclo, en las cinco razas y sin depender del zenkai. No cuenta en el Otherworld ni bajar sin recuperarse.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CriticalSurvivalGameTests {
    private CriticalSurvivalGameTests() {}

    private static void bodyAt(PlayerStatsAttachment att, double frac) {
        att.setBody((int) Math.round(att.getBodyMax() * frac));
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void firesOncePerCycleInEveryRace(GameTestHelper helper) {
        List<CriticalSurvivalEvent> seen = new ArrayList<>();
        Consumer<CriticalSurvivalEvent> l = seen::add;
        NeoForge.EVENT_BUS.addListener(CriticalSurvivalEvent.class, l);
        try {
            for (Race race : Race.values()) {
                ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
                PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                att.setRace(race);
                seen.clear();
                String who = "raza " + race + ": ";

                bodyAt(att, 1.0);
                CriticalSurvival.tick(sp, att);
                bodyAt(att, 0.15);
                CriticalSurvival.tick(sp, att);
                helper.assertTrue(seen.isEmpty(), who + "bajar no dispara");
                bodyAt(att, 0.5);
                CriticalSurvival.tick(sp, att);
                helper.assertTrue(seen.isEmpty(), who + "al 50 % todavía no (umbral 60 %)");
                bodyAt(att, 0.65);
                CriticalSurvival.tick(sp, att);
                helper.assertTrue(seen.size() == 1 && seen.get(0).survivor() == sp && !seen.get(0).wasDowned(),
                        who + "al 65 % dispara una vez, sin derribo; eventos=" + seen.size());
                CriticalSurvival.tick(sp, att);
                bodyAt(att, 0.9);
                CriticalSurvival.tick(sp, att);
                helper.assertTrue(seen.size() == 1, who + "no se repite sin un nuevo ciclo; eventos=" + seen.size());
                CriticalSurvival.forget(sp.getUUID());
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void downedThenRescuedCountsAsDowned(GameTestHelper helper) {
        List<CriticalSurvivalEvent> seen = new ArrayList<>();
        Consumer<CriticalSurvivalEvent> l = seen::add;
        NeoForge.EVENT_BUS.addListener(CriticalSurvivalEvent.class, l);
        try {
            ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.setInOtherworld(false);
            att.setBody(0);
            try {
                DownedSystem.handleBodyDepleted(new TickCtx(sp, att,
                        sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_FORM.get()),
                        sp.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.PLAYER_VISUAL.get())));
            } catch (UnsupportedOperationException noConnection) { /* sync sin conexión */ }
            helper.assertTrue(att.flags().isDowned(), "Debe quedar derribado");

            // Lo levantan con un body un pelo por encima del 20 % (el redondeo del revive puede pasarse del umbral).
            bodyAt(att, 0.23);
            CriticalSurvival.tick(sp, att);
            helper.assertTrue(seen.isEmpty(), "Recién levantado no dispara");
            bodyAt(att, 0.7);
            CriticalSurvival.tick(sp, att);
            helper.assertTrue(seen.size() == 1 && seen.get(0).wasDowned(),
                    "Derribado y recuperado: 1 evento con wasDowned; eventos=" + seen.size());
            att.flags().setDowned(false);
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void otherworldDoesNotCount(GameTestHelper helper) {
        List<CriticalSurvivalEvent> seen = new ArrayList<>();
        Consumer<CriticalSurvivalEvent> l = seen::add;
        NeoForge.EVENT_BUS.addListener(CriticalSurvivalEvent.class, l);
        try {
            ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.setInOtherworld(true);
            bodyAt(att, 0.1);
            CriticalSurvival.tick(sp, att);
            bodyAt(att, 1.0);
            CriticalSurvival.tick(sp, att);
            helper.assertTrue(seen.isEmpty(), "En el Otherworld no hay supervivencia que contar; eventos=" + seen.size());
            att.setInOtherworld(false);
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
        helper.succeed();
    }
}
