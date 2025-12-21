package com.hmc.db_renewed.client.render_and_model.entity;

import com.hmc.db_renewed.content.entity.ki_attacks.KiBlastEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class KiBlastRenderer extends GeoEntityRenderer<KiBlastEntity> {
    public KiBlastRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new KiBlastModel());
    }
}
