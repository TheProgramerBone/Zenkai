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
 *
 * pulseHzGain/pulseAmpGain (antes un único pulseGain) se separaron a propósito: la
 * frecuencia y la amplitud de la respiración necesitaban poder moverse en direcciones
 * OPUESTAS. Una firma "divina" que debe sentirse lenta e imponente (respiración grande
 * pero pausada, no un temblor rápido) es literalmente imposible de expresar con un solo
 * número — bajarlo encoge el swing a la vez que lo ralentiza. Con dos números, "divine"
 * puede pedir pulseHzGain bajo (lento) y pulseAmpGain alto (swing grande) a la vez.
 *
 * SIN IMPORTS DE MINECRAFT A PROPÓSITO: junto con AuraFormula, es lo que permite que
 * AuraSelfTest se ejecute sin arrancar el juego (ver su javadoc). El codec de red para
 * sincronizar este tipo (AuraSignatureSyncPacket) vive fuera de esta clase precisamente
 * para no forzar la carga de clases de Minecraft en el <clinit> de este record.
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
        float pulseHzGain,
        float pulseAmpGain,
        // Booleano, no offset/gain: no desplaza ni multiplica ningún eje numérico, solo
        // activa una PASADA de render extra (ver AuraSkirtRenderer). Vive aquí porque sigue
        // siendo FORMA (una técnica de dibujado propia de la identidad del aura_type), nunca
        // PODER — no cambia size/alpha/core. PRUEBA DE VIABILIDAD (2026-09-02): hoy solo
        // ascension.json lo pide, para ver si una pasada aditiva sobre el cono existente da
        // el brillo "de fuego" que la mezcla translúcida normal no puede dar sin opacar al
        // jugador. Si no compensa el coste (extra draw call por jugador con esta firma), se
        // puede revertir sin afectar a ninguna otra firma: por defecto es false en todas.
        boolean additiveGlow,
        // Igual que additiveGlow: booleano de FORMA (técnica de dibujado), nunca PODER.
        // Cambia la GEOMETRÍA de las chispas del aura (ver AuraSparkRenderer): en vez del
        // destello recto de siempre, un rayo quebrado de 3 segmentos, aditivo, tintado con
        // el color interior. Hoy solo lo pide rose.json (SSJ Rose).
        boolean electricSparks,
        // Igual que los dos anteriores: booleano de FORMA, nunca PODER. Activa un tercer
        // emisor de partículas (ver AuraEmberRenderer) con SU PROPIA textura
        // (aura_ember.png, ni la hoja de faldones ni un ParticleType vanilla) — ascuas que
        // suben alrededor del cuerpo. Reemplaza dos intentos previos que no funcionaron:
        // un pase aditivo sobre el cono (lavaba al jugador a blanco) y partículas vanilla
        // ParticleTypes.FLAME (se veían fuera de lugar, estilo pixel-art vainilla contra
        // el aura translúcida del mod). Hoy solo lo pide ascension.json (SSJG).
        boolean fireEmbers
) {
    /** Neutro: offsets a 0, ganancias a 1, sin pasadas extra de ningún tipo. */
    public static final AuraModifier NONE =
            new AuraModifier(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, false, false, false);

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
        pulseHzGain = AuraTuning.clamp(pulseHzGain, AuraTuning.MIN_GAIN, AuraTuning.MAX_GAIN);
        pulseAmpGain = AuraTuning.clamp(pulseAmpGain, AuraTuning.MIN_GAIN, AuraTuning.MAX_GAIN);
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
                pulseHzGain * other.pulseHzGain,
                pulseAmpGain * other.pulseAmpGain,
                // OR, no suma: basta con que UNA de las dos (forma o raza) lo pida.
                additiveGlow || other.additiveGlow,
                electricSparks || other.electricSparks,
                fireEmbers || other.fireEmbers);
    }

    public boolean isNone() { return NONE.equals(this); }
}