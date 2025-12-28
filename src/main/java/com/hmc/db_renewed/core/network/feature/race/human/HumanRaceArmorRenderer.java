package com.hmc.db_renewed.core.network.feature.race.human;

import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class HumanRaceArmorRenderer extends GeoArmorRenderer<HumanRaceArmorItem> {
    public HumanRaceArmorRenderer() {
        super(new HumanRaceArmorModel());
    }
}
