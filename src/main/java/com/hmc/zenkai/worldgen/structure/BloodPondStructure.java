package com.hmc.zenkai.worldgen.structure;

import com.hmc.zenkai.registry.ModPlacedFeatures;
import com.hmc.zenkai.registry.ModStructures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Landmark del Blood Pond (Fase 6 del rework del HFIL, ver
 * .claude/pendiente/hfil-rework-propuesta.md secciones 7 y 8) — un charco de sangre ABIERTO
 * grande con un cartel y marcadores de hueso alrededor, ancla la identidad de {@code
 * hfil_blood_shore} a un punto localizable ({@code /locate structure zenkai:blood_pond}).
 *
 * DESVIACIÓN respecto al plan original de la sección 7 (documentada aquí, no allí, porque solo
 * se descubrió al implementar): el plan proponía "buscar una columna cerca de una instancia YA
 * GENERADA de HFIL_LAKE_BLOOD_KEY". Eso no es posible — las estructuras resuelven su punto de
 * generación (STRUCTURE_STARTS) ANTES de que corra cualquier feature de decoración de bioma
 * (FEATURES), en TODOS los chunks, así que en el momento de {@link #findGenerationPoint} nunca
 * existe ya ningún charco de sangre generado que buscar. En vez de depender de un charco
 * ajeno, esta estructura CAVA EL SUYO PROPIO como parte de su propia pieza ({@link
 * BloodPondPiece}, reutilizando {@code HfilBloodPoolFeature.carve} a un tamaño fijo mayor) —
 * más simple y sin la dependencia de orden imposible.
 *
 * No usa jigsaw ni {@code SegmentPiece} (a diferencia de {@link KamiStructure}): la única pieza
 * necesita CAVAR terreno además de colocar bloques, algo que {@code SegmentPiece} (solo estampa
 * un NBT) no hace — ver {@link BloodPondPiece}.
 */
public class BloodPondStructure extends Structure {

    public static final MapCodec<BloodPondStructure> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(settingsCodec(inst)).apply(inst, BloodPondStructure::new));

    /** Radio de huella a comprobar antes de aceptar un punto — más generoso que el radio real
     *  del charco (ver BloodPondPiece.POOL_RADIUS): no hace falta un suelo perfectamente plano,
     *  HfilBloodPoolFeature.carve ya sondea el suelo LOCAL de cada columna por su cuenta. Esto
     *  solo descarta los casos claramente malos (borde de un acantilado, vacío). */
    private static final int FOOTPRINT_RADIUS = 10;
    private static final int MAX_SLOPE = 8;
    private static final int MIN_BASE_Y = 45;
    private static final int MAX_BASE_Y = 110;

    public BloodPondStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        BlockPos anchor = findGroundAnchor(ctx);
        if (anchor == null) return Optional.empty();

        return Optional.of(new GenerationStub(anchor, builder ->
                builder.addPiece(new BloodPondPiece(anchor))));
    }

    /**
     * Centro del chunk, sondeado con el mismo principio que {@code LocalGroundProbe}/
     * {@code ClampedHeightmapPlacement}: el suelo REAL del HFIL, ignorando cualquier isla
     * flotante por encima (acotando el escaneo a {@code HFIL_FLOOR_MAX_Y} hacia abajo). No se
     * puede usar {@code LocalGroundProbe} aquí de verdad — necesita un {@code WorldGenLevel} con
     * bloques ya colocados, y en {@link #findGenerationPoint} solo hay acceso al generador
     * (vía {@code getBaseColumn}, que lee la densidad cruda, igual que hace {@link
     * KamiStructure#findGenerationPoint} con {@code getBaseHeight} — aquí hace falta la versión
     * acotada por altura en vez de la del heightmap normal, por la misma razón de siempre: un
     * heightmap sin acotar encontraría la isla, no el suelo).
     */
    private static BlockPos findGroundAnchor(GenerationContext ctx) {
        ChunkPos chunk = ctx.chunkPos();
        int bx = chunk.getMinBlockX() + 8;
        int bz = chunk.getMinBlockZ() + 8;

        int center = floorYBelowIslands(ctx, bx, bz);
        if (center == Integer.MIN_VALUE) return null;
        if (center < MIN_BASE_Y || center > MAX_BASE_Y) return null;

        int[][] offsets = {{FOOTPRINT_RADIUS, 0}, {-FOOTPRINT_RADIUS, 0}, {0, FOOTPRINT_RADIUS}, {0, -FOOTPRINT_RADIUS}};
        for (int[] off : offsets) {
            int h = floorYBelowIslands(ctx, bx + off[0], bz + off[1]);
            if (h == Integer.MIN_VALUE) return null; // vacío/borde del mundo en la huella
            if (Math.abs(h - center) > MAX_SLOPE) return null;
        }

        return new BlockPos(bx, center, bz);
    }

    private static int floorYBelowIslands(GenerationContext ctx, int x, int z) {
        LevelHeightAccessor heightAccessor = ctx.heightAccessor();
        RandomState randomState = ctx.randomState();
        NoiseColumn column = ctx.chunkGenerator().getBaseColumn(x, z, heightAccessor, randomState);

        int top = Math.min(ModPlacedFeatures.HFIL_FLOOR_MAX_Y, heightAccessor.getMaxBuildHeight() - 1);
        int bottom = heightAccessor.getMinBuildHeight();
        for (int y = top; y >= bottom; y--) {
            if (!column.getBlock(y).isAir()) return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.BLOOD_POND.get();
    }
}
