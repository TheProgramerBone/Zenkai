package com.hmc.zenkai.event;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;

public final class ZenkaiPalLayers {
    private ZenkaiPalLayers() {}

    public static final ResourceLocation TRANSFORM_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "transform");

    public static final ResourceLocation FLY_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "fly");

    public static final ResourceLocation BLOCK_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "block");

    public static final ResourceLocation COMBAT_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "combat");

    public static final ResourceLocation PHYS_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "phys");

    public static final ResourceLocation KI_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki");

    /** Preview de los editores (técnicas, y lo que venga). NO es una capa de gameplay:
     *  la escribe únicamente TechniqueAnimPreview y su controlador va en FirstPersonMode.NONE,
     *  así que la animación no entra en la pasada de 1ª persona.
     *  FUERA de ALL a propósito: ALL sirve para aplicar la política de 1ª persona, y una capa
     *  en modo NONE no tiene política de 1ª persona que aplicar. */
    public static final ResourceLocation PREVIEW_LAYER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "preview");

    /** El conjunto de capas del mod, para aplicarles políticas comunes de una pasada. */
    public static final ResourceLocation[] ALL = {
            TRANSFORM_LAYER, FLY_LAYER, BLOCK_LAYER, COMBAT_LAYER, PHYS_LAYER, KI_LAYER
    };
}