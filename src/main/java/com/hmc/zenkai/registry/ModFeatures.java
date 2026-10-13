package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.CloudLayerFeature;
import com.hmc.zenkai.worldgen.FallenLogFeature;
import com.hmc.zenkai.worldgen.HfilBloodPoolFeature;
import com.hmc.zenkai.worldgen.HfilBoilingPoolFeature;
import com.hmc.zenkai.worldgen.HfilBonePileFeature;
import com.hmc.zenkai.worldgen.HfilCairnFeature;
import com.hmc.zenkai.worldgen.HfilGeyserFeature;
import com.hmc.zenkai.worldgen.HfilFrozenLakeFeature;
import com.hmc.zenkai.worldgen.HfilGasCraterFeature;
import com.hmc.zenkai.worldgen.HfilOniCauldronFeature;
import com.hmc.zenkai.worldgen.HfilSulfurFieldFeature;
import com.hmc.zenkai.worldgen.HfilMudPotFeature;
import com.hmc.zenkai.worldgen.HfilTerraceFeature;
import com.hmc.zenkai.worldgen.HfilOreBoulderFeature;
import com.hmc.zenkai.worldgen.HfilSpikeFeature;
import com.hmc.zenkai.worldgen.RockyWastelandSpireFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registro de features propios del mod. Llama ModFeatures.register(modEventBus)
 * en el constructor del mod.
 */
