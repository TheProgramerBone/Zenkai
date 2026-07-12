package com.hmc.zenkai.core.technique;

/**
 * Los 7 tipos de técnica ki (v1.0). Cada tipo se DESBLOQUEA con TP una sola vez; luego el
 * jugador crea instancias con nombre (slots) eligiendo color y tamaño.
 *
 * Los parámetros base alimentan la Fase B (proyectil/disparo):
 *  - speed: bloques/tick del proyectil.
 *  - damageMult / kiCostMult: multiplicadores sobre la fórmula base (que escala con tamaño).
 *  - count: proyectiles por disparo (BURST = ráfaga).
 *  - defensive: BARRIER no dispara, crea la burbuja alrededor del jugador.
 *
 * defaultRgb: color inicial de la instancia recién creada (editable).
 * Claves de lang: technique.zenkai.<name minúsculas>.
 */
public enum KiTechniqueType {
    //     tpCost speed dmgMult kiMult count defensive defaultRgb
    WAVE     (1500, 0.8f, 1.6f, 1.5f, 1, false, 0x55AAFF), // Kamehameha: lento, fuerte
    BLAST    (200,  1.2f, 1.0f, 1.0f, 1, false, 0xFFE055), // bola estándar
    LAZER    (400,  2.5f, 0.7f, 0.8f, 1, false, 0xFF55FF), // finísimo y rapidísimo
    SPIRAL   (800,  1.0f, 1.2f, 1.1f, 1, false, 0xAA77FF), // trayectoria en espiral
    BIG_BLAST(2500, 0.6f, 2.2f, 2.0f, 1, false, 0xFF9944), // enorme y lento
    BARRIER  (2000, 0.0f, 0.0f, 1.8f, 0, true,  0x66FFDD), // burbuja defensiva
    BURST    (600,  1.6f, 0.22f, 0.9f, 5, false, 0xFFF3A0); // ráfaga de bolas pequeñas

    public final int tpCost;
    public final float speed;
    public final float damageMult;
    public final float kiCostMult;
    public final int count;
    public final boolean defensive;
    public final int defaultRgb;

    KiTechniqueType(int tpCost, float speed, float damageMult, float kiCostMult,
                    int count, boolean defensive, int defaultRgb) {
        this.tpCost = tpCost;
        this.speed = speed;
        this.damageMult = damageMult;
        this.kiCostMult = kiCostMult;
        this.count = count;
        this.defensive = defensive;
        this.defaultRgb = defaultRgb;
    }

    public String nameKey() { return "technique.zenkai." + name().toLowerCase(); }

    /** Parse seguro desde NBT/packets. null si no existe. */
    public static KiTechniqueType byName(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}