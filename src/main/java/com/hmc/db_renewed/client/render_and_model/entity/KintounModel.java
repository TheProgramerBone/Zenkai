package com.hmc.db_renewed.client.render_and_model.entity;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.content.entity.kintoun.KintounEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class KintounModel extends GeoModel<KintounEntity> {
    @Override
    public ResourceLocation getModelResource(KintounEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"geo/kintoun.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(KintounEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"textures/entity/kintoun.png");
    }

    @Override
    public ResourceLocation getAnimationResource(KintounEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/kintoun.animation.json");
    }
}
