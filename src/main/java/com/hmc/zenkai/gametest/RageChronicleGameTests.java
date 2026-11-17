package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.CharacterReset;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.rage.RageChronicle;
import com.hmc.zenkai.feature.rage.RageIntensity;
import com.hmc.zenkai.feature.rage.RageMigration;
import com.hmc.zenkai.feature.rage.RageResult;
import com.hmc.zenkai.feature.rage.RageSystem;
import com.hmc.zenkai.feature.rage.RageTrigger;
import com.hmc.zenkai.feature.skills.SuperForms;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Rage Forms, fase 2: la crónica (anillo, persistencia, qué anota RageSystem), la primera transformación de cada forma
 * ({@code long_cinematic_seen}), la migración de partidas anteriores y que la crónica sobreviva a la reencarnación.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RageChronicleGameTests {
    private RageChronicleGameTests() {}

    private static ServerPlayer player(GameTestHelper helper, Race race) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setRace(race);
        att.setInOtherworld(false);
        att.skills().grantAdmin(SuperForms.SKILL, 1);
        return sp;
    }

    private static RageChronicle.Entry entry(String id, long t) {
        return new RageChronicle.Entry(id, "ALLY_DEATH", "zenkai:ssj1", "AWAKENING", "rage", "Krillin", t, 0f, 5f, true, false, false);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void ringKeepsNewestAndSurvivesSaveLoad(GameTestHelper helper) {
        RageChronicle c = new RageChronicle();
        for (int i = 0; i < RageChronicle.CAP + 5; i++) c.add(entry("e" + i, i));
        helper.assertTrue(c.size() == RageChronicle.CAP, "El anillo no pasa de " + RageChronicle.CAP + "; tiene " + c.size());
        helper.assertTrue(c.entries().get(0).eventId().equals("e5"), "Cae la más antigua; la primera es " + c.entries().get(0).eventId());

        RageChronicle back = new RageChronicle();
        back.load(c.save());
        helper.assertTrue(back.size() == c.size() && back.entries().get(0).equals(c.entries().get(0)),
                "Guardar y cargar conserva las entradas y su contenido");
        helper.assertTrue(back.entries().get(back.size() - 1).related().equals("Krillin"), "Conserva los campos de texto");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void awakeningWritesAFullEntryAndMarksTheForm(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        RageResult r = RageSystem.evaluate(sp, RageTrigger.ALLY_DEATH, RageIntensity.HIGH, "muerto", "Krillin");
        helper.assertTrue(r == RageResult.AWAKENING, "Despierta SSJ1; r=" + r);
        List<RageChronicle.Entry> list = RageChronicle.get(sp).entries();
        helper.assertTrue(list.size() == 1, "Una entrada (la de FormChangedEvent no se duplica con la vía Rage); hay " + list.size());
        RageChronicle.Entry e = list.get(0);
        helper.assertTrue(e.trigger().equals("ALLY_DEATH") && e.result().equals("AWAKENING") && e.acquisition().equals("rage")
                        && e.targetForm().equals("zenkai:ssj1") && e.related().equals("Krillin"),
                "Acontecimiento, resultado, vía, forma y aliado: " + e);
        helper.assertTrue(e.cinematicPlayed(), "Primera transformación de SSJ1: secuencia larga");
        helper.assertTrue(PlayerStatsAttachment.get(sp).flags().hasSeenLongCinematic("zenkai:ssj1"), "La forma queda como vista");
        helper.assertTrue(e.worldTime() == sp.level().getGameTime(), "Lleva el tiempo del mundo");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void progressRecordsMasteryBeforeAndAfter(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        int prev = ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.get();
        ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(0);
        try {
            RageSystem.evaluate(sp, RageTrigger.ALLY_DEATH, RageIntensity.HIGH, "a", null);
            RageResult r = RageSystem.evaluate(sp, RageTrigger.ALLY_DEATH, RageIntensity.HIGH, "b", null);
            helper.assertTrue(r == RageResult.PROGRESS, "Segundo hecho: PROGRESS; r=" + r);
            List<RageChronicle.Entry> list = RageChronicle.get(sp).entries();
            RageChronicle.Entry e = list.get(list.size() - 1);
            helper.assertTrue(e.result().equals("PROGRESS") && e.masteryAfter() > e.masteryBefore() && e.masteryBefore() >= 0f,
                    "Maestría antes y después: " + e.masteryBefore() + " -> " + e.masteryAfter());
        } finally {
            ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.set(prev);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void cooldownAndUnrewardedEventsAreStillRecorded(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        RageSystem.evaluate(sp, RageTrigger.ALLY_DEATH, RageIntensity.HIGH, "a", null);
        RageResult r = RageSystem.evaluate(sp, RageTrigger.ALLY_DEATH, RageIntensity.HIGH, "b", null);
        List<RageChronicle.Entry> list = RageChronicle.get(sp).entries();
        RageChronicle.Entry e = list.get(list.size() - 1);
        helper.assertTrue(r == RageResult.RECORDED && e.result().equals("RECORDED") && e.ignoredByCooldown(),
                "Dentro del enfriamiento: RECORDED con la marca de ignorado; " + e);

        int before = list.size();
        RageSystem.evaluate(sp, RageTrigger.ALLY_DEATH, RageIntensity.HIGH, "b", null);   // mismo hecho
        helper.assertTrue(RageChronicle.get(sp).size() == before, "Un hecho repetido no vuelve a anotarse");

        ServerPlayer arc = player(helper, Race.ARCOSIAN);
        RageSystem.evaluate(arc, RageTrigger.ALLY_DEATH, RageIntensity.HIGH, "c", "Frieza");
        RageChronicle.Entry ae = RageChronicle.get(arc).entries().get(0);
        helper.assertTrue(ae.result().equals("RECORDED") && !ae.ignoredByCooldown() && ae.related().equals("Frieza"),
                "Sin despertar para su raza: solo historia; " + ae);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void firstTransformationByAnyRouteIsRecordedOnce(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.skills().grantAdmin(SuperForms.SKILL, 2);   // SSJ1 por compra, no por Rage
        PlayerFormAttachment forms = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());

        forms.setFormId(sp, FormIds.SSJ1);
        List<RageChronicle.Entry> list = RageChronicle.get(sp).entries();
        helper.assertTrue(list.size() == 1 && list.get(0).trigger().equals(RageChronicle.FORM_FIRST)
                        && list.get(0).acquisition().equals("other") && list.get(0).targetForm().equals("zenkai:ssj1"),
                "La primera vez por otra vía deja una entrada FORM_FIRST: " + list);
        forms.setFormId(sp, FormIds.BASE);
        forms.setFormId(sp, FormIds.SSJ1);
        helper.assertTrue(RageChronicle.get(sp).size() == 1, "Las siguientes no (secuencia corta); hay " + RageChronicle.get(sp).size());
        forms.setFormId(sp, FormIds.BASE);
        helper.assertTrue(RageChronicle.get(sp).size() == 1, "Volver a base no cuenta");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void migrationMarksOwnedFormsWithoutGrantingAnything(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.skills().grantAdmin(SuperForms.SKILL, 2);   // posee SSJ1 de antes
        att.flags().setRageMigrated(false);             // partida anterior a Rage Forms
        float mastery = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormMastery(FormIds.SSJ1);

        helper.assertTrue(RageMigration.migrate(sp), "Migra la primera vez");
        helper.assertTrue(att.flags().hasSeenLongCinematic("zenkai:ssj1"), "SSJ1 (ya poseída) cuenta como vista");
        helper.assertTrue(!att.flags().hasSeenLongCinematic("zenkai:ssj2"), "SSJ2 (no poseída) conserva su primera secuencia larga");
        helper.assertTrue(!SuperForms.unlocked(sp, FormIds.SSJ2), "No concede desbloqueos");
        helper.assertTrue(sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormMastery(FormIds.SSJ1) == mastery, "Ni maestría");
        helper.assertTrue(RageChronicle.get(sp).size() == 0, "Ni deja entradas en la crónica");
        helper.assertTrue(!RageMigration.migrate(sp), "No se repite");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void oldSaveWithoutTheKeyReadsAsPendingMigration(GameTestHelper helper) {
        var flags = new com.hmc.zenkai.feature.player.PlayerStateFlags();
        helper.assertTrue(flags.isRageMigrated(), "Una partida nueva nace ya migrada");
        var old = new com.hmc.zenkai.feature.player.PlayerStateFlags();
        old.load(new net.minecraft.nbt.CompoundTag());   // sin la clave: como un guardado anterior
        helper.assertTrue(!old.isRageMigrated(), "Un guardado sin la clave queda pendiente de migrar");
        flags.markLongCinematicSeen("zenkai:ssj1");
        var back = new com.hmc.zenkai.feature.player.PlayerStateFlags();
        back.load(flags.save());
        helper.assertTrue(back.isRageMigrated() && back.hasSeenLongCinematic("zenkai:ssj1"), "Guardar y cargar conserva ambas cosas");
        helper.succeed();
    }

    /** Crónica «interrumpida» (0.7.0): solo se marca la entrada reciente con secuencia larga; la historia vieja no se reescribe. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void interruptedMarksOnlyTheRecentCinematicEntry(GameTestHelper helper) {
        RageChronicle c = new RageChronicle();
        c.add(entry("vieja", 100));
        c.add(entry("nueva", 1000));
        helper.assertTrue(!c.markRecentInterrupted(5000, 166), "Fuera de la ventana no marca nada");
        helper.assertTrue(c.entries().stream().noneMatch(RageChronicle.Entry::interrupted), "Y deja las entradas intactas");
        helper.assertTrue(c.markRecentInterrupted(1100, 166), "Dentro de la ventana marca la más reciente");
        helper.assertTrue(c.entries().get(1).interrupted() && !c.entries().get(0).interrupted(),
                "Solo la reciente queda interrumpida; la anterior no");
        helper.assertTrue(c.entries().get(1).eventId().equals("nueva") && c.size() == 2, "Sin perder ni reordenar entradas");
        helper.assertTrue(!c.markRecentInterrupted(1100, 166), "Una entrada ya marcada no se vuelve a marcar");
        RageChronicle back = new RageChronicle();
        back.load(c.save());
        helper.assertTrue(back.entries().get(1).interrupted(), "La marca sobrevive a guardar y cargar");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void chronicleSurvivesReincarnationButSeenFormsDoNot(GameTestHelper helper) {
        ServerPlayer sp = player(helper, Race.SAIYAN);
        RageSystem.evaluate(sp, RageTrigger.ALLY_DEATH, RageIntensity.HIGH, "muerto", "Krillin");
        helper.assertTrue(PlayerStatsAttachment.get(sp).flags().hasSeenLongCinematic("zenkai:ssj1"), "Antes: SSJ1 vista");
        int entries = RageChronicle.get(sp).size();

        CharacterReset.resetData(sp);

        helper.assertTrue(RageChronicle.get(sp).size() == entries && entries > 0,
                "La crónica es historia: sobrevive a la reencarnación (" + RageChronicle.get(sp).size() + ")");
        helper.assertTrue(!PlayerStatsAttachment.get(sp).flags().hasSeenLongCinematic("zenkai:ssj1"),
                "El personaje nuevo vuelve a tener su primera secuencia larga");
        helper.succeed();
    }
}
