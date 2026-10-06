package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.CloudLayerFeature;
import com.hmc.zenkai.worldgen.placement.ClampedHeightmapPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
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
import net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight;
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
    /** Solo hfil_blood_shore, se SUMA a KATCHIN_ORE_HFIL — ver ModConfiguredFeatures.KATCHIN_ORE_OW_EXPOSED_KEY. */
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_HFIL_EXPOSED = registerKey("katchin_ore_hfil_exposed");
    public static final ResourceKey<PlacedFeature> KATCHIN_ORE_SKY       = registerKey("katchin_ore_sky");

    //  Vegetación de superficie 
    public static final ResourceKey<PlacedFeature> ROCKY_DEAD_BUSH_KEY   = registerKey("rocky_dead_bush");
    public static final ResourceKey<PlacedFeature> HFIL_DEAD_BUSH_KEY    = registerKey("hfil_dead_bush");

    //  Troncos caídos (biomas sin árboles)
    public static final ResourceKey<PlacedFeature> FALLEN_LOG_ROCKY_KEY = registerKey("fallen_log_rocky");

    //  Pinchos del HFIL (rediseño "infierno de Dragon Ball", ver ModBiomeGen)
    public static final ResourceKey<PlacedFeature> HFIL_SPIKE_KEY = registerKey("hfil_spike");
    /** Misma forma, roca cálida propia de las dunas — exclusiva de cinder_dunes. */
    public static final ResourceKey<PlacedFeature> HFIL_CINDER_SPIKE_KEY = registerKey("hfil_cinder_spike");

    //  Cañón de agujas rocosas de rocky_wasteland (imagen de referencia, sesión 2026-09-04)
    public static final ResourceKey<PlacedFeature> ROCKY_WASTELAND_SPIRE_KEY = registerKey("rocky_wasteland_spire");

    //  Lago de sangre del HFIL (mismo rediseño, punto 3 — se intercala con los lagos de lava)
    public static final ResourceKey<PlacedFeature> HFIL_LAKE_BLOOD_KEY = registerKey("hfil_lake_blood");

    /** Manantial de lava EXTRA, exclusivo de cinder_dunes, se SUMA al minecraft:spring_lava que
     *  ya llega a los 3 biomas vía BiomeDefaultFeatures.addDefaultSprings (ver ModBiomeGen). NO
     *  se puede lograr esto re-añadiendo la MISMA PlacedFeature vanilla (MiscOverworldPlacements.
     *  SPRING_LAVA) una segunda vez en la lista de un bioma — FeatureSorter.buildFeaturesPerStep
     *  identifica cada PlacedFeature por su propio Holder.value() y añade una arista "esta
     *  entrada debe ir antes que la siguiente" por cada par consecutivo del listado del bioma;
     *  dos entradas iguales generan una arista de la feature hacia SÍ MISMA, que el DFS de
     *  ordenación lee como ciclo y crashea el arranque con "Feature order cycle found" (visto en
     *  juego). Por eso esta es una PlacedFeature propia, con identidad distinta, que envuelve el
     *  MISMO configured feature vanilla (MiscOverworldFeatures.SPRING_LAVA_OVERWORLD) con los
     *  mismos placement modifiers que usa MiscOverworldPlacements.SPRING_LAVA, solo con menos
     *  tiradas — un "extra", no una réplica del original. */
    public static final ResourceKey<PlacedFeature> HFIL_LAVA_SPRING_BONUS_KEY = registerKey("hfil_lava_spring_bonus");

    //  Montón de huesos/calavera del HFIL (mismo rediseño, punto 5)
    public static final ResourceKey<PlacedFeature> HFIL_BONE_PILE_KEY = registerKey("hfil_bone_pile");

    //  Afloramiento de Katchin visible en superficie del HFIL — mismo configured feature, dos
    //  rarezas distintas (ver ModBiomeGen): frecuente en blood_shore, raro en needle/dunes.
    public static final ResourceKey<PlacedFeature> HFIL_ORE_BOULDER_COMMON_KEY = registerKey("hfil_ore_boulder_common");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_BOULDER_RARE_KEY   = registerKey("hfil_ore_boulder_rare");

    //  Mojones (propuesta C): frecuentes en blood_shore (la "orilla" por excelencia, junto a la
    //  sangre), raros en los otros dos. Pozas de Beppu (propuesta A): una rareza cada una, el
    //  reparto por bioma vive en ModBiomeGen.
    public static final ResourceKey<PlacedFeature> HFIL_CAIRNS_COMMON_KEY = registerKey("hfil_cairns_common");
    public static final ResourceKey<PlacedFeature> HFIL_CAIRNS_RARE_KEY   = registerKey("hfil_cairns_rare");
    public static final ResourceKey<PlacedFeature> HFIL_BOILING_POOL_KEY  = registerKey("hfil_boiling_pool");
    public static final ResourceKey<PlacedFeature> HFIL_MUD_POT_KEY       = registerKey("hfil_mud_pot");
    public static final ResourceKey<PlacedFeature> HFIL_GEYSER_KEY        = registerKey("hfil_geyser");
    //  Terrazas de Pamukkale (propuesta B): solo needle_wastes, la pieza grande y rara del bioma.
    public static final ResourceKey<PlacedFeature> HFIL_TERRACES_KEY      = registerKey("hfil_terraces");
    //  Infierno helado (propuesta D): lagos de sangre congelada y agujas de hielo, solo en
    //  hfil_frozen_lake.
    public static final ResourceKey<PlacedFeature> HFIL_FROZEN_LAKE_KEY   = registerKey("hfil_frozen_lake");
    public static final ResourceKey<PlacedFeature> HFIL_ICE_SPIKE_KEY     = registerKey("hfil_ice_spike");
    //  Janemba Rift (rehecho el 2026-10-05): de micro a landmark, ver ModBiomeGen.hfilJanembaRift.
    public static final ResourceKey<PlacedFeature> JANEMBA_MOUND_KEY    = registerKey("janemba_mound");
    public static final ResourceKey<PlacedFeature> JANEMBA_SPIRE_KEY    = registerKey("janemba_spire");
    public static final ResourceKey<PlacedFeature> JANEMBA_RIDGE_KEY    = registerKey("janemba_ridge");
    public static final ResourceKey<PlacedFeature> JANEMBA_RIB_KEY      = registerKey("janemba_rib");
    public static final ResourceKey<PlacedFeature> JANEMBA_ORB_KEY      = registerKey("janemba_orb");
    public static final ResourceKey<PlacedFeature> JANEMBA_DOME_KEY     = registerKey("janemba_dome");
    public static final ResourceKey<PlacedFeature> JANEMBA_NUB_KEY      = registerKey("janemba_nub");
    //  Propuestas E-G: azufre y cráter en cinder_dunes, caldero de los oni en blood_shore.
    public static final ResourceKey<PlacedFeature> HFIL_SULFUR_FIELD_KEY  = registerKey("hfil_sulfur_field");
    public static final ResourceKey<PlacedFeature> HFIL_GAS_CRATER_KEY    = registerKey("hfil_gas_crater");
    public static final ResourceKey<PlacedFeature> HFIL_ONI_CAULDRON_KEY  = registerKey("hfil_oni_cauldron");
    public static final ResourceKey<PlacedFeature> OTHERWORLD_CLOUDS_KEY    = registerKey("otherworld_clouds");
    public static final ResourceKey<PlacedFeature> OTHERWORLD_FLOWERS_KEY   = registerKey("otherworld_flowers");
    public static final ResourceKey<PlacedFeature> OTHERWORLD_DEAD_BUSH_KEY = registerKey("otherworld_dead_bush");

    //  Menas propias del HFIL: misma colocación que OrePlacements vanilla (cantidad y alturas),
    //  ver ModConfiguredFeatures.hfilOre. Se añaden en hfilBase en el orden vanilla.
    public static final ResourceKey<PlacedFeature> HFIL_ORE_COAL_UPPER      = registerKey("hfil_ore_coal_upper");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_COAL_LOWER      = registerKey("hfil_ore_coal_lower");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_IRON_UPPER      = registerKey("hfil_ore_iron_upper");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_IRON_MIDDLE     = registerKey("hfil_ore_iron_middle");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_IRON_SMALL      = registerKey("hfil_ore_iron_small");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_GOLD            = registerKey("hfil_ore_gold");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_GOLD_LOWER      = registerKey("hfil_ore_gold_lower");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_REDSTONE        = registerKey("hfil_ore_redstone");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_REDSTONE_LOWER  = registerKey("hfil_ore_redstone_lower");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_DIAMOND         = registerKey("hfil_ore_diamond");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_DIAMOND_MEDIUM  = registerKey("hfil_ore_diamond_medium");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_DIAMOND_LARGE   = registerKey("hfil_ore_diamond_large");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_DIAMOND_BURIED  = registerKey("hfil_ore_diamond_buried");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_LAPIS           = registerKey("hfil_ore_lapis");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_LAPIS_BURIED    = registerKey("hfil_ore_lapis_buried");
    public static final ResourceKey<PlacedFeature> HFIL_ORE_COPPER          = registerKey("hfil_ore_copper");

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

        // Solo hfil_blood_shore (ver ModBiomeGen.hfilBloodShore) — se SUMA a KATCHIN_ORE_HFIL,
        // menos tiradas que la base porque ya reciben las 15 comunes vía hfilBase.
        register(context, KATCHIN_ORE_HFIL_EXPOSED,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.KATCHIN_ORE_OW_EXPOSED_KEY),
                ModOrePlacement.commonOrePlacement(5, HeightRangePlacement.uniform(
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

        // Cañón de agujas de rocky_wasteland (imagen de referencia, sesión 2026-09-04) — mismo
        // filtro anti-flotación que ROCKY_DEAD_BUSH_KEY arriba (HEIGHTMAP_OCEAN_FLOOR +
        // SurfaceWaterDepthFilter(0)): rechaza el ORIGEN del clúster si cae sobre agua abierta,
        // y RockyWastelandSpireFeature además sondea el suelo real de CADA aguja por separado
        // (LocalGroundProbe) y se salta las que no encuentran suelo — doble filtro, a propósito,
        // dado el historial de este bioma con formaciones "flotando" en el mar (ver el pendiente).
        // Subido de 20 a 48 (sesión 2026-09-04, pedido explícito del usuario: "no los hagas tan
        // comunes"). Con 20 salía más denso que HFIL_SPIKE_KEY (40) pese a que cada intento YA
        // coloca un clúster de 2-5 agujas (RockyWastelandSpireFeature) — se sentía como relleno
        // en vez de un cañón puntual y dramático. 48 lo deja más raro que HFIL_SPIKE_KEY incluso
        // por intento, compensando que cada intento aquí sigue siendo un clúster de varias agujas,
        // no una sola.
        register(context, ROCKY_WASTELAND_SPIRE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.ROCKY_WASTELAND_SPIRE_KEY),
                List.of(RarityFilter.onAverageOnceEvery(48),
                        InSquarePlacement.spread(),
                        PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,
                        SurfaceWaterDepthFilter.forMaxDepth(0),
                        BiomeFilter.biome()));

        // (La hierba seca del HFIL, HFIL_DRY_GRASS_KEY, se borró el 2026-10-05: ningún bioma la usaba
        // desde que needle_wastes pasó a suelo de piedra pálida, donde la hierba no arraiga.)
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
        // ya cuenta con que no todos los intentos llegan a colocar nada. Solo en rocky_wasteland:
        // la variante del HFIL (FALLEN_LOG_HFIL_KEY) se borró el 2026-10-05, ningún bioma la usaba.
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

        // Más raro que los troncos/matas a propósito: son formaciones dramáticas pensadas para
        // dominar el horizonte, no decoración de relleno — ver HfilSpikeFeature. Mismo
        // ClampedHeightmapPlacement que el resto del suelo del HFIL (evita que el origen del
        // clúster herede la altura de una isla flotante del Otherworld).
        // Rediseño de identidad HFIL, Fase 3 (ver .claude/docs/worldgen.md):
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

        // Mismo espíritu que HFIL_SPIKE_KEY (elemento dramático de horizonte, no relleno) — le
        // da a cinder_dunes su propia formación rocosa, con la piedra cálida de la propia duna
        // en vez de la roca fría de needle_wastes (ver HfilSpikeFeature/ModFeatures).
        register(context, HFIL_CINDER_SPIKE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_CINDER_SPIKE_KEY),
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

        // Manantial de lava extra, exclusivo de cinder_dunes — ver el javadoc de la key para el
        // porqué es una PlacedFeature propia en vez de re-añadir MiscOverworldPlacements.
        // SPRING_LAVA una segunda vez (eso causaba "Feature order cycle found" al arrancar).
        // Mismo configured feature vanilla y mismos placement modifiers que SPRING_LAVA, solo
        // con menos tiradas (8 en vez de 20) — un extra, no una réplica exacta.
        register(context, HFIL_LAVA_SPRING_BONUS_KEY,
                configuredFeatures.getOrThrow(MiscOverworldFeatures.SPRING_LAVA_OVERWORLD),
                List.of(CountPlacement.of(8),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.of(VeryBiasedToBottomHeight.of(
                                VerticalAnchor.bottom(), VerticalAnchor.belowTop(8), 8)),
                        BiomeFilter.biome()));

        // Decoración puntual barata: más común que los troncos, en línea con matas/hierba.
        register(context, HFIL_BONE_PILE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_BONE_PILE_KEY),
                List.of(RarityFilter.onAverageOnceEvery(16),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));

        // Mineral visible en superficie, DELIBERADO (ver HfilOreBoulderFeature) — mismo
        // configured feature, dos rarezas: frecuente en blood_shore (ya tiene identidad minera
        // fuerte con la veta expuesta), raro en needle_wastes/cinder_dunes (su primer gancho
        // minero propio). Un único configured feature con dos placed features.
        register(context, HFIL_ORE_BOULDER_COMMON_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_ORE_BOULDER_KEY),
                List.of(RarityFilter.onAverageOnceEvery(70),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));

        register(context, HFIL_ORE_BOULDER_RARE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_ORE_BOULDER_KEY),
                List.of(RarityFilter.onAverageOnceEvery(200),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));

        // Propuestas C y A (2026-10-02). Mismo patrón de colocación que el resto del suelo del
        // HFIL (ClampedHeightmapPlacement, para no heredar la altura de una isla flotante).
        // Rarezas de partida, sin calibrar en juego: un campo de mojones cada ~6 chunks en
        // blood_shore, cada ~18 fuera; cada tipo de poza cada ~20-28 chunks del bioma que la lleva.
        hfilFloor(context, HFIL_CAIRNS_COMMON_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_CAIRNS_KEY), 6);
        hfilFloor(context, HFIL_CAIRNS_RARE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_CAIRNS_KEY), 18);
        hfilFloor(context, HFIL_BOILING_POOL_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_BOILING_POOL_KEY), 22);
        hfilFloor(context, HFIL_MUD_POT_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_MUD_POT_KEY), 20);
        hfilFloor(context, HFIL_GEYSER_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_GEYSER_KEY), 28);
        // Rara: es un paisaje entero (hasta ~17×9 bloques), no decoración. Además se descarta
        // sola en terreno muy irregular, así que la frecuencia real queda algo por debajo.
        hfilFloor(context, HFIL_TERRACES_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_TERRACES_KEY), 30);
        // El lago congelado es EL paisaje de su bioma: frecuente (y se descarta solo en cuesta).
        hfilFloor(context, HFIL_FROZEN_LAKE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_FROZEN_LAKE_KEY), 4);
        // Agujas de hielo vanilla (la "montaña de agujas", helada): la feature vanilla exige
        // nieve compacta debajo, justo el suelo de hfil_frozen_lake. Colocación propia del HFIL
        // (no la de ice_spikes del Overworld) para no heredar la altura de una isla flotante.
        hfilFloor(context, HFIL_ICE_SPIKE_KEY,
                configuredFeatures.getOrThrow(MiscOverworldFeatures.ICE_SPIKE), 3);
        // Janemba Rift, escala micro → landmark (referencia de Fusion Reborn): lo pequeño muy
        // frecuente, lo grande raro, para que el paisaje lea "estructura grande, mucho terreno,
        // formación pequeña, zona abierta..." y no un patrón repetido. Lomas y protuberancias
        // rebajadas de 1/1 a 1/2 y 1/3 tras verlo en juego (2026-10-05): el suelo salía cargado.
        // Mismo día, segunda ronda: crestas de 1/12 a 1/16 (los grupos de picos dominaban) y
        // esferas de 1/2 a 1/3 (tapaban el cielo; además, grupos más pequeños, ver JanembaOrbFeature).
        hfilFloor(context, JANEMBA_MOUND_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.JANEMBA_MOUND_KEY), 2);
        hfilFloor(context, JANEMBA_SPIRE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.JANEMBA_SPIRE_KEY), 26);
        hfilFloor(context, JANEMBA_RIDGE_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.JANEMBA_RIDGE_KEY), 16);
        hfilFloor(context, JANEMBA_DOME_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.JANEMBA_DOME_KEY), 8);
        hfilFloor(context, JANEMBA_RIB_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.JANEMBA_RIB_KEY), 9);
        hfilFloor(context, JANEMBA_ORB_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.JANEMBA_ORB_KEY), 3);
        hfilFloor(context, JANEMBA_NUB_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.JANEMBA_NUB_KEY), 3);
        // Azufre: frecuente en su bioma (es su textura de suelo). Cráter y caldero: landmarks
        // raros, y además se descartan solos en terreno irregular.
        hfilFloor(context, HFIL_SULFUR_FIELD_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_SULFUR_FIELD_KEY), 14);
        hfilFloor(context, HFIL_GAS_CRATER_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_GAS_CRATER_KEY), 70);
        hfilFloor(context, HFIL_ONI_CAULDRON_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.HFIL_ONI_CAULDRON_KEY), 110);
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

        // Menas del HFIL: cantidades y alturas de OrePlacements vanilla (1.21.1), sin retoques.
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_COAL_UPPER, ModConfiguredFeatures.HFIL_ORE_COAL,
                ModOrePlacement.commonOrePlacement(30, HeightRangePlacement.uniform(VerticalAnchor.absolute(136), VerticalAnchor.top())));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_COAL_LOWER, ModConfiguredFeatures.HFIL_ORE_COAL_BURIED,
                ModOrePlacement.commonOrePlacement(20, HeightRangePlacement.triangle(VerticalAnchor.absolute(0), VerticalAnchor.absolute(192))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_IRON_UPPER, ModConfiguredFeatures.HFIL_ORE_IRON,
                ModOrePlacement.commonOrePlacement(90, HeightRangePlacement.triangle(VerticalAnchor.absolute(80), VerticalAnchor.absolute(384))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_IRON_MIDDLE, ModConfiguredFeatures.HFIL_ORE_IRON,
                ModOrePlacement.commonOrePlacement(10, HeightRangePlacement.triangle(VerticalAnchor.absolute(-24), VerticalAnchor.absolute(56))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_IRON_SMALL, ModConfiguredFeatures.HFIL_ORE_IRON_SMALL,
                ModOrePlacement.commonOrePlacement(10, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(72))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_GOLD, ModConfiguredFeatures.HFIL_ORE_GOLD_BURIED,
                ModOrePlacement.commonOrePlacement(4, HeightRangePlacement.triangle(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(32))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_GOLD_LOWER, ModConfiguredFeatures.HFIL_ORE_GOLD_BURIED,
                ModOrePlacement.orePlacement(CountPlacement.of(UniformInt.of(0, 1)),
                        HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-48))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_REDSTONE, ModConfiguredFeatures.HFIL_ORE_REDSTONE,
                ModOrePlacement.commonOrePlacement(4, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(15))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_REDSTONE_LOWER, ModConfiguredFeatures.HFIL_ORE_REDSTONE,
                ModOrePlacement.commonOrePlacement(8, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-32), VerticalAnchor.aboveBottom(32))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_DIAMOND, ModConfiguredFeatures.HFIL_ORE_DIAMOND_SMALL,
                ModOrePlacement.commonOrePlacement(7, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_DIAMOND_MEDIUM, ModConfiguredFeatures.HFIL_ORE_DIAMOND_MEDIUM,
                ModOrePlacement.commonOrePlacement(2, HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(-4))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_DIAMOND_LARGE, ModConfiguredFeatures.HFIL_ORE_DIAMOND_LARGE,
                ModOrePlacement.rareOrePlacement(9, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_DIAMOND_BURIED, ModConfiguredFeatures.HFIL_ORE_DIAMOND_BURIED,
                ModOrePlacement.commonOrePlacement(4, HeightRangePlacement.triangle(VerticalAnchor.aboveBottom(-80), VerticalAnchor.aboveBottom(80))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_LAPIS, ModConfiguredFeatures.HFIL_ORE_LAPIS,
                ModOrePlacement.commonOrePlacement(2, HeightRangePlacement.triangle(VerticalAnchor.absolute(-32), VerticalAnchor.absolute(32))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_LAPIS_BURIED, ModConfiguredFeatures.HFIL_ORE_LAPIS_BURIED,
                ModOrePlacement.commonOrePlacement(4, HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(64))));
        hfilOrePlaced(context, configuredFeatures, HFIL_ORE_COPPER, ModConfiguredFeatures.HFIL_ORE_COPPER_SMALL,
                ModOrePlacement.commonOrePlacement(16, HeightRangePlacement.triangle(VerticalAnchor.absolute(-16), VerticalAnchor.absolute(112))));

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

    private static void hfilOrePlaced(BootstrapContext<PlacedFeature> context,
                                      net.minecraft.core.HolderGetter<ConfiguredFeature<?, ?>> configured,
                                      ResourceKey<PlacedFeature> key, ResourceKey<ConfiguredFeature<?, ?>> ore,
                                      List<PlacementModifier> placement) {
        register(context, key, configured.getOrThrow(ore), placement);
    }

    /** Decoración del suelo del HFIL "una vez cada N chunks": la misma cadena de modificadores
     *  que ya repiten a mano los huesos, pinchos y afloramientos de arriba. */
    private static void hfilFloor(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key,
                                  Holder<ConfiguredFeature<?, ?>> configuration, int onceEvery) {
        register(context, key, configuration,
                List.of(RarityFilter.onAverageOnceEvery(onceEvery),
                        InSquarePlacement.spread(),
                        ClampedHeightmapPlacement.belowY(HFIL_FLOOR_MAX_Y),
                        BiomeFilter.biome()));
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