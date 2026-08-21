package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.stats.RefundTpPacket;
import com.hmc.zenkai.feature.stats.SpendTpPacket;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Candidato #2 de .claude/pendiente/tests-automatizados.md: subir/reembolsar un atributo con
 * SpendTpPacket + RefundTpPacket, mismo patrón que SkillPurchaseGameTests pero para TP de
 * atributos en vez de TP de skill.
 * El coste esperado se calcula con PlayerStatsAttachment#previewTpCost — la MISMA fórmula que
 * usa el handler — en vez de un número hardcodeado, para que el test no se desincronice solo si
 * algún día se retoca TpCurve.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AttributeTpGameTests {
    private AttributeTpGameTests() {}

    private static final ZenkaiAttributes ATTR = ZenkaiAttributes.STRENGTH;

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void spendThenRefundAttribute(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        IPayloadContext ctx = ZenkaiGameTestHelpers.fakeCtx(sp);

        att.addTP(10_000);
        int baseAttr = att.getAttribute(ATTR);
        int baseTp   = att.getTP();
        int expectedCost = att.previewTpCost(ATTR, 50);
        helper.assertTrue(expectedCost > 0, "previewTpCost(STRENGTH, 50) debería ser positivo con TP y hueco de sobra");

        // ── Gastar 50 puntos ─────────────────────────────────────────────────
        ZenkaiGameTestHelpers.invokeHandler(() -> SpendTpPacket.handle(new SpendTpPacket(ATTR.name(), 50), ctx));

        helper.assertTrue(att.getAttribute(ATTR) == baseAttr + 50,
                "Tras gastar 50, el atributo debería ser " + (baseAttr + 50) + " pero es " + att.getAttribute(ATTR));
        helper.assertTrue(att.getTP() == baseTp - expectedCost,
                "El TP tras gastar debería ser " + (baseTp - expectedCost) + " (coste previsto " + expectedCost
                        + ") pero es " + att.getTP());

        int tpAfterSpend = att.getTP();

        // ── Devolver 20 de esos 50 puntos ───────────────────────────────────
        ZenkaiGameTestHelpers.invokeHandler(() -> RefundTpPacket.handle(new RefundTpPacket(ATTR.name(), 20), ctx));

        helper.assertTrue(att.getAttribute(ATTR) == baseAttr + 30,
                "Tras devolver 20 de los 50, el atributo debería ser " + (baseAttr + 30)
                        + " pero es " + att.getAttribute(ATTR));
        helper.assertTrue(att.getTP() > tpAfterSpend,
                "El reembolso de 20 puntos debería devolver algo de TP; antes " + tpAfterSpend
                        + ", ahora " + att.getTP());
        helper.assertTrue(att.getTP() <= baseTp,
                "El reembolso no puede devolver más TP del que se gastó en total (base " + baseTp
                        + ", actual " + att.getTP() + ")");

        helper.succeed();
    }
}
