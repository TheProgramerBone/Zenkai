package com.hmc.db_renewed.client.render_and_model.entity;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.content.entity.kintoun.ShadowKintounEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ShadowKintounModel extends GeoModel<ShadowKintounEntity> {
    @Override
    public ResourceLocation getModelResource(ShadowKintounEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"geo/kintoun.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShadowKintounEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"textures/entity/kintoun_shadow.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShadowKintounEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/kintoun.animation.json");
    }
}
