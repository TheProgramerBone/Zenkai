package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.CharacterReset;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Reencarnación (CharacterReset): lo que se pierde, lo que se conserva y que ningún attachment quede sin clasificar.
 * Solo prueba los datos ({@link CharacterReset#resetData}): el traslado y la red necesitan un cliente real.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CharacterResetGameTests {
    private CharacterResetGameTests() {}

    /** Un attachment nuevo debe decidirse: reiniciarse con el personaje o declararse que no es suyo. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void everyAttachmentIsClassified(GameTestHelper helper) {
        for (var entry : ZenkaiDataAttachments.REGISTER.getEntries()) {
            String id = entry.getId().getPath();
            helper.assertTrue(CharacterReset.RESET.contains(id) || CharacterReset.KEPT.contains(id),
                    "El attachment '" + id + "' no está en CharacterReset.RESET ni en CharacterReset.KEPT: "
                            + "una vida nueva heredaría su estado de la anterior");
        }
        helper.succeed();
    }

    /** Nacer Legendario (0.7.0): una tirada por personaje; la reencarnación la rearma y un personaje anterior a la 0.7.0 no tira. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void legendaryBirthRollsOncePerLife(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setLegendary(false);
        att.flags().setLegendaryRolled(false);
        var rng = net.minecraft.util.RandomSource.create(42L);

        helper.assertTrue(!com.hmc.zenkai.feature.forms.LegendaryBirth.roll(att, rng, false, 0.0) && !att.isLegendary(),
                "Con 0 % nunca nace Legendario");
        helper.assertTrue(att.flags().isLegendaryRolled(), "La tirada queda hecha aunque no salga");
        helper.assertTrue(!com.hmc.zenkai.feature.forms.LegendaryBirth.roll(att, rng, false, 100.0) && !att.isLegendary(),
                "Con la tirada ya hecha no se repite, ni al 100 %");

        CharacterReset.resetData(sp);
        PlayerStatsAttachment fresh = PlayerStatsAttachment.get(sp);
        helper.assertTrue(!fresh.flags().isLegendaryRolled() && !fresh.isLegendary(), "Reencarnar rearma la tirada y quita el estado");
        helper.assertTrue(com.hmc.zenkai.feature.forms.LegendaryBirth.roll(fresh, rng, false, 100.0) && fresh.isLegendary(),
                "Con 100 % la vida nueva nace Legendaria");

        // Partida anterior a la 0.7.0: la clave no existe y el personaje ya estaba creado -> no recibe tirada después.
        PlayerStatsAttachment old = new PlayerStatsAttachment();
        old.setRace(com.hmc.zenkai.feature.Race.SAIYAN);
        old.setRaceChosen(true);
        old.setStyleChosen(true);
        net.minecraft.nbt.CompoundTag tag = old.save();
        tag.getCompound("flags").remove("legendaryRolled");
        PlayerStatsAttachment loaded = new PlayerStatsAttachment();
        loaded.load(tag);
        helper.assertTrue(loaded.flags().isLegendaryRolled(), "Un personaje ya creado antes de la 0.7.0 se da por tirado");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void reincarnationKeepsOnlyWhatTheSoulRemembers(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.addTP(100000);
        helper.assertTrue(att.spendTP(ZenkaiAttributes.STRENGTH, 50), "El test necesita invertir 50 puntos en STR");
        att.addTP(77);                                   // TP suelto: debe perderse
        att.setAlignment(60);
        att.setAlignment(-61);
        att.markMasterMet("kami");
        att.skills().grant("ki_fist", 1);
        att.techniques().unlock(KiTechniqueType.values()[0]);
        att.addTechniqueMastery("TEST_TECH", 50f);

        CharacterReset.resetData(sp);
        PlayerStatsAttachment now = PlayerStatsAttachment.get(sp);

        int attrPct = ServerConfig.PERMADEATH_REINC_ATTRIBUTE_PERCENT.get();
        helper.assertTrue(now.raceStats().investedIn(ZenkaiAttributes.STRENGTH) == 50 * attrPct / 100,
                "Debe conservar el " + attrPct + " % de los puntos invertidos en STR, pero tiene "
                        + now.raceStats().investedIn(ZenkaiAttributes.STRENGTH));
        helper.assertTrue(now.getTP() == 0, "Se pierde todo el TP; tiene " + now.getTP());
        helper.assertTrue(now.raceStats().getTpSpent() == 0, "Los puntos conservados no son reembolsables por TP");
        helper.assertTrue(!now.isRaceChosen(), "Debe volver a elegir raza (pantalla de raza)");
        helper.assertTrue(now.skills().level("ki_fist") == 0, "Las skills se olvidan");
        helper.assertTrue(now.techniques().isUnlocked(KiTechniqueType.values()[0]), "Las técnicas aprendidas se conservan");

        float expectedMastery = 50f * ServerConfig.PERMADEATH_REINC_MASTERY_PERCENT.get() / 100f;
        helper.assertTrue(Math.abs(now.getTechniqueMastery("TEST_TECH") - expectedMastery) < 0.001f,
                "Debe conservar el porcentaje de maestría de técnica");
        helper.assertTrue(now.getAlignment() == -61 * ServerConfig.PERMADEATH_REINC_ALIGNMENT_PERCENT.get() / 100,
                "El alineamiento se acerca a neutro; vale " + now.getAlignment());
        helper.assertTrue(now.getMetMasters().contains("kami"), "Los maestros conocidos se conservan");

        helper.assertTrue(sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).save()
                        .equals(new PlayerFormAttachment().save()),
                "Las formas y su maestría se pierden: la forma debe ser la de un jugador nuevo");
        helper.succeed();
    }

    /** Elegir raza después de reencarnar no debe perder los puntos conservados (los atributos son base + invertidos). */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void keptPointsSurviveChoosingARace(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.addTP(100000);
        att.spendTP(ZenkaiAttributes.STRENGTH, 100);

        CharacterReset.resetData(sp);
        PlayerStatsAttachment now = PlayerStatsAttachment.get(sp);
        int kept = now.raceStats().investedIn(ZenkaiAttributes.STRENGTH);

        now.setRace(com.hmc.zenkai.feature.Race.SAIYAN);
        now.setRaceChosen(true);

        int base = com.hmc.zenkai.feature.RaceStatTable.baseAttributes(com.hmc.zenkai.feature.Race.SAIYAN)
                [ZenkaiAttributes.STRENGTH.ordinal()];
        helper.assertTrue(now.getAttribute(ZenkaiAttributes.STRENGTH) == base + kept,
                "STR debe ser la base de la raza (" + base + ") más los puntos conservados (" + kept + "), pero vale "
                        + now.getAttribute(ZenkaiAttributes.STRENGTH));
        helper.succeed();
    }

    /** Flujo completo en el servidor: reencarnar, elegir raza y estilo con los paquetes reales; lo conservado sobrevive y el flag se apaga. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void fullFlowKeepsStatsAndEndsReincarnation(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        var ctx = ZenkaiGameTestHelpers.fakeCtx(sp);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        // Atributo subido FUERA de spendTP (como hace el comando de administrador): también debe dejar un eco.
        att.setAttribute(ZenkaiAttributes.STRENGTH, 500);
        att.setAlignment(80);

        CharacterReset.resetData(sp);
        PlayerStatsAttachment now = PlayerStatsAttachment.get(sp);
        helper.assertTrue(now.isReincarnating(), "Tras reencarnar el flag debe estar encendido hasta terminar la elección");

        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.feature.stats.ChooseRacePacket.handle(
                new com.hmc.zenkai.feature.stats.ChooseRacePacket(com.hmc.zenkai.feature.Race.SAIYAN), ctx));
        helper.assertTrue(PlayerStatsAttachment.get(sp).isReincarnating(), "Con solo la raza elegida aún no termina");
        ZenkaiGameTestHelpers.invokeHandler(() -> com.hmc.zenkai.network.ChooseStylePacket.handle(
                new com.hmc.zenkai.network.ChooseStylePacket(com.hmc.zenkai.feature.Style.MARTIAL_ARTIST), ctx));

        PlayerStatsAttachment end = PlayerStatsAttachment.get(sp);
        int base = com.hmc.zenkai.feature.RaceStatTable.baseAttributes(com.hmc.zenkai.feature.Race.SAIYAN)
                [ZenkaiAttributes.STRENGTH.ordinal()];
        int oldBase = com.hmc.zenkai.feature.RaceStatTable.baseAttributes(com.hmc.zenkai.feature.Race.HUMAN)
                [ZenkaiAttributes.STRENGTH.ordinal()];
        int expected = base + (500 - oldBase) * ServerConfig.PERMADEATH_REINC_ATTRIBUTE_PERCENT.get() / 100;
        helper.assertTrue(end.getAttribute(ZenkaiAttributes.STRENGTH) == expected,
                "STR final debe ser " + expected + " (base de la raza + el eco de lo que había por encima), pero vale "
                        + end.getAttribute(ZenkaiAttributes.STRENGTH));
        helper.assertTrue(!end.isReincarnating(), "Al confirmar el estilo la reencarnación termina");
        helper.assertTrue(end.isRaceChosen() && end.isStyleChosen(), "Debe quedar con raza y estilo elegidos");
        helper.assertTrue(end.getAlignment() == 80 * ServerConfig.PERMADEATH_REINC_ALIGNMENT_PERCENT.get() / 100,
                "El alineamiento conservado debe sobrevivir a la elección de raza");
        helper.succeed();
    }
}
