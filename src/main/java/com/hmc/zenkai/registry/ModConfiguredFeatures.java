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

/**
 * Cadena de worldgen: CF -> PF -> BM (configured feature, placed feature, biome modifier).
 * Aquí se define QUÉ se genera y de qué tamaño; el DÓNDE y el CUÁNTO viven en
 * ModPlacedFeatures, y a qué biomas llega, en ModBiomeModifiers.
 *
 * Sobre el segundo parámetro float de OreConfiguration (discardChanceOnAirExposure): NO
 * regula cuánto mineral hay. Solo descarta los bloques de la veta que quedan tocando aire,
 * que bajo tierra son una minoría. Su efecto real es si la veta ASOMA en la pared de una
 * cueva o hay que picarla a ciegas. Por eso las variantes "que se buscan" van a 0.0F y las
 * de fondo a 0.5F.
 */
public class ModConfiguredFeatures {

    // ── Katchin ──────────────────────────────────────────────────────────────
    /** Overworld genérico. Calibrado contra el diamante. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> KATCHIN_ORE_KEY         = registerKey("katchin_ore");
    /** Rocky wasteland. Calibrado contra el oro y visible en cuevas. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> KATCHIN_ORE_EXPOSED_KEY = registerKey("katchin_ore_exposed");
    /** HFIL, el subsuelo del Otherworld. Calibrado contra el hierro. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> KATCHIN_ORE_OW_KEY      = registerKey("katchin_ore_otherworld");
    /** Solo hfil_blood_shore, se SUMA a KATCHIN_ORE_OW_KEY — veta mayor, mismo patrón que KATCHIN_ORE_EXPOSED_KEY. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> KATCHIN_ORE_OW_EXPOSED_KEY = registerKey("katchin_ore_otherworld_exposed");
    /** Islas flotantes del Otherworld. Veta pequeña, ver abajo. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> KATCHIN_ORE_SKY_KEY     = registerKey("katchin_ore_sky");

    // ── Otherworld ───────────────────────────────────────────────────────────
    public static final ResourceKey<ConfiguredFeature<?, ?>> OTHERWORLD_CLOUDS_KEY = registerKey("otherworld_clouds");

    // ── Decoración de biomas sin árboles ────────────────────────────────────
    /** Tronco caído, madera vanilla. Ver FallenLogFeature. Compartida por rocky_wasteland
     *  y los 3 biomas de HFIL — el reparto de rareza vive en ModPlacedFeatures. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> FALLEN_LOG_KEY = registerKey("fallen_log");

    /** Clúster de pinchos altos del HFIL. Ver HfilSpikeFeature. Solo hfil_needle_wastes hoy (ver
     *  ModBiomeGen) — el reparto de rareza vive en ModPlacedFeatures. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HFIL_SPIKE_KEY = registerKey("hfil_spike");

    /** Misma forma que HFIL_SPIKE_KEY pero con la roca cálida de las dunas (ver
     *  ModFeatures.HFIL_CINDER_SPIKE) — exclusiva de hfil_cinder_dunes. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HFIL_CINDER_SPIKE_KEY = registerKey("hfil_cinder_spike");

    /** Cañón de agujas rocosas (hoodoos/mesas) de rocky_wasteland. Ver RockyWastelandSpireFeature
     *  — el reparto de rareza vive en ModPlacedFeatures. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> ROCKY_WASTELAND_SPIRE_KEY = registerKey("rocky_wasteland_spire");

    /** Montón de huesos/calavera del HFIL. Ver HfilBonePileFeature. Los 3 biomas HFIL — el
     *  reparto de rareza vive en ModPlacedFeatures. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HFIL_BONE_PILE_KEY = registerKey("hfil_bone_pile");

    /** Afloramiento de Katchin visible en superficie del HFIL, DELIBERADO — ver
     *  HfilOreBoulderFeature. Los 3 biomas HFIL, el reparto de rareza (más frecuente en
     *  blood_shore, más raro en needle_wastes/cinder_dunes) vive en ModPlacedFeatures. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HFIL_ORE_BOULDER_KEY = registerKey("hfil_ore_boulder");

    /** Charco de "sangre" (agua vainilla — el tinte lo pone WATER_COLOR del bioma, ver
     *  ModBiomeGen.hfilEffects). Compartido por los 3 biomas HFIL, en el mismo paso LAKES que
     *  ya usan los lagos de lava de BiomeDefaultFeatures.addDefaultCarversAndLakes — al ser dos
     *  features independientes en el mismo paso, se intercalan solas por el terreno sin
     *  necesitar lógica propia que alterne entre una y otra. Fase 5 del rework (ver
     *  .claude/pendiente/hfil-rework-propuesta.md): usa HfilBloodPoolFeature en vez de
     *  Feature.LAKE — LakeFeature.Configuration solo expone fluido+barrier, no la forma del
     *  hueco, y en juego se veía como un disco tapado en vez de un charco abierto. Ver
     *  HfilBloodPoolFeature para el detalle. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> HFIL_LAKE_BLOOD_KEY = registerKey("hfil_lake_blood");

    // ── Namek: menas ─────────────────────────────────────────────────────────
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
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_CRYSTAL_ORE     = registerKey("namek_crystal_ore");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ENERGY_CRYSTAL_ORE    = registerKey("energy_crystal_ore");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SACRED_STONE_ORE      = registerKey("sacred_stone_ore");

    // ── Namek: vegetación ────────────────────────────────────────────────────
    public static final ResourceKey<ConfiguredFeature<?, ?>> AJISA_TREE         = registerKey("ajisa_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> AJISA_FLOWER_PATCH = registerKey("ajisa_flower_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> NAMEK_GRASS_PATCH  = registerKey("namek_grass_patch");

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        RuleTest stoneReplaceables     = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest deepslateReplaceables = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);

        // ── Katchin ──────────────────────────────────────────────────────────
        // DOS objetivos: piedra y deepslate. Hacen falta los dos porque la banda del overworld
        // va de y=-144 a y=16, o sea casi entera por debajo de la transición a deepslate. Con
        // solo stoneReplaceables no saldría prácticamente nada donde más se busca.
        List<OreConfiguration.TargetBlockState> katchinOres = List.of(
                OreConfiguration.target(stoneReplaceables,
                        ModBlocks.KATCHIN_ORE.get().defaultBlockState()),
                OreConfiguration.target(deepslateReplaceables,
                        ModBlocks.DEEPSLATE_KATCHIN_ORE.get().defaultBlockState()));

        // Overworld genérico: veta de 6 con descarte del 50% al aire. No la encuentras
        // paseando por una cueva; hay que picar. Es el goteo, no la fuente.
        register(context, KATCHIN_ORE_KEY, Feature.ORE,
                new OreConfiguration(katchinOres, 6, 0.5F));

        // Rocky wasteland: veta mayor y SIN descarte. Esa es la diferencia real del bioma —
        // aquí el katchin se ve en la pared de la cueva. Se SUMA a la genérica, no la
        // sustituye: el bioma está en is_overworld y recibe las dos.
        register(context, KATCHIN_ORE_EXPOSED_KEY, Feature.ORE,
                new OreConfiguration(katchinOres, 9, 0.0F));

        // Otherworld (HFIL): UN solo objetivo porque el default_block de esa dimensión es un
        // único bloque en todo el rango (HFIL_SCORCHED_STONE desde el rediseño de atmósfera,
        // ver ModBlocks) — no hay deepslate que reemplazar, así que DEEPSLATE_KATCHIN_ORE
        // es exclusivo del overworld. Veta de 9 para igualar al hierro: vainilla reparte
        // 10x9 + 10x4 = 130 en la franja profunda y aquí son 15 tiradas x 9 = 135.
        register(context, KATCHIN_ORE_OW_KEY, Feature.ORE, new OreConfiguration(
                List.of(OreConfiguration.target(stoneReplaceables,
                        ModBlocks.KATCHIN_ORE.get().defaultBlockState())), 9, 0.0F));

        // hfil_blood_shore: veta mayor, se SUMA a KATCHIN_ORE_OW_KEY (no la sustituye) — mismo
        // patrón que KATCHIN_ORE_EXPOSED_KEY con la genérica del overworld. Refuerza a propósito
        // la peculiaridad "se ve mineral en la costa de sangre" (que ya salía sola de la
        // surface_rule, floor=HFIL_SCORCHED_STONE sin capa de tierra encima en este bioma).
        register(context, KATCHIN_ORE_OW_EXPOSED_KEY, Feature.ORE, new OreConfiguration(
                List.of(OreConfiguration.target(stoneReplaceables,
                        ModBlocks.KATCHIN_ORE.get().defaultBlockState())), 12, 0.0F));

        // Islas flotantes: mismo objetivo que HFIL pero NO su tamaño, y por eso es una
        // configured feature aparte. En una plataforma de pocos bloques de grosor una veta de
        // 9 no cabe: asoma por las dos caras y la isla queda con costra de mineral.
        register(context, KATCHIN_ORE_SKY_KEY, Feature.ORE, new OreConfiguration(
                List.of(OreConfiguration.target(stoneReplaceables,
                        ModBlocks.KATCHIN_ORE.get().defaultBlockState())), 4, 0.0F));

        // ── Otherworld ───────────────────────────────────────────────────────
        register(context, OTHERWORLD_CLOUDS_KEY,
                ModFeatures.CLOUD_LAYER.get(), NoneFeatureConfiguration.INSTANCE);

        // ── Decoración de biomas sin árboles ─────────────────────────────────
        register(context, FALLEN_LOG_KEY,
                ModFeatures.FALLEN_LOG.get(), NoneFeatureConfiguration.INSTANCE);
        register(context, HFIL_SPIKE_KEY,
                ModFeatures.HFIL_SPIKE.get(), NoneFeatureConfiguration.INSTANCE);
        register(context, HFIL_CINDER_SPIKE_KEY,
                ModFeatures.HFIL_CINDER_SPIKE.get(), NoneFeatureConfiguration.INSTANCE);
        register(context, ROCKY_WASTELAND_SPIRE_KEY,
                ModFeatures.ROCKY_WASTELAND_SPIRE.get(), NoneFeatureConfiguration.INSTANCE);
        register(context, HFIL_BONE_PILE_KEY,
                ModFeatures.HFIL_BONE_PILE.get(), NoneFeatureConfiguration.INSTANCE);
        register(context, HFIL_ORE_BOULDER_KEY,
                ModFeatures.HFIL_ORE_BOULDER.get(), NoneFeatureConfiguration.INSTANCE);

        // Ya NO es Feature.LAKE (ver comentario de la key arriba) — HfilBloodPoolFeature cava y
        // rellena a mano, sin capa de barrier por encima del agua, para que el charco quede
        // siempre abierto en vez de tapado.
        register(context, HFIL_LAKE_BLOOD_KEY,
                ModFeatures.HFIL_BLOOD_POOL.get(), NoneFeatureConfiguration.INSTANCE);

        // ── Namek: menas ─────────────────────────────────────────────────────
        // Cada una apunta al tag PROPIO: ningún mod que añada menas al overworld puede colarse
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
        namekOre(context, NAMEK_CRYSTAL_ORE,    namekRock, ModBlocks.NAMEK_CRYSTAL_ORE,      5, 0.0F);
        namekOre(context, ENERGY_CRYSTAL_ORE,   namekRock, ModBlocks.ENERGY_CRYSTAL_ORE,     4, 0.5F);
        namekOre(context, SACRED_STONE_ORE,     namekRock, ModBlocks.SACRED_STONE_ORE,      12, 0.0F);

        // ── Namek: vegetación ────────────────────────────────────────────────
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
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
    }

    private static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(
            BootstrapContext<ConfiguredFeature<?, ?>> context,
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