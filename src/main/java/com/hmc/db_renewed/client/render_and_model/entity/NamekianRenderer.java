package com.hmc.db_renewed.client.render_and_model.entity;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.content.entity.namekian.NamekianEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class  NamekianRenderer extends GeoEntityRenderer<NamekianEntity> {

    public NamekianRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new NamekianModel(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "geo/namekian.geo.json"), true));
        this.shadowRadius = 0.5f;
    }
}