package com.hmc.zenkai.feature.gravity;

/**
 * Dificultad de las rondas de drones (Fase 4 de la 0.7.0; tabla aprobada por el usuario el 2026-11-22). Se elige
 * en la vista Drones de la consola y se guarda en ella. ÚNICA fuente de estas cifras (constantes por la regla de
 * pocas opciones; se afinan jugando). NORMAL = la ronda que ya se probó antes de existir el selector.
 *
 * <ul>
 *   <li><b>Pasivo:</b> menos tiempo, drones más rápidos y que ESQUIVAN (el golpe no cuenta y el dron se aparta).</li>
 *   <li><b>Agresivo:</b> más daño por impacto, disparan más a menudo, aguantan más golpes, más rápidos y esquivan.</li>
 *   <li><b>TP:</b> {@link #tpMult} multiplica el TP base de la ronda (y el agresivo además paga su ×2).</li>
 * </ul>
 * El orden de las constantes es la clave guardada en la consola (ordinal): añadir siempre al final.
 */
public enum DroneDifficulty {
    //        tpMult  pasivo: base  +dron  vel   esq   agresivo: daño%  disparo  golpes  vel   esq   tope
    EASY(     0.6,    30 * 20, 7 * 20, 0.08, 0.00,            2.0,    70,      2,      0.12, 0.00, 180 * 20),
    NORMAL(   1.0,    20 * 20, 5 * 20, 0.11, 0.00,            4.0,    50,      3,      0.16, 0.00, 180 * 20),
    HARD(     1.6,    14 * 20, 70,     0.16, 0.20,            7.0,    35,      4,      0.20, 0.10, 180 * 20),
    EXTREME(  2.5,    10 * 20, 50,     0.22, 0.35,           10.0,    25,      5,      0.26, 0.20, 150 * 20);

    /** Ticks sin poder volver a esquivar tras una esquiva (1,5 s): que no sea un muro imposible. */
    public static final int DODGE_COOLDOWN_TICKS = 30;

    public final double tpMult;
    public final int passiveBaseTicks, passivePerDroneTicks;
    public final double passiveSpeed, passiveDodge;
    public final double shotBodyPercent;
    public final int shotIntervalTicks, hitsToBreak;
    public final double aggressiveSpeed, aggressiveDodge;
    public final int aggressiveMaxTicks;

    DroneDifficulty(double tpMult, int passiveBaseTicks, int passivePerDroneTicks, double passiveSpeed, double passiveDodge,
                    double shotBodyPercent, int shotIntervalTicks, int hitsToBreak, double aggressiveSpeed,
                    double aggressiveDodge, int aggressiveMaxTicks) {
        this.tpMult = tpMult;
        this.passiveBaseTicks = passiveBaseTicks;
        this.passivePerDroneTicks = passivePerDroneTicks;
        this.passiveSpeed = passiveSpeed;
        this.passiveDodge = passiveDodge;
        this.shotBodyPercent = shotBodyPercent;
        this.shotIntervalTicks = shotIntervalTicks;
        this.hitsToBreak = hitsToBreak;
        this.aggressiveSpeed = aggressiveSpeed;
        this.aggressiveDodge = aggressiveDodge;
        this.aggressiveMaxTicks = aggressiveMaxTicks;
    }

    public double speed(boolean aggressive) { return aggressive ? aggressiveSpeed : passiveSpeed; }
    public double dodgeChance(boolean aggressive) { return aggressive ? aggressiveDodge : passiveDodge; }

    /** Duración de una ronda con {@code drones} drones. */
    public int roundTicks(boolean aggressive, int drones) {
        return aggressive ? aggressiveMaxTicks : passiveBaseTicks + passivePerDroneTicks * drones;
    }

    public String langKey() { return "screen.zenkai.gravity_chamber.difficulty." + name().toLowerCase(java.util.Locale.ROOT); }

    public DroneDifficulty next() { return values()[(ordinal() + 1) % values().length]; }

    public static DroneDifficulty byId(int id) {
        DroneDifficulty[] v = values();
        return id >= 0 && id < v.length ? v[id] : NORMAL;
    }
}
