package com.hmc.zenkai.feature.sense;

/**
 * Catálogo de mejoras del scouter. HARDCODEADO a propósito: el datapack solo toca los
 * COSTES (ver ScouterUpgradeCost), nunca los efectos. Un datapack que pudiera regalar
 * alcance infinito no es contenido, es un exploit con formato JSON.
 *
 * El orden del enum ES el orden de la GUI del banco.
 *
 * Los arrays van indexados por NIVEL, con el índice 0 = scouter sin mejorar. Por eso tienen
 * maxLevel+1 entradas: el nivel 0 existe y tiene valores, no es "sin dato".
 */
public enum ScouterUpgrade {
    /** Alcance del raycast de la mira, en bloques. */
    RANGE("range", 5),
    /** PL máximo legible sin sobrecargarse. */
    PL_CAP("pl_cap", 5),
    /** Desbloquea el modo STATS. */
    ANALYZER("analyzer", 1),
    /** Desbloquea el modo MÁS FUERTE. */
    AREA_SCANNER("area_scanner", 1),
    /** Desbloquea el modo RADAR. Sustituye al componente radar_upgrade. */
    DRAGON_RADAR("dragon_radar", 1);

    /** Índice = nivel. El 0 es el scouter de fábrica: ve poco, pero ve. */
    private static final double[] RANGE_BY_LEVEL = { 16.0, 32.0, 48.0, 64.0, 96.0, 128.0 };

    /**
     * Índice = nivel. El tope del nivel 5 es un número normal y grande, no un centinela:
     * Long.MAX_VALUE desborda en cuanto alguien lo multiplica, y -1 obliga a que cada sitio
     * que lea el tope recuerde el caso especial. 1T no se alcanza y no rompe nada.
     */
    private static final long[] PL_CAP_BY_LEVEL = {
            1_500L, 9_000L, 50_000L, 350_000L, 3_000_000L, 1_000_000_000_000L
    };

    private final String id;
    private final int maxLevel;

    ScouterUpgrade(String id, int maxLevel) {
        this.id = id;
        this.maxLevel = maxLevel;
    }

    public String id()   { return id; }
    public int maxLevel(){ return maxLevel; }

    /** Clave de traducción del nombre y de la descripción (tooltip del +). */
    public String nameKey() { return "scouter.zenkai.upgrade." + id; }
    public String descKey() { return nameKey() + ".desc"; }

    /** ¿Es de las que solo se desbloquean? Puramente informativo para la GUI. */
    public boolean isBinary() { return maxLevel == 1; }

    /** id -> mejora, o null. Lo usa el cargador del datapack. */
    public static ScouterUpgrade byId(String id) {
        for (ScouterUpgrade u : values()) if (u.id.equals(id)) return u;
        return null;
    }

    // ── Efectos (única fuente de verdad) ─────────────────────────────────────

    public static double rangeFor(int level) {
        return RANGE_BY_LEVEL[clamp(level, RANGE.maxLevel)];
    }

    public static long plCapFor(int level) {
        return PL_CAP_BY_LEVEL[clamp(level, PL_CAP.maxLevel)];
    }

    private static int clamp(int level, int max) {
        return Math.max(0, Math.min(max, level));
    }
}