package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.CloudLayerFeature;
import com.hmc.zenkai.worldgen.placement.ClampedHeightmapPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.*;

import java.util.List;

/**
 * DÓNDE y CUÁNTO. El tamaño de veta y los bloques están en ModConfiguredFeatures; a qué
 * biomas llega cada una, en ModBiomeModifiers y en ModBiomeGen.
 */
public class ModPlacedFeatures {

    /** Techo del suelo del HFIL para ClampedHeightmapPlacement: por DEBAJO del punto más bajo
     *  posible de las islas flotantes, no solo de su base nominal. CloudLayerFeature varía la
     *  altura de arranque de cada columna de nube en ±BASE_VARIATION respecto a CLOUD_BASE_Y
     *  (128), así que una nube puede empezar en y=124, no solo en 128 — con el clamp puesto
     *  en CLOUD_BASE_Y-1=127 (como estaba antes), el escaneo desde 127 hacia abajo todavía
     *  podía toparse con el bloque de nube de una columna baja ANTES de llegar al suelo real
     *  del HFIL; el BiomeFilter descartaba esa colocación por bioma "otherworld" en vez de
     *  "hfil_*", dejando el hueco residual reportado en juego (menos frecuente que el bug
     *  original porque solo afecta a las columnas de nube más bajas, no a todas). Deja margen
     *  de sobra sobre el techo real del suelo (los ores de KATCHIN_ORE_HFIL llegan como mucho
     *  a y=120, ver más abajo). Público: BloodPondStructure (Fase 6 del rework del HFIL) lo
     *  reutiliza como techo de búsqueda de suelo al buscar dónde generar el landmark — mismo
     *  problema de fondo (una isla flotante por encima no debe confundirse con el suelo real),
     *  una sola constante para las dos cosas. */
    public static final int HFIL_FLOOR_MAX_Y =
            CloudLayerFeature.CLOUD_BASE_Y - CloudLayerFeature.BASE_VARIATION - 1;

    //  Katchin
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_OVERWORLD = registerKey("katchin_ore_overworld");
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_ROCKY     = registerKey("katchin_ore_rocky");
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_HFIL      = registerKey("katchin_ore_hfil");
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_SKY       = registerKey("katchin_ore_sky");

    //  Vegetación de superficie 
    public static final ResourceKey<PlacedFeature> ROCKY_DEAD_BUSH_KEY   = registerKey("rocky_dead_bush");
    public static final ResourceKey<PlacedFeature> HFIL_DRY_GRASS_KEY    = registerKey("hfil_dry_grass");
    public static final ResourceKey<PlacedFeature> HFIL_DEAD_BUSH_KEY    = registerKey("hfil_dead_bush");

    //  Troncos caídos (biomas sin árboles)
    public static final ResourceKey<PlacedFeature> FALLEN_LOG_ROCKY_KEY = registerKey("fallen_log_rocky");
    public static final ResourceKey<PlacedFeature> FALLEN_LOG_HFIL_KEY  = registerKey("fallen_log_hfil");

    //  Pinchos del HFIL (rediseño "infierno de Dragon Ball", ver ModBiomeGen)
    public static final ResourceKey<PlacedFeature> HFIL_SPIKE_KEY = registerKey("hfil_spike");

    //  Lago de sangre del HFIL (mismo rediseño, punto 3 — se intercala con los lagos de lava)
    public static final ResourceKey<PlacedFeature> HFIL_LAKE_BLOOD_KEY = registerKey("hfil_lake_blood");

    //  Montón de huesos/calavera del HFIL (mismo rediseño, punto 5)
    public static final ResourceKey<PlacedFeature> HFIL_BONE_PILE_KEY = registerKey("hfil_bone_pile");

    public static final ResourceKey<PlacedFeature> OTHERWORLD_CLOUDS_KEY    = registerKey("otherworld_clouds");
    public static final ResourceKey<PlacedFeature> OTHERWORLD_FLOWERS_KEY   = registerKey("otherworld_flowers");
    public static final ResourceKey<PlacedFeature> OTHERWORLD_DEAD_BUSH_KEY = registerKey("otherworld_dead_bush");

