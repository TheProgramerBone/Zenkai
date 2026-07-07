package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;

public final class ZenkaiPalLayers {
    private ZenkaiPalLayers() {}

    public static final ResourceLocation TRANSFORM_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "transform");

    public static final ResourceLocation FLY_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fly");
}