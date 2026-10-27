package com.hmc.zenkai.client.aura.silhouette;

import com.google.gson.JsonObject;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.compose.component.AuraComponentTypes;
import com.hmc.zenkai.client.aura.compose.component.TintSpec;
import com.hmc.zenkai.client.aura.flame.AuraFlameColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.feature.aura.AuraColors;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Contorno de llama sobre la SILUETA real del jugador (el "humo" del Legendario/Ikari, 2026-10-26,
 * referencia del usuario {@code aura vfx/smoke.png}): lenguas finas que lamen hacia arriba pegadas al
 * borde del cuerpo, del pelo y de lo que lleve encima. Bloque {@code "silhouette"} de un estilo de aura
 * ({@code assets/<ns>/zenkai_aura_styles/<aura_type>.json}); se ve SIEMPRE que el jugador esté en esa
 * forma, con o sin el aura encendida.
 * <p>
 * Colores: fijos ({@code "#RRGGBB"}, el Legendario: decisión del usuario) o los del AURA REAL del
 * jugador ({@code "inner"}, {@code "outer"}, {@code "hot"}), para estilos que comparten varias formas de
 * colores distintos (el de {@code god}: Humano blanco, Arcosiano morado, Majin rosa...). Desde el
 * 2026-10-26 es también el contorno de las formas que antes lo llevaban sobre las cajas de la pose
 * (familias divine y electric_flame, Beast forzando).
 *
 * @param inner     color de la banda pegada al cuerpo (la más clara).
 * @param outer     color de las lenguas de fuera.
 * @param thickness multiplicador del grosor del contorno (1 = el de la referencia).
 * @param reach     multiplicador del largo de las lenguas.
 * @param speed     multiplicador de la velocidad a la que suben.
 * @param alpha     opacidad general.
 * @param whileCharging {@code "while_charging": true} = MOMENTÁNEO (formas divinas, pedido del usuario
 *                  2026-10-26): aparece poco a poco al cargar ki, transformarse o en turbo
 *                  (AuraClientState.isAuraActive) y se desvanece poco a poco al soltar. false = siempre
 *                  que esté en la forma (Legendarios, SSJ2/SSJ3, Beast forzando).
 */
public record SilhouetteFlameSpec(TintSpec inner, TintSpec outer, float thickness, float reach, float speed, float alpha,
                                  boolean whileCharging) {

    private static final Set<String> KEYS = Set.of("inner", "outer", "thickness", "reach", "speed", "alpha", "while_charging");

    public static SilhouetteFlameSpec parse(String type, JsonObject o) {
        String unknown = AuraComponentTypes.unknownKeys(o, KEYS);
        if (unknown != null) Zenkai.LOGGER.warn("[Zenkai] Estilo de aura {}: claves de silhouette desconocidas: {}", type, unknown);
        return new SilhouetteFlameSpec(
                TintSpec.parse(AuraComponentTypes.s(o, "inner", "hot")),
                TintSpec.parse(AuraComponentTypes.s(o, "outer", "outer")),
                Mth.clamp(AuraComponentTypes.f(o, "thickness", 1f), 0.2f, 4f),
                Mth.clamp(AuraComponentTypes.f(o, "reach", 1f), 0f, 4f),
                Mth.clamp(AuraComponentTypes.f(o, "speed", 1f), 0f, 5f),
                Mth.clamp(AuraComponentTypes.f(o, "alpha", 0.95f), 0f, 1f),
                o.has("while_charging") && o.get("while_charging").getAsBoolean());
    }

    /** {inner, outer} ya resueltos para este jugador: mismas capas y saturación que el aura compuesta
     *  (AuraComposer), así el humo sale del color que tendría su aura. */
    public float[][] colors(Player p) {
        AuraColors.Layers layers = AuraColors.resolveLayers(p);
        float[] in = AuraFlameColors.saturated(layers.inner());
        float[] out = AuraFlameColors.saturated(layers.hasOuter() ? layers.outer() : layers.inner());
        float[] hot = KiVfxColors.derivedHot(out[0], out[1], out[2]);
        return new float[][]{inner.of(in, out, hot), outer.of(in, out, hot)};
    }
}
