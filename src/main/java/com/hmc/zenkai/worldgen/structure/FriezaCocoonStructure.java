package com.hmc.zenkai.worldgen.structure;

import com.hmc.zenkai.registry.ModPlacedFeatures;
import com.hmc.zenkai.registry.ModStructures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * El capullo de Freezer — propuesta H de .claude/pendiente/worldgen-pendiente.md, guiño a Dragon
 * Ball Super. Estructura (no feature) por petición del usuario (2026-10-02): localizable con
 * {@code /locate structure zenkai:frieza_cocoon} y con su propio espaciado (structure_set), en vez
 * de una tirada de rareza por chunk. Solo hfil_needle_wastes (#has_structure/frieza_cocoon).
 * La geometría vive en {@link FriezaCocoonPiece}. Mismo sondeo de suelo que {@link BloodPondStructure}.
 */
public class FriezaCocoonStructure extends Structure {

    public static final MapCodec<FriezaCocoonStructure> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(settingsCodec(inst)).apply(inst, FriezaCocoonStructure::new));

    /** Radio de huella comprobado: el prado y el tronco llegan a ~8 bloques del capullo. */
    private static final int FOOTPRINT_RADIUS = 8;
    private static final int MAX_SLOPE = 1;
    private static final int MIN_BASE_Y = 45;
    private static final int MAX_BASE_Y = 110;

    public FriezaCocoonStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos chunk = ctx.chunkPos();
        int bx = chunk.getMinBlockX() + 8;
        int bz = chunk.getMinBlockZ() + 8;
        int center = floorY(ctx, bx, bz);
        if (center == Integer.MIN_VALUE || center < MIN_BASE_Y || center > MAX_BASE_Y) return Optional.empty();
        for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx += FOOTPRINT_RADIUS) {
            for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz += FOOTPRINT_RADIUS) {
                int h = floorY(ctx, bx + dx, bz + dz);
                if (h == Integer.MIN_VALUE || Math.abs(h - center) > MAX_SLOPE) return Optional.empty();
            }
        }
        BlockPos anchor = new BlockPos(bx, center, bz);
        // La orientación se decide aquí (semilla de la estructura) y viaja en la pieza, para que
        // cada chunk que la procese dibuje el MISMO árbol.
        boolean alongX = ctx.random().nextBoolean();
        return Optional.of(new GenerationStub(anchor, b -> b.addPiece(new FriezaCocoonPiece(anchor, alongX))));
    }

    /** Suelo real del HFIL bajo las islas flotantes: ver BloodPondStructure.floorYBelowIslands. */
    private static int floorY(GenerationContext ctx, int x, int z) {
        LevelHeightAccessor h = ctx.heightAccessor();
        NoiseColumn column = ctx.chunkGenerator().getBaseColumn(x, z, h, ctx.randomState());
        int top = Math.min(ModPlacedFeatures.HFIL_FLOOR_MAX_Y, h.getMaxBuildHeight() - 1);
        for (int y = top; y >= h.getMinBuildHeight(); y--) {
            if (!column.getBlock(y).isAir()) return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.FRIEZA_COCOON.get();
    }
}
