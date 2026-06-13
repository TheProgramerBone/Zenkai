package com.hmc.zenkai.client.render_and_model.entity;

import com.hmc.zenkai.content.entity.kintoun.ShadowKintounEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ShadowKintounRenderer extends GeoEntityRenderer<ShadowKintounEntity> {

    public ShadowKintounRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ShadowKintounModel());
        this.shadowRadius = 1f;
    }
}
