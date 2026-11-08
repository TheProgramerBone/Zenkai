package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerSkills;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.SuperForms;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Rage Forms, fase 0: {@link PlayerSkills#grantWaived} regala un nivel de super_forms SIN cobrar MIND. Sin esto, conceder
 * la forma por un despertar dejaba {@code mindFree} negativo (grant() no mira la MIND). Se comprueba que lo regalado no
 * ocupa concentración, que lo comprado encima sí (su delta de siempre), que el respec no reembolsa lo regalado y que
 * la exención sobrevive a guardar y cargar (viaja con el attachment al cliente por ese mismo canal).
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MindWaivedGameTests {
    private MindWaivedGameTests() {}

    private static final String SKILL = SuperForms.SKILL;

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void waivedLevelCostsNoMind(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerSkills sk = PlayerStatsAttachment.get(sp).skills();
        SkillDef def = SkillDef.get(SKILL);
        helper.assertTrue(def != null, "super_forms no está cargada como SkillDef");

        int before = sk.mindUsed();
        sk.grantWaived(SKILL, 2);
        helper.assertTrue(sk.level(SKILL) == 2, "grantWaived debería dejar el nivel en 2 y es " + sk.level(SKILL));
        helper.assertTrue(sk.mindUsed() == before,
                "Un nivel regalado no debe ocupar MIND: antes=" + before + " ahora=" + sk.mindUsed());

        // Sin la exención el mismo nivel ocuparía mindReqFor(2): comprobamos que la diferencia es real.
        PlayerSkills plain = new PlayerSkills();
        plain.grant(SKILL, 2);
        helper.assertTrue(plain.mindUsed() == def.mindReqFor(2),
                "Control: grant() normal debería ocupar " + def.mindReqFor(2) + " y ocupa " + plain.mindUsed());
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void levelBoughtOnTopStillCharges(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerSkills sk = PlayerStatsAttachment.get(sp).skills();
        SkillDef def = SkillDef.get(SKILL);

        int before = sk.mindUsed();
        sk.grantWaived(SKILL, 2);
        sk.raise(SKILL, def.maxLevel());   // nivel 3 pagado con TP
        int expected = def.mindReqFor(3) - def.mindReqFor(2);
        helper.assertTrue(sk.level(SKILL) == 3, "raise debería llevar a nivel 3 y es " + sk.level(SKILL));
        helper.assertTrue(sk.mindUsed() - before == expected,
                "El nivel 3 comprado debe cobrar solo su delta (" + expected + ") y cobra " + (sk.mindUsed() - before));
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void respecKeepsWaivedAndNoFakeRefund(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerSkills sk = PlayerStatsAttachment.get(sp).skills();
        SkillDef def = SkillDef.get(SKILL);

        sk.grantWaived(SKILL, 2);
        helper.assertTrue(sk.boughtLevels(SKILL) == 0,
                "Lo regalado no cuenta como comprado (reembolsaría TP falsos): boughtLevels=" + sk.boughtLevels(SKILL));
        sk.raise(SKILL, def.maxLevel());
        sk.clearBought();
        helper.assertTrue(sk.level(SKILL) == 2, "El respec debe bajar al nivel regalado (2) y deja " + sk.level(SKILL));
        helper.assertTrue(sk.mindWaivedUpTo(SKILL) == 2, "El respec no debe borrar la exención: " + sk.mindWaivedUpTo(SKILL));

        sk.revoke(SKILL);
        helper.assertTrue(sk.mindWaivedUpTo(SKILL) == 0, "revoke debe borrar la exención: " + sk.mindWaivedUpTo(SKILL));
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void waiverSurvivesSaveLoad(GameTestHelper helper) {
        PlayerSkills a = new PlayerSkills();
        a.grantWaived(SKILL, 3);
        int used = a.mindUsed();

        PlayerSkills b = new PlayerSkills();
        b.load(a.save());
        helper.assertTrue(b.mindWaivedUpTo(SKILL) == 3, "La exención se pierde al guardar/cargar: " + b.mindWaivedUpTo(SKILL));
        helper.assertTrue(b.mindUsed() == used, "mindUsed cambia tras guardar/cargar: " + used + " -> " + b.mindUsed());
        helper.succeed();
    }
}
