package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.master.KorinEntity;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Candidato #3 de .claude/pendiente/tests-automatizados.md: compra en maestro
 * (MasterManager.check() + SkillBuyPacket con masterId), cubriendo la validación de distancia.
 * Usa ki_fist (master: korin, tp_cost: 500) contra un korin.json real (pl_req: 800,
 * alignment [-20,100]). El jugador se sube a los atributos al tope de ServerConfig en vez de a
 * un número calculado a mano, para no acoplar el test a la fórmula exacta de Power Level: solo
 * necesita estar MUY por encima de 800, no un valor concreto.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MasterPurchaseGameTests {
    private MasterPurchaseGameTests() {}

    private static final String SKILL  = "ki_fist";
    private static final BlockPos MASTER_POS = new BlockPos(2, 1, 2);
    private static final BlockPos FAR_POS    = new BlockPos(13, 1, 13);   // ~15.5 bloques del maestro
    private static final BlockPos NEAR_POS   = new BlockPos(2, 1, 3);     // 1 bloque del maestro

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void masterGatesDistanceAndAdmission(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        IPayloadContext ctx = ZenkaiGameTestHelpers.fakeCtx(sp);

        // PL muy por encima de cualquier pl_req del datapack, sin acoplarse a la fórmula exacta.
        int cap = ServerConfig.globalAttributeCap();
        att.setAttribute(ZenkaiAttributes.STRENGTH, cap);
        att.setAttribute(ZenkaiAttributes.CONSTITUTION, cap);
        att.setAttribute(ZenkaiAttributes.DEXTERITY, cap);
        att.setAttribute(ZenkaiAttributes.WILLPOWER, cap);
        att.setAttribute(ZenkaiAttributes.SPIRIT, cap);
        att.addTP(500);

        KorinEntity korin = helper.spawn(ModEntities.KORIN.get(), MASTER_POS);
        helper.assertTrue("korin".equals(korin.masterId()), "El masterId de KorinEntity debería ser 'korin'");

        // ── Lejos del maestro: la compra no debe progresar ──────────────────
        moveMockPlayer(helper, sp, FAR_POS);
        ZenkaiGameTestHelpers.invokeHandler(() -> SkillBuyPacket.handle(new SkillBuyPacket(SKILL, "korin"), ctx));

        helper.assertTrue(att.skills().level(SKILL) == 0,
                "Comprando lejos del maestro (>8 bloques) no debería concederse ningún nivel, pero level() da "
                        + att.skills().level(SKILL));
        helper.assertTrue(att.getTP() == 500,
                "Comprando lejos del maestro no debería gastarse TP, pero el TP actual es " + att.getTP());

        // ── Cerca del maestro: la compra debe progresar ─────────────────────
        moveMockPlayer(helper, sp, NEAR_POS);
        ZenkaiGameTestHelpers.invokeHandler(() -> SkillBuyPacket.handle(new SkillBuyPacket(SKILL, "korin"), ctx));

        helper.assertTrue(att.skills().level(SKILL) == 1,
                "Comprando cerca del maestro con PL y alineamiento válidos debería conceder el nivel 1, pero level() da "
                        + att.skills().level(SKILL));
        helper.assertTrue(att.getTP() == 0,
                "La compra de ki_fist (500 TP) con 500 disponibles debería dejar el TP en 0, pero es " + att.getTP());

        helper.succeed();
    }

    private static void moveMockPlayer(GameTestHelper helper, ServerPlayer sp, BlockPos relativePos) {
        Vec3 abs = helper.absoluteVec(Vec3.atCenterOf(relativePos));
        sp.moveTo(abs.x, abs.y, abs.z);
    }
}
