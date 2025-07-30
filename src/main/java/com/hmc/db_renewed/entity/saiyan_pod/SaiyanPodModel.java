package com.hmc.db_renewed.entity.saiyan_pod;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SaiyanPodModel extends GeoModel<SaiyanPodEntity> {
    @Override
    public ResourceLocation getModelResource(SaiyanPodEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"geo/saiyan_pod.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SaiyanPodEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"textures/entity/saiyan_pod.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SaiyanPodEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/saiyan_pod.animation.json");
    }

    @Override
    public RenderType getRenderType(SaiyanPodEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}