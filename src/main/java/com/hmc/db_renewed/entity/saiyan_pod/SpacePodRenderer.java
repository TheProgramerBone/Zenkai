package com.hmc.db_renewed.entity.saiyan_pod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SpacePodRenderer extends GeoEntityRenderer<SpacePodEntity> {

    public SpacePodRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SpacePodModel());
    }
}
