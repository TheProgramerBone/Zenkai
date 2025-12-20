package com.hmc.db_renewed.entity.kintoun;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class KintounRenderer extends GeoEntityRenderer<KintounEntity> {

    public KintounRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new KintounModel());
        this.shadowRadius = 1f;
    }
}
