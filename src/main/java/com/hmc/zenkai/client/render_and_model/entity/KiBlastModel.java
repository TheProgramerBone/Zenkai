package com.hmc.zenkai.client.render_and_model.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.ki_attacks.KiBlastEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class KiBlastModel extends GeoModel<KiBlastEntity>{

    @Override
    public ResourceLocation getModelResource(KiBlastEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"geo/ki_blast.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(KiBlastEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"textures/entity/ki_blast.png");
    }

    @Override
    public ResourceLocation getAnimationResource(KiBlastEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,"animations/ki_blast.animation.json");
    }
}
