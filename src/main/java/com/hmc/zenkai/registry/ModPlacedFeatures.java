package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.*;

import java.util.List;

public class ModPlacedFeatures {
    // CF -> PF -> BM

    public static final ResourceKey<PlacedFeature> TERRAGEM_ORE_PLACED_KEY = registerKey("terragem_ore_placed");

    /** Matojos secos del rocky_wasteland. Antes era un JSON suelto; vive aquí porque el
     *  bootstrap del bioma lo resuelve con getOrThrow y solo ve el registro de datagen. */
    public static final ResourceKey<PlacedFeature> ROCKY_DEAD_BUSH_KEY = registerKey("rocky_dead_bush");
    /** Hierba seca de HFIL. short_grass sobre coarse_dirt: no decae sin luz y se tiñe con
     *  el grass_color del bioma, que es lo que le da el aspecto de pasto muerto. */
    public static final ResourceKey<PlacedFeature> HFIL_DRY_GRASS_KEY = registerKey("hfil_dry_grass");
    public static final ResourceKey<PlacedFeature> HFIL_DEAD_BUSH_KEY = registerKey("hfil_dead_bush");

    public static final ResourceKey<PlacedFeature> OTHERWORLD_CLOUDS_KEY    = registerKey("otherworld_clouds");
    public static final ResourceKey<PlacedFeature> OTHERWORLD_FLOWERS_KEY   = registerKey("otherworld_flowers");
    public static final ResourceKey<PlacedFeature> OTHERWORLD_DEAD_BUSH_KEY = registerKey("otherworld_dead_bush");

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
    public static final ResourceKey<PlacedFeature> AJISA_FOREST = registerKey("ajisa_forest");
    public static final ResourceKey<PlacedFeature> AJISA_PLAINS = registerKey("ajisa_plains");
    public static final ResourceKey<PlacedFeature> AJISA_HILLS  = registerKey("ajisa_hills");
    public static final ResourceKey<PlacedFeature> AJISA_SHORE  = registerKey("ajisa_shore");
    public static final ResourceKey<PlacedFeature> AJISA_FLOWERS = registerKey("ajisa_flowers");
    public static final ResourceKey<PlacedFeature> NAMEK_GRASS   = registerKey("namek_grass");

    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        var configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        register(context, TERRAGEM_ORE_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.OVERWORLD_TERRAGEM_ORE_KEY),
                ModOrePlacement.commonOrePlacement(12, HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));

        register(context, ROCKY_DEAD_BUSH_KEY,
                configuredFeatures.getOrThrow(VegetationFeatures.PATCH_DEAD_BUSH),
                List.of(NoiseThresholdCountPlacement.of(-0.8D, 0, 7),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                        BiomeFilter.biome()));

        register(context, HFIL_DRY_GRASS_KEY,
                configuredFeatures.getOrThrow(VegetationFeatures.PATCH_TAIGA_GRASS),
                List.of(NoiseThresholdCountPlacement.of(-0.4D, 2, 8),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                        BiomeFilter.biome()));

        register(context, HFIL_DEAD_BUSH_KEY,
                configuredFeatures.getOrThrow(VegetationFeatures.PATCH_DEAD_BUSH),
                List.of(NoiseThresholdCountPlacement.of(-0.8D, 0, 4),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_WORLD_SURFACE,
                        BiomeFilter.biome()));

        // Sin modificadores: la feature se coloca sola en cada chunk y se posiciona ella misma.
        register(context, OTHERWORLD_CLOUDS_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.OTHERWORLD_CLOUDS_KEY),
                List.of());

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

        // Rangos de altura y tamaños de veta CLAVADOS a vainilla. Solo cambia el count:
        // cobre +25%, oro +20%, diamante -70% repartido entre sus cuatro variantes.
        // Las bandas altas se recortan a y=192 porque el noise de Namek termina ahí; las de
        // vainilla llegan a 320/384 y ese tramo aquí es aire.

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

        // Cobre: 16 -> 20 (+25%)
        register(context, NAMEK_COPPER, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_COPPER),
                ModOrePlacement.commonOrePlacement(20, HeightRangePlacement.triangle(VerticalAnchor.absolute(-16), VerticalAnchor.absolute(112))));

        // Oro: 4 -> 5 (+20%). La veta profunda queda igual: su count ya es UniformInt(0,1).
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

        // Diamante: 7 -> 2, medium 2 -> 1, buried 4 -> 1, y la large pasa de 1/9 a 1/30.
        register(context, NAMEK_DIAMOND, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_DIAMOND_SMALL),
                ModOrePlacement.commonOrePlacement(2, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));
        register(context, NAMEK_DIAMOND_MEDIUM, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_DIAMOND_MEDIUM),
                ModOrePlacement.commonOrePlacement(1, HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-4))));
        register(context, NAMEK_DIAMOND_LARGE, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_DIAMOND_LARGE),
                ModOrePlacement.rareOrePlacement(30, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));
        register(context, NAMEK_DIAMOND_BURIED, configuredFeatures.getOrThrow(ModConfiguredFeatures.NAMEK_DIAMOND_BURIED),
                ModOrePlacement.commonOrePlacement(1, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));

        Holder<ConfiguredFeature<?, ?>> ajisa = configuredFeatures.getOrThrow(ModConfiguredFeatures.AJISA_TREE);

        // Bosque cerrado: unos 7 intentos por chunk.
        register(context, AJISA_FOREST, ajisa,
                treePlacement(PlacementUtils.countExtra(6, 0.1F, 2)));
        // Llanura: grupos sueltos. noise_threshold_count crea claros y arboledas en vez de
        // reparto uniforme, que es lo que hace que se lean como bosquecillos.
        register(context, AJISA_PLAINS, ajisa,
                treePlacement(NoiseThresholdCountPlacement.of(-0.5D, 0, 3)));
        // Colinas: muy pocos.
        register(context, AJISA_HILLS, ajisa,
                treePlacement(RarityFilter.onAverageOnceEvery(12)));
        // Orilla: alguno aislado.
        register(context, AJISA_SHORE, ajisa,
                treePlacement(RarityFilter.onAverageOnceEvery(40)));

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
        return ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
    }

    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }

    /**
     * Colocación común de árboles. Los dos filtros que importan:
     *  - SurfaceWaterDepthFilter(0) impide que broten dentro del agua.
     *  - wouldSurvive del sapling: solo crecen donde el sapling podría plantarse, o sea
     *    sobre namekian_grass_block y namekian_dirt. Sin él aparecen sobre arena y roca.
     */
    private static List<PlacementModifier> treePlacement(PlacementModifier count) {
        return List.of(
                count,
                InSquarePlacement.spread(),
                SurfaceWaterDepthFilter.forMaxDepth(0),
                PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
                BiomeFilter.biome(),
                BlockPredicateFilter.forPredicate(BlockPredicate.wouldSurvive(
                        ModBlocks.AJISA_SAPLING.get().defaultBlockState(), BlockPos.ZERO)));
    }
}