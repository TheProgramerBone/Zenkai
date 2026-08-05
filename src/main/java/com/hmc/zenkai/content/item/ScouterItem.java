package com.hmc.zenkai.content.item;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.feature.sense.ScouterStacks;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * Scouter: casco GeckoLib que REUSA la infraestructura de las armaduras de raza (patrón HALO).
 * TINTABLE con los tintes vanilla (craftear con tinte / lavar en caldero):
 *  - Requiere estar en el tag #minecraft:dyeable (data/minecraft/tags/item/dyeable.json).
 *  - Puesto: DyedTintGeoLayer pinta &lt;textura&gt;_tint.png (grises) con el color del tinte.
 *  - Icono: ScouterItemColors tiñe la layer1 del modelo de item (también en grises).
 *  - Sin teñir: color por defecto DEFAULT_TINT.
 * ROTO: el stack lleva el componente scouter_broken y el modelo cambia de textura. La decisión
 * vive AQUÍ y no en el renderer — el renderer no tiene por qué saber qué es un scouter.
 */
public class ScouterItem extends GeoLayerArmorItem {

    /** Verde clásico de scouter (color del cristal cuando no está teñido). */
    public static final int DEFAULT_TINT = 0xd82624;

    /** Mismo UV que scouter.png: solo cambian los píxeles, el .geo.json no se toca. */
    private static final ResourceLocation BROKEN_TEX = ResourceLocation.fromNamespaceAndPath(
            Zenkai.MOD_ID, "textures/models/armor/scouter_broken.png");

    public ScouterItem(Holder<ArmorMaterial> material, Properties properties,
                       String modelPath, String texturePath) {
        super(material, ArmorItem.Type.HELMET, properties, modelPath, texturePath, "");
        this.dyeTint(DEFAULT_TINT);
        this.stackTexture(stack -> ScouterStacks.isBroken(stack) ? BROKEN_TEX : null);
    }
}