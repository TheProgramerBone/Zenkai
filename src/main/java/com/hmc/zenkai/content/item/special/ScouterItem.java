package com.hmc.zenkai.content.item.special;

import com.hmc.zenkai.core.network.feature.race.GeoLayerArmorItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

public class ScouterItem extends GeoLayerArmorItem {

    public ScouterItem(Holder<ArmorMaterial> material, Properties properties,
                       String modelPath, String texturePath) {
        super(material, ArmorItem.Type.HELMET, properties, modelPath, texturePath, "");
    }
}