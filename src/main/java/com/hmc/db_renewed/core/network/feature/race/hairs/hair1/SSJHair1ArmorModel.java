package com.hmc.db_renewed.core.network.feature.race.hairs.hair1;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SSJHair1ArmorModel extends GeoModel<SSJHair1ArmorItem> {

    @Override
    public ResourceLocation getModelResource(SSJHair1ArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "geo/hairs/hair1/ssj_hair_1.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SSJHair1ArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/hairs/hair1/ssj_hair_1.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SSJHair1ArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/arcosian_default.animation.json");
    }
}