package com.hmc.db_renewed.client.render_and_model.entity;

import com.hmc.db_renewed.content.entity.space_pod.SpacePodEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SpacePodRenderer extends GeoEntityRenderer<SpacePodEntity> {

    public SpacePodRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SpacePodModel());
        this.shadowRadius = 1f;
    }
}
