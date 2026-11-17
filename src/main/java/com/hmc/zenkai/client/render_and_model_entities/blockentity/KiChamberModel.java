package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Modelo de la cámara de contención de ki. Geo, textura y animación los genera
 * tools/gen_ki_chamber_model.py.
 * RenderType por defecto (recortado): esta textura NO lleva el cristal. El cristal se pinta
 * aparte y el último, translúcido, en KiChamberGlassLayer; el porqué está allí.
 */
public class KiChamberModel extends GeoModel<KiContainmentChamberBlockEntity> {
    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/ki_containment_chamber.geo.json");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/block/ki_containment_chamber.png");
    /** Geo y textura del ÍTEM: copia del geo (dos modelos con el mismo geo comparten huesos y el
     *  ítem del inventario congelaba la animación del bloque) y textura con el cristal incluido. */
    public static final ResourceLocation ITEM_MODEL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/ki_containment_chamber_item.geo.json");
    public static final ResourceLocation ITEM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/block/ki_containment_chamber_item.png");
    public static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "animations/ki_containment_chamber.animation.json");

    // GeckoLib deja esta firma deprecada pero abstracta: hay que implementarla.
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getModelResource(KiContainmentChamberBlockEntity be) { return MODEL; }

    // GeckoLib deja esta firma deprecada pero abstracta: hay que implementarla.
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureResource(KiContainmentChamberBlockEntity be) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(KiContainmentChamberBlockEntity be) { return ANIMATION; }
}
