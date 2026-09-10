package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.content.entity.misc.SpacePodEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Igual que un {@link GenericGeoRenderer} de toda la vida, pero con una capa emisiva propia
 * (pantallas de cabina + toberas, ver {@link SpacePodGlowLayer}) — una capa extra no cabe en el
 * constructor genérico, así que esta es la única entidad "genérica" que necesita su propio
 * renderer, mismo motivo por el que el banco de scouter (bloque) tiene el suyo
 * (ScouterBenchRenderer) en vez de reusar uno compartido.
 */
public class SpacePodRenderer extends GenericGeoRenderer<SpacePodEntity> {
    public SpacePodRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GenericGeoModel<>("space_pod", false, true), 1f);
        addRenderLayer(new SpacePodGlowLayer(this));
    }
}
