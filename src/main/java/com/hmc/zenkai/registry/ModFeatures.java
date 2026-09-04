package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.CloudLayerFeature;
import com.hmc.zenkai.worldgen.FallenLogFeature;
import com.hmc.zenkai.worldgen.HfilBloodPoolFeature;
import com.hmc.zenkai.worldgen.HfilBonePileFeature;
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

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}