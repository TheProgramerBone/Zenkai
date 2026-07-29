package com.hmc.zenkai.client.render_and_model_entities.item;

import com.hmc.zenkai.content.item.KiWeaponItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Modelo de las armas de ki. Las rutas las decide el propio item a partir de su nombre de
 * asset, así que registrar una tercera arma no obliga a tocar esta clase ni el renderer:
 * basta con el item nuevo y sus tres archivos.
 */
public class KiWeaponModel extends GeoModel<KiWeaponItem> {

    @Override
    public ResourceLocation getModelResource(KiWeaponItem item) {
        return item.geoPath();
    }

    @Override
    public ResourceLocation getTextureResource(KiWeaponItem item) {
        return item.texturePath();
    }

    @Override
    public ResourceLocation getAnimationResource(KiWeaponItem item) {
        return item.animationPath();
    }
}