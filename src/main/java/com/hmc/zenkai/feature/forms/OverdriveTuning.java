package com.hmc.zenkai.feature.forms;

/**
 * Aritmética pura del sistema de "forzar" (powerPercent por encima de 100%). Sin imports de
 * Minecraft a propósito, igual que AuraFormula: así se puede razonar/testear sin arrancar el
 * juego. El adaptador con Player es OverdriveSystem.
 *
 * GENÉRICO, no exclusivo de ninguna raza: cualquiera con Ki Control suficiente puede forzar
 * hasta GENERIC_OVERDRIVE_MAX. Una forma con overdriveCeilingBonus > 0 (hoy solo second_form/
 * third_form/final_form del arcosiano) amplía ese techo MIENTRAS está puesta.
 *
 * Todas las constantes de esta clase son PLACEHOLDERS a tunear jugando — no hay balance real
 * detrás todavía, solo la forma de la curva (creciente, cara de sostener cuanto más te pases).
 */
public final class OverdriveTuning {
    private OverdriveTuning() {}

    /** Techo de forzar disponible para CUALQUIER raza, sin bonus de ninguna forma. */
    public static final double GENERIC_OVERDRIVE_MAX = 130.0;

    /** Coste base (ki/tick) por cada punto por encima de 100, antes del exponente. */
    private static final double COST_BASE_PER_POINT = 2.0;

    /** Exponente de la curva: > 1 la hace cada vez más cara cuanto más te pasas de 100. */
    private static final double COST_EXPONENT = 1.6;

    /** Ticks sosteniendo Shift+cargar ya al 100% antes de que el % empiece a subir de verdad
     *  ("romper el candado"): la primera vez del personaje, y las siguientes una vez ya
     *  conseguido el logro oculto. Ver KiChargeSystem/PlayerStateFlags.hasBrokenOverdriveOnce. */
    public static final int BREAKTHROUGH_TICKS_FIRST = 200;  // ~10 s
    public static final int BREAKTHROUGH_TICKS_REPEAT = 100; // ~5 s

    /** Crecimiento extra de escala mientras se fuerza por encima de 100% (aparte de la escala
     *  de la forma): tope y a cuántos puntos-sobre-100 se alcanza ese tope, interpolado lineal. */
    public static final double SCALE_BONUS_MAX = 0.05;
    public static final double SCALE_BONUS_REF_POINTS = 50.0;

    /** Subida YA por encima de 100%: más pequeña y más lenta que la subida normal 0-100
     *  (KiChargeSystem.STEP_AMOUNT/STEP_INTERVAL) a propósito — el jugador debe poder tantear
     *  con cuánto % extra aguanta cómodo, no pegar un salto grande de golpe. */
    public static final int OVERDRIVE_STEP_AMOUNT = 5;
    public static final int OVERDRIVE_STEP_INTERVAL_TICKS = 20; // 1 s (el normal es 0.5 s)

    /** Techo de forzar = genérico + bonus de la forma puesta (0 si no aplica). */
    public static double ceiling(double formCeilingBonus) {
        return GENERIC_OVERDRIVE_MAX + Math.max(0.0, formCeilingBonus);
    }

    /** Ki/tick drenado al forzar, dado cuánto se está por encima de 100 y el multiplicador de
     *  la forma puesta (1.0 si ninguna forma da descuento/recargo). pointsOver100 &lt;= 0 => 0. */
    public static double costPerTick(double pointsOver100, double drainMult) {
        if (pointsOver100 <= 0.0) return 0.0;
        double raw = COST_BASE_PER_POINT * Math.pow(pointsOver100, COST_EXPONENT) / 100.0;
        return raw * Math.max(0.0, drainMult);
    }

    /** Ticks de temblor necesarios para romper el 100% esta vez: menos si ya se rompió alguna
     *  vez antes (logro oculto ya conseguido). */
    public static int breakthroughTicksNeeded(boolean brokenBefore) {
        return brokenBefore ? BREAKTHROUGH_TICKS_REPEAT : BREAKTHROUGH_TICKS_FIRST;
    }

    /** Bonus de escala (aparte del de la forma) mientras se fuerza. 0 en pointsOver100 &lt;= 0,
     *  crece lineal hasta SCALE_BONUS_MAX en SCALE_BONUS_REF_POINTS. "Ligero", a propósito. */
    public static double overdriveScaleBonus(double pointsOver100) {
        if (pointsOver100 <= 0.0) return 0.0;
        return SCALE_BONUS_MAX * Math.min(1.0, pointsOver100 / SCALE_BONUS_REF_POINTS);
    }
}
