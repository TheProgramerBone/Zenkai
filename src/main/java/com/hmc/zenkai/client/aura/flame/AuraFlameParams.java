package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.feature.aura.AuraProfile;
import com.hmc.zenkai.feature.aura.AuraTuning;
import net.minecraft.util.Mth;

/**
 * Uniforms de la llama exterior para UN jugador en UN frame — FASE 6 del plan (2026-09-29): la
 * personalidad de raza+forma ya existente ({@link AuraProfile}, resuelta por AuraManager desde
 * RaceSignature + zenkai_aura_signatures/) gobierna también el sistema nuevo, en vez de un valor
 * fijo igual para todos. Es el primer consumidor real de mass/spike/spread/height/density (el
 * legacy solo leía turbulence y el pulso).
 * <p>
 * CENTRADO EN LOS DEFAULTS DE {@link AuraFlameTuning}: un perfil con la FORMA en los centros de
 * AuraTuning (C_MASS, C_SPIKE...) da exactamente los números calibrados en juego en las fases
 * 1-4; cada campo se aleja de ellos en proporción a cuánto se aleja el perfil de su centro. Así
 * la calibración no se pierde, y /zenkaiauraflame set sigue moviendo el centro de cada rango.
 * El parámetro {@code profile_mix} (0..3) mezcla entre "sin personalidad" (0, lo de la Fase 4),
 * el mapeo completo (1) y una versión exagerada (hasta 3), para comparar en caliente.
 * <p>
 * LÍMITE DE DATOS (2026-09-29): las firmas de zenkai_aura_signatures/ tienen offsets pequeños y
 * hay parejas idénticas (divine = ssj2, dark = rose), y ninguna tiene spike negativo — así que
 * ninguna da lóbulos redondos todavía. Diferenciarlas más es trabajo de datos de
 * .claude/docs/auras.md, no de este mapeo.
 * <p>
 * Mapeo (ver también la tabla del plan):
 * <ul>
 *   <li>spike → nº de picos y forma de la onda: bajo = lóbulos redondos (villano, aura_5), alto =
 *       triángulo afilado (SSJ).</li>
 *   <li>turbulence → velocidad de las lenguas, temblor del contorno y rayas.</li>
 *   <li>spread → ancho de la base y apertura: alto = abanico en V (Beast), menos vertical.</li>
 *   <li>mass → opacidad (cuerpo de la llama).</li>
 *   <li>height → altura de la elipsoide y de los picos de arriba.</li>
 *   <li>density → pisos de lenguas en vertical y peso del bloom.</li>
 *   <li>PODER (size con su pulso, alpha, core) → la llama crece con el poder y late con Kaioken;
 *       core blanquea algo el centro caliente (nunca blanco sin la capa caliente debajo).</li>
 * </ul>
 * Función pura: sin estado, sin tocar el mundo — la llama la calcula una vez por jugador y frame
 * y la pasa a la tarea diferida.
 */
