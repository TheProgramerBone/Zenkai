package com.hmc.db_renewed.client.render_and_model.entity;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.content.entity.namekian.NamekianWarriorEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

public class NamekianWarriorModel extends DefaultedEntityGeoModel<NamekianWarriorEntity> {

    public NamekianWarriorModel(ResourceLocation assetSubpath, boolean turnsHead) {
        super(assetSubpath, turnsHead);
    }

    @Override
    public ResourceLocation getModelResource(NamekianWarriorEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"geo/namekian_warrior.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NamekianWarriorEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"textures/entity/namekian_warrior.png");
    }

    @Override
    public ResourceLocation getAnimationResource(NamekianWarriorEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/namekian_default.animation.json");
    }

    @Override
    public RenderType getRenderType(NamekianWarriorEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}
