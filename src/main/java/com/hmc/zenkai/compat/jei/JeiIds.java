package com.hmc.zenkai.compat.jei;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;

/** Ids de las categorías de JEI propias. */
final class JeiIds {
    private JeiIds() {}

    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path); }
}
