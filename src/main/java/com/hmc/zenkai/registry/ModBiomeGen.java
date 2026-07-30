package com.hmc.zenkai.registry;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Biomas del mod construidos en datagen.
 *
 * POR QUÉ NO SON JSON A MANO: el juego construye UNA ordenación global de features por paso
 * de decoración mezclando todos los biomas cargados. Si dos biomas discrepan sobre el orden
 * relativo de dos features compartidas, no existe orden global válido y el arranque muere
 * con "Feature order cycle found". Llamar a BiomeDefaultFeatures da ese orden por
 * construcción. Bonus: aquí un id de vainilla equivocado es error de compilación, no un
 * crash al crear el mundo.
 *
 * EL ORDEN DE LAS LLAMADAS ES SEMÁNTICA, NO ESTILO: replica globalOverworldGeneration()
 * seguido de los ores. Reordenarlas reintroduce el ciclo.
 */
public final class ModBiomeGen {
    private ModBiomeGen() {}

    // Paleta compartida de HFIL. El agua es sangre en las tres variantes.
    private static final int SKY_COLOR       = 3803658; // #3A0A0A
    private static final int WATER_COLOR     = 9044739; // #8A0303
    private static final int WATER_FOG_COLOR = 4849921; // #4A0101

    public static void bootstrap(BootstrapContext<Biome> ctx) {
        HolderGetter<PlacedFeature> features = ctx.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carvers = ctx.lookup(Registries.CONFIGURED_CARVER);

        ctx.register(ModBiomes.ROCKY_WASTELAND, rockyWasteland(features, carvers));
        ctx.register(ModBiomes.HFIL_BADLANDS,   hfilBadlands(features, carvers));
        ctx.register(ModBiomes.HFIL_WASTES,     hfilWastes(features, carvers));
        ctx.register(ModBiomes.HFIL_DUNES,      hfilDunes(features, carvers));
        ctx.register(ModBiomes.OTHERWORLD, otherworld(features, carvers));
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
     * Base común de las tres variantes de HFIL: cuevas, lagos de lava, dungeons, líquenes
     * (única fuente de luz ahí abajo), manantiales y TODOS los ores vanilla.
     * Sin discos blandos: arcilla y arena junto al agua no pegan en el infierno.
     */
    private static BiomeGenerationSettings.Builder hfilBase(HolderGetter<PlacedFeature> features,
                                                            HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = new BiomeGenerationSettings.Builder(features, carvers);
        BiomeDefaultFeatures.addDefaultCarversAndLakes(gen);
        BiomeDefaultFeatures.addDefaultMonsterRoom(gen);
        BiomeDefaultFeatures.addDefaultUndergroundVariety(gen);
        BiomeDefaultFeatures.addDefaultSprings(gen);
        BiomeDefaultFeatures.addDefaultOres(gen);
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

    /** Terracota bandeada. Es el fallback de la surface rule, o sea lo que sale por defecto. */
    private static Biome hfilBadlands(HolderGetter<PlacedFeature> features,
                                      HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_DEAD_BUSH_KEY);
        return hfilBuild(hfilEffects(6034452).build(), gen); // #5C1414
    }

    /**
     * Llanura de hierba muerta. El suelo es coarse_dirt (ver surface rule): NO grass_block,
     * que moriría por falta de luz bajo la capa de nubes. El aspecto de pasto lo da el
     * short_grass, que sobrevive a oscuras y se tiñe con grassColorOverride.
     */
    private static Biome hfilWastes(HolderGetter<PlacedFeature> features,
                                    HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_DRY_GRASS_KEY);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_DEAD_BUSH_KEY);

        BiomeSpecialEffects effects = hfilEffects(5118482)   // #4E1A12
                .grassColorOverride(8016432)                 // #7A5230 ocre pardo
                .foliageColorOverride(7029798)               // #6B4426
                .build();
        return hfilBuild(effects, gen);
    }

    /** Dunas de arena roja. Sin vegetación salvo algún matojo. */
    private static Biome hfilDunes(HolderGetter<PlacedFeature> features,
                                   HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        BiomeGenerationSettings.Builder gen = hfilBase(features, carvers);
        gen.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION,
                ModPlacedFeatures.HFIL_DEAD_BUSH_KEY);
        return hfilBuild(hfilEffects(7219736).build(), gen); // #6E2A18
    }

    /**
     * Islas flotantes sobre el mar de nubes. Sin carvers ni ores: son plataformas, no un mundo
     * subterráneo.
     *
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

        BiomeDefaultFeatures.addCherryGroveVegetation(gen);                 // ⚠ API

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
}