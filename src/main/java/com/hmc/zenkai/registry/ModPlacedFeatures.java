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
import net.minecraft.world.level.block.Block;
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

    //  Katchin 
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_OVERWORLD = registerKey("katchin_ore_overworld");
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_ROCKY     = registerKey("katchin_ore_rocky");
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_HFIL      = registerKey("katchin_ore_hfil");
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_SKY       = registerKey("katchin_ore_sky");

    //  Vegetación de superficie 
    public static final ResourceKey<PlacedFeature> ROCKY_DEAD_BUSH_KEY   = registerKey("rocky_dead_bush");
    public static final ResourceKey<PlacedFeature> HFIL_DRY_GRASS_KEY    = registerKey("hfil_dry_grass");
    public static final ResourceKey<PlacedFeature> HFIL_DEAD_BUSH_KEY    = registerKey("hfil_dead_bush");

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