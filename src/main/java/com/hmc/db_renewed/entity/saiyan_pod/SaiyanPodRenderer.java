package com.hmc.db_renewed.entity.saiyan_pod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SaiyanPodRenderer extends GeoEntityRenderer<SaiyanPodEntity> {

    public SaiyanPodRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SaiyanPodModel());
    }
}
