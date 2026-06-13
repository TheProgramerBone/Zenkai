package com.hmc.zenkai.client.render_and_model.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.space_pod.SpacePodEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SpacePodModel extends GeoModel<SpacePodEntity> {
    @Override
    public ResourceLocation getModelResource(SpacePodEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"geo/space_pod.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SpacePodEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"textures/entity/space_pod.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SpacePodEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"animations/space_pod.animation.json");
    }

    @Override
    public RenderType getRenderType(SpacePodEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}