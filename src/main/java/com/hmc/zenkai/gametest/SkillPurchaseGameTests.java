package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.ForgetSkillPacket;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Candidato #1 de .claude/pendiente/tests-automatizados.md: comprar/olvidar una skill contra un
 * PlayerStatsAttachment de prueba. Usa ki_control (master: kami, tp_cost: 600, max_level: 10)
 * comprando el nivel 2 en adelante — el nivel 1 ya lo tiene simulado con grantAdmin(), igual que
 * hace /zenkai skill give, así que no hace falta montar un maestro para este test (eso lo cubre
 * MasterPurchaseGameTests).
 * ESTE es el test que habría detectado el bug de grant() vs raise() en SkillBuyPacket corregido
 * el 2026-08-20: si el handler usara grant() en vez de raise(), el paso 2 dejaría
 * grantedFloor == boughtLevel y boughtLevels() daría 0 en vez de 1, y el forget del paso 3
 * fallaría en seco por culpa del suelo mal puesto.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillPurchaseGameTests {
    private SkillPurchaseGameTests() {}

    private static final String SKILL = "ki_control";

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void buyThenForgetSkill(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        IPayloadContext ctx = ZenkaiGameTestHelpers.fakeCtx(sp);

        // Nivel 1 ya "enseñado" por un maestro (igual que /zenkai skill give): protege solo ese
        // nivel, no lo que se compre después.
        att.skills().grantAdmin(SKILL, 1);
        att.addTP(600);

        // ── Comprar el nivel 2 con TP ────────────────────────────────────────
        ZenkaiGameTestHelpers.invokeHandler(() -> SkillBuyPacket.handle(new SkillBuyPacket(SKILL, ""), ctx));

        helper.assertTrue(att.skills().level(SKILL) == 2,
                "Tras comprar, el nivel debería ser 2 pero es " + att.skills().level(SKILL));
        helper.assertTrue(att.getTP() == 0,
                "Tras comprar por 600 TP con 600 disponibles, el TP restante debería ser 0 pero es " + att.getTP());
        helper.assertTrue(att.skills().boughtLevels(SKILL) == 1,
                "boughtLevels() debería ser 1 (el nivel 2 comprado) pero es " + att.skills().boughtLevels(SKILL)
                        + " — si diera 0, SkillBuyPacket estaría usando grant() en vez de raise() y habría "
                        + "subido el suelo otorgado junto con el nivel");

        // ── Olvidar ese nivel: reembolso completo y vuelve al suelo ─────────
        ZenkaiGameTestHelpers.invokeHandler(() -> ForgetSkillPacket.handle(new ForgetSkillPacket(SKILL), ctx));

        helper.assertTrue(att.skills().level(SKILL) == 1,
                "Tras olvidar, el nivel debería volver a 1 pero es " + att.skills().level(SKILL));
        helper.assertTrue(att.getTP() == 600,
                "El olvido reembolsa el TP completo (600) pero el TP actual es " + att.getTP());
        helper.assertTrue(att.skills().boughtLevels(SKILL) == 0,
                "Tras el reembolso no debería quedar ningún nivel comprado, pero boughtLevels() da "
                        + att.skills().boughtLevels(SKILL));

        // ── Un segundo olvido no puede bajar del suelo otorgado por el maestro ──
        ZenkaiGameTestHelpers.invokeHandler(() -> ForgetSkillPacket.handle(new ForgetSkillPacket(SKILL), ctx));

        helper.assertTrue(att.skills().level(SKILL) == 1,
                "grantedFloor debería impedir bajar del nivel 1 otorgado, pero el nivel quedó en "
                        + att.skills().level(SKILL));
        helper.assertTrue(att.getTP() == 600,
                "El olvido rechazado no debería devolver TP de más; TP actual es " + att.getTP());

        helper.succeed();
    }
}
