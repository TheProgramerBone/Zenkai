package com.hmc.zenkai.client.aura.compose;

import com.google.gson.JsonObject;
import com.hmc.zenkai.client.aura.compose.component.AuraComponentTypes;
import net.minecraft.util.Mth;

/**
 * Forma de la ESTELA DE VUELO de una familia ({@link FlightTrail}). La estela habla el mismo idioma
 * que el cuerpo del aura: tres bandas cel, dos tonos, y el borde que corresponde a la familia
 * (dentado como lenguas en las llamas, redondo en la divina, denso en la oscura, fino en Ultra
 * Instinct). Un estilo JSON puede ajustarla con un objeto {@code "trail"}.
 *
 * @param width  ancho de la cinta junto a los pies, en bloques (a poder base).
 * @param jag    cuánto se denta el borde (0 = liso; 0.5 = lenguas muy marcadas).
 * @param sway   ondulación lateral de la cinta (0 = recta).
 * @param alpha  opacidad máxima del halo exterior.
 * @param core   peso de la banda clara del centro (0..1).
 * @param deep   1 = el interior va OSCURECIDO saturado, como el cuerpo denso (familia oscura).
 * @param points largo de la estela en puntos (cada uno a ≥0.15 bloques del anterior).
 */
public record AuraTrailSpec(float width, float jag, float sway, float alpha, float core, boolean deep, int points) {

    public static final AuraTrailSpec FLAME = new AuraTrailSpec(0.8f, 0.35f, 0.0f, 0.55f, 0.85f, false, 18);

    public AuraTrailSpec withJson(JsonObject o) {
        return new AuraTrailSpec(
                AuraComponentTypes.f(o, "width", width),
                Mth.clamp(AuraComponentTypes.f(o, "jag", jag), 0f, 0.8f),
                Mth.clamp(AuraComponentTypes.f(o, "sway", sway), 0f, 1f),
                Mth.clamp(AuraComponentTypes.f(o, "alpha", alpha), 0f, 1f),
                Mth.clamp(AuraComponentTypes.f(o, "core", core), 0f, 1f),
                o.has("deep") ? o.get("deep").getAsBoolean() : deep,
                Mth.clamp((int) AuraComponentTypes.f(o, "points", points), 4, FlightTrail.CAP));
    }
}
