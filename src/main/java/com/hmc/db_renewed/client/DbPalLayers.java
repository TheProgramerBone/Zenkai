package com.hmc.db_renewed.client;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.resources.ResourceLocation;

public final class DbPalLayers {
    private DbPalLayers() {}

    // Una capa dedicada solo a transformaciones
    public static final ResourceLocation TRANSFORM_LAYER =
            ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "transform");
}