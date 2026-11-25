package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerSkills;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skilltree.FormAccess;
import com.hmc.zenkai.feature.skilltree.SkillTree;
import net.minecraft.resources.ResourceLocation;
import com.hmc.zenkai.feature.Race;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Rage Forms, fase 0: {@link PlayerSkills#grantWaived} regala un nivel de una skill (aquí ki_control) SIN cobrar MIND, y desde la 0.7.0 el
 * equivalente por nodo ({@code SkillTree.grantNode(..., waived)}) para los peldaños de la cadena mortal. Sin esto, conceder
 * la forma por un despertar dejaba {@code mindFree} negativo (grant() no mira la MIND). Se comprueba que lo regalado no
 * ocupa concentración, que lo comprado encima sí (su delta de siempre), que el respec no reembolsa lo regalado y que
 * la exención sobrevive a guardar y cargar (viaja con el attachment al cliente por ese mismo canal).
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MindWaivedGameTests {
    private MindWaivedGameTests() {}

    private static final String SKILL = SkillEffects.KI_CONTROL;

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void waivedLevelCostsNoMind(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerSkills sk = PlayerStatsAttachment.get(sp).skills();
        SkillDef def = SkillDef.get(SKILL);
        helper.assertTrue(def != null, "ki_control no está cargada como SkillDef");

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

    // ── Nodos de forma (0.7.0): lo mismo, por nodo ───────────────────────────

    private static final ResourceLocation SSJ1 = ResourceLocation.fromNamespaceAndPath("zenkai", "ssj1");
    private static final ResourceLocation SSJ2 = ResourceLocation.fromNamespaceAndPath("zenkai", "ssj2");

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void waivedNodeCostsNoMindButBoughtOneDoes(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setRace(Race.SAIYAN);
        int before = com.hmc.zenkai.feature.player.MindBudget.used(att);

        SkillTree.grantNode(att, SSJ1, true);
        helper.assertTrue(att.skillTree().owns(SSJ1), "grantNode debe conceder el nodo");
        helper.assertTrue(com.hmc.zenkai.feature.player.MindBudget.used(att) == before,
                "Un nodo regalado no debe ocupar MIND: antes=" + before + " ahora=" + com.hmc.zenkai.feature.player.MindBudget.used(att));

        SkillTree.grantNode(att, SSJ2, false);
        int expected = com.hmc.zenkai.feature.skilltree.SkillTreeData.get(SSJ2).mindReqFor(1);
        helper.assertTrue(expected > 0, "ssj2 debe declarar su mind_req en zenkai_skill_tree/ssj2.json");
        helper.assertTrue(com.hmc.zenkai.feature.player.MindBudget.used(att) - before == expected,
                "Un nodo normal cobra su mind_req (" + expected + ") y cobra " + (com.hmc.zenkai.feature.player.MindBudget.used(att) - before));
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void nodeWaiverSurvivesSaveLoad(GameTestHelper helper) {
        com.hmc.zenkai.feature.player.PlayerSkillTree a = new com.hmc.zenkai.feature.player.PlayerSkillTree();
        a.setOwned(SSJ1, 1);
        a.setMindWaived(SSJ1, true);
        com.hmc.zenkai.feature.player.PlayerSkillTree b = new com.hmc.zenkai.feature.player.PlayerSkillTree();
        b.load(a.save());
        helper.assertTrue(b.isMindWaived(SSJ1), "La exención del nodo se pierde al guardar/cargar");
        a.setOwned(SSJ1, 0);
        helper.assertTrue(!a.isMindWaived(SSJ1), "Quitar el nodo debe quitar su exención");
        helper.succeed();
    }

    /** Partida v4 con super_forms nivel 3 (exento hasta 2): la v5 da ssj1 y ssj2, solo ssj1 exento, y borra la skill. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void migrationV5TurnsLevelIntoNodes(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setRace(Race.SAIYAN);
        att.skillTree().clear();
        att.skills().grantWaived("super_forms", 2);
        att.skills().grantAdmin("super_forms", 3);
        att.skillTree().setVersion(4);

        com.hmc.zenkai.feature.skilltree.SkillTreeMigration.migrate(att);

        helper.assertTrue(att.skillTree().owns(SSJ1) && att.skillTree().owns(SSJ2), "El nivel 3 debe conceder ssj1 y ssj2");
        helper.assertTrue(!att.skillTree().owns(ResourceLocation.fromNamespaceAndPath("zenkai", "ssj3")), "No debe conceder de más");
        helper.assertTrue(att.skillTree().isMindWaived(SSJ1) && !att.skillTree().isMindWaived(SSJ2),
                "La exención (hasta nivel 2) cubre solo ssj1");
        helper.assertTrue(att.skills().level("super_forms") == 0, "La skill super_forms debe desaparecer");
        helper.assertTrue(att.skillTree().version() == com.hmc.zenkai.feature.player.PlayerSkillTree.VERSION, "Debe sellar la versión");
        helper.succeed();
    }

    /** Cada forma divina declara el MIND que ocupa su nodo (`mind_req` en zenkai_skill_tree/<forma>.json), y comprarla lo pide. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void divineFormsRequireMind(GameTestHelper helper) {
        for (String id : new String[]{"ssj_god", "ssj_blue", "ssj_rose", "ssj_blue_evolution", "ssj_rose_evolution",
                "human_god", "namek_god", "majin_god", "arcosian_god"}) {
            var data = com.hmc.zenkai.feature.skilltree.SkillTreeData.get(ResourceLocation.fromNamespaceAndPath("zenkai", id));
            helper.assertTrue(data != null && data.mindReqFor(1) > 0, id + " debe declarar mind_req en su nodo");
        }
        helper.succeed();
    }
}

