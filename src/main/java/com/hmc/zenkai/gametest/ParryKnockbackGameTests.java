package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.combat.ParryServer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * El empujón del parry se configura en BLOQUES ({@code parry.knockback_blocks}) y se traduce a una
 * velocidad simulando los ticks de vanilla. Si esa traducción se descalibra, el parry lanza al rival
 * mucho más cerca o más lejos de lo que dice la config sin ningún error: lógica pura, sin jugador.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ParryKnockbackGameTests {
    private ParryKnockbackGameTests() {}

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void knockbackLandsNearTheConfiguredDistance(GameTestHelper helper) {
        for (double blocks : new double[]{1.0, 2.0, 2.5, 3.0, 5.0}) {
            double v = ParryServer.speedForDistance(blocks, 0.2);
            double real = ParryServer.travelled(v, 0.2);
            helper.assertTrue(Math.abs(real - blocks) < 0.05,
                    "pedido " + blocks + " bloques, la velocidad " + v + " recorre " + real);
        }
        helper.assertTrue(ParryServer.speedForDistance(0.0, 0.2) == 0.0, "0 bloques no empuja");
        helper.assertTrue(ParryServer.speedForDistance(3.0, 0.2) > ParryServer.speedForDistance(2.0, 0.2),
                "más distancia exige más velocidad");
        helper.succeed();
    }
}
