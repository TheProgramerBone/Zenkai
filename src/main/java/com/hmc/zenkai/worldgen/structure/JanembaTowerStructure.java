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
 * La torre de Janemba — landmark del Janemba Rift (hfil_janemba_rift): la torre orgánica con picos
 * del centro de la referencia de Fusion Reborn (.claude/pendiente/referencia_janemba.png). Sustituye
 * el 2026-10-05 a la "Compuerta del Juicio" de púrpur del primer borrador, que no salía en la
 * referencia (decisión del usuario: el bioma entero se rehace según la imagen). Posible arena de un
 * jefe futuro (un Janemba GeckoLib humanoide, ver docs/npcs-de-combate.md).
 *
 * Estructura y no feature para poder localizarla con {@code /locate structure zenkai:janemba_tower}
 * y darle su propio espaciado (y para que StructureSites aparte la decoración de su pie). Solo en el
 * bioma (#has_structure/janemba_tower). Geometría en {@link JanembaTowerPiece}.
 */
public class JanembaTowerStructure extends Structure {

    public static final MapCodec<JanembaTowerStructure> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(settingsCodec(inst)).apply(inst, JanembaTowerStructure::new));

    private static final int FOOTPRINT_RADIUS = 7;
    private static final int MAX_SLOPE = 3;
    private static final int MIN_BASE_Y = 45;
    private static final int MAX_BASE_Y = 110;

    public JanembaTowerStructure(StructureSettings settings) {
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
        long seed = ctx.random().nextLong();
        return Optional.of(new GenerationStub(anchor, b -> b.addPiece(new JanembaTowerPiece(anchor, seed))));
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
        return ModStructures.JANEMBA_TOWER.get();
    }
}
