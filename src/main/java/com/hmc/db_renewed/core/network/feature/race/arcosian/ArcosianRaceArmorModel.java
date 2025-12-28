package com.hmc.db_renewed.core.network.feature.race.arcosian;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class ArcosianRaceArmorModel extends GeoModel<ArcosianRaceArmorItem> {

    @Override
    public ResourceLocation getModelResource(ArcosianRaceArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "geo/races/arcosian_final_form_player.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ArcosianRaceArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "textures/models/races/arcosian_final_form.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ArcosianRaceArmorItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,"animations/arcosian_default.animation.json");
    }
}