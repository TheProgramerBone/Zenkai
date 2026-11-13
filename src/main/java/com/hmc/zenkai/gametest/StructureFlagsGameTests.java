package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModDimensions;
import com.hmc.zenkai.worldgen.DimensionStructureData;
import com.hmc.zenkai.worldgen.ZenkaiStructurePlacement;
import com.hmc.zenkai.worldgen.ZenkaiWorldData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Marca de «estructura fija ya colocada» por dimensión (2026-11-12): vive en los datos de cada dimensión (así borrar la
 * dimensión la borra), se migra una sola vez desde la marca antigua del Overworld, y {@code /zenkai struct restore} no
 * pisa nada sin {@code force}. Las claves llevan prefijo {@code gametest_} y se limpian al final.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StructureFlagsGameTests {
    private StructureFlagsGameTests() {}

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void placedFlagLivesInTheDimension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        String key = "gametest_flag_" + level.getGameTime();
        helper.assertTrue(!ZenkaiStructurePlacement.isPlaced(server, level, key), "al principio no está marcada");
        DimensionStructureData.get(level).markPlaced(key);
        helper.assertTrue(ZenkaiStructurePlacement.isPlaced(server, level, key), "marcada en la dimensión: se ve como colocada");
        helper.assertTrue(!ZenkaiWorldData.get(server).isPlaced(key), "la marca NO se guarda en los datos del Overworld");
        DimensionStructureData.get(level).unmarkPlaced(key);
        helper.assertTrue(!ZenkaiStructurePlacement.isPlaced(server, level, key),
                "al desaparecer la marca de la dimensión (dimensión borrada) vuelve a verse como NO colocada");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void legacyFlagMigratesOnlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        String key = "gametest_legacy_" + level.getGameTime();
        ZenkaiWorldData legacy = ZenkaiWorldData.get(server);
        legacy.markPlaced(key);
        helper.assertTrue(ZenkaiStructurePlacement.isPlaced(server, level, key), "una marca antigua cuenta como colocada (no se pisa nada)");
        helper.assertTrue(DimensionStructureData.get(level).isPlaced(key), "se copió a la dimensión");
        helper.assertTrue(!legacy.isPlaced(key), "y se QUITÓ la antigua, para migrar una sola vez");
        // Dimensión borrada después del arreglo: sin marca nueva y sin marca antigua, la estructura vuelve a colocarse.
        DimensionStructureData.get(level).unmarkPlaced(key);
        helper.assertTrue(!ZenkaiStructurePlacement.isPlaced(server, level, key),
                "tras borrar la dimensión la marca antigua ya no resucita");
        helper.succeed();
    }

    /**
     * El servidor de gametests normalmente NO carga el Otherworld (ver docs/worldgen.md): entonces {@code restore} debe avisar
     * de que la dimensión no está disponible sin lanzar ni colocar nada. Si algún día se carga, se comprueba la regla de verdad:
     * con la marca puesta y sin {@code force} solo avisa de que hace falta {@code force} (y NO coloca el palacio).
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void restoreWithoutForceNeverOverwrites(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var unknown = ZenkaiStructurePlacement.restore(server, "no_existe", false);
        helper.assertTrue(unknown.size() == 1 && unknown.get(0).getString().contains("desconocida"), "estructura desconocida: " + unknown);

        ServerLevel ow = server.getLevel(ModDimensions.OTHERWORLD_LEVEL);
        if (ow == null) {
            var lines = ZenkaiStructurePlacement.restore(server, "otherworld", false);
            helper.assertTrue(lines.size() == 1 && lines.get(0).getString().contains("no disponible"),
                    "sin dimensión cargada avisa y no hace nada: " + lines);
        } else {
            var data = DimensionStructureData.get(ow);
            boolean already = data.isPlaced(ZenkaiStructurePlacement.KEY_OTHERWORLD);
            data.markPlaced(ZenkaiStructurePlacement.KEY_OTHERWORLD);
            try {
                var lines = ZenkaiStructurePlacement.restore(server, "otherworld", false);
                helper.assertTrue(lines.size() == 1 && lines.get(0).getString().contains("force"),
                        "sin force y con la marca puesta solo avisa de que hace falta force: " + lines);
            } finally {
                if (!already) data.unmarkPlaced(ZenkaiStructurePlacement.KEY_OTHERWORLD);
            }
        }
        helper.succeed();
    }
}
