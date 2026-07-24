package com.hmc.zenkai.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * RenderType único del aura: TRANSLÚCIDO EMISIVO (SRC_ALPHA, 1-SRC_ALPHA, fullbright).
 * El sombreado del aura vive EN LA TEXTURA (bandas centro-blanco → borde-gris → puntas
 * oscuras) y se tinta por vértice: funciona igual para blanco, negro y cualquier tono,
 * sin pases aditivos ni crossfades por luminancia.
 * Depth-test LEQUAL (el mundo/jugador ocluyen) sin escribir profundidad (COLOR_WRITE)
 * para que los planos del cono se apilen entre sí en orden de dibujo.
 * Blur=true (filtrado bilineal en magnificación): sin él, escalar la llama a ~4 bloques
 * hace que cada téxel se lea como un escalón. La hoja lleva margen transparente por
 * cuadrante y el renderer inserta las UV media téxel para que el filtro no sangre.
 * Memoizado por textura.
 */
public final class ModAuraRenderType extends RenderType {
    // Nunca se instancia: solo heredamos para acceder a los shards protegidos de RenderStateShard.
    private ModAuraRenderType() { super("", null, null, 0, false, false, null, null); }

    private static final Function<ResourceLocation, RenderType> ENERGY =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_energy",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER) // ⚠
                            .setTextureState(new TextureStateShard(tex, true, true))      // ⚠ blur=true: magnificación bilineal
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)                // ⚠
                            .setCullState(NO_CULL)                                         // ⚠
                            .setLightmapState(LIGHTMAP)                                    // ⚠
                            .setOverlayState(OVERLAY)                                      // ⚠
                            .setDepthTestState(LEQUAL_DEPTH_TEST)                          // ⚠
                            .setWriteMaskState(COLOR_WRITE)                                // ⚠ no escribe profundidad
                            .createCompositeState(false)));

    /** Pasada única del aura (tintada por vértice; sombreado horneado en la textura). */
    public static RenderType energy(ResourceLocation tex) { return ENERGY.apply(tex); }
}