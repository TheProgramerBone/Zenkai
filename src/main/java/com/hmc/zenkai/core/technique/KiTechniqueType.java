package com.hmc.zenkai.core.technique;

import java.util.Locale;

/**
 * Tipos de técnica ki. El enum es solo IDENTIDAD:
 *  - name() = clave NBT / packets.
 *  - ordinal() = celda del ícono en technique_icons.png (NO reordenar).
 *  - la estela 3D se queda aquí (es visual, no balance).
 *
 * Todos los NÚMEROS viven en datapack: data/&lt;ns&gt;/zenkai_techniques/ki/&lt;id&gt;.json
 * (ver TechniqueDef / TechniqueManager). Sin JSON, enabled() es false y la técnica
 * no se puede desbloquear, guardar ni disparar.
 */
public enum KiTechniqueType {
    WAVE,       // Kamehameha: lento, fuerte
    BLAST,      // bola estándar
    LAZER,      // finísimo y rapidísimo
    SPIRAL,     // trayectoria en espiral
    BIG_BLAST,  // enorme y lento
    BARRIER,    // burbuja defensiva
    BURST,      // ráfaga de bolas pequeñas
    DISK;       // disco destructor

    /** Fracción mínima de carga para poder disparar (estilo DBC). */
    public static final double MIN_CHARGE = 0.25;

    private final String id;

    KiTechniqueType() {
        this.id = name().toLowerCase(Locale.ROOT);
    }

    /** id del datapack (= name() en minúsculas). */
    public String id() { return id; }

    public String nameKey() { return "technique.zenkai." + id; }

    /** Parse seguro desde NBT/packets. null si no existe. */
    public static KiTechniqueType byName(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Números (datapack) ──────────────────────────────────────────────────

    public TechniqueDef def() { return TechniqueDef.get(TechniqueDef.Kind.KI, id); }

    /** false = sin JSON: técnica desactivada en todas partes. */
    public boolean enabled() { return def() != null; }

    /** TP de desbloqueo. MAX_VALUE si está desactivada (nunca asequible). */
    public int tpCost() { TechniqueDef d = def(); return d == null ? Integer.MAX_VALUE : d.tpCost(); }

    /** MND mínimo para desbloquear. MAX_VALUE si está desactivada. */
    public int mindReq() { TechniqueDef d = def(); return d == null ? Integer.MAX_VALUE : d.mindReq(); }

    /** Bloques/tick del proyectil. */
    public float speed() { TechniqueDef d = def(); return d == null ? 0f : (float) d.speed(); }

    /** Multiplicador de daño sobre la fórmula base (escala con tamaño). */
    public float damageMult() { TechniqueDef d = def(); return d == null ? 0f : (float) d.damageMult(); }

    /** Multiplicador de coste de ki. */
    public float kiCostMult() { TechniqueDef d = def(); return d == null ? 0f : (float) d.kiCostMult(); }

    /** Proyectiles por disparo (BURST = ráfaga). */
    public int count() { TechniqueDef d = def(); return d == null ? 1 : d.count(); }

    /** BARRIER no dispara: crea la burbuja alrededor del jugador. */
    public boolean defensive() { TechniqueDef d = def(); return d != null && d.defensive(); }

    /** Color inicial de una instancia recién creada (editable). */
    public int defaultRgb() { TechniqueDef d = def(); return d == null ? 0xFFFFFF : d.defaultRgb(); }

    /** CASTTIME: ticks para cargar al 100%. Se puede soltar desde MIN_CHARGE. */
    public int chargeTicks() { TechniqueDef d = def(); return d == null ? 20 : d.chargeTicks(); }

    /** Enfriamiento POR SLOT tras disparar. */
    public int cooldownTicks() { TechniqueDef d = def(); return d == null ? 20 : d.cooldownTicks(); }

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