package com.hmc.zenkai.client.render_and_model.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renderer GeckoLib genérico. NO necesitas un Renderer ni un Model propios por
 * entidad: pásale un {@link GenericGeoModel} y, opcionalmente, el radio de sombra.
 *
 * Ejemplos de registro (en Zenkai.java):
 *   // Kintoun: 3 nombres iguales, sin head-turn, sombra 1
 *   EntityRenderers.register(ModEntities.KINTOUN.get(),
 *       ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("kintoun"), 1f));
 *
 *   // KiBlast: sin sombra (usa el default del renderer)
 *   EntityRenderers.register(ModEntities.KI_BLAST.get(),
 *       ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("ki_blast")));
 *
 *   // Shenlong: head-turn, sombra 0.5
 *   EntityRenderers.register(ModEntities.SHENLONG.get(),
 *       ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("shenlong", true), 0.5f));
 */
public class GenericGeoRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {

    /** No toca shadowRadius: conserva el default del renderer (útil p. ej. KiBlast). */
    public GenericGeoRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model) {
        super(ctx, model);
    }

    public GenericGeoRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model, float shadowRadius) {
        super(ctx, model);
        this.shadowRadius = shadowRadius;
    }
}