package com.hmc.zenkai.registry;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

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

    // Rediseño HFIL fase 1, punto 2 (ver .claude/pendiente/hfil-infierno-rediseno.md): ceniza
    // cayendo pasivamente, nativo de BiomeSpecialEffects (sin entidad ni tick propio que
    // mantener). Solo blood_shore/needle_wastes — dunas queda fuera, ya se lee como "arena en el aire"
    // sin necesitarlo (mismo reparto que HFIL_SPIKE_KEY). Probabilidad calcada de
    // minecraft:nether_wastes (0.00625) en vez de inventar un número — incluso en un bioma
    // pensado para verse denso, esta cifra ya se lee claramente sin saturar la pantalla.
    private static final AmbientParticleSettings HFIL_ASH_PARTICLES =
            new AmbientParticleSettings(ParticleTypes.ASH, 0.00625F);

    public static void bootstrap(BootstrapContext<Biome> ctx) {
        HolderGetter<PlacedFeature> features = ctx.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carvers = ctx.lookup(Registries.CONFIGURED_CARVER);

        ctx.register(ModBiomes.ROCKY_WASTELAND, rockyWasteland(features, carvers));
        ctx.register(ModBiomes.HFIL_BLOOD_SHORE, hfilBloodShore(features, carvers));
        ctx.register(ModBiomes.HFIL_NEEDLE_WASTES, hfilNeedleWastes(features, carvers));
        ctx.register(ModBiomes.HFIL_CINDER_DUNES, hfilCinderDunes(features, carvers));
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
        BiomeDefaultFeatures.commonSpawns(spawns);                       // ⚠ API

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
     * Base común de las tres variantes de HFIL: cuevas, lagos de lava, líquenes
     * (única fuente de luz ahí abajo), manantiales y cada uno de los ores vanilla.
     * Sin discos blandos: arcilla y arena junto al agua no pegan en el infierno.
     */
    private static BiomeGenerationSettings.Builder hfilBase(HolderGetter<PlacedFeature> features,
                                                            HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = new BiomeGenerationSettings.Builder(features, carvers);
        BiomeDefaultFeatures.addDefaultCarversAndLakes(gen);
        // Lago de sangre propio, punto 3 del rediseño de atmósfera (ver
        // .claude/pendiente/hfil-infierno-rediseno.md): mismo paso LAKES que los lagos de lava
        // que addDefaultCarversAndLakes ya añadió arriba, para que se intercalen por el terreno
        // sin lógica propia — ver el comentario de HFIL_LAKE_BLOOD_KEY en ModPlacedFeatures.
        gen.addFeature(GenerationStep.Decoration.LAKES, ModPlacedFeatures.HFIL_LAKE_BLOOD_KEY);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(gen);
        BiomeDefaultFeatures.addDefaultSprings(gen);
        BiomeDefaultFeatures.addDefaultOres(gen);
        gen.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ModPlacedFeatures.KATCHIN_ORE_HFIL);
        return gen;
    }

    /** Efectos comunes. grass/foliage solo los usa la variante con hierba. */
    private static BiomeSpecialEffects.Builder hfilEffects(int fogColor) {
        return new BiomeSpecialEffects.Builder()
                .skyColor(SKY_COLOR)
                .fogColor(fogColor)
                .waterColor(WATER_COLOR)
                .waterFogColor(WATER_FOG_COLOR)
                .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS);
    }

    private static Biome hfilBuild(BiomeSpecialEffects effects, BiomeGenerationSettings.Builder gen) {
        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(2.0F)
                .downfall(0.0F)
                .specialEffects(effects)
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(gen.build())
                .build();
    }

    /**
     * Rediseño de identidad HFIL (ver .claude/pendiente/hfil-rework-propuesta.md, Fase 2) —
     * antes "hfil_badlands": pintaba terracota bandeada de vainilla ({@code minecraft:bandlands}
     * en la surface_rule), la identidad prestada más obvia de los 3 biomas del HFIL, sin relación
     * con el imaginario de Dragon Ball. Ahora es la identidad "Blood Pond": el charco de sangre
     * (HFIL_LAKE_BLOOD_KEY) es la pieza central de este bioma, sobre roca calcinada propia
     * (HFIL_SCORCHED_STONE, ver la surface_rule de otherworld_noise.json). El rename es solo de
     * identidad — la forma de terreno/densidad no cambia (ver la sección 2 de la propuesta, no se
     * toca la función de densidad por el riesgo documentado en CLAUDE.md).
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
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_DEAD_BUSH_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.FALLEN_LOG_HFIL_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_BONE_PILE_KEY);
        BiomeSpecialEffects effects = hfilEffects(6034452)   // #5C1414
                .ambientParticle(HFIL_ASH_PARTICLES)
                .build();
        return hfilBuild(effects, gen);
    }

    /**
     * Rediseño de identidad HFIL (ver .claude/pendiente/hfil-rework-propuesta.md, Fase 3) —
     * antes "hfil_wastes". Los pinchos (HFIL_SPIKE_KEY) ya vivían exclusivamente aquí desde
     * Ronda 5 del rediseño anterior; esta fase solo sube su frecuencia (ver ModPlacedFeatures)
     * para que definan el horizonte nada más entrar, en vez de ser un evento raro, y afloja la
     * hierba a matojos dispersos en vez de cobertura densa, para que el suelo se lea más "yermo
     * rocoso" que "pradera muerta". El suelo sigue siendo coarse_dirt (ver surface rule): NO
     * grass_block, que moriría por falta de luz bajo la capa de nubes. El aspecto de pasto lo da
     * el short_grass, que sobrevive a oscuras y se tiñe con grassColorOverride.
     * PENDIENTE DE DECISIÓN DE ARTE (ver la propuesta, sección 3.2): las imágenes de referencia
     * muestran pinchos sobre colinas VERDES o sobre suelo BLANCO/nevado, ninguna sobre este
     * ocre/pardo — esta fase NO toca la paleta de superficie, solo la densidad de decoración.
     */
    private static Biome hfilNeedleWastes(HolderGetter<PlacedFeature> features,
                                          HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers);
        // Pinchos primero, igual que en Namek los árboles van antes que la hierba: es el
        // elemento "grande" del paisaje, la decoración pequeña va después. Solo aquí (ver
        // hfilBloodShore): el ocre/pardo de needle_wastes no compite con el tinte frío de la roca.
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_SPIKE_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_DRY_GRASS_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_DEAD_BUSH_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.FALLEN_LOG_HFIL_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_BONE_PILE_KEY);

        BiomeSpecialEffects effects = hfilEffects(5118482)   // #4E1A12
                .grassColorOverride(8016432)                 // #7A5230 ocre pardo
                .foliageColorOverride(7029798)               // #6B4426
                .ambientParticle(HFIL_ASH_PARTICLES)
                .build();
        return hfilBuild(effects, gen);
    }

    /**
     * Rediseño de identidad HFIL (ver .claude/pendiente/hfil-rework-propuesta.md, Fase 4) —
     * antes "hfil_dunes": dunas literalmente de {@code minecraft:red_sand}/{@code red_sandstone},
     * el más "prestado" de los 3 biomas (ni siquiera llevaba HFIL_SCORCHED_STONE visible en
     * superficie, la arena lo tapaba). Ahora la arena/arenisca son bloques propios
     * (HFIL_CINDER_SAND/HFIL_CINDER_SANDSTONE, ver la surface_rule) — ceniza del HFIL, no arena
     * de desierto reskinneada. Se mantiene la MECÁNICA de dunas de vainilla (el bloque cae igual
     * que arena real, `ColoredFallingBlock`) — solo cambia qué bloque pinta. Suma
     * HFIL_ASH_PARTICLES aquí también (antes era el único de los 3 sin ceniza ambiental — la
     * razón original, "ya se lee como arena en el aire", se debilita ahora que la arena misma es
     * ceniza). Sin vegetación salvo algún matojo.
     */
    private static Biome hfilCinderDunes(HolderGetter<PlacedFeature> features,
                                         HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_DEAD_BUSH_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.FALLEN_LOG_HFIL_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_BONE_PILE_KEY);
        BiomeSpecialEffects effects = hfilEffects(7219736)   // #6E2A18
                .ambientParticle(HFIL_ASH_PARTICLES)
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