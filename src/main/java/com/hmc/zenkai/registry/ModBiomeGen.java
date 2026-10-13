package com.hmc.zenkai.registry;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.joml.Vector3f;

/**
 * Biomas del mod construidos en datagen.
 * POR QUÉ NO SON JSON A MANO: el juego construye UNA ordenación global de features por paso
 * de decoración mezclando el conjunto de biomas cargados. Si dos biomas discrepan sobre el orden
 * relativo de dos features compartidas, no existe orden global válido y el arranque muere
 * con "Feature order cycle found". Llamar a BiomeDefaultFeatures da ese orden por
 * construcción. Bonus: aquí un id de vainilla equivocado es error de compilación, no un
 * crash al crear el mundo.
 * EL ORDEN DE LAS LLAMADAS ES SEMÁNTICA, NO ESTILO: replica globalOverworldGeneration()
 * seguido de los ores. Reordenarlas reintroduce el ciclo.
 */
public final class ModBiomeGen {
    private ModBiomeGen() {}

    // Paleta compartida de HFIL. El agua es sangre en las tres variantes.
    private static final int SKY_COLOR       = 3803658; // #3A0A0A
    private static final int WATER_COLOR     = 9044739; // #8A0303
    private static final int WATER_FOG_COLOR = 4849921; // #4A0101
    private static final int NAMEK_GRASS   = 1725043; // #1A5273
    private static final int NAMEK_FOLIAGE = 2055820; // #1F5E8C

    // Rediseño HFIL fase 1 (ver .claude/docs/worldgen.md): ceniza
    // cayendo pasivamente, nativo de BiomeSpecialEffects (sin entidad ni tick propio que
    // mantener). Probabilidad base calcada de minecraft:nether_wastes (0.00625) en vez de
    // inventar un número — incluso en un bioma pensado para verse denso, esa cifra ya se lee
    // claramente sin saturar la pantalla.
    // Personalidad por bioma (ver .claude/pendiente): la densidad ya no es un único valor
    // uniforme en los 3 biomas — blood_shore es un charco tranquilo (ceniza ligera), cinder_dunes
    // es viento y ceniza en el aire por diseño (densa), needle_wastes se queda con el valor base.
    private static final AmbientParticleSettings HFIL_ASH_PARTICLES_LIGHT =
            new AmbientParticleSettings(ParticleTypes.ASH, 0.0035F);
    private static final AmbientParticleSettings HFIL_ASH_PARTICLES =
            new AmbientParticleSettings(ParticleTypes.ASH, 0.00625F);
    private static final AmbientParticleSettings HFIL_ASH_PARTICLES_DENSE =
            new AmbientParticleSettings(ParticleTypes.ASH, 0.011F);

    public static void bootstrap(BootstrapContext<Biome> ctx) {
        HolderGetter<PlacedFeature> features = ctx.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carvers = ctx.lookup(Registries.CONFIGURED_CARVER);

        ctx.register(ModBiomes.ROCKY_WASTELAND, rockyWasteland(features, carvers));
        ctx.register(ModBiomes.HFIL_BLOOD_SHORE, hfilBloodShore(features, carvers));
        ctx.register(ModBiomes.HFIL_NEEDLE_WASTES, hfilNeedleWastes(features, carvers));
        ctx.register(ModBiomes.HFIL_CINDER_DUNES, hfilCinderDunes(features, carvers));
        ctx.register(ModBiomes.HFIL_FROZEN_LAKE, hfilFrozenLake(features, carvers));
        ctx.register(ModBiomes.HFIL_JANEMBA_RIFT, hfilJanembaRift(features, carvers));
        ctx.register(ModBiomes.OTHERWORLD, otherworld(features, carvers));
        ctx.register(ModBiomes.NAMEK_PLAINS, namek(features, carvers, NamekVeg.PLAINS));
        ctx.register(ModBiomes.NAMEK_FOREST, namek(features, carvers, NamekVeg.FOREST));
        ctx.register(ModBiomes.NAMEK_HILLS,  namek(features, carvers, NamekVeg.HILLS));
        ctx.register(ModBiomes.NAMEK_SHORE,  namek(features, carvers,NamekVeg.SHORE));
        ctx.register(ModBiomes.NAMEK_OCEAN,  namek(features, carvers, NamekVeg.OCEAN));
    }

    // ── Overworld ────────────────────────────────────────────────────────────

