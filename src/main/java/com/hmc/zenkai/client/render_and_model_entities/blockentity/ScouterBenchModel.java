package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ScouterBenchModel extends GeoModel<ScouterBenchBlockEntity> {
    @Override
    public ResourceLocation getModelResource(ScouterBenchBlockEntity be) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/scouter_bench.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ScouterBenchBlockEntity be) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/block/scouter_bench.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ScouterBenchBlockEntity be) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "animations/scouter_bench.animation.json");
    }
}