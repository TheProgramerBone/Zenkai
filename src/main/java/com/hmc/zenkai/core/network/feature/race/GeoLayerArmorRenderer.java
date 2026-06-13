package com.hmc.zenkai.core.network.feature.race;

import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class GeoLayerArmorRenderer extends GeoArmorRenderer<GeoLayerArmorItem> {
    public GeoLayerArmorRenderer(GeoLayerArmorItem item) {
        super(new GeoLayerArmorModel());
    }
}