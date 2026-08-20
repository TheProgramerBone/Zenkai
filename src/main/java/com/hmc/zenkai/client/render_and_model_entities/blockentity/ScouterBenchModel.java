package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ScouterBenchModel extends GeoModel<ScouterBenchBlockEntity> {
    // Cacheados: GeckoLib llama a los tres getters de abajo cada frame (mismo patrón que
    // GenericGeoModel ya usa para el resto de entidades).
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/scouter_bench.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/block/scouter_bench.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "animations/scouter_bench.animation.json");

    @Override
    public ResourceLocation getModelResource(ScouterBenchBlockEntity be) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ScouterBenchBlockEntity be) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ScouterBenchBlockEntity be) {
        return ANIMATION;
    }
}