public final class ModFeatures {
    private ModFeatures() {}

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Zenkai.MOD_ID);

    public static final DeferredHolder<Feature<?>, CloudLayerFeature> CLOUD_LAYER =
            FEATURES.register("cloud_layer", () -> new CloudLayerFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, FallenLogFeature> FALLEN_LOG =
            FEATURES.register("fallen_log", () -> new FallenLogFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, HfilSpikeFeature> HFIL_SPIKE =
            FEATURES.register("hfil_spike", () -> new HfilSpikeFeature(NoneFeatureConfiguration.CODEC, ModBlocks.HFIL_SPIKE_ROCK));

    /** Misma clase que HFIL_SPIKE, paleta cálida propia de las dunas en vez de la roca fría —
     *  ver el javadoc de HfilSpikeFeature. Exclusiva de cinder_dunes. */
    public static final DeferredHolder<Feature<?>, HfilSpikeFeature> HFIL_CINDER_SPIKE =
            FEATURES.register("hfil_cinder_spike", () -> new HfilSpikeFeature(NoneFeatureConfiguration.CODEC, ModBlocks.HFIL_CINDER_SANDSTONE));

    public static final DeferredHolder<Feature<?>, HfilBonePileFeature> HFIL_BONE_PILE =
            FEATURES.register("hfil_bone_pile", () -> new HfilBonePileFeature(NoneFeatureConfiguration.CODEC));

    /** Charco de sangre abierto (Fase 5 del rework, sustituye a Feature.LAKE — ver
     *  HfilBloodPoolFeature para el porqué). */
    public static final DeferredHolder<Feature<?>, HfilBloodPoolFeature> HFIL_BLOOD_POOL =
            FEATURES.register("hfil_blood_pool", () -> new HfilBloodPoolFeature(NoneFeatureConfiguration.CODEC));

    /** Afloramiento de Katchin visible en superficie — ver HfilOreBoulderFeature. Registrado dos
     *  veces (ModPlacedFeatures) con distinta rareza según el bioma. */
    public static final DeferredHolder<Feature<?>, HfilOreBoulderFeature> HFIL_ORE_BOULDER =
            FEATURES.register("hfil_ore_boulder", () -> new HfilOreBoulderFeature(NoneFeatureConfiguration.CODEC));

    /** Cañón de agujas rocosas anaranjadas (hoodoos/mesas) de rocky_wasteland — pedido con imagen
     *  de referencia de Dragon Ball, sesión 2026-09-04. Ver RockyWastelandSpireFeature para el
     *  porqué NO reusa HfilSpikeFeature pese al parecido superficial. */
    public static final DeferredHolder<Feature<?>, RockyWastelandSpireFeature> ROCKY_WASTELAND_SPIRE =
            FEATURES.register("rocky_wasteland_spire", () -> new RockyWastelandSpireFeature(NoneFeatureConfiguration.CODEC, ModBlocks.ROCKY_BLOCK));

    // Propuestas C y A del HFIL (2026-10-02, ver .claude/docs/worldgen.md):
    // mojones de Sai no Kawara y pozas de los "infiernos" de Beppu.
    public static final DeferredHolder<Feature<?>, HfilCairnFeature> HFIL_CAIRNS =
            FEATURES.register("hfil_cairns", () -> new HfilCairnFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, HfilBoilingPoolFeature> HFIL_BOILING_POOL =
            FEATURES.register("hfil_boiling_pool", () -> new HfilBoilingPoolFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, HfilMudPotFeature> HFIL_MUD_POT =
            FEATURES.register("hfil_mud_pot", () -> new HfilMudPotFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, HfilGeyserFeature> HFIL_GEYSER =
            FEATURES.register("hfil_geyser", () -> new HfilGeyserFeature(NoneFeatureConfiguration.CODEC));

    /** Terrazas escalonadas con pozas de sangre (propuesta B, Pamukkale). Solo needle_wastes. */
    public static final DeferredHolder<Feature<?>, HfilTerraceFeature> HFIL_TERRACES =
            FEATURES.register("hfil_terraces", () -> new HfilTerraceFeature(NoneFeatureConfiguration.CODEC));

    /** Lago de sangre congelada (propuesta D). Solo hfil_frozen_lake. */
    public static final DeferredHolder<Feature<?>, HfilFrozenLakeFeature> HFIL_FROZEN_LAKE =
            FEATURES.register("hfil_frozen_lake", () -> new HfilFrozenLakeFeature(NoneFeatureConfiguration.CODEC));

    // Propuestas E, F y G (2026-10-02): campos de azufre (Osorezan), cráter de gas en llamas
    // (Darvaza) y caldero de los oni (kama-yude).
    public static final DeferredHolder<Feature<?>, HfilSulfurFieldFeature> HFIL_SULFUR_FIELD =
            FEATURES.register("hfil_sulfur_field", () -> new HfilSulfurFieldFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, HfilGasCraterFeature> HFIL_GAS_CRATER =
            FEATURES.register("hfil_gas_crater", () -> new HfilGasCraterFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, HfilOniCauldronFeature> HFIL_ONI_CAULDRON =
            FEATURES.register("hfil_oni_cauldron", () -> new HfilOniCauldronFeature(NoneFeatureConfiguration.CODEC));

    // Janemba Rift (rehecho el 2026-10-05 sobre la referencia de Fusion Reborn): montículos,
    // agujas, crestas de pinchos, masas azules, costillas, esferas y protuberancias.
    public static final DeferredHolder<Feature<?>, com.hmc.zenkai.worldgen.JanembaMoundFeature> JANEMBA_MOUND =
            FEATURES.register("janemba_mound", () -> new com.hmc.zenkai.worldgen.JanembaMoundFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, com.hmc.zenkai.worldgen.JanembaSpireFeature> JANEMBA_SPIRE =
            FEATURES.register("janemba_spire", () -> new com.hmc.zenkai.worldgen.JanembaSpireFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, com.hmc.zenkai.worldgen.JanembaSpineRidgeFeature> JANEMBA_RIDGE =
            FEATURES.register("janemba_ridge", () -> new com.hmc.zenkai.worldgen.JanembaSpineRidgeFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, com.hmc.zenkai.worldgen.JanembaRibFeature> JANEMBA_RIB =
            FEATURES.register("janemba_rib", () -> new com.hmc.zenkai.worldgen.JanembaRibFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, com.hmc.zenkai.worldgen.JanembaOrbFeature> JANEMBA_ORB =
            FEATURES.register("janemba_orb", () -> new com.hmc.zenkai.worldgen.JanembaOrbFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, com.hmc.zenkai.worldgen.JanembaDomeFeature> JANEMBA_DOME =
            FEATURES.register("janemba_dome", () -> new com.hmc.zenkai.worldgen.JanembaDomeFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, com.hmc.zenkai.worldgen.JanembaNubFeature> JANEMBA_NUB =
            FEATURES.register("janemba_nub", () -> new com.hmc.zenkai.worldgen.JanembaNubFeature(NoneFeatureConfiguration.CODEC));

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}