    private static Biome rockyWasteland(HolderGetter<PlacedFeature> features,
                                        HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder();
        BiomeDefaultFeatures.commonSpawns(spawns);

        BiomeGenerationSettings.Builder gen = new BiomeGenerationSettings.Builder(features, carvers);

        BiomeDefaultFeatures.addDefaultCarversAndLakes(gen);
        BiomeDefaultFeatures.addDefaultCrystalFormations(gen);
        BiomeDefaultFeatures.addDefaultMonsterRoom(gen);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(gen);
        BiomeDefaultFeatures.addDefaultSprings(gen);
        BiomeDefaultFeatures.addSurfaceFreezing(gen);
        BiomeDefaultFeatures.addDefaultOres(gen);
        BiomeDefaultFeatures.addDefaultSoftDisks(gen);
        BiomeDefaultFeatures.addExtraEmeralds(gen);
        BiomeDefaultFeatures.addInfestedStone(gen);

        // Cañón de agujas primero, igual que en el HFIL (ver hfilNeedleWastes): es el elemento
        // "grande" del paisaje que define el horizonte, la decoración pequeña va después. Imagen
        // de referencia de Dragon Ball pedida por el usuario, sesión 2026-09-04 — ver
        // RockyWastelandSpireFeature.
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.ROCKY_WASTELAND_SPIRE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.ROCKY_DEAD_BUSH_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.FALLEN_LOG_ROCKY_KEY);

        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(2.0F)
                .downfall(0.0F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .skyColor(8103167)
                        .fogColor(12638463)
                        .waterColor(4159204)
                        .waterFogColor(329011)
                        .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                        .build())
                .mobSpawnSettings(spawns.build())
                .generationSettings(gen.build())
                .build();
    }

    // ── HFIL ─────────────────────────────────────────────────────────────────

    /**
     * Base común de las tres variantes de HFIL: cuevas, líquenes (única fuente de luz ahí
     * abajo), manantiales y cada uno de los ores vanilla + Katchin.
     * Sin discos blandos: arcilla y arena junto al agua no pegan en el infierno.
     * NO incluye lagos (ni de lava ni de sangre): personalidad por bioma (ver
     * .claude/pendiente) — cada variante decide su propio lago exclusivo en su función, en vez
     * de que los 3 compartan el mismo set. BiomeDefaultFeatures.addDefaultCarversAndLakes NO
     * separa cuevas de lagos en dos funciones públicas (verificado en su fuente: un único
     * método que añade los 3 carvers Y los 2 lagos de lava juntos), así que aquí se replican a
     * mano solo las 3 llamadas a builder.addCarver(...) — las cuevas SÍ se quedan comunes a los
     * 3 biomas; los lagos de lava vanilla los añade únicamente hfilCinderDunes.
     */
    private static BiomeGenerationSettings.Builder hfilBase(HolderGetter<PlacedFeature> features,
                                                            HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        return hfilBase(features, carvers, true);
    }

    /**
     * {@code lavaSprings = false}: solo los manantiales de agua. Para el Janemba Rift, cuya
     * referencia (Fusion Reborn) no tiene lava en ninguna parte; con lava saliendo de las paredes
     * se leía como Nether. Quitar el de lava y dejar el de agua en su sitio conserva el orden
     * relativo de los demás biomas (agua → lava → manantial extra de cinder_dunes): sin ciclo.
     */
    private static BiomeGenerationSettings.Builder hfilBase(HolderGetter<PlacedFeature> features,
                                                            HolderGetter<ConfiguredWorldCarver<?>> carvers,
                                                            boolean lavaSprings) {
        BiomeGenerationSettings.Builder gen = new BiomeGenerationSettings.Builder(features, carvers);
        gen.addCarver(GenerationStep.Carving.AIR, Carvers.CAVE);
        gen.addCarver(GenerationStep.Carving.AIR, Carvers.CAVE_EXTRA_UNDERGROUND);
        gen.addCarver(GenerationStep.Carving.AIR, Carvers.CANYON);
        // SIN addDefaultUndergroundVariety (2026-10-04, petición del usuario: que la dimensión no
        // se sienta un copia y pega del Overworld). Esas vetas (grava, granito, diorita, andesita,
        // toba, calcita, tierra) son piedra vanilla y generan en todo lo que esté en
        // stone_ore_replaceables, que el HFIL SÍ necesita para sus menas propias.
        if (lavaSprings) {
            BiomeDefaultFeatures.addDefaultSprings(gen);
        } else {
            gen.addFeature(GenerationStep.Decoration.FLUID_SPRINGS, MiscOverworldPlacements.SPRING_WATER);
        }
        // Menas PROPIAS del HFIL en vez de BiomeDefaultFeatures.addDefaultOres (2026-10-02,
        // petición del usuario): las vanilla salían con fondo de piedra gris del Overworld sobre
        // la roca del bioma. Misma abundancia y alturas que la vanilla, en su mismo orden; cada
        // veta usa la variante de la piedra que atraviesa (ver HfilOres).
        for (var ore : java.util.List.of(
                ModPlacedFeatures.HFIL_ORE_COAL_UPPER, ModPlacedFeatures.HFIL_ORE_COAL_LOWER,
                ModPlacedFeatures.HFIL_ORE_IRON_UPPER, ModPlacedFeatures.HFIL_ORE_IRON_MIDDLE,
                ModPlacedFeatures.HFIL_ORE_IRON_SMALL, ModPlacedFeatures.HFIL_ORE_GOLD,
                ModPlacedFeatures.HFIL_ORE_GOLD_LOWER, ModPlacedFeatures.HFIL_ORE_REDSTONE,
                ModPlacedFeatures.HFIL_ORE_REDSTONE_LOWER, ModPlacedFeatures.HFIL_ORE_DIAMOND,
                ModPlacedFeatures.HFIL_ORE_DIAMOND_MEDIUM, ModPlacedFeatures.HFIL_ORE_DIAMOND_LARGE,
                ModPlacedFeatures.HFIL_ORE_DIAMOND_BURIED, ModPlacedFeatures.HFIL_ORE_LAPIS,
                ModPlacedFeatures.HFIL_ORE_LAPIS_BURIED, ModPlacedFeatures.HFIL_ORE_COPPER)) {
            gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ore);
        }
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.KATCHIN_ORE_HFIL);
        return gen;
    }

    /**
     * Pozas de los "infiernos" de Beppu (propuesta A, 2026-10-02): agua hirviendo azul
     * (Umi Jigoku), barro gris hirviendo (Oniishibozu Jigoku) y géiser (Tatsumaki Jigoku, en
     * los 3 biomas). En el paso LAKES y SIEMPRE en este mismo orden relativo (poza → barro →
     * géiser) en cualquier bioma: FeatureSorter exige que dos placed features compartidas
     * aparezcan en el mismo orden en cada bioma que las use, o el arranque falla con "Feature
     * order cycle found". Por eso un solo sitio las añade, con interruptores, en vez de
     * repetirlas a mano en cada bioma.
     */
    private static void addBeppuPools(BiomeGenerationSettings.Builder gen, boolean boilingWater, boolean mud) {
        if (boilingWater) {
            gen.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.HFIL_BOILING_POOL_KEY);
        }
        if (mud) {
            gen.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.HFIL_MUD_POT_KEY);
        }
        gen.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.HFIL_GEYSER_KEY);
    }

    /** Efectos comunes con el cielo rojo oscuro de siempre. */
    private static BiomeSpecialEffects.Builder hfilEffects(int fogColor) {
        return hfilEffects(SKY_COLOR, fogColor);
    }

    /**
     * Efectos comunes con cielo propio. Niebla Y cielo por bioma (petición del usuario,
     * 2026-10-02): con los tres biomas en rojos casi iguales, cruzar de uno a otro no se notaba
     * en el aire, solo en el suelo. Cada bioma tiene ahora su atmósfera — y como el horizonte
     * del Otherworld es casi todo niebla, cambiar la niebla cambia lo que se ve del "cielo".
     */
    private static BiomeSpecialEffects.Builder hfilEffects(int skyColor, int fogColor) {
        return new BiomeSpecialEffects.Builder()
                .skyColor(skyColor)
                .fogColor(fogColor)
                .waterColor(WATER_COLOR)
                .waterFogColor(WATER_FOG_COLOR)
                .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS);
    }

    private static Biome hfilBuild(BiomeSpecialEffects effects, BiomeGenerationSettings.Builder gen) {
        return hfilBuild(effects, gen, 2.0F);
    }

    private static Biome hfilBuild(BiomeSpecialEffects effects, BiomeGenerationSettings.Builder gen, float temperature) {
        // Ogros (propuesta I) en cualquier bioma del HFIL, sueltos o en pareja, rojo y azul por
        // igual. Además, el caldero de los oni genera su propia pareja de guardia.
        MobSpawnSettings.Builder spawns = new MobSpawnSettings.Builder()
                .addSpawn(net.minecraft.world.entity.MobCategory.CREATURE,
                        new MobSpawnSettings.SpawnerData(ModEntities.RED_OGRE.get(), 10, 1, 2))
                .addSpawn(net.minecraft.world.entity.MobCategory.CREATURE,
                        new MobSpawnSettings.SpawnerData(ModEntities.BLUE_OGRE.get(), 10, 1, 2));
        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(temperature)
                .downfall(0.0F)
                .specialEffects(effects)
                .mobSpawnSettings(spawns.build())
                .generationSettings(gen.build())
                .build();
    }

    /**
     * Rediseño de identidad HFIL (ver .claude/docs/worldgen.md, Fase 2) —
     * antes "hfil_badlands": pintaba terracota bandeada de vainilla ({@code minecraft:bandlands}
     * en la surface_rule), la identidad prestada más obvia de los 3 biomas del HFIL, sin relación
     * con el imaginario de Dragon Ball. Ahora es la identidad "Blood Pond": el charco de sangre
     * (HFIL_LAKE_BLOOD_KEY) es la pieza central de este bioma, sobre roca calcinada propia
     * (HFIL_SCORCHED_STONE, ver la surface_rule de otherworld_noise.json). El rename es solo de
     * identidad — la forma de terreno/densidad no cambia (ver la sección 2 de la propuesta, no se
     * toca la función de densidad por el riesgo documentado en CLAUDE.md).
     * Personalidad por bioma (ver .claude/pendiente): el lago de sangre pasa a ser EXCLUSIVO de
     * aquí (ya no lo comparte con needle_wastes/cinder_dunes vía hfilBase — ver el javadoc de
     * hfilBase). Se quitan los troncos caídos (se quedan solo en needle_wastes, ver esa función)
     * y las matas secas (un charco húmedo no pega con vegetación árida); la decoración de
     * superficie queda reducida a solo huesos. La peculiaridad "el mineral se ve en la pared de
     * la costa" ya salía sola de la surface_rule (floor=HFIL_SCORCHED_STONE sin capa de tierra
     * encima, a diferencia de los otros dos biomas) — aquí se refuerza a propósito con una veta
     * de Katchin adicional más grande (KATCHIN_ORE_HFIL_EXPOSED, se SUMA a la base de hfilBase,
     * mismo patrón que rocky_wasteland con KATCHIN_ORE_EXPOSED_KEY), y la ceniza ambiental baja
     * de densidad (HFIL_ASH_PARTICLES_LIGHT): un charco es quieto, no una tormenta de ceniza.
     * Además del accidente de la veta subterránea asomando, ahora también un afloramiento de
     * Katchin DELIBERADO en superficie (HFIL_ORE_BOULDER_COMMON_KEY, ver HfilOreBoulderFeature)
     * — más frecuente aquí que en needle/dunes, reforzando la identidad minera del bioma.
     */
    private static Biome hfilBloodShore(HolderGetter<PlacedFeature> features,
                                        HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers);
        // SIN pinchos aquí a propósito: la roca fría azul-violeta choca con la banda naranja
        // saturada de la terracota vista de cerca en juego (reportado — en las 4 imágenes de
        // referencia el contraste leía bien porque eran fondos 2D lejanos, pero contra el
        // bloque real de terracota, cerca, se ve fuera de lugar). Esa terracota ya no existe en
        // este bioma (ver la surface_rule), pero la decisión de no mezclar el tinte frío de los
        // pinchos con la paleta cálida del charco de sangre sigue siendo válida, así que se
        // mantiene. Los pinchos se quedan solo en hfil_needle_wastes.
        gen.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.HFIL_LAKE_BLOOD_KEY);
        addBeppuPools(gen, true, true);
        // Caldero de los oni (kama-yude, propuesta G): el castigo junto al Blood Pond.
        gen.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, ModPlacedFeatures.HFIL_ONI_CAULDRON_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_BONE_PILE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_ORE_BOULDER_COMMON_KEY);
        // La orilla de sangre es la "orilla del Sanzu": mojones frecuentes (propuesta C).
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_CAIRNS_COMMON_KEY);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES,
                ModPlacedFeatures.KATCHIN_ORE_HFIL_EXPOSED);
        // Rojo sangre: el original del HFIL, ahora exclusivo de la orilla del Blood Pond.
        BiomeSpecialEffects effects = hfilEffects(0x6E1414)
                .ambientParticle(HFIL_ASH_PARTICLES_LIGHT)
                .build();
        return hfilBuild(effects, gen);
    }

    /**
     * Rediseño de identidad HFIL (ver .claude/docs/worldgen.md, Fase 3) —
     * antes "hfil_wastes". Los pinchos (HFIL_SPIKE_KEY) ya vivían exclusivamente aquí desde
     * Ronda 5 del rediseño anterior; esta fase solo sube su frecuencia (ver ModPlacedFeatures)
     * para que definan el horizonte nada más entrar, en vez de ser un evento raro, y afloja la
     * hierba a matojos dispersos en vez de cobertura densa.
     * SUELO BLANCO (2026-10-02, decisión del usuario): las imágenes de referencia muestran los
     * pinchos sobre colinas VERDES o sobre suelo BLANCO/nevado, nunca sobre el ocre/pardo de
     * coarse_dirt que había. Ahora la surface_rule pinta HFIL_PALE_STONE (roca blanca-azulada,
     * ver ModBlocks), que hace de fondo claro para la roca fría de los pinchos. La hierba seca y
     * las matas se QUITARON: ni short_grass ni dead_bush sobreviven sobre roca (exigen
     * tierra/arena), así que solo habrían gastado intentos sin colocar nada; por lo mismo ya no
     * hacen falta grassColorOverride/foliageColorOverride.
     * Los troncos caídos fueron exclusivos de este bioma hasta el 2026-10-02; se quitaron también
     * de aquí al volverse blanco y nevado (madera fuera de lugar, petición del usuario). Su placed
     * feature del HFIL se borró el 2026-10-05: no la usaba ningún bioma.
     * Su primer gancho minero propio: un afloramiento raro de Katchin en superficie
     * (HFIL_ORE_BOULDER_RARE_KEY, ver HfilOreBoulderFeature) — mucho menos frecuente que en
     * blood_shore, que ya tiene la veta expuesta como seña de identidad.
     */
    private static Biome hfilNeedleWastes(HolderGetter<PlacedFeature> features,
                                          HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers);
        // Pozas azules sobre el suelo blanco (el contraste más fuerte del HFIL) y géiseres; sin
        // barro: el gris del barro se perdería contra la piedra pálida.
        addBeppuPools(gen, true, false);
        // Terrazas de Pamukkale (propuesta B): piedra pálida con pozas de sangre escalonadas.
        // Exclusivas de este bioma (es el único con suelo blanco), así que no comparten orden
        // de features con ningún otro.
        gen.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.HFIL_TERRACES_KEY);
        // El capullo de Freezer (propuesta H) NO va aquí: es una estructura (FriezaCocoonStructure,
        // #has_structure/frieza_cocoon), localizable con /locate.
        // Pinchos primero, igual que en Namek los árboles van antes que la hierba: es el
        // elemento "grande" del paisaje, la decoración pequeña va después. Solo aquí (ver
        // hfilBloodShore): el suelo blanco de needle_wastes hace de fondo a la roca fría.
        // Sin troncos caídos desde 2026-10-02 (petición del usuario): sobre el suelo blanco y
        // nevado la madera no pega.
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_SPIKE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_BONE_PILE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_ORE_BOULDER_RARE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_CAIRNS_RARE_KEY);

        // Rosa-magenta: el cielo de la referencia del monte de agujas (Hari no Yama) del anime,
        // que sobre el suelo blanco da el contraste de la imagen original.
        BiomeSpecialEffects effects = hfilEffects(0x7A2E5A, 0xB8708F)
                .ambientParticle(HFIL_ASH_PARTICLES)
                .build();
        return hfilBuild(effects, gen);
    }

    /**
     * Rediseño de identidad HFIL (ver .claude/docs/worldgen.md, Fase 4) —
     * antes "hfil_dunes": dunas literalmente de {@code minecraft:red_sand}/{@code red_sandstone},
     * el más "prestado" de los 3 biomas (ni siquiera llevaba HFIL_SCORCHED_STONE visible en
     * superficie, la arena lo tapaba). Ahora la arena/arenisca son bloques propios
     * (HFIL_CINDER_SAND/HFIL_CINDER_SANDSTONE, ver la surface_rule) — ceniza del HFIL, no arena
     * de desierto reskinneada. Se mantiene la MECÁNICA de dunas de vainilla (el bloque cae igual
     * que arena real, `ColoredFallingBlock`) — solo cambia qué bloque pinta. Suma
     * HFIL_ASH_PARTICLES aquí también (antes era el único de los 3 sin ceniza ambiental — la
     * razón original, "ya se lee como arena en el aire", se debilita ahora que la arena misma es
     * ceniza). Sin vegetación salvo algún matojo.
     * Personalidad por bioma (ver .claude/pendiente): los lagos de lava vanilla (las mismas 2
     * placed features que BiomeDefaultFeatures.addDefaultCarversAndLakes añadía antes en
     * hfilBase, compartidas por los 3 — ver el javadoc de hfilBase) pasan a ser EXCLUSIVOS de
     * aquí — dunas de ceniza con huecos de lava, no el charco de sangre. Se quitan los troncos
     * caídos (se quedan solo en needle_wastes) y sube la densidad de ceniza ambiental
     * (HFIL_ASH_PARTICLES_DENSE): un desierto de ceniza se lee más "viento cargado" que los
     * otros dos.
     * Además: su propia formación rocosa (HFIL_CINDER_SPIKE_KEY, misma clase que HFIL_SPIKE_KEY
     * pero con HFIL_CINDER_SANDSTONE — cálida, nunca la roca fría de needle_wastes ni
     * HFIL_SCORCHED_STONE, compartida con blood_shore, para no competir visualmente con
     * ninguno), un afloramiento raro de Katchin en superficie (HFIL_ORE_BOULDER_RARE_KEY, su
     * primer gancho minero propio, igual de escaso que en needle_wastes) y un manantial de lava
     * extra (HFIL_LAVA_SPRING_BONUS_KEY, PlacedFeature propia, ver su javadoc en
     * ModPlacedFeatures — NO se puede lograr esto re-añadiendo minecraft:spring_lava una segunda
     * vez a la lista del bioma: eso rompe el arranque con "Feature order cycle found", porque
     * FeatureSorter identifica cada PlacedFeature por su propio Holder.value() y una entrada
     * duplicada genera una arista de la feature hacia sí misma) para que la lava suelta se
     * sienta más presente aquí que en los otros dos biomas.
     */
    private static Biome hfilCinderDunes(HolderGetter<PlacedFeature> features,
                                         HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers);
        gen.addFeature(GenerationStep.Decoration.LAKES, MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND);
        gen.addFeature(GenerationStep.Decoration.LAKES, MiscOverworldPlacements.LAKE_LAVA_SURFACE);
        // Barro hirviendo y géiseres: el lado volcánico de Beppu, en el bioma de lava. Sin pozas
        // azules: el agua hirviendo junto a lagos de lava se leería como un error de color.
        addBeppuPools(gen, false, true);
        // Campos de azufre (Osorezan, propuesta E) y cráter de gas en llamas (Darvaza, en el
        // desierto de Karakum, propuesta F): lo volcánico/desértico, exclusivo de este bioma.
        gen.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.HFIL_SULFUR_FIELD_KEY);
        gen.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.HFIL_GAS_CRATER_KEY);
        gen.addFeature(GenerationStep.Decoration.FLUID_SPRINGS, ModPlacedFeatures.HFIL_LAVA_SPRING_BONUS_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_CINDER_SPIKE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_DEAD_BUSH_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_BONE_PILE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_ORE_BOULDER_RARE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_CAIRNS_RARE_KEY);
        // Naranja-ceniza: aire cargado de ceniza caliente, el del desierto de lava.
        BiomeSpecialEffects effects = hfilEffects(0x4A2410, 0x8A5530)
                .ambientParticle(HFIL_ASH_PARTICLES_DENSE)
                .build();
        return hfilBuild(effects, gen);
    }

    /**
     * Infierno helado — propuesta D (2026-10-02, ver .claude/docs/worldgen.md).
     * Referencias: el infierno de hielo de Dragon Ball GT (Goku contra Freezer y Cell, con una
     * zona helada junto a la de lava) y el Guren, el "infierno del loto rojo" de los infiernos
     * fríos budistas, donde el frío agrieta la piel como pétalos rojos. El Blood Pond, congelado:
     * lagos de HFIL_BLOOD_ICE (HfilFrozenLakeFeature) sobre nieve, agujas de hielo (la montaña de
     * agujas, helada) y huesos. Suelo: nieve compacta + hielo compacto (surface_rule de
     * otherworld_noise.json), los bloques propios de ESTE bioma; las menas siguen apareciendo en
     * la roca calcinada de debajo.
     * Ocupa la franja más fría del clima del Otherworld, pegada a needle_wastes: el paso del
     * blanco de piedra al blanco de nieve es coherente.
     * Sin pozas de Beppu ni mojones: el géiser y la orilla de las pozas tienen variantes por suelo
     * de piedra (no de hielo), y los mojones son de piedra — fuera de lugar sobre nieve.
     */
    private static Biome hfilFrozenLake(HolderGetter<PlacedFeature> features,
                                        HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers);
        gen.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.HFIL_FROZEN_LAKE_KEY);
        gen.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, ModPlacedFeatures.HFIL_ICE_SPIKE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.HFIL_BONE_PILE_KEY);
        // Violeta helado; copos de nieve cayendo en vez de ceniza.
        BiomeSpecialEffects effects = hfilEffects(0x2A3266, 0x8C94C8)
                .ambientParticle(new AmbientParticleSettings(ParticleTypes.SNOWFLAKE, 0.012F))
                .build();
        // Bajo cero: el agua que se deje aquí se hiela, coherente con el bioma.
        return hfilBuild(effects, gen, -0.7F);
    }

    /**
     * Janemba Rift: el Otro Mundo de Dragon Ball Z: Fusion Reborn, deformado por la maldad que se
     * libera de la máquina purificadora de almas. REHECHO ENTERO el 2026-10-05 sobre la referencia
     * .claude/pendiente/referencia_janemba.png (decisión del usuario: cualquier elemento del bioma
     * sale de la imagen; lo que no está en ella se quitó: la Lentitud, las venas luminosas y la
     * Compuerta de púrpur). La regla de arte: la referencia manda sobre lo que se asocia con
     * "infierno". Sin lava (ni manantiales: hfilBase con lavaSprings=false), obsidiana, fuego ni
     * piedra negra; cielo rojo-naranja, suelo púrpura/rosa ondulado, masas azules, torres y esferas.
     *
     * Suelo: franjas onduladas de carne rosa y piedra violeta (surface_rule de
     * otherworld_noise.json). Sin tocar la densidad compartida del HFIL (riesgo del "bache", ver
     * docs/worldgen.md): el relieve lo ponen features, por escala:
     * <ul>
     *   <li>terreno: montículos bajos con joroba (JanembaMoundFeature), uno cada 2 chunks;</li>
     *   <li>grande: agujas sin corona (JanembaSpireFeature), crestas de pinchos
     *   (JanembaSpineRidgeFeature), lomos y olas azules (JanembaDomeFeature);</li>
     *   <li>mediano: arcos retorcidos (JanembaRibFeature);</li>
     *   <li>aire: grupos de esferas de gelatina de colores (JanembaOrbFeature);</li>
     *   <li>micro: protuberancias y burbujas diminutas (JanembaNubFeature);</li>
     *   <li>landmark: la torre con corona, como estructura (JanembaTowerStructure).</li>
     * </ul>
     * Los montículos van primero porque son terreno: el resto se apoya encima. Cada una es
     * exclusiva de este bioma, así que su orden no puede chocar con el de ningún otro.
     */
    private static Biome hfilJanembaRift(HolderGetter<PlacedFeature> features,
                                         HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers, false);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.JANEMBA_MOUND_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.JANEMBA_SPIRE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.JANEMBA_RIDGE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.JANEMBA_DOME_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.JANEMBA_RIB_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.JANEMBA_ORB_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.JANEMBA_NUB_KEY);
        // Atmósfera de la referencia. Vanilla funde el color del cielo (cenit) con el de la niebla
        // (horizonte): rojo arriba y naranja abajo es el degradado de la película. La distancia
        // de la niebla la acerca el cliente (JanembaRiftFog) para que lo lejano se desvanezca en
        // naranja. El agua, rosa (no la sangre del resto del HFIL): rompería la paleta.
        // Motas violetas flotando: polvo pequeño (escala 0.6) y escaso. Antes era aliento de dragón
        // a 0.008, pero su sprite es grande y no admite escala: de cerca se leía como un píxel roto
        // gigante y de lejos ensuciaba el paisaje (capturas del 2026-10-05).
        BiomeSpecialEffects effects = new BiomeSpecialEffects.Builder()
                .skyColor(0xE51A24)
                .fogColor(0xFF6A22)
                .waterColor(0xF05A9D)
                .waterFogColor(0x8A2E6A)
                .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                .ambientParticle(new AmbientParticleSettings(
                        new DustParticleOptions(new Vector3f(0.69F, 0.31F, 0.86F), 0.6F), 0.003F))
                .build();
        return hfilBuild(effects, gen);
    }

    /**
     * Islas flotantes sobre el mar de nubes. Sin carvers ni ores: son plataformas, no un mundo
     * subterráneo.
     * addCherryGroveVegetation trae cerezos, flores de cerezo, hierba y pétalos rosas en el
     * orden canónico de vainilla, que es lo que evita el "Feature order cycle". Las dos
     * features propias van después porque llevan namespace zenkai y no aparecen en ningún
     * otro bioma: una placed feature que solo usa tu mod nunca puede entrar en conflicto de
     * orden con nadie. Esa es la regla general — las de vainilla siguen el orden de vainilla,
     * las tuyas van donde quieras.
     */
    private static Biome otherworld(HolderGetter<PlacedFeature> features,
                                    HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = new BiomeGenerationSettings.Builder(features, carvers);

        gen.addFeature(GenerationStep.Decoration.RAW_GENERATION,
                ModPlacedFeatures.OTHERWORLD_CLOUDS_KEY);

        BiomeDefaultFeatures.addCherryGroveVegetation(gen);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.KATCHIN_ORE_SKY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.OTHERWORLD_FLOWERS_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.OTHERWORLD_DEAD_BUSH_KEY);

        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(2.0F)
                .downfall(0.0F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .skyColor(16773514)
                        .fogColor(16751563)
                        .waterColor(9109504)
                        .waterFogColor(4849664)
                        .grassColorOverride(6344510)
                        .foliageColorOverride(5028651)
                        .build())
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(gen.build())
                .build();
    }

    /** Qué vegetación lleva cada bioma de Namek. OCEAN no lleva ninguna. */
    private enum NamekVeg { PLAINS, FOREST, HILLS, SHORE, OCEAN }

    /**
     * Los cinco biomas de Namek comparten cielo, niebla y agua a propósito: la identidad la
     * dan el agua verde y la hierba, no una paleta distinta por bioma. Solo varía el tono de
     * hierba de las colinas.
     */
    private static Biome namek(HolderGetter<PlacedFeature> features,
                               HolderGetter<ConfiguredWorldCarver<?>> carvers,
                               NamekVeg veg) {
        BiomeGenerationSettings.Builder gen = new BiomeGenerationSettings.Builder(features, carvers);

        if (veg != NamekVeg.OCEAN) {
            gen.addCarver(GenerationStep.Carving.AIR, ModCarvers.NAMEK_CAVE);
            gen.addCarver(GenerationStep.Carving.AIR, ModCarvers.NAMEK_CANYON);
        }

        BiomeDefaultFeatures.addDefaultUndergroundVariety(gen);
        BiomeDefaultFeatures.addDefaultSprings(gen);

        // Menas de Namek. El orden es el de vainilla y estas features son namespace zenkai,
        // así que no pueden entrar en conflicto de orden con ningún bioma ajeno.
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_COAL_UPPER);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_COAL_LOWER);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_IRON_UPPER);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_IRON_MIDDLE);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_IRON_SMALL);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_COPPER);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_GOLD);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_GOLD_LOWER);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_REDSTONE);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_REDSTONE_LOWER);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_LAPIS);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_LAPIS_BURIED);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_DIAMOND);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_DIAMOND_MEDIUM);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_DIAMOND_LARGE);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_DIAMOND_BURIED);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.NAMEK_CRYSTAL);

        if (veg != NamekVeg.OCEAN) {
            gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.SACRED_STONE);
        }

        switch (veg) {
            // Las colinas son la única fuente decente de Cristal Energético.
            case HILLS -> gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.ENERGY_CRYSTAL);
            // Llanura y bosque, la mitad.
            case PLAINS, FOREST -> gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.ENERGY_CRYSTAL_RARE);
            // Orilla y océano, nada.
            case SHORE, OCEAN -> { }
        }

        // Vegetación. Árboles primero y matas después, que es el orden de vainilla: si la
        // hierba fuera antes, los troncos brotarían encima y la borrarían.
        switch (veg) {
            case FOREST -> {
                gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.AJISA_FOREST);
                gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.AJISA_FLOWERS);
                gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.NAMEK_GRASS);
            }
            case PLAINS -> {
                gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.AJISA_PLAINS);
                gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.AJISA_FLOWERS);
                gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.NAMEK_GRASS);
            }
            case HILLS -> {
                gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.AJISA_HILLS);
                gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.NAMEK_GRASS);
            }
            case SHORE -> gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.AJISA_SHORE);
            case OCEAN -> { }
        }

        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.8F)
                .downfall(0.0F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .skyColor(3977375)
                        .fogColor(3969183)
                        .waterColor(2529116)
                        .waterFogColor(6870166)
                        .grassColorOverride(NAMEK_GRASS)
                        .foliageColorOverride(NAMEK_FOLIAGE)
                        .build())
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(gen.build())
                .build();
    }
}