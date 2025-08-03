package com.hmc.db_renewed.entity.namekian;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NamekianWarriorRenderer extends GeoEntityRenderer<NamekianWarriorEntity> {

    public NamekianWarriorRenderer(EntityRendererProvider.Context context) {
        super(context, new NamekianWarriorModel(ResourceLocation.fromNamespaceAndPath("db_renewed", "geo/namekian_warrior.geo.json"), true));
        this.shadowRadius = 0.5f;
    }
}
