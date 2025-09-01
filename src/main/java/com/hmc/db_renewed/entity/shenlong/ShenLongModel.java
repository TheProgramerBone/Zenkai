package com.hmc.db_renewed.entity.shenlong;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class ShenLongModel extends DefaultedEntityGeoModel<ShenLongEntity> {
    public ShenLongModel(ResourceLocation assetSubpath, boolean turnsHead) {
        super(assetSubpath, turnsHead);
    }

    @Override
    public ResourceLocation getModelResource(ShenLongEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"geo/shenlong.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShenLongEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"textures/entity/shenlong.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShenLongEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/shenlong.animation.json");
    }
}
