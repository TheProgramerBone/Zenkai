package com.hmc.zenkai.feature.race;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * RenderTypes compartidos para el render de razas.
 * viewOffset(tex): delega en el RenderType VANILLA armorCutoutNoCull, que ya incluye
 * exactamente lo que necesitamos: cutout (pasada opaca → no lo tapa geometría translúcida
 * como el cristal del space pod), NO_CULL, y VIEW_OFFSET_Z_LAYERING (empuja hacia la cámara
 * en VIEW space → overlays siempre por delante de la superficie base, sin z-fighting y sin
 * desaparecer en sneak).
 * ANTES era un RenderType. Créate(...) custom ("zenkai_view_offset_overlay") con los mismos
 * shards. Problema: los RenderTypes custom no son reconocidos por pipelines de shaders
 * (Iris/Oculus) → en el mundo los pases de ojos/detalles/tints no se dibujaban en clientes
 * con shaders, aunque en las GUI sí (las GUI no pasan por el pipeline de shaders).
 * Al usar el tipo vanilla, cualquier shader pack lo trata igual que la armadura normal.
 * Orden de dibujo: GeckoLib renderiza base primero y layers después; con depth test EQUAL,
 * a igual profundidad gana el último pase → los overlays se ven sobre la base, como antes.
 */
public final class RaceRenderTypes {

    private RaceRenderTypes() {}

    public static RenderType viewOffset(ResourceLocation tex) {
        return RenderType.armorCutoutNoCull(tex);
    }

    /**
     * Pasada TRANSLÚCIDA para máscaras que deben dejar ver lo que hay detrás (el cristal del
     * scouter). Vanilla y no custom, por lo mismo que viewOffset: un RenderType propio deja de
     * dibujarse en clientes con Iris/Oculus.
     * No lleva VIEW_OFFSET_Z_LAYERING a propósito — no existe una variante vanilla que
     * combine translúcido y layering. Por eso la textura BASE debe tener esa zona
     * transparente: sin píxeles opacos coplanares debajo, no hay z-fighting que evitar, y
     * además es la única forma de que el cristal mezcle contra la cara y no contra el armazón.
     * ⚠ VERIFICAR 1.21.1: entityTranslucent(tex) es la variante NO_CULL. Si en tu mapeo
     * culea, usa entityTranslucentCull(tex) — para una lente de un solo plano da igual, pero
     * si el cristal tiene grosor, NO_CULL mezcla las dos caras y oscurece el doble.
     */
    public static RenderType translucent(ResourceLocation tex) {
        return RenderType.entityTranslucent(tex);
    }
}