    //  Namek: menas 
    public static final ResourceKey<PlacedFeature> NAMEK_COAL_UPPER      = registerKey("namek_coal_upper");
    public static final ResourceKey<PlacedFeature> NAMEK_COAL_LOWER      = registerKey("namek_coal_lower");
    public static final ResourceKey<PlacedFeature> NAMEK_IRON_UPPER      = registerKey("namek_iron_upper");
    public static final ResourceKey<PlacedFeature> NAMEK_IRON_MIDDLE     = registerKey("namek_iron_middle");
    public static final ResourceKey<PlacedFeature> NAMEK_IRON_SMALL      = registerKey("namek_iron_small");
    public static final ResourceKey<PlacedFeature> NAMEK_COPPER          = registerKey("namek_copper");
    public static final ResourceKey<PlacedFeature> NAMEK_GOLD            = registerKey("namek_gold");
    public static final ResourceKey<PlacedFeature> NAMEK_GOLD_LOWER      = registerKey("namek_gold_lower");
    public static final ResourceKey<PlacedFeature> NAMEK_REDSTONE        = registerKey("namek_redstone");
    public static final ResourceKey<PlacedFeature> NAMEK_REDSTONE_LOWER  = registerKey("namek_redstone_lower");
    public static final ResourceKey<PlacedFeature> NAMEK_LAPIS           = registerKey("namek_lapis");
    public static final ResourceKey<PlacedFeature> NAMEK_LAPIS_BURIED    = registerKey("namek_lapis_buried");
    public static final ResourceKey<PlacedFeature> NAMEK_DIAMOND         = registerKey("namek_diamond");
    public static final ResourceKey<PlacedFeature> NAMEK_DIAMOND_MEDIUM  = registerKey("namek_diamond_medium");
    public static final ResourceKey<PlacedFeature> NAMEK_DIAMOND_LARGE   = registerKey("namek_diamond_large");
    public static final ResourceKey<PlacedFeature> NAMEK_DIAMOND_BURIED  = registerKey("namek_diamond_buried");
    public static final ResourceKey<PlacedFeature> NAMEK_CRYSTAL         = registerKey("namek_crystal");
    public static final ResourceKey<PlacedFeature> ENERGY_CRYSTAL        = registerKey("energy_crystal");
    public static final ResourceKey<PlacedFeature> ENERGY_CRYSTAL_RARE   = registerKey("energy_crystal_rare");
    public static final ResourceKey<PlacedFeature> SACRED_STONE          = registerKey("sacred_stone");

    //  Namek: vegetación
    public static final ResourceKey<PlacedFeature> AJISA_FOREST  = registerKey("ajisa_forest");
    public static final ResourceKey<PlacedFeature> AJISA_PLAINS  = registerKey("ajisa_plains");
    public static final ResourceKey<PlacedFeature> AJISA_HILLS   = registerKey("ajisa_hills");
    public static final ResourceKey<PlacedFeature> AJISA_SHORE   = registerKey("ajisa_shore");
    public static final ResourceKey<PlacedFeature> AJISA_FLOWERS = registerKey("ajisa_flowers");
    public static final ResourceKey<PlacedFeature> NAMEK_GRASS   = registerKey("namek_grass");

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        var configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        //  Katchin ─
        register(context, KATCHIN_ORE_OVERWORLD,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.KATCHIN_ORE_KEY),
                ModOrePlacement.commonOrePlacement(14, HeightRangePlacement.triangle(
                        VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));

