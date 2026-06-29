package com.hmc.zenkai.core.network.feature.race;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * RenderTypes compartidos para el render de razas.
 *
 * viewOffset(tex): translúcido + sin cull + VIEW_OFFSET_Z_LAYERING (empuja hacia la cámara en
 * VIEW space). Sirve para overlays que deben quedar SIEMPRE justo por delante de la superficie
 * base (ojos/boca/nariz, detalles/líneas del cuerpo) sin escalar el modelo ni dejar huecos en la
 * textura base, y sin desaparecer al hacer sneak + mirar arriba.
 *
 * ⚠ API 1.21.1 — verificar nombres: RENDERTYPE_ENTITY_TRANSLUCENT_SHADER, VIEW_OFFSET_Z_LAYERING,
 *   TextureStateShard(tex, blur, mipmap), Util.memoize.
 */
public final class RaceRenderTypes {

    private RaceRenderTypes() {}

    private static final Function<ResourceLocation, RenderType> VIEW_OFFSET = Util.memoize(tex ->
            RenderType.create(
                    "zenkai_view_offset_overlay",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setLightmapState(RenderStateShard.LIGHTMAP)
                            .setOverlayState(RenderStateShard.OVERLAY)
                            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                            .createCompositeState(true)));

    public static RenderType viewOffset(ResourceLocation tex) {
        return VIEW_OFFSET.apply(tex);
    }
}