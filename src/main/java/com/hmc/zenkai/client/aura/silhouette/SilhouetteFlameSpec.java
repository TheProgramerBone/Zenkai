package com.hmc.zenkai.client.aura.silhouette;

import com.google.gson.JsonObject;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.compose.component.AuraComponentTypes;
import com.hmc.zenkai.client.aura.compose.component.TintSpec;
import net.minecraft.util.Mth;

import java.util.Set;

/**
 * Contorno de llama sobre la SILUETA real del jugador (el "humo" del Legendario/Ikari, 2026-10-26,
 * referencia del usuario {@code aura vfx/smoke.png}): lenguas finas que lamen hacia arriba pegadas al
 * borde del cuerpo, del pelo y de lo que lleve encima. Bloque {@code "silhouette"} de un estilo de aura
 * ({@code assets/<ns>/zenkai_aura_styles/<aura_type>.json}); se ve SIEMPRE que el jugador esté en esa
 * forma, con o sin el aura encendida. Colores fijos por forma (decisión del usuario).
 *
 * @param inner     color de la banda pegada al cuerpo (la más clara).
 * @param outer     color de las lenguas de fuera.
 * @param thickness multiplicador del grosor del contorno (1 = el de la referencia).
 * @param reach     multiplicador del largo de las lenguas.
 * @param speed     multiplicador de la velocidad a la que suben.
 * @param alpha     opacidad general.
 */
public record SilhouetteFlameSpec(float[] inner, float[] outer, float thickness, float reach, float speed, float alpha) {

    private static final Set<String> KEYS = Set.of("inner", "outer", "thickness", "reach", "speed", "alpha");

    public static SilhouetteFlameSpec parse(String type, JsonObject o) {
        String unknown = AuraComponentTypes.unknownKeys(o, KEYS);
        if (unknown != null) Zenkai.LOGGER.warn("[Zenkai] Estilo de aura {}: claves de silhouette desconocidas: {}", type, unknown);
        return new SilhouetteFlameSpec(
                TintSpec.parse(AuraComponentTypes.s(o, "inner", "#FFF35A")).fixed(),
                TintSpec.parse(AuraComponentTypes.s(o, "outer", "#5CFF3A")).fixed(),
                Mth.clamp(AuraComponentTypes.f(o, "thickness", 1f), 0.2f, 4f),
                Mth.clamp(AuraComponentTypes.f(o, "reach", 1f), 0f, 4f),
                Mth.clamp(AuraComponentTypes.f(o, "speed", 1f), 0f, 5f),
                Mth.clamp(AuraComponentTypes.f(o, "alpha", 0.95f), 0f, 1f));
    }
}
