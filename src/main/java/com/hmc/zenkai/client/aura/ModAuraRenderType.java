package com.hmc.zenkai.client.aura;

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
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(tex, true, true))      // ⚠ blur=true: magnificación bilineal
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)                                // ⚠ no escribe profundidad
                            .createCompositeState(false)));

    /** Igual que ENERGY pero SIN filtrado bilineal: para texturas pixel art (bola de ki,
     *  proyectiles) donde el escalonado ES el estilo. El aura sigue usando blur=true. */
    private static final Function<ResourceLocation, RenderType> ENERGY_CRISP =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_energy_crisp",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(tex, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    public static RenderType energyCrisp(ResourceLocation tex) { return ENERGY_CRISP.apply(tex); }

    /** Pasada única del aura (tintada por vértice; sombreado horneado en la textura). */
    public static RenderType energy(ResourceLocation tex) { return ENERGY.apply(tex); }

    /**
     * Igual que {@link #energy} pero ADITIVO (SRC_ALPHA, ONE) en vez de TRANSLUCENT — mismo
     * patrón que {@code KiVfxRenderTypes.ADDITIVE} (halo/núcleo de estela de las técnicas ki):
     * la luz que se solapa suma hacia blanco en vez de mezclarse con lo de detrás.
     * PRUEBA DE VIABILIDAD (2026-09-02, ver AuraModifier.additiveGlow): pasada extra opcional
     * para las firmas de aura que la pidan, encima del cono translúcido normal — el cuerpo del
     * aura sigue siendo translúcido siempre, esto es solo un brillo añadido, nunca un
     * reemplazo. Misma hoja de textura que energy() (memoizado por textura, RenderType
     * distinto), así que no hace falta un atlas aparte.
     */
    private static final Function<ResourceLocation, RenderType> ENERGY_ADDITIVE =
            Util.memoize(tex -> RenderType.create(
                    "zenkai_aura_energy_additive",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(tex, true, true))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)));

    public static RenderType energyAdditive(ResourceLocation tex) { return ENERGY_ADDITIVE.apply(tex); }
}
