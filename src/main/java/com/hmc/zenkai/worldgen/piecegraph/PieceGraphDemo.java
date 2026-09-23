package com.hmc.zenkai.worldgen.piecegraph;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * Arnés de prueba del motor de piezas: genera y coloca al vuelo una "aldea" a partir de las 3
 * piezas de {@code tools/gen_piecegraph_test_pieces.py} (arte de relleno, no namek real —
 * sirve para confirmar en juego que enganchar/rotar/colocar funciona antes de construir el
 * contenido de verdad). Disparado por {@code /zenkai struct place piecegraph <pos>}.
 */
public final class PieceGraphDemo {
    private PieceGraphDemo() {}

    private static ResourceLocation nbt(String name) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name);
    }

    public static boolean place(ServerLevel level, BlockPos pos) {
        var mgr = level.getStructureManager();

        PieceCatalog catalog = PieceCatalog.scan(mgr, List.of(
                nbt("piecegraph_test_straight"),
                nbt("piecegraph_test_junction")));

        var start = PieceTemplate.scan(mgr, nbt("piecegraph_test_start"));
        if (start.isEmpty()) return false;

        List<PlacedPiece> pieces = PieceGraphPlacer.generate(
                mgr, level.getRandom(), start.get(), pos, Rotation.NONE, catalog, PieceGraphPlacer.Config.defaults());

        PieceGraphPlacer.placeAll(level, pieces);
        return !pieces.isEmpty();
    }
}
