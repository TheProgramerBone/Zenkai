package com.hmc.zenkai.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * RenderTypes para el aura de energía:
 *   energy(tex)     → ADITIVO (SRC_ALPHA, ONE): los planos SUMAN luz → núcleo blanco.
 *                     Para colores claros (ki que brilla).
 *   energyDark(tex) → ALPHA (SRC_ALPHA, 1-SRC_ALPHA): los planos OSCURECEN/ocluyen el fondo.
 *                     Para colores oscuros/negros (ki oscuro que NO puede "brillar" en aditivo).
 * Ambos: depth-test LEQUAL (el jugador ocluye lo de atrás) sin escribir profundidad
 * (COLOR_WRITE) para que los planos se acumulen entre sí. Memoizados por textura.
 */
public final class ModAuraRenderType extends RenderType {
    // Nunca se instancia: solo heredamos para acceder a los shards protegidos de RenderStateShard.
    private ModAuraRenderType() { super("", null, null, 0, false, false, null, null); }

    private static RenderType build(ResourceLocation tex, TransparencyStateShard transparency, String name) {
        return RenderType.create(
                name,
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER) // ⚠
                        .setTextureState(new TextureStateShard(tex, false, false))     // ⚠
                        .setTransparencyState(transparency)
                        .setCullState(NO_CULL)                                         // ⚠
                        .setLightmapState(LIGHTMAP)                                    // ⚠
                        .setOverlayState(OVERLAY)                                      // ⚠
                        .setDepthTestState(LEQUAL_DEPTH_TEST)                          // ⚠
                        .setWriteMaskState(COLOR_WRITE)                                // ⚠ no escribe profundidad
                        .createCompositeState(false));
    }

    private static final Function<ResourceLocation, RenderType> ENERGY =
            Util.memoize(tex -> build(tex, ADDITIVE_TRANSPARENCY, "zenkai_energy"));            // ⚠ ADDITIVE_TRANSPARENCY
    private static final Function<ResourceLocation, RenderType> ENERGY_DARK =
            Util.memoize(tex -> build(tex, TRANSLUCENT_TRANSPARENCY, "zenkai_energy_dark"));    // ⚠ TRANSLUCENT_TRANSPARENCY

    /** Aditivo (colores claros → brilla). */
    public static RenderType energy(ResourceLocation tex) { return ENERGY.apply(tex); }

    /** Alpha-blended (colores oscuros → oscurece/ocluye). */
    public static RenderType energyDark(ResourceLocation tex) { return ENERGY_DARK.apply(tex); }
}