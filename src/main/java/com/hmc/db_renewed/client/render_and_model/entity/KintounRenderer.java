package com.hmc.db_renewed.client.render_and_model.entity;

import com.hmc.db_renewed.content.entity.kintoun.KintounEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class KintounRenderer extends GeoEntityRenderer<KintounEntity> {

    public KintounRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new KintounModel());
        this.shadowRadius = 1f;
    }
}
