package com.hmc.zenkai.core.network.feature.race;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
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

    @Override
    public @Nullable RenderType getRenderType(GeoLayerArmorItem animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}