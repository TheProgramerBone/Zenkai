package com.hmc.zenkai.worldgen.structure;

import com.hmc.zenkai.registry.ModStructures;
import com.hmc.zenkai.worldgen.JanembaShapes;
import com.hmc.zenkai.worldgen.JanembaTowerShape;
import com.hmc.zenkai.worldgen.LocalGroundProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Pieza única de {@link JanembaTowerStructure}: la torre grande, con corona de picos y pinchos en
 * la base ({@link JanembaTowerShape} con {@code crown = true}).
 *
 * Cada chunk que solapa la pieza llama a postProcess: los bloques se filtran por chunkBox y la
 * forma sale ENTERA de la semilla guardada (nada del RandomSource del chunk), para que cada uno de
 * los chunks dibuje la misma torre. Por eso la semilla y las medidas se guardan en el NBT: al recargar
 * un chunk a medio generar la torre tiene que seguir siendo la misma. La caja se dimensiona con
 * {@link JanembaTowerShape#reach}: un bloque fuera de ella no se dibujaría.
 */
public class JanembaTowerPiece extends StructurePiece {

    private static final int GROUND_SEARCH_RADIUS = 4;

    private final BlockPos origin;
    private final long seed;
    private final JanembaTowerShape.Params params;

    public JanembaTowerPiece(BlockPos origin, long seed) {
        this(origin, seed, paramsFor(seed));
    }

    private JanembaTowerPiece(BlockPos origin, long seed, JanembaTowerShape.Params params) {
        super(ModStructures.JANEMBA_TOWER_PIECE.get(), 0, box(origin, params));
        this.origin = origin;
        this.seed = seed;
        this.params = params;
    }

    public JanembaTowerPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.JANEMBA_TOWER_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
        this.seed = tag.getLong("Seed");
        this.params = paramsFor(seed);
    }

    /** Medidas de la torre a partir de su semilla: 32-46 de fuste, radio 4.5-5.5. */
    private static JanembaTowerShape.Params paramsFor(long seed) {
        double a = JanembaShapes.hash01(seed, 0, 1, 0);
        double b = JanembaShapes.hash01(seed, 0, 2, 0);
        return new JanembaTowerShape.Params(32 + (int) (a * 15), 4.5 + b, 3.0, true);
    }

    private static BoundingBox box(BlockPos o, JanembaTowerShape.Params p) {
        int r = JanembaTowerShape.reach(p);
        // +GROUND_SEARCH_RADIUS: el sondeo puede subir o bajar la base respecto al ancla.
        return new BoundingBox(o.getX() - r, o.getY() - 3 - GROUND_SEARCH_RADIUS, o.getZ() - r,
                o.getX() + r, o.getY() + JanembaTowerShape.top(p) + GROUND_SEARCH_RADIUS, o.getZ() + r);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        // Sondeo en la columna del ancla, que tiene que dar lo mismo en cada chunk:
        // - la caja (reach ≤ 21 desde el centro de un chunk) no pasa del chunk vecino, así que esa
        //   columna siempre está dentro de la región que se está generando;
        // - ninguna feature la toca: StructureSites aparta la decoración del HFIL 30 bloques del
        //   ancla, más que el alcance de cualquier feature del bioma.
        // Si aun así falla, el ancla de la estructura.
        int g0 = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (g0 == Integer.MIN_VALUE) g0 = origin.getY();
        int minY = level.getMinBuildHeight();
        JanembaTowerShape.build((x, y, z, s) -> {
            BlockPos p = new BlockPos(x, y, z);
            if (chunkBox.isInside(p) && y >= minY) level.setBlock(p, s, JanembaShapes.FLAGS);
        }, seed, origin.getX(), g0, origin.getZ(), params);
    }
}
