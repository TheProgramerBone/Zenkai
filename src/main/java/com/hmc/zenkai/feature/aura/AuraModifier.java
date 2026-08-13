package com.hmc.zenkai.feature.aura;

/**
 * Lo que una FORMA o una RAZA aportan al aura. Mismo tipo para las dos a propósito:
 * se suman, y la jerarquía "la forma manda, la raza deja firma" sale de la MAGNITUD,
 * no de la precedencia (forma ±0.25 por convención, raza ±0.10). Así FormDef y
 * RaceSignature cargan la misma estructura desde datapack y no hay dos rutas de
 * composición que mantener sincronizadas.
 *
 * ═══ LA GARANTÍA ESTÁ EN LO QUE ESTE RECORD NO TIENE ═══
 * No hay dSize, dAlpha, dCore, dPresence, dWisps, dSparks ni dGround. No es un olvido:
 * es EL mecanismo por el que se cumple "la raza modifica la personalidad, nunca el
 * poder". Un datapack puede escribir dMass 0.20 y turbGain 1.5 y hacer que el aura
 * saiyan sea mucho más agresiva, pero no existe ningún campo con el que decir "este
 * saiyan tiene más poder". La potencia percibida sale solo de presence -> size/alpha/
 * core, y presence sale solo del PL.
 * Si alguna vez alguien añade un campo de poder aquí, esa regla se rompe en silencio.
 *
 * DOS FAMILIAS DE CAMPOS, y no hacen lo mismo:
 *   d*     desplazan la forma BASE           -> identidad estructural, siempre visible
 *   *Gain  multiplican la respuesta DINÁMICA -> solo se nota al perder el control
 * Por eso un Majin no necesita parecer más poderoso en reposo para desintegrarse de
 * forma característica cuando se le va el ki de las manos.
 */
public record AuraModifier(
        float dMass,
        float dSpike,
        float dTurb,
        float dSpread,
        float dHeight,
        float dDensity,
        float turbGain,
        float spreadGain,
        float pulseGain
) {
    /** Neutro: offsets a 0, ganancias a 1. */
    public static final AuraModifier NONE =
            new AuraModifier(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);

    /** Constructor compacto: un datapack roto no puede saturar el sistema. */
    public AuraModifier {
        float o = AuraTuning.MAX_OFFSET;
        dMass = AuraTuning.clamp(dMass, -o, o);
        dSpike = AuraTuning.clamp(dSpike, -o, o);
        dTurb = AuraTuning.clamp(dTurb, -o, o);
        dSpread = AuraTuning.clamp(dSpread, -o, o);
        dHeight = AuraTuning.clamp(dHeight, -o, o);
        dDensity = AuraTuning.clamp(dDensity, -o, o);
        turbGain = AuraTuning.clamp(turbGain, AuraTuning.MIN_GAIN, AuraTuning.MAX_GAIN);
        spreadGain = AuraTuning.clamp(spreadGain, AuraTuning.MIN_GAIN, AuraTuning.MAX_GAIN);
        pulseGain = AuraTuning.clamp(pulseGain, AuraTuning.MIN_GAIN, AuraTuning.MAX_GAIN);
    }

    /**
     * Composición forma + raza. Los offsets se SUMAN (dos empujones en el mismo eje se
     * refuerzan) y las ganancias se MULTIPLICAN (1.0 es el neutro, así que sumarlas
     * daría 2.0 al componer dos neutros). El clamp final lo hace AuraProfile: aquí no
     * se recorta todavía, porque dos offsets legítimos pueden pasarse de rango juntos
     * y el sitio correcto de resolver eso es el resultado, no el intermedio.
     */
    public AuraModifier plus(AuraModifier other) {
        if (other == null) return this;
        return new AuraModifier(
                dMass + other.dMass,
                dSpike + other.dSpike,
                dTurb + other.dTurb,
                dSpread + other.dSpread,
                dHeight + other.dHeight,
                dDensity + other.dDensity,
                turbGain * other.turbGain,
                spreadGain * other.spreadGain,
                pulseGain * other.pulseGain);
    }

    public boolean isNone() { return NONE.equals(this); }
}