package com.hmc.zenkai.client.render_and_model.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.shenlong.ShenLongEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class ShenLongModel extends DefaultedEntityGeoModel<ShenLongEntity> {
    public ShenLongModel(ResourceLocation assetSubpath, boolean turnsHead) {
        super(assetSubpath, turnsHead);
    }

    @Override
    public ResourceLocation getModelResource(ShenLongEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"geo/shenlong.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShenLongEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"textures/entity/shenlong.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShenLongEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"animations/shenlong.animation.json");
    }
}
