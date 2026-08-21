package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.aura.AuraCeiling;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Candidato #4 de .claude/pendiente/tests-automatizados.md: una fórmula con CommonConfig detrás
 * y un comentario largo explicando por qué el número es el que es. AuraCeiling deriva el techo
 * de la escala de presencia recorriendo TODO el registro de formas/razas/estilos a partir de
 * CommonConfig.auraReferenceTp() (ver el javadoc de la clase) — es justo el tipo de cálculo que
 * se rompe en silencio si alguien retoca la config o el datapack sin leer el porqué.
 * No necesita jugador: RaceStatTable/FormDef ya están cargados por el propio arranque del
 * gameTestServer (carga real de datapacks), así que esto es lógica pura sobre datos reales.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AuraCeilingGameTests {
    private AuraCeilingGameTests() {}

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void ceilingFloorAndPointsForTp(GameTestHelper helper) {
        AuraCeiling.invalidate();

        long floor = AuraCeiling.floor();
        long ceil  = AuraCeiling.ceiling();

        helper.assertTrue(floor > 0, "AuraCeiling.floor() debería ser positivo pero es " + floor);
        helper.assertTrue(ceil > floor,
                "AuraCeiling.ceiling() (" + ceil + ") debería ser mayor que floor() (" + floor + ")");

        helper.assertTrue(AuraCeiling.pointsForTp(0d) == 0d,
                "pointsForTp(0) debería ser 0 pero es " + AuraCeiling.pointsForTp(0d));

        double small = AuraCeiling.pointsForTp(1_000d);
        double big   = AuraCeiling.pointsForTp(2_000_000d);
        helper.assertTrue(small < big,
                "pointsForTp debería ser creciente en TP: pointsForTp(1000)=" + small
                        + " no es menor que pointsForTp(2000000)=" + big);

        // El techo se cachea (ver el javadoc de AuraCeiling): invalidar y recalcular con la
        // misma config/datapack debe dar el mismo valor, no otro — si no, el cache estaría
        // devolviendo basura o la invalidación estaría rompiendo el recálculo.
        AuraCeiling.invalidate();
        long ceilAgain = AuraCeiling.ceiling();
        helper.assertTrue(ceilAgain == ceil,
                "Recalcular ceiling() tras invalidate() con la misma config debería dar " + ceil
                        + " pero dio " + ceilAgain);

        helper.succeed();
    }
}
