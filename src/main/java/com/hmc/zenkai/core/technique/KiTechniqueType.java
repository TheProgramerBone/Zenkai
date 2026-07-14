package com.hmc.zenkai.core.technique;

/**
 * Tipos de técnica ki. Cada tipo se DESBLOQUEA con TP una sola vez; el jugador crea
 * instancias con nombre (slots) eligiendo color, tamaño y si es explosiva.
 *
 * Parámetros:
 *  - speed: bloques/tick del proyectil.
 *  - damageMult / kiCostMult: multiplicadores sobre la fórmula base (escala con tamaño).
 *  - count: proyectiles por disparo (BURST = ráfaga).
 *  - defensive: BARRIER no dispara, crea la burbuja alrededor del jugador.
 *  - chargeTicks: CASTTIME — ticks para cargar al 100% (R + click derecho sostenido).
 *    Se puede soltar desde el 25%; daño y coste escalan lineal con la carga.
 *  - cooldownTicks: enfriamiento POR SLOT tras disparar.
 *
 * El ORDEN del enum = orden de los íconos en technique_icons.png (celda = ordinal).
 * defaultRgb: color inicial de la instancia recién creada (editable).
 */
public enum KiTechniqueType {
    //     tpCost speed dmgMult kiMult count defensive defaultRgb charge cooldown
    WAVE     (1500, 0.8f, 1.6f, 1.5f, 1, false, 0x55AAFF, 60, 60),   // Kamehameha: lento, fuerte
    BLAST    (200,  1.2f, 1.0f, 1.0f, 1, false, 0xFFE055, 20, 10),   // bola estándar
    LAZER    (400,  2.5f, 0.7f, 0.8f, 1, false, 0xFF55FF, 15, 15),   // finísimo y rapidísimo
    SPIRAL   (800,  1.0f, 1.2f, 1.1f, 1, false, 0xAA77FF, 30, 25),   // trayectoria en espiral
    BIG_BLAST(2500, 0.6f, 2.2f, 2.0f, 1, false, 0xFF9944, 80, 100),  // enorme y lento
    BARRIER  (2000, 0.0f, 0.0f, 1.8f, 0, true,  0x66FFDD, 10, 200),  // burbuja defensiva
    BURST    (600,  1.6f, 0.22f, 0.9f, 5, false, 0xFFF3A0, 25, 40),  // ráfaga de bolas pequeñas
    DISK     (1800, 1.8f, 1.8f, 1.6f, 1, false, 0xFFF7C0, 50, 60);   // disco destructor

    /** Fracción mínima de carga para poder disparar (estilo DBC). */
    public static final double MIN_CHARGE = 0.25;

    public final int tpCost;
    public final float speed;
    public final float damageMult;
    public final float kiCostMult;
    public final int count;
    public final boolean defensive;
    public final int defaultRgb;
    public final int chargeTicks;
    public final int cooldownTicks;

    KiTechniqueType(int tpCost, float speed, float damageMult, float kiCostMult,
                    int count, boolean defensive, int defaultRgb,
                    int chargeTicks, int cooldownTicks) {
        this.tpCost = tpCost;
        this.speed = speed;
        this.damageMult = damageMult;
        this.kiCostMult = kiCostMult;
        this.count = count;
        this.defensive = defensive;
        this.defaultRgb = defaultRgb;
        this.chargeTicks = chargeTicks;
        this.cooldownTicks = cooldownTicks;
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

    // ── Estela 3D (cinta por posiciones históricas; solo tipos "viajeros") ──

    /** ¿Este tipo dibuja estela detrás del proyectil? */
    public boolean hasTrail() {
        return this == LAZER || this == WAVE || this == SPIRAL;
    }

    /** Longitud de la estela en puntos (≈ ticks de historia). */
    public int trailPoints() {
        return switch (this) {
            case LAZER -> 34;   // haz largo y fino
            case WAVE  -> 18;   // haz corto y grueso
            case SPIRAL -> 26;  // la oscilación dibuja la espiral sola
            default -> 0;
        };
    }

    /** Ancho total de la estela como múltiplo del ancho del hitbox. */
    public float trailWidth() {
        return switch (this) {
            case LAZER -> 1.0f;
            case WAVE  -> 2.4f;
            case SPIRAL -> 1.4f;
            default -> 0f;
        };
    }
}