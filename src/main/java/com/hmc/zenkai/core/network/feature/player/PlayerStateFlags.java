package com.hmc.zenkai.core.network.feature.player;

import net.minecraft.nbt.CompoundTag;

public class PlayerStateFlags {

    private boolean isImmortal  = false;
    private boolean isDivine    = false;
    private boolean isMajin     = false;
    private boolean isLegendary = false;
    private boolean flyEnabled  = false;
    private boolean chargingKi  = false;

    public boolean isImmortal()  { return isImmortal; }
    public boolean isDivine()    { return isDivine; }
    public boolean isMajin()     { return isMajin; }
    public boolean isLegendary() { return isLegendary; }
    public boolean isFlyEnabled()  { return flyEnabled; }
    public boolean isChargingKi()  { return chargingKi; }

    public void setImmortal(boolean v)  { this.isImmortal  = v; }
    public void setDivine(boolean v)    { this.isDivine    = v; }
    public void setMajin(boolean v)     { this.isMajin     = v; }
    public void setLegendary(boolean v) { this.isLegendary = v; }
    public void setFlyEnabled(boolean v)  { this.flyEnabled  = v; }
    public void setChargingKi(boolean v)  { this.chargingKi  = v; }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isImmortal",  isImmortal);
        tag.putBoolean("isDivine",    isDivine);
        tag.putBoolean("isMajin",     isMajin);
        tag.putBoolean("isLegendary", isLegendary);
        tag.putBoolean("flyEnabled",  flyEnabled);
        tag.putBoolean("chargingKi",  chargingKi);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.isImmortal  = tag.getBoolean("isImmortal");
        this.isDivine    = tag.getBoolean("isDivine");
        this.isMajin     = tag.getBoolean("isMajin");
        this.isLegendary = tag.getBoolean("isLegendary");
        this.flyEnabled  = tag.getBoolean("flyEnabled");
        this.chargingKi  = tag.getBoolean("chargingKi");
    }
}