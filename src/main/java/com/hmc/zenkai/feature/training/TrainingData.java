package com.hmc.zenkai.feature.training;

import net.minecraft.nbt.CompoundTag;

/**
 * Estado de entrenamiento del jugador (attachment TRAINING).
 *  - fatigue/carry/lastDecayTime: UNA copia por {@link TrainingCategory} (2026-09-09, pedido
 *    explícito del usuario — antes era un solo contador compartido por todo, ver el javadoc de
 *    TrainingCategory para el porqué y el reparto de fuentes). fatigue = TP ganado entrenando
 *    esta "sesión" de ESA categoría, NORMALIZADO por el PL propio (así los umbrales funcionan
 *    igual a PL 50 que a PL 1M); sube al ganar, baja con el tiempo (lazy decay: se aplica al
 *    ganar, sin tick handler) — ver TrainingHooks.grant(). carry es el resto fraccional de TP
 *    de esa categoría (a PL bajo un golpe da &lt;1 TP; sin carry se perdería).
 *  - lastSwingTime: gameTime del último golpe al aire contado (rate-limit servidor) — no es
 *    "por categoría" porque solo existe una fuente de golpes al aire (siempre COMBAT).
 *  - bestMeditationTp / bestTargetPracticeTp / bestShadowTp: récord personal (mayor TP
 *    concedido en una sola sesión) de cada minijuego de Training — ver TrainingInfoPacket
 *    (mostrado en INTRO junto al TP potencial) y TrainingSessionRewardPacket/
 *    ShadowSessionResultPacket (mostrado en RESULTS junto al TP recién ganado).
 *  - shadowSessionStartTp: snapshot de PlayerStatsAttachment.getTP() tomado al arrancar una
 *    sesión de "Train with your shadow" (ShadowTrainingManager.start()) — al morir el clon,
 *    (TP actual - este snapshot) es el TP "de esta pelea", aproximación deliberada: más simple
 *    que enganchar TrainingHooks.grant() para etiquetar cada golpe como "contra tu sombra", a
 *    costa de contar de más si el jugador gana TP de otra fuente a mitad de la pelea (poco
 *    probable en el rato corto que dura un sparring).
 * Persiste en NBT y se copia al morir (evita el exploit de resetear la fatiga muriendo).
 */
public final class TrainingData {

    private static final int N = TrainingCategory.values().length;

    private final double[] fatigue = new double[N];
    private final double[] carry = new double[N];
    private final long[] lastDecayTime = new long[N];
    private long lastSwingTime = 0L;

    private int bestMeditationTp = 0;
    private int bestTargetPracticeTp = 0;
    private int bestShadowTp = 0;
    private int shadowSessionStartTp = 0;

    public double getFatigue(TrainingCategory cat)         { return fatigue[cat.ordinal()]; }
    public void setFatigue(TrainingCategory cat, double f) { fatigue[cat.ordinal()] = Math.max(0.0, f); }

    public double getCarry(TrainingCategory cat)           { return carry[cat.ordinal()]; }
    public void setCarry(TrainingCategory cat, double c)   { carry[cat.ordinal()] = c; }

    public long getLastDecayTime(TrainingCategory cat)       { return lastDecayTime[cat.ordinal()]; }
    public void setLastDecayTime(TrainingCategory cat, long t) { lastDecayTime[cat.ordinal()] = t; }

    public long getLastSwingTime()          { return lastSwingTime; }
    public void setLastSwingTime(long t)    { lastSwingTime = t; }

    public int getBestMeditationTp()        { return bestMeditationTp; }
    public void setBestMeditationTp(int v)  { bestMeditationTp = Math.max(0, v); }

    public int getBestTargetPracticeTp()       { return bestTargetPracticeTp; }
    public void setBestTargetPracticeTp(int v) { bestTargetPracticeTp = Math.max(0, v); }

    public int getBestShadowTp()      { return bestShadowTp; }
    public void setBestShadowTp(int v){ bestShadowTp = Math.max(0, v); }

    public int getShadowSessionStartTp()       { return shadowSessionStartTp; }
    public void setShadowSessionStartTp(int v) { shadowSessionStartTp = v; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (TrainingCategory cat : TrainingCategory.values()) {
            String suffix = cat.name();
            tag.putDouble("fatigue_" + suffix, fatigue[cat.ordinal()]);
            tag.putDouble("carry_" + suffix, carry[cat.ordinal()]);
            tag.putLong("lastDecay_" + suffix, lastDecayTime[cat.ordinal()]);
        }
        tag.putLong("lastSwing", lastSwingTime);
        tag.putInt("bestMeditationTp", bestMeditationTp);
        tag.putInt("bestTargetPracticeTp", bestTargetPracticeTp);
        tag.putInt("bestShadowTp", bestShadowTp);
        tag.putInt("shadowSessionStartTp", shadowSessionStartTp);
        return tag;
    }

    public void load(CompoundTag tag) {
        // Compatibilidad con guardados viejos (un solo contador "fatigue"/"carry"/"lastDecay",
        // antes de que existieran las categorías): si no hay ninguna clave con sufijo todavía,
        // el valor viejo se vuelca a COMBAT — es la categoría que más TP movía en la práctica y
        // la que menos raro se siente arrancando con algo de fatiga heredada, en vez de perder
        // el dato sin más o repartirlo a ciegas entre las tres.
        boolean hasCategorized = tag.contains("fatigue_" + TrainingCategory.COMBAT.name());
        for (TrainingCategory cat : TrainingCategory.values()) {
            String suffix = cat.name();
            if (hasCategorized) {
                fatigue[cat.ordinal()] = tag.getDouble("fatigue_" + suffix);
                carry[cat.ordinal()] = tag.getDouble("carry_" + suffix);
                lastDecayTime[cat.ordinal()] = tag.getLong("lastDecay_" + suffix);
            } else if (cat == TrainingCategory.COMBAT) {
                fatigue[cat.ordinal()] = tag.getDouble("fatigue");
                carry[cat.ordinal()] = tag.getDouble("carry");
                lastDecayTime[cat.ordinal()] = tag.getLong("lastDecay");
            }
        }
        lastSwingTime = tag.getLong("lastSwing");
        bestMeditationTp = tag.getInt("bestMeditationTp");
        bestTargetPracticeTp = tag.getInt("bestTargetPracticeTp");
        bestShadowTp = tag.getInt("bestShadowTp");
        shadowSessionStartTp = tag.getInt("shadowSessionStartTp");
    }
}
