package com.hmc.zenkai.worldgen.structure;

import com.hmc.zenkai.registry.ModStructureSegments;
import com.hmc.zenkai.registry.ModStructures;
import com.hmc.zenkai.worldgen.StaticStructurePlacer.Segment;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

/**
 * La atalaya de Kami. No usa jigsaw: emite las piezas de {@link ModStructureSegments#KAMI}
 * en sus offsets exactos, así que no le afecta el tope de 128 de max_distance_from_center
 * (la torre mide 250 de alto).
 */
public class KamiStructure extends Structure {

    public static final MapCodec<KamiStructure> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(settingsCodec(inst)).apply(inst, KamiStructure::new));

    public KamiStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    /** Huella real en el suelo: kami_1 es 15×15 desde la base. El resto arranca en y+42. */
    private static final int FOOT_SIZE = 15;
    private static final int FOOT_STEP = 5;
    /** Paso del barrido de anclas candidatas dentro del chunk. */
    private static final int SCAN_STEP = 4;
    /** Desnivel máximo tolerado bajo la huella del pie, en bloques. */
    private static final int MAX_SLOPE = 2;
    /** Altura mínima de la base. */
    private static final int MIN_BASE_Y = 58;
    /** Alto total de la torre. Define el techo de la base: por encima, la cima se corta. */
    private static final int KAMI_HEIGHT = 250;

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        BlockPos base = findFlatBase(ctx);
        if (base == null) return Optional.empty();

        return Optional.of(new GenerationStub(base, builder -> {
            StructureTemplateManager mgr = ctx.structureTemplateManager();
            for (Segment seg : ModStructureSegments.KAMI) {
                builder.addPiece(new SegmentPiece(mgr, seg.nbt(), base.offset(seg.offset())));
            }
        }));
    }

    /**
     * Barre anclas dentro del chunk y devuelve la más plana que cumpla: sin agua encima,
     * desnivel <= MAX_SLOPE bajo la huella del pie, y altura en rango. Ancla al MÍNIMO
     * de la huella, así que en el peor caso el pie se entierra MAX_SLOPE bloques, que es
     * mucho menos visible que flotar los mismos. Devuelve null si el chunk no sirve.
     */
    private static BlockPos findFlatBase(GenerationContext ctx) {
        ChunkPos chunk = ctx.chunkPos();
        int maxBaseY = ctx.heightAccessor().getMaxBuildHeight() - KAMI_HEIGHT;

        BlockPos best = null;
        int bestSlope = Integer.MAX_VALUE;

        for (int ox = 0; ox < 16; ox += SCAN_STEP) {
            for (int oz = 0; oz < 16; oz += SCAN_STEP) {
                int bx = chunk.getMinBlockX() + ox;
                int bz = chunk.getMinBlockZ() + oz;

                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                boolean water = false;

                for (int x = 0; x <= FOOT_SIZE && !water; x += FOOT_STEP) {
                    for (int z = 0; z <= FOOT_SIZE; z += FOOT_STEP) {
                        int surface = ctx.chunkGenerator().getBaseHeight(bx + x, bz + z,
                                Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
                        int floor = ctx.chunkGenerator().getBaseHeight(bx + x, bz + z,
                                Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
                        if (surface != floor) { water = true; break; }   // columna de agua encima
                        lo = Math.min(lo, floor);
                        hi = Math.max(hi, floor);
                    }
                }
                if (water) continue;

                int slope = hi - lo;
                if (slope > MAX_SLOPE) continue;
                if (lo < MIN_BASE_Y || lo > maxBaseY) continue;

                if (slope < bestSlope) {
                    bestSlope = slope;
                    best = new BlockPos(bx, lo, bz);
                }
            }
        }
        return best == null ? null : best.above(ModStructureSegments.KAMI_Y_OFFSET);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.KAMI_PALACE.get();
    }
}