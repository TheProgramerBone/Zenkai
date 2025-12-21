package com.hmc.db_renewed.core.network.feature.race;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class NamekianRaceArmorModel extends GeoModel<NamekainRaceArmorItem> {

    @Override
    public ResourceLocation getModelResource(NamekainRaceArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "geo/races/namekian_player.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NamekainRaceArmorItem animatable) {
        // Aquí debes devolver la textura en formato .png
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/models/races/namekian_player.png");
    }

    @Override
    public ResourceLocation getAnimationResource(NamekainRaceArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/namekian_default.animation.json");
    }
}