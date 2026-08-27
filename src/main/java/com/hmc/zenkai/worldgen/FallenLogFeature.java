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
import net.minecraft.world.level.block.state.BlockState;
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
 * Decoración de biomas sin árboles (rocky_wasteland, hfil_needle_wastes/blood_shore/cinder_dunes): elige uno
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
        // mitad enterrado (reportado en juego). Se descarta la colocación entera si CUALQUIER
        // columna de la huella real no tiene suelo sólido justo a la altura de origen (ver
        // groundIsFlatEnough) — igual que una feature vanilla que falla su comprobación de
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
        //
        // A propósito NO se usa Heightmap.Types.* aquí (se usaba OCEAN_FLOOR_WG antes): un
        // heightmap siempre devuelve el bloque sólido MÁS ALTO de TODA la columna, con dos
        // problemas distintos que seguían dejando troncos flotando incluso tras el barrido por
        // columna completa:
        //  1) En el Otherworld una columna puede tener el suelo del HFIL abajo Y una isla
        //     flotante mucho más arriba (mismo caso que documenta ClampedHeightmapPlacement,
        //     usado para el ORIGEN de esta feature) — si una columna del hueco (no la de
        //     origen) cae bajo una isla, el heightmap global reporta la altura de la ISLA, muy
        //     por encima de baseY, así que la comprobación fallaba y descartaba la colocación
        //     de más troncos válidos de los necesarios sin explicar por qué alguno seguía
        //     flotando.
        //  2) Las variantes "_WG" (World-Gen) quedan CONGELADAS justo tras generar el terreno
        //     base, ANTES de que corra cualquier feature de decoración — si otra feature (lago,
        //     manantial...) ya modificó el terreno de una columna del hueco en este mismo chunk
        //     antes de que le toque el turno a esta, el heightmap congelado seguía reportando
        //     la altura VIEJA (de antes de esa modificación) y la comprobación pasaba con datos
        //     obsoletos aunque el terreno REAL bajo esa columna ya no coincidiera con baseY.
        // El sondeo local de abajo evita los dos problemas de raíz: en vez de preguntar "¿cuál
        // es el bloque sólido más alto de esta columna entera?", pregunta directamente "¿hay
        // suelo sólido justo en baseY-1 y hueco libre justo en baseY?" — nunca mira más allá de
        // esa ventana de 2 bloques, así que una isla a y=150 o un heightmap desactualizado no
        // pueden influir en el resultado.
        for (int x = min.getX(); x <= maxX; x++) {
            for (int z = min.getZ(); z <= maxZ; z++) {
                BlockPos groundPos = new BlockPos(x, baseY - 1, z);
                BlockState ground = level.getBlockState(groundPos);
                // Aire (o líquido) justo debajo = hueco/estanque bajo esa columna: no hay
                // suelo real donde apoyar el tronco.
                if (ground.isAir() || !ground.getFluidState().isEmpty()) return false;
                BlockPos logPos = new BlockPos(x, baseY, z);
                BlockState atLog = level.getBlockState(logPos);
                // Bloque sólido o líquido justo donde debería ir el tronco = un bache/roca
                // asoma por encima de baseY en esta columna (o hay agua ahí mismo, la orilla de
                // un lago al ras): el tronco quedaría enterrado o medio sumergido.
                if (!atLog.isAir() || !atLog.getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }
}
