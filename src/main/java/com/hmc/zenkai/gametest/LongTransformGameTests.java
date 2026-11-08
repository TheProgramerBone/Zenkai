package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.rage.LongTransformState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Rage Forms, fase 3: el aviso de «primera transformación = secuencia larga» ({@link LongTransformState}). Se gasta una sola vez, es
 * por jugador y no queda armado para una transformación posterior. (El resto de la secuencia es presentación de cliente y se comprueba
 * con las capturas del arnés: SkillTreeShots modo {@code longtransform}.)
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LongTransformGameTests {
    private LongTransformGameTests() {}

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void warningIsPerPlayerAndSingleUse(GameTestHelper helper) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        try {
            helper.assertTrue(!LongTransformState.pending(a) && !LongTransformState.consume(a), "Sin aviso no hay nada pendiente");
            LongTransformState.mark(a);
            helper.assertTrue(LongTransformState.pending(a), "Marcado: pendiente");
            helper.assertTrue(!LongTransformState.pending(b), "El aviso es de ese jugador, no de otro");
            helper.assertTrue(LongTransformState.pending(a), "Mirar no lo gasta");
            helper.assertTrue(LongTransformState.consume(a), "Se consume una vez");
            helper.assertTrue(!LongTransformState.pending(a) && !LongTransformState.consume(a),
                    "Gastado: no queda armado para una transformación posterior");
        } finally {
            LongTransformState.clear();
        }
        helper.succeed();
    }
}
