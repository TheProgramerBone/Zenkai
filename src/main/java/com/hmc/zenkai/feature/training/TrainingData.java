package com.hmc.zenkai.feature.training;

import net.minecraft.nbt.CompoundTag;

/**
 * Estado de entrenamiento del jugador (attachment TRAINING).
 *  - fatigue: TP ganado entrenando esta "sesión", NORMALIZADO por el PL propio (así los
 *    umbrales funcionan igual a PL 50 que a PL 1M). Sube al ganar, baja con el tiempo
 *    (lazy decay: se aplica al ganar, sin tick handler).
 *  - carry: resto fraccional de TP (a PL bajo un golpe da <1 TP; sin carry se perdería).
 *  - lastDecayTime / lastSwingTime: gameTime del último decay aplicado / último golpe al
 *    aire contado (rate-limit servidor).
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

    private double fatigue = 0.0;
    private double carry = 0.0;
    private long lastDecayTime = 0L;
    private long lastSwingTime = 0L;

    private int bestMeditationTp = 0;
    private int bestTargetPracticeTp = 0;
    private int bestShadowTp = 0;
    private int shadowSessionStartTp = 0;

    public double getFatigue()      { return fatigue; }
    public void setFatigue(double f){ fatigue = Math.max(0.0, f); }

    public double getCarry()        { return carry; }
    public void setCarry(double c)  { carry = c; }

    public long getLastDecayTime()          { return lastDecayTime; }
    public void setLastDecayTime(long t)    { lastDecayTime = t; }

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
        tag.putDouble("fatigue", fatigue);
        tag.putDouble("carry", carry);
        tag.putLong("lastDecay", lastDecayTime);
        tag.putLong("lastSwing", lastSwingTime);
        tag.putInt("bestMeditationTp", bestMeditationTp);
        tag.putInt("bestTargetPracticeTp", bestTargetPracticeTp);
        tag.putInt("bestShadowTp", bestShadowTp);
        tag.putInt("shadowSessionStartTp", shadowSessionStartTp);
        return tag;
    }

    public void load(CompoundTag tag) {
        fatigue = tag.getDouble("fatigue");
        carry = tag.getDouble("carry");
        lastDecayTime = tag.getLong("lastDecay");
        lastSwingTime = tag.getLong("lastSwing");
        bestMeditationTp = tag.getInt("bestMeditationTp");
        bestTargetPracticeTp = tag.getInt("bestTargetPracticeTp");
        bestShadowTp = tag.getInt("bestShadowTp");
        shadowSessionStartTp = tag.getInt("shadowSessionStartTp");
    }
}