        register(context, KATCHIN_ORE_ROCKY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.KATCHIN_ORE_EXPOSED_KEY),
                ModOrePlacement.commonOrePlacement(6, HeightRangePlacement.triangle(
                        VerticalAnchor.aboveBottom(-64), VerticalAnchor.absolute(32))));

        register(context, KATCHIN_ORE_HFIL,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.KATCHIN_ORE_OW_KEY),
                ModOrePlacement.commonOrePlacement(15, HeightRangePlacement.uniform(
                        VerticalAnchor.absolute(-60), VerticalAnchor.absolute(120))));

        register(context, KATCHIN_ORE_SKY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.KATCHIN_ORE_SKY_KEY),
                ModOrePlacement.commonOrePlacement(3, HeightRangePlacement.uniform(
                        VerticalAnchor.absolute(130), VerticalAnchor.absolute(190))));

        // Vegetación de superficie
        // HEIGHTMAP_OCEAN_FLOOR + SurfaceWaterDepthFilter(0), no HEIGHTMAP_WORLD_SURFACE: ese
        // heightmap cuenta el agua como "superficie" (cualquier bloque no-aire cuenta), así
        // que en una columna con laguna/costa la mata se colocaba flotando en la lámina de
        // agua — mismo bug que los troncos caídos de abajo, mismo arreglo que ya usa
        // correctamente treePlacement() para los árboles de Namek.
        register(context, ROCKY_DEAD_BUSH_KEY,
                configuredFeatures.getOrThrow(VegetationFeatures.PATCH_DEAD_BUSH),
                List.of(NoiseThresholdCountPlacement.of(-0.8D, 0, 7),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
                        SurfaceWaterDepthFilter.forMaxDepth(0),
                        BiomeFilter.biome()));

        // Rediseño de identidad HFIL, Fase 3 (ver .claude/pendiente/hfil-rework-propuesta.md):
        // bajado de (2, 8) a (1, 3) intentos por chunk — la hierba pasa de cobertura densa a
        // matojos dispersos, para que en hfil_needle_wastes el terreno base se lea "yermo
        // rocoso" y los pinchos (HFIL_SPIKE_KEY, más abajo) sean lo primero que define el
        // horizonte, no una pradera de fondo compitiendo con ellos.
        register(context, HFIL_DRY_GRASS_KEY,
                configuredFeatures.getOrThrow(VegetationFeatures.PATCH_TAIGA_GRASS),
                List.of(NoiseThresholdCountPlacement.of(-0.4D, 1, 3),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));

        register(context, HFIL_DEAD_BUSH_KEY,
                configuredFeatures.getOrThrow(VegetationFeatures.PATCH_DEAD_BUSH),
                List.of(NoiseThresholdCountPlacement.of(-0.8D, 0, 4),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));

        register(context, OTHERWORLD_CLOUDS_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.OTHERWORLD_CLOUDS_KEY),
                List.of());

        //  Troncos caídos: decoración puntual para poder hacerse un set de madera básico, no
        // un bosque, pero sin pasarse de escasos — parte de los intentos se descartan en
        // FallenLogFeature.groundIsFlatEnough (terreno irregular), así que la rareza de aquí
        // ya cuenta con que no todos los intentos llegan a colocar nada. Dos placed features
        // por si algún día se quiere ajustar la rareza de cada lado por separado, aunque hoy
        // comparten configured feature.
        // Mismo motivo que ROCKY_DEAD_BUSH_KEY arriba: HEIGHTMAP_WORLD_SURFACE deja pasar
        // columnas de agua, así que el origen del tronco se colocaba sobre la lámina en vez
        // de rechazarse. groundIsFlatEnough (dentro de FallenLogFeature) ya comprueba el
        // fondo real bajo las 4 esquinas del hueco tras rotar/desplazar; este filtro cubre la
        // columna de ORIGEN antes de eso.
        register(context, FALLEN_LOG_ROCKY_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.FALLEN_LOG_KEY),
                List.of(RarityFilter.onAverageOnceEvery(14),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
                        SurfaceWaterDepthFilter.forMaxDepth(0),
                        BiomeFilter.biome()));

        register(context, FALLEN_LOG_HFIL_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.FALLEN_LOG_KEY),
                List.of(RarityFilter.onAverageOnceEvery(12),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));

        // Más raro que los troncos/matas a propósito: son formaciones dramáticas pensadas para
        // dominar el horizonte, no decoración de relleno — ver HfilSpikeFeature. Mismo
        // ClampedHeightmapPlacement que el resto del suelo del HFIL (evita que el origen del
        // clúster herede la altura de una isla flotante del Otherworld).
        // Rediseño de identidad HFIL, Fase 3 (ver .claude/pendiente/hfil-rework-propuesta.md):
        // subido de 1/120 a 1/40 — con la identidad "Needle Wastes", los pinchos deben ser lo
        // que define el horizonte nada más entrar al bioma, no un evento raro/accesorio como
        // en el rediseño anterior. Sigue siendo bastante más raro que HFIL_BONE_PILE_KEY (1/16)
        // o los troncos (1/12): es la pieza dramática del paisaje, no relleno.
        register(context, HFIL_SPIKE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_SPIKE_KEY),
                List.of(RarityFilter.onAverageOnceEvery(40),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));

        // Mismo chance (200) que minecraft:lake_lava_surface — al vivir en el mismo paso LAKES
        // que los lagos de lava (BiomeDefaultFeatures.addDefaultCarversAndLakes, ver
        // ModBiomeGen.hfilBase) con la misma probabilidad, ninguna de las dos domina sobre la
        // otra: se intercalan por el terreno solas. A diferencia del lago de lava vainilla, que
        // usa el heightmap WORLD_SURFACE_WG (puede capturar la altura de una isla flotante del
        // Otherworld en vez del suelo real del HFIL — mismo problema de fondo que ya resolvió
        // ClampedHeightmapPlacement para el resto de esta lista), este SÍ usa el clamp propio.
        register(context, HFIL_LAKE_BLOOD_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_LAKE_BLOOD_KEY),
                List.of(RarityFilter.onAverageOnceEvery(200),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));

        // Decoración puntual barata: más común que los troncos, en línea con matas/hierba.
        register(context, HFIL_BONE_PILE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_BONE_PILE_KEY),
                List.of(RarityFilter.onAverageOnceEvery(16),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));

        register(context, OTHERWORLD_FLOWERS_KEY,
                configuredFeatures.getOrThrow(VegetationFeatures.FLOWER_DEFAULT),
                List.of(NoiseThresholdCountPlacement.of(-0.6D, 2, 5),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP,
                        BiomeFilter.biome()));

        register(context, OTHERWORLD_DEAD_BUSH_KEY,
                configuredFeatures.getOrThrow(VegetationFeatures.PATCH_DEAD_BUSH),
                List.of(RarityFilter.onAverageOnceEvery(2),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                        BiomeFilter.biome()));

        // Namek: menas 
        register(context, NAMEK_COAL_UPPER, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_COAL),
                ModOrePlacement.commonOrePlacement(30, HeightRangePlacement.uniform(VerticalAnchor.absolute(136), VerticalAnchor.absolute(192))));
        register(context, NAMEK_COAL_LOWER, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_COAL_BURIED),
                ModOrePlacement.rareOrePlacement(20, HeightRangePlacement.triangle(VerticalAnchor.absolute(0), VerticalAnchor.absolute(192))));

        register(context, NAMEK_IRON_UPPER, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_IRON),
                ModOrePlacement.commonOrePlacement(90, HeightRangePlacement.triangle(VerticalAnchor.absolute(80), VerticalAnchor.absolute(192))));
        register(context, NAMEK_IRON_MIDDLE, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_IRON),
                ModOrePlacement.commonOrePlacement(10, HeightRangePlacement.triangle(VerticalAnchor.absolute(-24), VerticalAnchor.absolute(56))));
        register(context, NAMEK_IRON_SMALL, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_IRON_SMALL),
                ModOrePlacement.commonOrePlacement(10, HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(72))));

        register(context, NAMEK_COPPER, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_COPPER),
                ModOrePlacement.commonOrePlacement(20, HeightRangePlacement.triangle(VerticalAnchor.absolute(-16), VerticalAnchor.absolute(112))));

        register(context, NAMEK_GOLD, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_GOLD),
                ModOrePlacement.commonOrePlacement(5, HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(32))));
        register(context, NAMEK_GOLD_LOWER, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_GOLD_BURIED),
                ModOrePlacement.orePlacement(CountPlacement.of(UniformInt.of(0, 1)),
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-48))));

        register(context, NAMEK_REDSTONE, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_REDSTONE),
                ModOrePlacement.commonOrePlacement(4, HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(15))));
        register(context, NAMEK_REDSTONE_LOWER, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_REDSTONE),
                ModOrePlacement.commonOrePlacement(8, HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-32))));

        register(context, NAMEK_LAPIS, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_LAPIS),
                ModOrePlacement.commonOrePlacement(2, HeightRangePlacement.triangle(VerticalAnchor.absolute(-32), VerticalAnchor.absolute(32))));
        register(context, NAMEK_LAPIS_BURIED, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_LAPIS_BURIED),
                ModOrePlacement.commonOrePlacement(4, HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(64))));

        register(context, NAMEK_DIAMOND, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_DIAMOND_SMALL),
                ModOrePlacement.commonOrePlacement(2, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));
        register(context, NAMEK_DIAMOND_MEDIUM, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_DIAMOND_MEDIUM),
                ModOrePlacement.commonOrePlacement(1, HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-4))));
        register(context, NAMEK_DIAMOND_LARGE, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_DIAMOND_LARGE),
                ModOrePlacement.rareOrePlacement(30, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));
        register(context, NAMEK_DIAMOND_BURIED, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_DIAMOND_BURIED),
                ModOrePlacement.commonOrePlacement(1, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));

        register(context, NAMEK_CRYSTAL, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_CRYSTAL_ORE),
                ModOrePlacement.commonOrePlacement(8,
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-48), VerticalAnchor.absolute(48))));

        register(context, ENERGY_CRYSTAL, configuredFeatures.getOrThrow(ModConfiguredFeatures.ENERGY_CRYSTAL_ORE),
                ModOrePlacement.commonOrePlacement(4,
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-16))));

        register(context, ENERGY_CRYSTAL_RARE, configuredFeatures.getOrThrow(ModConfiguredFeatures.ENERGY_CRYSTAL_ORE),
                ModOrePlacement.commonOrePlacement(2,
                        HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-16))));

        register(context, SACRED_STONE, configuredFeatures.getOrThrow(ModConfiguredFeatures.SACRED_STONE_ORE),
                ModOrePlacement.commonOrePlacement(14,
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(0), VerticalAnchor.absolute(128))));

        //  Namek: vegetación
        Holder<ConfiguredFeature<?, ?>> ajisa = configuredFeatures.getOrThrow(ModConfiguredFeatures.AJISA_TREE);

        Block saplingBlock = ModBlocks.AJISA_SAPLING.get();

        register(context, AJISA_FOREST, ajisa,
                treePlacement(NoiseThresholdCountPlacement.of(-0.5D, 0, 3), saplingBlock));
        register(context, AJISA_PLAINS, ajisa,
                treePlacement(NoiseThresholdCountPlacement.of(-0.5D, 0, 1), saplingBlock));
        register(context, AJISA_HILLS, ajisa,
                treePlacement(RarityFilter.onAverageOnceEvery(12), saplingBlock));
        register(context, AJISA_SHORE, ajisa,
                treePlacement(RarityFilter.onAverageOnceEvery(40), saplingBlock));

        register(context, AJISA_FLOWERS,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.AJISA_FLOWER_PATCH),
                List.of(RarityFilter.onAverageOnceEvery(6), InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP, BiomeFilter.biome()));

        register(context, NAMEK_GRASS,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_GRASS_PATCH),
                List.of(CountPlacement.of(5), InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP, BiomeFilter.biome()));
    }

    private static ResourceKey<PlacedFeature> registerKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
    }

    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key,
                                 Holder<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }

    /**
     * Colocación común de árboles.
     * Orden corregido: 
     * 1. Distribución de cantidad y dispersión horizontal.
     * 2. Ajuste de altura en la superficie (HEIGHTMAP_OCEAN_FLOOR).
     * 3. Filtro de profundidad de agua.
     * 4. Validación del predicado sobre la superficie calculada.
     * 5. BiomeFilter al final.
     */
    private static List<PlacementModifier> treePlacement(PlacementModifier count, Block sapling) {
        return List.of(
                count,
                InSquarePlacement.spread(),
                PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
                SurfaceWaterDepthFilter.forMaxDepth(0),
                BlockPredicateFilter.forPredicate(BlockPredicate.wouldSurvive(
                        sapling.defaultBlockState(), BlockPos.ZERO)),
                BiomeFilter.biome());
    }
}