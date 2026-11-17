package com.hmc.zenkai.client.render_and_model_entities.item;

import com.hmc.zenkai.client.render_and_model_entities.blockentity.KiChamberModel;
import com.hmc.zenkai.content.item.KiChamberBlockItem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Modelo del ítem: los mismos dibujos que el bloque, pero con su COPIA del geo y una textura que
 * ya trae el cristal (una sola pasada translúcida: el ítem no brilla). Los genera el mismo
 * tools/gen_ki_chamber_model.py, así que no hay nada que mantener a mano.
 */
public class KiChamberItemModel extends GeoModel<KiChamberBlockItem> {

    // GeckoLib deja esta firma deprecada pero abstracta: hay que implementarla.
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getModelResource(KiChamberBlockItem item) { return KiChamberModel.ITEM_MODEL; }

    // GeckoLib deja esta firma deprecada pero abstracta: hay que implementarla.
    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureResource(KiChamberBlockItem item) { return KiChamberModel.ITEM_TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(KiChamberBlockItem item) { return KiChamberModel.ANIMATION; }

    @Override
    public RenderType getRenderType(KiChamberBlockItem item, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
