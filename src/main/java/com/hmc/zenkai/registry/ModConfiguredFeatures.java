package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.*;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.CherryFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.CherryTrunkPlacer;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

public class ModConfiguredFeatures {
    // CF -> PF -> BM

    public static final ResourceKey<ConfiguredFeature<?, ?>> OVERWORLD_TERRAGEM_ORE_KEY = registerKey("terragem_ore");
    public static final ResourceKey<ConfiguredFeature<?, ?>> OTHERWORLD_CLOUDS_KEY = registerKey("otherworld_clouds");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_COAL            = registerKey("namek_coal");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_COAL_BURIED     = registerKey("namek_coal_buried");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_IRON            = registerKey("namek_iron");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_IRON_SMALL      = registerKey("namek_iron_small");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_COPPER          = registerKey("namek_copper");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_GOLD            = registerKey("namek_gold");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_GOLD_BURIED     = registerKey("namek_gold_buried");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_REDSTONE        = registerKey("namek_redstone");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_LAPIS           = registerKey("namek_lapis");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_LAPIS_BURIED    = registerKey("namek_lapis_buried");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_DIAMOND_SMALL   = registerKey("namek_diamond_small");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_DIAMOND_MEDIUM  = registerKey("namek_diamond_medium");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_DIAMOND_LARGE   = registerKey("namek_diamond_large");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_DIAMOND_BURIED  = registerKey("namek_diamond_buried");
    public static final ResourceKey<ConfiguredFeature<?, ?>> AJISA_TREE = registerKey("ajisa_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> AJISA_FLOWER_PATCH = registerKey("ajisa_flower_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_GRASS_PATCH  = registerKey("namek_grass_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_CRYSTAL_ORE  = registerKey("namek_crystal_ore");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ENERGY_CRYSTAL_ORE = registerKey("energy_crystal_ore");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SACRED_STONE_ORE   = registerKey("sacred_stone_ore");

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        RuleTest stoneReplaceables = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest deepslateReplaceables = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);

        List<OreConfiguration.TargetBlockState> overworldWarenaiOres = List.of(
                OreConfiguration.target(stoneReplaceables, ModBlocks.TERRAGEM_ORE.get().defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables, ModBlocks.DEEPSLATE_TERRAGEM_ORE.get().defaultBlockState()));

        register(context, OVERWORLD_TERRAGEM_ORE_KEY, Feature.ORE, new OreConfiguration(overworldWarenaiOres, 9));

        register(context, OTHERWORLD_CLOUDS_KEY,
                ModFeatures.CLOUD_LAYER.get(), NoneFeatureConfiguration.INSTANCE);

        // Todas apuntan al tag PROPIO: ningún mod que añada menas al overworld puede colarse
        // en Namek, y estas no pueden aparecer en el overworld.
        RuleTest namekRock = new TagMatchTest(ModTags.Blocks.NAMEKIAN_ORE_REPLACEABLES);

        namekOre(context, NAMEK_COAL,           namekRock, ModBlocks.NAMEKIAN_COAL_ORE,     17, 0.0F);
        namekOre(context, NAMEK_COAL_BURIED,    namekRock, ModBlocks.NAMEKIAN_COAL_ORE,     17, 0.5F);
        namekOre(context, NAMEK_IRON,           namekRock, ModBlocks.NAMEKIAN_IRON_ORE,      9, 0.0F);
        namekOre(context, NAMEK_IRON_SMALL,     namekRock, ModBlocks.NAMEKIAN_IRON_ORE,      4, 0.0F);
        namekOre(context, NAMEK_COPPER,         namekRock, ModBlocks.NAMEKIAN_COPPER_ORE,   10, 0.0F);
        namekOre(context, NAMEK_GOLD,           namekRock, ModBlocks.NAMEKIAN_GOLD_ORE,      9, 0.0F);
        namekOre(context, NAMEK_GOLD_BURIED,    namekRock, ModBlocks.NAMEKIAN_GOLD_ORE,      9, 0.5F);
        namekOre(context, NAMEK_REDSTONE,       namekRock, ModBlocks.NAMEKIAN_REDSTONE_ORE,  8, 0.0F);
        namekOre(context, NAMEK_LAPIS,          namekRock, ModBlocks.NAMEKIAN_LAPIS_ORE,     7, 0.0F);
        namekOre(context, NAMEK_LAPIS_BURIED,   namekRock, ModBlocks.NAMEKIAN_LAPIS_ORE,     7, 0.7F);
        namekOre(context, NAMEK_DIAMOND_SMALL,  namekRock, ModBlocks.NAMEKIAN_DIAMOND_ORE,   4, 0.5F);
        namekOre(context, NAMEK_DIAMOND_MEDIUM, namekRock, ModBlocks.NAMEKIAN_DIAMOND_ORE,   8, 0.5F);
        namekOre(context, NAMEK_DIAMOND_LARGE,  namekRock, ModBlocks.NAMEKIAN_DIAMOND_ORE,  12, 0.7F);
        namekOre(context, NAMEK_DIAMOND_BURIED, namekRock, ModBlocks.NAMEKIAN_DIAMOND_ORE,   8, 1.0F);
        namekOre(context, NAMEK_CRYSTAL_ORE,  namekRock, ModBlocks.NAMEK_CRYSTAL_ORE,   5, 0.0F);
        namekOre(context, ENERGY_CRYSTAL_ORE, namekRock, ModBlocks.ENERGY_CRYSTAL_ORE,  4, 0.5F);
        namekOre(context, SACRED_STONE_ORE,   namekRock, ModBlocks.SACRED_STONE_ORE,   12, 0.0F);

        register(context, AJISA_TREE, Feature.TREE, new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(ModBlocks.AJISA_LOG.get()),
                new CherryTrunkPlacer(                                        // ⚠ firma
                        7, 1, 0,
                        UniformInt.of(1, 3),      // nº de ramas
                        UniformInt.of(2, 4),      // longitud horizontal de cada rama
                        UniformInt.of(-4, -3),    // dónde arrancan, contando desde la copa
                        UniformInt.of(-1, 0)),    // dónde terminan
                BlockStateProvider.simple(ModBlocks.AJISA_LEAVES.get()),
                new CherryFoliagePlacer(                                      // ⚠ firma
                        ConstantInt.of(4),        // radio
                        ConstantInt.of(0),        // offset
                        ConstantInt.of(5),        // altura de la copa
                        0.25F,                    // huecos en la capa ancha inferior
                        0.5F,                     // huecos en las esquinas
                        0.16666667F,              // hojas colgantes
                        0.33333334F),
                new TwoLayersFeatureSize(1, 0, 2))
                .ignoreVines()
                .build());

