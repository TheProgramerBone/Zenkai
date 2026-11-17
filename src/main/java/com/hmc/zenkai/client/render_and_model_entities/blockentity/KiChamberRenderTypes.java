package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * RenderType del cristal de la cámara de contención: como entityTranslucent de vanilla (con
 * iluminación del mundo, sin recorte de caras) pero SIN ESCRIBIR PROFUNDIDAD.
 * Por qué hace falta uno propio: entityTranslucent escribe profundidad, y con Iris/Sodium el
 * orden entre esta pasada y la del brillo aditivo no está garantizado. Si el cristal cae antes,
 * su profundidad tapa lo que brilla DETRÁS de él (los raíles de ki del fondo), aunque el cristal
 * sea casi transparente. Sin escribir profundidad da igual quién vaya primero: lo de detrás
 * siempre pasa la prueba.
 * Mismo patrón que ModAuraRenderType: se hereda de RenderType solo para alcanzar los shards
 * protegidos; nunca se instancia.
 */
public final class KiChamberRenderTypes extends RenderType {
    private KiChamberRenderTypes() { super("", null, null, 0, false, false, null, null); }

    private static final Function<ResourceLocation, RenderType> GLASS =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_tech_glass",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    public static RenderType glass(ResourceLocation tex) { return GLASS.apply(tex); }
}
