package com.hmc.db_renewed.core.network.feature.race.human;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class HumanRaceArmorModel extends GeoModel<HumanRaceArmorItem> {

    @Override
    public ResourceLocation getModelResource(HumanRaceArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "geo/races/human_player.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(HumanRaceArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/models/races/human_player.png");
    }

    @Override
    public ResourceLocation getAnimationResource(HumanRaceArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/human_default.animation.json");
    }
}