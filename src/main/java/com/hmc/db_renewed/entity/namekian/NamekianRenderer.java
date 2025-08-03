package com.hmc.db_renewed.entity.namekian;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NamekianRenderer extends GeoEntityRenderer<NamekianEntity> {

    public NamekianRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new NamekianModel(ResourceLocation.fromNamespaceAndPath("db_renewed", "geo/namekian.geo.json"), true));
        this.shadowRadius = 0.5f;
    }
}