package com.hmc.zenkai.content.item.special;

import com.hmc.zenkai.core.network.feature.race.GeoLayerArmorItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * Scouter: casco GeckoLib que REUSA la infraestructura de las armaduras de raza (patrón HALO).
 * TINTABLE con los tintes vanilla (craftear con tinte / lavar en caldero):
 *  - Requiere estar en el tag #minecraft:dyeable (data/minecraft/tags/item/dyeable.json).
 *  - Puesto: DyedTintGeoLayer pinta <textura>_tint.png (escala de grises) con el color del tinte.
 *  - Icono: ScouterItemColors tiñe la layer1 del modelo de item (también en grises).
 *  - Sin teñir: color por defecto DEFAULT_TINT (verde scouter clásico).
 * Marcador para F4: ScouterClientState hace `instanceof ScouterItem`.
 */
public class ScouterItem extends GeoLayerArmorItem {

    /** Verde clásico de scouter (color del cristal cuando no está teñido). */
    public static final int DEFAULT_TINT = 0xd82624;

    public ScouterItem(Holder<ArmorMaterial> material, Properties properties,
                       String modelPath, String texturePath) {
        super(material, ArmorItem.Type.HELMET, properties, modelPath, texturePath, "");
        this.dyeTint(DEFAULT_TINT);
    }
}