public record AuraFlameParams(
        float radius, float halfHeight, float centerY,
        float amp, float topBoost, float peaks, float sharp, float freqY,
        float speed, float detail, float streak, float upDir,
        float alphaCenter, float alphaEdge, float fresnelPower, float fresnelDivis,
        float emitBody, float emitEdge, float bloom, float bloomBody,
        /** Opacidad general (PODER alpha), multiplica el alfa de vértice. */
        float opacity,
        /** Cuánto se acerca el centro caliente al blanco (PODER core), 0..0.35. */
        float coreWhite,
        /** Estrechamiento arriba (lágrima) y punta extra sobre la cabeza, ver AuraFlameTuning. */
        float taper, float tip, float poleCalm,
        /** Semieje de la mitad de abajo (bloques) y banda de fundido de la base (Y unitaria). */
        float lowerHalf, float baseFadeLo, float baseFadeHi,
        /** Lenguas de los flancos, corona blanda y forma de las bandas (cuarto vídeo, ver AuraFlameTuning). */
        float sideUpDir, float crownKnee, float crownSoft, float peakWeight, float bottomRim,
        /** Cel-shading: interruptor y umbrales de las bandas borde / clara / cuerpo. */
        float cel, float celRim, float celLight, float celBody) {

    private static float t(AuraFlameTuning.Param p) { return AuraFlameTuning.get(p); }

    /**
     * Variante para la CAPA INTERIOR de las formas de dos tonos (Blue, Rose, Beast, Kaioken
     * encima de una forma...): la nube de color ceñida al cuerpo de aura_2/aura_8, dentro de la
     * llama de picos exterior. Más pequeña, picos cortos y redondeados, centro más opaco (se lee
     * como un cuerpo de color, no solo como contorno) y emisión baja: cubre como PIGMENTO en vez
     * de sumar luz — con la emisión de la exterior, el cian sobre un cielo claro se sumaba hacia
     * blanco y se leía pálido (usuario, 2026-09-29). Bloom igual que la exterior: en aura_8 los
     * DOS colores sangran, no solo el de fuera.
     */
    public AuraFlameParams innerLayer() {
        float s = t(AuraFlameTuning.Param.INNER_SCALE);
        return new AuraFlameParams(
                radius * s, halfHeight * s, centerY,
                amp * t(AuraFlameTuning.Param.INNER_AMP), topBoost * 0.5f, peaks, sharp * 0.4f, freqY,
                speed, detail, streak, upDir,
                Mth.clamp(alphaCenter * t(AuraFlameTuning.Param.INNER_ALPHA), 0f, 0.5f), alphaEdge * 0.8f,
                fresnelPower, fresnelDivis,
                emitBody * 0.3f, emitEdge * 0.5f, bloom, bloomBody,
                opacity, coreWhite,
                // Misma lágrima que la exterior, con la punta más corta: la nube interior no
                // debe asomar por encima de la llama de fuera.
                taper, tip * 0.5f, poleCalm,
                lowerHalf * s, baseFadeLo, baseFadeHi,
                sideUpDir, crownKnee, crownSoft, peakWeight, bottomRim,
                cel, celRim, celLight, celBody);
    }

    /** Sin personalidad: los defaults de AuraFlameTuning tal cual (comportamiento de la Fase 4). */
    public static AuraFlameParams neutral() {
        return from(null, 0f);
    }

    /**
     * PODER del perfil respecto a la base: 1 = personaje recién creado liberando al 100%, ~2-2.3 un
     * endgame a plena potencia en turbo. Ya lleva aplicado {@code profile_mix} y sus límites.
     * pulsedSize incluye el latido (±4% en reposo, mucho más con Kaioken). Público porque la
     * estrella del suelo ({@link AuraFlameGroundRenderer}) crece con el MISMO número que la llama.
     * @param profile perfil del jugador; null = poder base (1).
     */
    public static float powerFactor(AuraProfile profile, float seconds) {
        if (profile == null) return 1f;
        float mix = Mth.clamp(t(AuraFlameTuning.Param.PROFILE_MIX), 0f, 3f);
        float powerF = Math.max(profile.pulsedSize(seconds), AuraTuning.SIZE_MIN) / AuraTuning.SIZE_BASE;
        return Mth.lerp(mix, 1f, Mth.clamp(powerF, 0.8f, 3.5f));
    }

    /**
     * Escala de la llama entera para un poder dado ({@code size} × crecimiento por poder). El factor
     * del jugador ({@code aura.power_growth_percent}, mínimo 1) solo actúa por ENCIMA del poder base:
     * por debajo se mantiene el diseño de siempre, así un factor alto no encoge a un personaje débil.
     */
    public static float sizeScale(float powerF) {
        float growth = t(AuraFlameTuning.Param.POWER_GROWTH);
        if (powerF > 1f) growth *= com.hmc.zenkai.config.ClientConfig.auraPowerGrowthFactor();
        return t(AuraFlameTuning.Param.SIZE) * (1f + growth * (powerF - 1f));
    }

    /**
     * @param profile perfil del jugador; null = neutral.
     * @param seconds tiempo continuo en segundos, para el pulso (Kaioken).
     */
    public static AuraFlameParams from(AuraProfile profile, float seconds) {
        // Hasta 3 y no hasta 1: por encima de 1 EXAGERA la personalidad (extrapola), útil para
        // calibrar porque los offsets de las firmas actuales son pequeños (±0.2). Los clamps de
        // cada factor de abajo mantienen el resultado en rangos seguros aunque se extrapole.
        float mix = profile == null ? 0f : Mth.clamp(t(AuraFlameTuning.Param.PROFILE_MIX), 0f, 3f);

        // Desviaciones respecto al centro de AuraTuning; 0 = perfil neutro.
        float dSpike = 0f, dTurb = 0f, dSpread = 0f, massF = 1f, heightF = 1f, densF = 1f;
        float powerF = 1f, opacity = 1f, coreWhite = 0f;
        if (profile != null) {
            dSpike = profile.spike() - AuraTuning.C_SPIKE;
            dTurb = profile.turbulence() - AuraTuning.C_TURB;
            dSpread = profile.spread() - AuraTuning.C_SPREAD;
            massF = profile.mass() / AuraTuning.C_MASS;
            heightF = profile.height() / AuraTuning.C_HEIGHT;
            densF = profile.density() / AuraTuning.C_DENSITY;
            powerF = powerFactor(profile, seconds);
            opacity = profile.alpha() / (AuraTuning.ALPHA_BASE + AuraTuning.ALPHA_PER_ENERGY);
            // Tope 0.15 (antes 0.35): sumado a la capa caliente y al solape de lenguas, el centro
            // acababa en blanco puro (capturas 2026-09-29). El blanco es un acento, no el relleno.
            coreWhite = Mth.clamp((profile.core() - 0.25f) / 0.30f, 0f, 1f) * 0.15f;
        }

        // Lerp de cada factor hacia 1 (o de cada desviación hacia 0) según profile_mix.
        dSpike *= mix;
        dTurb *= mix;
        dSpread *= mix;
        massF = Mth.lerp(mix, 1f, Mth.clamp(massF, 0.5f, 1.4f));
        heightF = Mth.lerp(mix, 1f, Mth.clamp(heightF, 0.5f, 1.7f));
        densF = Mth.lerp(mix, 1f, Mth.clamp(densF, 0.5f, 2.2f));
        // powerF ya viene con profile_mix aplicado (powerFactor).
        opacity = Mth.lerp(mix, 1f, Mth.clamp(opacity, 0.6f, 1.2f));
        coreWhite *= mix;

        // Nº de picos ENTERO: con uno fraccionario la onda angular no cierra la vuelta y deja un
        // salto visible en la espalda (mismo motivo que los multiplicadores enteros del ruido).
        float peaks = Math.round(Mth.clamp(t(AuraFlameTuning.Param.PEAKS) * (1f + 0.8f * dSpike), 4f, 20f));
        // 0 = lóbulos (onda coseno), 1 = triángulo afilado. En el centro (spike 0.52) vale 1, la
        // onda triangular pura calibrada hasta la Fase 4; solo las firmas de spike bajo la
        // redondean.
        float sharp = Mth.clamp(1f + 1.5f * dSpike, 0f, 1f);

        float speed = t(AuraFlameTuning.Param.SPEED) * Mth.clamp(1f + 1.5f * dTurb, 0.5f, 2.5f);
        float detail = t(AuraFlameTuning.Param.DETAIL) * Mth.clamp(1f + 2.0f * dTurb, 0.4f, 3.0f);
        float streak = Mth.clamp(t(AuraFlameTuning.Param.STREAK) * (1f + 1.0f * dTurb), 0f, 1f);

        // TAMAÑO (2026-09-30): el poder escala la llama ENTERA desde los pies (radio, alto,
        // centro, lenguas y punta), no solo el largo de las lenguas — con la lágrima de lenguas
        // cortas, eso dejaba a un novato y a un endgame casi iguales (vídeo del usuario). Encima,
        // el poder sigue alargando algo las lenguas: la llama de un personaje fuerte también SUBE
        // más, no es solo la misma llama más grande.
        float scale = sizeScale(powerF);
        float radius = t(AuraFlameTuning.Param.RADIUS) * Mth.clamp(1f + 0.6f * dSpread, 0.8f, 1.6f) * scale;
        float upDir = Mth.clamp(t(AuraFlameTuning.Param.UP_DIR) - 0.6f * dSpread, 0.1f, 0.95f);
        // Mismo empuje del spread en los flancos: un abanico (Beast) también abre los costados.
        float sideUpDir = Mth.clamp(t(AuraFlameTuning.Param.SIDE_UP_DIR) - 0.6f * dSpread, 0.05f, 0.95f);

        float halfHeight = t(AuraFlameTuning.Param.HALF_HEIGHT) * Mth.lerp(0.5f, 1f, heightF) * scale;
        float centerY = t(AuraFlameTuning.Param.CENTER_Y) * scale;
        float amp = t(AuraFlameTuning.Param.AMP) * Mth.lerp(0.8f, 1f, heightF) * scale
                * (1f + 0.2f * (powerF - 1f));
        float topBoost = t(AuraFlameTuning.Param.TOP_BOOST) * heightF;

        float freqY = t(AuraFlameTuning.Param.FREQ_Y) * densF;
        float bloom = t(AuraFlameTuning.Param.BLOOM) * Mth.lerp(0.5f, 1f, densF);

        float alphaCenter = t(AuraFlameTuning.Param.ALPHA_CENTER) * massF;
        float alphaEdge = Mth.clamp(t(AuraFlameTuning.Param.ALPHA_EDGE) * Mth.lerp(0.5f, 1f, massF), 0f, 1f);

        // Lágrima: un spread alto (abanico en V, Beast) la abre, igual que ya bajaba up_dir; el
        // poder alarga la punta (sube, no engorda), con el mismo criterio que radius/amp.
        float taper = Mth.clamp(t(AuraFlameTuning.Param.TAPER) - 0.8f * dSpread, 0f, 1f);
        float tip = t(AuraFlameTuning.Param.TIP) * heightF * scale;

        return new AuraFlameParams(
                radius, halfHeight, centerY,
                amp, topBoost, peaks, sharp, freqY,
                speed, detail, streak, upDir,
                alphaCenter, alphaEdge,
                t(AuraFlameTuning.Param.FRESNEL_POWER), t(AuraFlameTuning.Param.FRESNEL_DIVIS),
                t(AuraFlameTuning.Param.EMIT_BODY), t(AuraFlameTuning.Param.EMIT_EDGE),
                bloom, t(AuraFlameTuning.Param.BLOOM_BODY),
                opacity, coreWhite,
                taper, tip, t(AuraFlameTuning.Param.POLE_CALM),
                t(AuraFlameTuning.Param.LOWER_HALF) * scale,
                t(AuraFlameTuning.Param.BASE_FADE_LO), t(AuraFlameTuning.Param.BASE_FADE_HI),
                sideUpDir, t(AuraFlameTuning.Param.CROWN_KNEE), t(AuraFlameTuning.Param.CROWN_SOFT),
                t(AuraFlameTuning.Param.PEAK_WEIGHT), t(AuraFlameTuning.Param.BOTTOM_RIM),
                t(AuraFlameTuning.Param.CEL), t(AuraFlameTuning.Param.CEL_RIM),
                t(AuraFlameTuning.Param.CEL_LIGHT), t(AuraFlameTuning.Param.CEL_BODY));
    }
}
