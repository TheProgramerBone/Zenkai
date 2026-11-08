package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.EarthVisit;
import com.hmc.zenkai.feature.player.PlayerStateFlags;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Reloj de la visita a la Tierra y su espera entre visitas. Se llama al tick de {@link EarthVisit} a mano: el reloj es
 * «un tick por tick del jugador», así que sin evento (jugador desconectado) no avanza. El fin de la visita
 * ({@link EarthVisit#end}) teletransporta al Otherworld y necesita esa dimensión, por eso aquí solo se prueba hasta el
 * último tick anterior.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EarthVisitGameTests {
    private EarthVisitGameTests() {}

    private static void tick(ServerPlayer sp) {
        // El tick sincroniza con el cliente al final y el canal falso del gametest no lo admite (ver invokeHandler).
        ZenkaiGameTestHelpers.invokeHandler(() -> EarthVisit.onTick(new PlayerTickEvent.Post(sp)));
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void clockCountsOneTickPerPlayerTick(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();
        f.setInOtherworld(true);
        f.setEarthVisitTicksLeft(100L);

        helper.assertTrue(EarthVisit.isOnVisit(sp), "Muerto y con tiempo restante debe contar como visita");
        for (int i = 0; i < 10; i++) tick(sp);
        helper.assertTrue(f.getEarthVisitTicksLeft() == 90L,
                "10 ticks de jugador restan 10; quedan " + f.getEarthVisitTicksLeft());
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void clockIsPausedWithoutPlayerTicks(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();
        f.setInOtherworld(true);
        f.setEarthVisitTicksLeft(500L);

        // Desconectado: el servidor sigue avanzando pero no hay PlayerTickEvent de este jugador.
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(f.getEarthVisitTicksLeft() == 500L,
                    "Sin ticks del jugador el reloj no avanza; vale " + f.getEarthVisitTicksLeft());
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void visitIsDroppedIfPlayerIsNotDead(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();
        f.setInOtherworld(false);                      // revivido por un camino que no cerró la visita
        f.setEarthVisitTicksLeft(300L);

        helper.assertTrue(!EarthVisit.isOnVisit(sp), "Un jugador vivo no está de visita");
        tick(sp);
        helper.assertTrue(f.getEarthVisitTicksLeft() == 0L,
                "Una visita sin estar muerto se cierra; vale " + f.getEarthVisitTicksLeft());
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void cooldownRunsOnlyWithoutVisit(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();
        f.setInOtherworld(true);

        f.setEarthVisitCooldownTicks(50L);
        for (int i = 0; i < 5; i++) tick(sp);
        helper.assertTrue(f.getEarthVisitCooldownTicks() == 45L,
                "Sin visita la espera baja 1 por tick; vale " + f.getEarthVisitCooldownTicks());

        f.setEarthVisitTicksLeft(100L);                // con visita en curso la espera no corre
        for (int i = 0; i < 5; i++) tick(sp);
        helper.assertTrue(f.getEarthVisitCooldownTicks() == 45L,
                "Durante la visita la espera queda quieta; vale " + f.getEarthVisitCooldownTicks());

        f.setEarthVisitTicksLeft(0L);
        f.setEarthVisitCooldownTicks(1L);
        tick(sp);
        tick(sp);
        helper.assertTrue(f.getEarthVisitCooldownTicks() == 0L, "La espera se queda en 0, nunca negativa");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void setterClampsNegatives(GameTestHelper helper) {
        PlayerStateFlags f = new PlayerStateFlags();
        f.setEarthVisitTicksLeft(-5L);
        f.setEarthVisitCooldownTicks(-5L);
        helper.assertTrue(f.getEarthVisitTicksLeft() == 0L && f.getEarthVisitCooldownTicks() == 0L,
                "Los ticks de visita y de espera no bajan de 0");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void remainingLabelUsesDaysThenHours(GameTestHelper helper) {
        String[][] cases = {
                {"168000", "7 d"}, {"24000", "1 d"}, {"24001", "2 d"},
                {"23999", "24 h"}, {"18000", "18 h"}, {"1000", "1 h"}, {"1", "1 h"}};
        for (String[] c : cases) {
            String got = EarthVisit.remainingLabel(Long.parseLong(c[0]));
            helper.assertTrue(got.equals(c[1]), c[0] + " ticks deben decir '" + c[1] + "' y dicen '" + got + "'");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void checkReportsStateInOrder(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();

        // Con la muerte permanente apagada (valor por defecto) manda DISABLED; si está encendida, el orden de las demás.
        EarthVisit.Check base = EarthVisit.check(sp);
        if (base == EarthVisit.Check.DISABLED) {
            helper.succeed();
            return;
        }
        helper.assertTrue(base == EarthVisit.Check.NOT_DEAD, "Vivo: NOT_DEAD; es " + base);
        f.setInOtherworld(true);
        helper.assertTrue(EarthVisit.check(sp) == EarthVisit.Check.OK, "Muerto sin espera: OK");
        f.setEarthVisitCooldownTicks(100L);
        helper.assertTrue(EarthVisit.check(sp) == EarthVisit.Check.COOLDOWN, "Con espera: COOLDOWN");
        f.setEarthVisitTicksLeft(100L);
        helper.assertTrue(EarthVisit.check(sp) == EarthVisit.Check.ALREADY_ON_VISIT, "De visita: ALREADY_ON_VISIT");
        f.setHardcoreDeath(true);
        f.setEarthVisitTicksLeft(0L);
        helper.assertTrue(EarthVisit.check(sp) == EarthVisit.Check.HARDCORE, "Muerte hardcore: HARDCORE");
        helper.succeed();
    }
}
