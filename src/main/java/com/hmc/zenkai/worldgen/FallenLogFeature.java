package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.Optional;

/**
 * Decoración de biomas sin árboles (rocky_wasteland, hfil_wastes/badlands/dunes): elige uno
 * de los troncos caídos NBT (madera vanilla, ver tools/gen_fallen_logs.py) y lo coloca con
 * rotación aleatoria. Mismo estilo ligero que CloudLayerFeature — NO jigsaw ni structure_set,
 * eso es solo para estructuras grandes tipo dragon ball.
 */
public class FallenLogFeature extends Feature<NoneFeatureConfiguration> {

    private static final ResourceLocation[] VARIANTS = {
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fallen_log_1"),
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fallen_log_2"),
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fallen_log_3"),
    };

    /** Diferencia de altura máxima tolerada entre las 4 esquinas del hueco real y la columna
     *  de origen antes de descartar la colocación. Ver groundIsFlatEnough. */
    private static final int TERRAIN_TOLERANCE = 1;

    public FallenLogFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        // WorldGenLevel no expone getStructureManager() directamente (a diferencia de
        // ServerLevel) — mismo camino que usa FossilFeature, la única feature vanilla que
        // coloca un StructureTemplate desde dentro de place().
        StructureTemplateManager mgr = level.getLevel().getStructureManager();
        ResourceLocation chosen = VARIANTS[random.nextInt(VARIANTS.length)];
        Optional<StructureTemplate> opt = mgr.get(chosen);
        if (opt.isEmpty()) return false;
        StructureTemplate tpl = opt.get();

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.getRandom(random))
                .addProcessor(new BlockIgnoreProcessor(List.of(Blocks.STRUCTURE_VOID)))
                .setKnownShape(true);

        Vec3i size = tpl.getSize(settings.getRotation());
        BlockPos placeOrigin = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);

        // El heightmap del placement modifier (ver ModPlacedFeatures.FALLEN_LOG_*) solo mira
        // la columna de origen ANTES de este desplazamiento/rotación — el tronco real puede
        // acabar extendido sobre columnas que esa comprobación nunca miró. Sin este chequeo,
        // un tronco que cae sobre una pendiente o el borde de un risco queda mitad flotando
        // mitad enterrado (reportado en juego). Se descarta la colocación entera si el
        // terreno bajo alguna de las 4 esquinas del hueco real no está a la misma altura que
        // la columna de origen — igual que una feature vanilla que falla su comprobación de
        // superficie, simplemente no coloca nada en este intento.
        if (!groundIsFlatEnough(level, placeOrigin, size, origin.getY())) return false;

        return tpl.placeInWorld(level, placeOrigin, placeOrigin, settings, random,
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
    }

    private static boolean groundIsFlatEnough(WorldGenLevel level, BlockPos min, Vec3i size, int baseY) {
        int maxX = min.getX() + size.getX() - 1;
        int maxZ = min.getZ() + size.getZ() - 1;
        // Todas las columnas del hueco real, NO solo las 4 esquinas: un tronco es pequeño (unos
        // pocos bloques por lado), así que barrer el rectángulo entero sigue siendo barato, y
        // solo mirar esquinas dejaba pasar un estanque/hueco justo en el CENTRO del tronco con
        // las 4 esquinas a la misma altura que el origen — el tronco quedaba flotando por en
        // medio aunque sus dos extremos apoyaran bien (bug reportado en juego, seguía pasando
        // tras el primer arreglo de esquinas).
        for (int x = min.getX(); x <= maxX; x++) {
            for (int z = min.getZ(); z <= maxZ; z++) {
                // OCEAN_FLOOR_WG ignora fluidos: da la altura del suelo REAL bajo cualquier
                // lámina de agua, a diferencia de WORLD_SURFACE_WG (que cuenta el agua como
                // "superficie" y dejaba pasar columnas sobre un estanque). Con eso ya detectamos
                // desnivel real, pero además hace falta el chequeo de fluido de abajo: un tronco
                // puede caer sobre la orilla de un lago con TODA la huella a la misma altura que
                // el origen (el lago está al ras) y aun así quedar medio sumergido si alguna
                // columna cae dentro del agua — de ahí el bug reportado de troncos "encima del
                // agua"/"en el aire" (la lámina no se ve como apoyo).
                int h = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                if (Math.abs(h - baseY) > TERRAIN_TOLERANCE) return false;
                if (!level.getBlockState(new BlockPos(x, h, z)).getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }
}
