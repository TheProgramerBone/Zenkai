package com.hmc.zenkai.core.network.feature.race;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Utilidades de textura compartidas por las capas de render de raza.
 * Centraliza los helpers que antes estaban copiados en ZenkaiFirstPersonArmHooks,
 * FaceOverlayGeoLayer y BodyTintGeoLayer.
 */
public final class RaceTextureUtil {

    private RaceTextureUtil() {}

    /** ¿Existe el recurso en el resource manager del cliente? */
    public static boolean resourceExists(ResourceLocation rl) {
        return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
    }

    /**
     * Deriva una máscara a partir de la textura base: quita el sufijo {@code _colorable} (si lo tiene)
     * y añade {@code suffix} antes de la extensión.
     * Ej: {@code body_colorable.png} + {@code "_detail"} -> {@code body_detail.png}
     */
    public static ResourceLocation deriveMask(ResourceLocation base, String suffix) {
        String p = base.getPath();
        int dot = p.lastIndexOf('.');
        String ext  = (dot >= 0) ? p.substring(dot) : ".png";
        String stem = (dot >= 0) ? p.substring(0, dot) : p;
        if (stem.endsWith("_colorable")) stem = stem.substring(0, stem.length() - "_colorable".length());
        return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), stem + suffix + ext);
    }

    /**
     * Inserta {@code suffix} antes de la extensión SIN quitar {@code _colorable}.
     * Ej: {@code eyes.png} + {@code "_iris"} -> {@code eyes_iris.png}
     */
    public static ResourceLocation withSuffix(ResourceLocation rl, String suffix) {
        String p = rl.getPath();
        int dot = p.lastIndexOf('.');
        String np = (dot >= 0) ? p.substring(0, dot) + suffix + p.substring(dot) : p + suffix;
        return ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), np);
    }
}