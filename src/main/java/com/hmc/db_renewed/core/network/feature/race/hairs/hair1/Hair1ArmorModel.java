package com.hmc.db_renewed.core.network.feature.race.hairs.hair1;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class Hair1ArmorModel extends GeoModel<Hair1ArmorItem> {

    @Override
    public ResourceLocation getModelResource(Hair1ArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "geo/hairs/hair1/hair_1.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(Hair1ArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/hairs/hair1/hair_1.png");
    }

    @Override
    public ResourceLocation getAnimationResource(Hair1ArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/arcosian_default.animation.json");
    }
}