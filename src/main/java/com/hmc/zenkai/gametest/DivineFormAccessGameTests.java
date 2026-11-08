package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.DivineForms;
import com.hmc.zenkai.feature.skills.SuperForms;
import com.hmc.zenkai.feature.skilltree.GodKi;
import com.hmc.zenkai.feature.skilltree.SkillTree;
import com.hmc.zenkai.feature.skilltree.SkillTreeBuyPacket;
import com.hmc.zenkai.feature.skilltree.SkillTreeMigration;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Acceso a la PRIMERA forma divina de cada raza (human_god, namek_god, majin_god, ssj_god, arcosian_god) por las dos vías
 * del juego: el ritual de God Ki (gratis) y la compra con TP del nodo tras tener God Ki. Un jugador simulado recorre el flujo
 * REAL (nodos del árbol, {@link SkillTreeBuyPacket}, ritual) y se comprueba, paso a paso, que la forma queda desbloqueada
 * y seleccionable en la rueda. Un fallo dice en qué paso se quedó la raza.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DivineFormAccessGameTests {
    private DivineFormAccessGameTests() {}

    /** Jugador de la raza con toda su cadena mortal comprada (super_forms al máximo) y alineamiento neutro. */
    private static ServerPlayer mortalPeak(GameTestHelper helper, Race race) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setRace(race);
        att.setAlignment(50);
        att.setAttribute(com.hmc.zenkai.feature.ZenkaiAttributes.MIND, 1000);   // MIND suficiente para lo que ocupa la cadena
        att.skills().grantAdmin(SuperForms.SKILL, SuperForms.maxLevel(race));
        return sp;
    }

    private static String describe(ServerPlayer sp, Race race, ResourceLocation first) {
        var node = SkillTreeNodes.find(race, first);
        return "race=" + race + " forma=" + first
                + " nodo=" + (node == null ? "NO EXISTE" : "padre=" + node.parent())
                + " mortalChainReady=" + GodKi.mortalChainReady(sp)
                + " superLevel=" + SuperForms.level(sp) + "/" + SuperForms.maxLevel(race)
                + " nivelMortalNecesario=" + SuperForms.mortalLevelNeededForDivine(race, first)
                + " godKi=" + SkillTree.hasGodKi(sp)
                + " posee=" + SkillTree.owns(sp, first)
                + " unlocked=" + SuperForms.unlocked(sp, first) + " usable=" + SuperForms.usable(sp, first);
    }

    /**
     * Lo que haría el jugador con la tecla de transformar y con la rueda: recorre la cadena con
     * {@code resolveNextForm} (el único gate de «subir un peldaño») desde BASE hasta donde llegue y comprueba que la forma
     * divina sale en el camino, y que la rueda la dejaría elegir (raza permitida, {@code wheel_selectable}, usable).
     */
    private static void keyAndWheelReach(GameTestHelper helper, ServerPlayer sp, Race race, ResourceLocation first) {
        StringBuilder path = new StringBuilder("BASE");
        ResourceLocation cur = com.hmc.zenkai.feature.forms.FormIds.BASE;
        boolean reached = false;
        for (int i = 0; i < 12; i++) {
            ResourceLocation next = com.hmc.zenkai.feature.player.PlayerFormAttachment.resolveNextForm(sp, race, cur);
            if (next == null) break;
            path.append(" -> ").append(next.getPath());
            if (next.equals(first)) { reached = true; break; }
            cur = next;
        }
        // Humano, Majin y Arcosiano tienen una rama mortal que cuelga junto a la divina (Flow, Saciado/Primitivo, Golden/Black):
        // la tecla sigue esa rama y la forma divina se elige desde la RUEDA. Namekiano y Saiyan van derechos a la divina.
        boolean keyShouldReach = race == Race.NAMEKIAN || race == Race.SAIYAN;
        helper.assertTrue(reached || !keyShouldReach, "Tecla de transformar: no llega a " + first + ". Camino: " + path + ". " + describe(sp, race, first));
        var def = FormRegistry.get(first);
        helper.assertTrue(FormRegistry.isAllowed(race, first) && def.wheelSelectable() && SuperForms.usable(sp, first),
                "Rueda: no deja elegir " + first + " (permitida=" + FormRegistry.isAllowed(race, first)
                        + " wheelSelectable=" + def.wheelSelectable() + " usable=" + SuperForms.usable(sp, first) + ")");
    }

    private static void ritualPath(GameTestHelper helper, Race race) {
        ServerPlayer sp = mortalPeak(helper, race);
        ResourceLocation first = SkillTreeNodes.firstDivineForm(race);
        helper.assertTrue(first != null, race + ": no tiene primera forma divina");
        helper.assertTrue(FormRegistry.get(first) != null, race + ": la forma " + first + " no está cargada");
        helper.assertTrue(GodKi.mortalChainReady(sp), "Ritual: la cadena mortal no cuenta como lista. " + describe(sp, race, first));
        helper.assertTrue(GodKi.ritualCandidate(sp), "Ritual: no es candidato. " + describe(sp, race, first));
        boolean[] done = new boolean[1];
        ZenkaiGameTestHelpers.invokeHandler(() -> done[0] = GodKi.debugCompleteRitual(sp));
        helper.assertTrue(done[0] || SkillTree.owns(sp, first), "Ritual: no se pudo completar. " + describe(sp, race, first));
        helper.assertTrue(SkillTree.owns(sp, first) && SkillTree.hasGodKi(sp),
                "Ritual: no concede forma + God Ki. " + describe(sp, race, first));
        helper.assertTrue(SuperForms.usable(sp, first), "Ritual: la forma no queda usable. " + describe(sp, race, first));
        keyAndWheelReach(helper, sp, race, first);
    }

    private static void purchasePath(GameTestHelper helper, Race race) {
        ServerPlayer sp = mortalPeak(helper, race);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        ResourceLocation first = SkillTreeNodes.firstDivineForm(race);
        helper.assertTrue(first != null, race + ": no tiene primera forma divina");
        // God Ki como lo da la prueba del maestro o el ritual; luego el TP exacto de la forma.
        SkillTree.grant(sp, SkillTreeMigration.NODE_GOD_KI, 1, SkillTree.Via.MASTER_TRIAL);
        var node = SkillTreeNodes.find(race, first);
        helper.assertTrue(node != null, "Compra: el nodo de " + first + " no existe en el árbol de " + race);
        att.addTP(FormRegistry.get(first).tpCost());
        var eval = SkillTreeNodes.evaluate(sp, node);
        helper.assertTrue(eval.purchasable(), "Compra: el nodo no es comprable (estado " + eval.state() + "). " + describe(sp, race, first));
        ZenkaiGameTestHelpers.invokeHandler(() ->
                SkillTreeBuyPacket.handle(new SkillTreeBuyPacket(first), ZenkaiGameTestHelpers.fakeCtx(sp)));
        helper.assertTrue(SkillTree.owns(sp, first), "Compra: tras pagar no queda poseída. tp=" + att.getTP() + " coste=" + eval.cost() + " ownNode=" + eval.ownNode() + " mindFree=" + att.mindFree() + ". " + describe(sp, race, first));
        helper.assertTrue(SuperForms.usable(sp, first), "Compra: la forma no queda usable. " + describe(sp, race, first));
        helper.assertTrue(DivineForms.unlocked(sp, first), "Compra: DivineForms.unlocked es false. " + describe(sp, race, first));
        keyAndWheelReach(helper, sp, race, first);
    }

    /** Namekiano con el interruptor del Gigante encendido (escala 3,0): no debe cerrar el camino a namek_god. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void namekGodWithGiantOn(GameTestHelper helper) {
        ServerPlayer sp = mortalPeak(helper, Race.NAMEKIAN);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.skills().setLevel(com.hmc.zenkai.feature.skills.SkillEffects.NAMEK_GIANT, 1);
        ZenkaiGameTestHelpers.invokeHandler(() ->
                com.hmc.zenkai.feature.skills.SkillToggles.set(sp, com.hmc.zenkai.feature.skills.SkillEffects.NAMEK_GIANT, true));
        helper.assertTrue(com.hmc.zenkai.feature.skills.SkillToggles.isOn(sp, com.hmc.zenkai.feature.skills.SkillEffects.NAMEK_GIANT),
                "El Gigante no se pudo encender");
        ResourceLocation first = SkillTreeNodes.firstDivineForm(Race.NAMEKIAN);
        ZenkaiGameTestHelpers.invokeHandler(() -> GodKi.debugCompleteRitual(sp));
        helper.assertTrue(SkillTree.owns(sp, first) && SuperForms.usable(sp, first),
                "Con el Gigante encendido el ritual no deja namek_god usable. " + describe(sp, Race.NAMEKIAN, first));
        keyAndWheelReach(helper, sp, Race.NAMEKIAN, first);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void humanGodByRitual(GameTestHelper helper) { ritualPath(helper, Race.HUMAN); helper.succeed(); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void namekGodByRitual(GameTestHelper helper) { ritualPath(helper, Race.NAMEKIAN); helper.succeed(); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void saiyanGodByRitual(GameTestHelper helper) { ritualPath(helper, Race.SAIYAN); helper.succeed(); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void arcosianGodByRitual(GameTestHelper helper) { ritualPath(helper, Race.ARCOSIAN); helper.succeed(); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void majinGodByRitual(GameTestHelper helper) { ritualPath(helper, Race.MAJIN); helper.succeed(); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void humanGodByPurchase(GameTestHelper helper) { purchasePath(helper, Race.HUMAN); helper.succeed(); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void namekGodByPurchase(GameTestHelper helper) { purchasePath(helper, Race.NAMEKIAN); helper.succeed(); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void saiyanGodByPurchase(GameTestHelper helper) { purchasePath(helper, Race.SAIYAN); helper.succeed(); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void arcosianGodByPurchase(GameTestHelper helper) { purchasePath(helper, Race.ARCOSIAN); helper.succeed(); }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void majinGodByPurchase(GameTestHelper helper) { purchasePath(helper, Race.MAJIN); helper.succeed(); }
}
