package com.hmc.db_renewed.entity.namekian;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

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
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/namekian_warrior.animation.json");
    }

    @Override
    public RenderType getRenderType(NamekianWarriorEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}
