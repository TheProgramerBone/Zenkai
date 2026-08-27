package com.hmc.zenkai.worldgen.placement;

import com.hmc.zenkai.registry.ModPlacementModifierTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Como HeightmapPlacement (busca el primer bloque no-aire desde arriba), pero IGNORA
 * cualquier bloque por encima de maxY. Hace falta porque en el Otherworld una misma columna
 * puede tener el suelo del HFIL abajo Y una isla flotante arriba (ver
 * CloudLayerFeature.CLOUD_BASE_Y=128 y la surface rule de otherworld_noise.json, que trata
 * y>=130 como terreno de isla) — el heightmap normal (WORLD_SURFACE_WG) siempre encuentra el
 * bloque sólido MÁS ALTO de la columna, así que en cuanto hay una isla encima, TODA la
 * decoración de superficie del HFIL (hierba muerta, troncos caídos...) intenta colocarse a
 * la altura de la isla en vez del suelo real. Como el bioma AHÍ es "otherworld", no
 * "hfil_*", el BiomeFilter que corre justo después descarta la colocación entera — el suelo
 * bajo esa isla se queda sin decoración (el "bache" reportado en juego). Reemplaza a
 * PlacementUtils.HEIGHTMAP_WORLD_SURFACE solo en las placed features del SUELO del HFIL
 * (ver HFIL_DRY_GRASS_KEY, HFIL_DEAD_BUSH_KEY y FALLEN_LOG_HFIL_KEY en ModPlacedFeatures)
 * — la decoración propia de las islas
 * (OTHERWORLD_DEAD_BUSH_KEY, OTHERWORLD_FLOWERS_KEY) sigue usando el heightmap normal, que
 * para esas columnas ya apunta a la isla correctamente.
 */
public class ClampedHeightmapPlacement extends PlacementModifier {
    public static final MapCodec<ClampedHeightmapPlacement> CODEC = RecordCodecBuilder.mapCodec(inst -> inst
            .group(Codec.INT.fieldOf("max_y").forGetter(p -> p.maxY))
            .apply(inst, ClampedHeightmapPlacement::new));

    private final int maxY;

    private ClampedHeightmapPlacement(int maxY) {
        this.maxY = maxY;
    }

    public static ClampedHeightmapPlacement belowY(int maxY) {
        return new ClampedHeightmapPlacement(maxY);
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource random, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        int minY = ctx.getMinBuildHeight();

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, maxY, z);
        for (int y = maxY; y >= minY; y--) {
            m.setY(y);
            if (!ctx.getBlockState(m).isAir()) {
                // El primer bloque no-aire puede ser líquido (agua/lava): si lo tratáramos
                // como "superficie" igual que WORLD_SURFACE, la decoración quedaría flotando
                // sobre la lámina en vez de sobre suelo real — el bug reportado en juego como
                // troncos/matas "encima del agua" o "en el aire" (la lámina de agua se ve
                // como si no hubiera nada debajo). En vez de seguir bajando hasta el fondo
                // real (que dejaría la decoración bajo el agua, igual de mal), se descarta
                // esta columna entera — igual que SurfaceWaterDepthFilter.forMaxDepth(0) hace
                // para los árboles de Namek (ver ModPlacedFeatures.treePlacement).
                if (!ctx.getBlockState(m).getFluidState().isEmpty()) return Stream.of();
                return Stream.of(new BlockPos(x, y + 1, z));
            }
        }
        return Stream.of();
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacementModifierTypes.CLAMPED_HEIGHTMAP.get();
    }
}
