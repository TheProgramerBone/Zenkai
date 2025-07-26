package com.hmc.db_renewed.block.entity.client;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.block.entity.AllDragonBallsEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class AllDragonBallsModel extends GeoModel<AllDragonBallsEntity> {
    @Override
    public ResourceLocation getModelResource(AllDragonBallsEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"geo/all_dragon_balls.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AllDragonBallsEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"textures/block/all_dragon_balls_texture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(AllDragonBallsEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/all_dragon_balls.animation.json");
    }

    @Override
    public RenderType getRenderType(AllDragonBallsEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}
