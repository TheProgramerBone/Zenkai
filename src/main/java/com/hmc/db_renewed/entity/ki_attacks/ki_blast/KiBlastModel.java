package com.hmc.db_renewed.entity.ki_attacks.ki_blast;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class KiBlastModel extends GeoModel<KiBlastEntity>{

    @Override
    public ResourceLocation getModelResource(KiBlastEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"geo/ki_blast.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(KiBlastEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"textures/entity/ki_blast.png");
    }

    @Override
    public ResourceLocation getAnimationResource(KiBlastEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/ki_blast.animation.json");
    }
}
