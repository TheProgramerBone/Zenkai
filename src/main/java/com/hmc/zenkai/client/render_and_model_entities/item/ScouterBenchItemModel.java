package com.hmc.zenkai.client.render_and_model_entities.item;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.ScouterBenchBlockItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Mismos assets que el bloque colocado. Es el punto del acuerdo: hay UN modelo y UNA textura,
 * y el ítem no puede desincronizarse del bloque porque no tiene nada propio que mantener.
 * El archivo de animación se declara porque GeoModel lo pide, pero el ítem no registra ningún
 * controlador, así que no se reproduce nada.
 */
public class ScouterBenchItemModel extends GeoModel<ScouterBenchBlockItem> {

    // Cacheados: GeckoLib llama a los tres getters de abajo cada frame (mismo patrón que
    // GenericGeoModel ya usa para el resto de entidades).
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/scouter_bench_item.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/block/scouter_bench.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "animations/scouter_bench.animation.json");

    @Override
    public ResourceLocation getModelResource(ScouterBenchBlockItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ScouterBenchBlockItem item) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ScouterBenchBlockItem item) {
        return ANIMATION;
    }
}