package com.hmc.db_renewed.entity.namekian;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class NamekianModel extends DefaultedEntityGeoModel<NamekianEntity> {
    public NamekianModel(ResourceLocation assetSubpath, boolean turnsHead) {
        super(assetSubpath, turnsHead);
    }

    @Override
    public ResourceLocation getModelResource(NamekianEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"geo/namekian.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NamekianEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"textures/entity/namekian.png");
    }

    @Override
    public ResourceLocation getAnimationResource(NamekianEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/namekian.animation.json");
    }

    @Override
    public RenderType getRenderType(NamekianEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}
