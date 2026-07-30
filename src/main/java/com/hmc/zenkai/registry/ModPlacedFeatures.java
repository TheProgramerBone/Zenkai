package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.VerticalAnchor;
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

    }

    private static ResourceKey<PlacedFeature> registerKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
    }

    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }
}