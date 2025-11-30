package com.hmc.db_renewed.entity.ki_attacks.ki_blast;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class KiBlastRenderer extends GeoEntityRenderer<KiBlastEntity> {
    public KiBlastRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new KiBlastModel());
    }
}
