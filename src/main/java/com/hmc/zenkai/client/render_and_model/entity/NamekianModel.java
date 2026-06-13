package com.hmc.zenkai.client.render_and_model.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.namekian.NamekianEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class NamekianModel extends DefaultedEntityGeoModel<NamekianEntity> {
    public NamekianModel(ResourceLocation assetSubpath, boolean turnsHead) {
        super(assetSubpath, turnsHead);
    }

    @Override
    public ResourceLocation getModelResource(NamekianEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"geo/namekian.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NamekianEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"textures/entity/namekian.png");
    }

    @Override
    public ResourceLocation getAnimationResource(NamekianEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"animations/namekian_default.animation.json");
    }

    @Override
    public RenderType getRenderType(NamekianEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}