        // Flor de ajisa: mata de hasta 32 intentos, como las flores de vainilla.
        register(context, AJISA_FLOWER_PATCH, Feature.FLOWER,
                FeatureUtils.simpleRandomPatchConfiguration(32,               // ⚠ helper
                        PlacementUtils.onlyWhenEmpty(Feature.SIMPLE_BLOCK,
                                new SimpleBlockConfiguration(
                                        BlockStateProvider.simple(ModBlocks.AJISA_FLOWER.get())))));

        // Hierba: short_grass VANILLA, que se tiñe con el grass_color del bioma. Cero
        // bloques nuevos y cambia de tono solo si algún día retocas la paleta de Namek.
        register(context, NAMEK_GRASS_PATCH, Feature.RANDOM_PATCH,
                FeatureUtils.simpleRandomPatchConfiguration(32,
                        PlacementUtils.onlyWhenEmpty(Feature.SIMPLE_BLOCK,
                                new SimpleBlockConfiguration(
                                        BlockStateProvider.simple(Blocks.SHORT_GRASS)))));

    }

    public static ResourceKey<ConfiguredFeature<?, ?>> registerKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
    }

    private static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(BootstrapContext<ConfiguredFeature<?, ?>> context,
                                                                                          ResourceKey<ConfiguredFeature<?, ?>> key, F feature, FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }

    /** size = bloques por veta, discard = probabilidad de descartar la veta si está expuesta al aire. */
    private static void namekOre(BootstrapContext<ConfiguredFeature<?, ?>> ctx,
                                 ResourceKey<ConfiguredFeature<?, ?>> key,
                                 RuleTest target, DeferredBlock<Block> ore,
                                 int size, float discardOnAir) {
        register(ctx, key, Feature.ORE, new OreConfiguration(
                List.of(OreConfiguration.target(target, ore.get().defaultBlockState())),
                size, discardOnAir));
    }
}