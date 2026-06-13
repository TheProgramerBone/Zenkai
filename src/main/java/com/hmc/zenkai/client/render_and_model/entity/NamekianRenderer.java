package com.hmc.zenkai.client.render_and_model.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.namekian.NamekianEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class  NamekianRenderer extends GeoEntityRenderer<NamekianEntity> {

    public NamekianRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new NamekianModel(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/namekian.geo.json"), true));
        this.shadowRadius = 0.5f;
    }
}