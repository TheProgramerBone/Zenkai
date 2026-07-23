package com.hmc.zenkai.core.network.feature.forms;

/**
 * Escalones del Kaioken. El enum es IDENTIDAD (ordinal = valor guardado en NBT y enviado por
 * red; NO reordenar). Los números son el punto de partida: se tocan aquí porque son solo
 * cinco y no justifican un datapack propio, a diferencia de las técnicas.
 *
 * MODELO ADITIVO: el porcentaje se SUMA al de la transformación activa, y la suma multiplica
 * la base -> total = 1 + %forma + %kaioken (ver MasteryEffects.formStatFactor).
 * Ojo al balancear: con suma, la capa de números más grandes ensombrece a la otra. Si las
 * transformaciones acaban en +900%, estos porcentajes hay que subirlos en la misma escala o
 * el Kaioken deja de notarse encima de una forma.
 *
 * El drenaje es de VIDA (pool body), no de ki: esa es la firma del Kaioken. La maestría lo
 * reduce (SkillEffects.kaiokenDrainFactor).
 */
public enum KaiokenTier {
    OFF ("",     0.0,  0.0, 0),
    X2  ("x2",   1.0,  0.8, 1),
    X3  ("x3",   2.0,  1.5, 3),
    X4  ("x4",   3.0,  2.5, 5),
    X10 ("x10",  9.0,  8.0, 7),
    X20 ("x20", 19.0, 20.0, 10);

    private final String label;
    private final double statPercent;
    private final double drainPctPerSecond;
    private final int requiredLevel;

    KaiokenTier(String label, double statPercent, double drainPctPerSecond, int requiredLevel) {
        this.label = label;
        this.statPercent = statPercent;
        this.drainPctPerSecond = drainPctPerSecond;
        this.requiredLevel = requiredLevel;
    }

    /** Sufijo visible ("x20"). Vacío en OFF. */
    public String label() { return label; }

    /** Fracción que SUMA al multiplicador total (1.0 = +100%). */
    public double statPercent() { return statPercent; }

    /** Vida drenada por segundo, como % del body máximo. */
    public double drainPctPerSecond() { return drainPctPerSecond; }

    /** Nivel de la habilidad kaioken necesario para usar este escalón. */
    public int requiredLevel() { return requiredLevel; }

    public boolean isOn() { return this != OFF; }

    public static KaiokenTier byOrdinal(int i) {
        KaiokenTier[] all = values();
        return (i >= 0 && i < all.length) ? all[i] : OFF;
    }

    /** El escalón más alto que permite este nivel de habilidad (OFF si no llega a ninguno). */
    public static KaiokenTier highestFor(int skillLevel) {
        KaiokenTier best = OFF;
        for (KaiokenTier t : values()) {
            if (t.isOn() && skillLevel >= t.requiredLevel()) best = t;
        }
        return best;
    }

    /** ¿Este nivel de habilidad permite este escalón? */
    public boolean allowedAt(int skillLevel) {
        return this == OFF || skillLevel >= requiredLevel;
    }
}