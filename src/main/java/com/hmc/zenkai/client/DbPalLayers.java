package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;

public final class DbPalLayers {
    private DbPalLayers() {}

    public static final ResourceLocation TRANSFORM_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "transform");
}