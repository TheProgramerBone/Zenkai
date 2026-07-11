package com.hmc.zenkai.core.training;

import net.minecraft.nbt.CompoundTag;

/**
 * Estado de entrenamiento del jugador (attachment TRAINING).
 *
 *  - fatigue: TP ganado entrenando esta "sesión", NORMALIZADO por el PL propio (así los
 *    umbrales funcionan igual a PL 50 que a PL 1M). Sube al ganar, baja con el tiempo
 *    (lazy decay: se aplica al ganar, sin tick handler).
 *  - carry: resto fraccional de TP (a PL bajo un golpe da <1 TP; sin carry se perdería).
 *  - lastDecayTime / lastSwingTime: gameTime del último decay aplicado / último golpe al
 *    aire contado (rate-limit servidor).
 *
 * Persiste en NBT y se copia al morir (evita el exploit de resetear la fatiga muriendo).
 */
public final class TrainingData {

    private double fatigue = 0.0;
    private double carry = 0.0;
    private long lastDecayTime = 0L;
    private long lastSwingTime = 0L;

    public double getFatigue()      { return fatigue; }
    public void setFatigue(double f){ fatigue = Math.max(0.0, f); }

    public double getCarry()        { return carry; }
    public void setCarry(double c)  { carry = c; }

    public long getLastDecayTime()          { return lastDecayTime; }
    public void setLastDecayTime(long t)    { lastDecayTime = t; }

    public long getLastSwingTime()          { return lastSwingTime; }
    public void setLastSwingTime(long t)    { lastSwingTime = t; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("fatigue", fatigue);
        tag.putDouble("carry", carry);
        tag.putLong("lastDecay", lastDecayTime);
        tag.putLong("lastSwing", lastSwingTime);
        return tag;
    }

    public void load(CompoundTag tag) {
        fatigue = tag.getDouble("fatigue");
        carry = tag.getDouble("carry");
        lastDecayTime = tag.getLong("lastDecay");
        lastSwingTime = tag.getLong("lastSwing");
    }
}