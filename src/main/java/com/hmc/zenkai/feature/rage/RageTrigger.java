package com.hmc.zenkai.feature.rage;

/**
 * Acontecimientos que reconoce el sistema (tabla de archivo/2026-11-08-rage-forms-diseno-v2.md §12.1). Es un enum y no datos: cada uno es un
 * DETECTOR de código (un evento del mod) y la tabla es corta y fija; el datapack solo decide qué despertar cuelga de cuál.
 * {@code consumesCooldown}: las filas que gastan el enfriamiento por aliado (rage.ally_event_cooldown).
 */
public enum RageTrigger {
    /** Cargar o enterrar el cuerpo de un aliado. */
    BODY_GESTURE(RageIntensity.LOW, false),
    /** Muerte de una mascota propia. */
    PET_DEATH(RageIntensity.LOW, false),
    /** Un aliado de la party cae derribado cerca. */
    ALLY_DOWNED(RageIntensity.MEDIUM, false),
    /** Muerte REAL de un aliado de la party presenciada. */
    ALLY_DEATH(RageIntensity.HIGH, true),
    /** Salvar a un aliado dentro de los 5 s de su derribo. */
    ALLY_RESCUED(RageIntensity.HIGH, true),
    /** Sobrevivir al borde con un aliado caído cerca. */
    CRITICAL_SURVIVAL(RageIntensity.EXCEPTIONAL, true),
    /** Un aliado muere mientras el beneficiario está derribado. */
    ALLY_DIES_WHILE_DOWNED(RageIntensity.EXCEPTIONAL, true),
    /** Un hecho que dispara un addon con {@code RageApi.trigger}; gasta el enfriamiento para que no encadene despertares. */
    ADDON(RageIntensity.MEDIUM, true);

    private final RageIntensity intensity;
    private final boolean consumesCooldown;

    RageTrigger(RageIntensity intensity, boolean consumesCooldown) {
        this.intensity = intensity;
        this.consumesCooldown = consumesCooldown;
    }

    public RageIntensity intensity() { return intensity; }
    public boolean consumesCooldown() { return consumesCooldown; }

    /**
     * Hechos MENORES que gastan un enfriamiento corto propio ({@code rage.minor_event_cooldown}) y no el de aliados: sin él, matar
     * mascotas propias o derribarse por turnos entre dos jugadores de una party era maestría ilimitada de baja intensidad.
     */
    public boolean consumesMinorCooldown() { return this == PET_DEATH || this == ALLY_DOWNED; }

    public static RageTrigger parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }
}
