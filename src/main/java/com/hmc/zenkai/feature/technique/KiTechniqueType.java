package com.hmc.zenkai.feature.technique;

import java.util.Locale;

/**
 * Tipos de técnica ki. El enum es solo IDENTIDAD:
 *  - name() = clave NBT / packets.
 *  - ordinal() = celda del ícono en technique_icons.png (NO reordenar). EXPLOSION ocupa la
 *    celda 8, que antes era la base de la marca explosiva; el atlas no cambia de tamaño.
 *  - la estela 3D se queda aquí (es visual, no balance).
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
    DISK,       // disco destructor
    EXPLOSION;  // autodetonación centrada en el lanzador: no viaja, no apunta

    /** Fracción mínima de carga para poder disparar (estilo DBC). */
    public static final double MIN_CHARGE = 0.25;

    private final String id;

    KiTechniqueType() {
        this.id = name().toLowerCase(Locale.ROOT);
    }

    /** id del datapack (= name() en minúsculas). */
    public String id() { return id; }

    public String nameKey() { return "technique.zenkai." + id; }

    // ── Naturaleza del tipo ──────────────────────────────────────────────────

    /** ¿El proyectil VIAJA? La barrera sigue al dueño y la explosión estalla donde estás.
     *  Lo que no viaja no apunta, no converge con la mira y no se desvía. */
    public boolean travels() { return this != BARRIER && this != EXPLOSION; }

    /** Animación impuesta por el tipo, o null si el jugador elige el set. */
    public TechniqueAnimOverride animOverride() {
        return switch (this) {
            case BARRIER   -> TechniqueAnimOverride.BARRIER;
            case EXPLOSION -> TechniqueAnimOverride.EXPLOSION;
            default        -> null;
        };
    }

    /** ¿Admite este efecto? Un disco que explota deja de ser un disco, una ráfaga multiplicaría
     *  la destrucción por el número de bolas, y una barrera no impacta contra nada.
     *  El LÁSER SÍ lo admite. */
    public boolean allowsEffect(TechniqueEffect effect) {
        if (effect == TechniqueEffect.NONE) return true;
        return switch (this) {
            case DISK, BARRIER, BURST -> false;
            default -> true;
        };
    }

    // ── Escala física ────────────────────────────────────────────────────────

    /** Diámetro del proyectil en bloques. Un tamaño 5 NO significa lo mismo en un láser que en
     *  un big blast: cada tipo tiene su base y su paso, y por eso esto no puede ser una
     *  fórmula única aplicada fuera. */
    public double projectileSize(int size) {
        int s = Math.max(1, size) - 1;
        return switch (this) {
            case LAZER     -> 0.20 + 0.05 * s;
            case BURST     -> 0.30 + 0.08 * s;
            case SPIRAL    -> 0.40 + 0.15 * s;
            case BLAST     -> 0.45 + 0.18 * s;
            case DISK      -> 0.60 + 0.20 * s;
            case WAVE      -> 0.70 + 0.30 * s;
            case BIG_BLAST -> 1.20 + 0.55 * s;
            case EXPLOSION -> 2.00 + 1.00 * s;
            case BARRIER   -> 2.60 + 0.20 * s;   // como estaba
        };
    }

    /** Radio de la explosión en bloques. SIN TECHO a propósito: los techos por tipo aplastaban
     *  la diferencia entre tamaños justo donde tenía que notarse, y las peleas grandes deben
     *  poder ser desproporcionadas. */
    public double explosionRadius(int size) {
        return explosionRadiusMult() * Math.max(1, size);
    }

    private double explosionRadiusMult() {
        return switch (this) {
            case LAZER     -> 0.60;
            case BLAST     -> 1.20;
            case SPIRAL    -> 1.35;
            case WAVE      -> 1.60;
            case BIG_BLAST -> 2.40;
            case EXPLOSION -> 3.60;
            case DISK      -> 0.70;
            case BURST     -> 0.60;
            case BARRIER   -> 0.00;
        };
    }

    /** Fracción del daño directo que se reparte en el área, EN EL CENTRO. Ya no depende de la
     *  marca de efecto: toda técnica que impacta reparte área. */
    public double aoeFactor() {
        return switch (this) {
            case EXPLOSION -> 1.00;   // la explosión ES la técnica: no hay golpe directo que descontar
            case BIG_BLAST -> 0.85;
            case WAVE      -> 0.75;
            case SPIRAL    -> 0.60;
            case BLAST     -> 0.50;
            case DISK      -> 0.40;
            case BURST     -> 0.35;
            case LAZER     -> 0.20;
            case BARRIER   -> 0.00;
        };
    }

    /** Fracción del daño que queda EN EL BORDE del radio. Los proyectiles caen a cero; la
     *  explosión mantiene el 65 %, que es lo que la convierte en una zona de muerte y no en un
     *  golpe con halo. */
    public double aoeEdgeFalloff() {
        return this == EXPLOSION ? 0.65 : 0.0;
    }

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
        return travels() && (this == LAZER || this == WAVE || this == SPIRAL);
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
    /** Duración del estado de disparo (RELEASING) para los observadores. */
    public int animTicks() { TechniqueDef d = def(); return d == null ? 20 : d.animTicks(); }
}