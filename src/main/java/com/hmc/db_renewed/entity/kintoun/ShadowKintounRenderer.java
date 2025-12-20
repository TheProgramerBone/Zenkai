package com.hmc.db_renewed.entity.kintoun;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ShadowKintounRenderer extends GeoEntityRenderer<ShadowKintounEntity> {

    public ShadowKintounRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ShadowKintounModel());
        this.shadowRadius = 1f;
    }
}
