package com.hmc.zenkai.core.network.feature.race;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class GeoLayerArmorModel extends GeoModel<GeoLayerArmorItem> {

    @Override
    public ResourceLocation getModelResource(GeoLayerArmorItem item) {
        return item.getModelPath();
    }

    @Override
    public ResourceLocation getTextureResource(GeoLayerArmorItem item) {
        return item.getTexturePath();
    }

    @Override
    public ResourceLocation getAnimationResource(GeoLayerArmorItem item) {
        return item.getAnimationPath();
    }
}