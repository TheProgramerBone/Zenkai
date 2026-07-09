package com.hmc.zenkai.core.network.feature.player;

import net.minecraft.nbt.CompoundTag;

public class PlayerStateFlags {

    private boolean isImmortal  = false;
    private boolean isDivine    = false;
    private boolean isMajin     = false;
    private boolean isLegendary = false;
    private boolean flyEnabled  = false;
    private boolean chargingKi  = false;

    /**
     * El jugador está en BOOST de vuelo -> pose/hitbox horizontal ("acostado").
     * Transitorio (runtime, server-only): NO se guarda en NBT (evita quedar horizontal al
     * reconectar) ni hace falta en el sync (la pose se propaga sola por DATA_POSE).
     */
    private boolean flyBoosting = false;

    /**
     * Tracker transitorio (por lado, NO se guarda ni sincroniza): si la hitbox de boost ya está
     * aplicada. Sirve para llamar refreshDimensions() SOLO en la transición (evita recalcular cada tick).
     */
    private boolean boostSizeApplied = false;

    /** El jugador está muerto / en el otro mundo. */
    private boolean inOtherworld = false;
    /** gameTime en que fue enviado al otro mundo (para el contador de Yemma). */
    private long otherworldSince = 0L;

    /** El jugador está "derribado" (acostado, transición previa al otro mundo). */
    private boolean downed = false;
    /** gameTime en que el derribado termina y muere de verdad si no lo curan. */
    private long downedUntil = 0L;

    public boolean isImmortal()  { return isImmortal; }
    public boolean isDivine()    { return isDivine; }
    public boolean isMajin()     { return isMajin; }
    public boolean isLegendary() { return isLegendary; }
    public boolean isFlyEnabled()  { return flyEnabled; }
    public boolean isChargingKi()  { return chargingKi; }
    public boolean isFlyBoosting() { return flyBoosting; }
    public boolean isBoostSizeApplied() { return boostSizeApplied; }
    public boolean isInOtherworld() { return inOtherworld; }
    public long getOtherworldSince() { return otherworldSince; }
    public boolean isDowned()     { return downed; }
    public long getDownedUntil()  { return downedUntil; }

    public void setImmortal(boolean v)  { this.isImmortal  = v; }
    public void setDivine(boolean v)    { this.isDivine    = v; }
    public void setMajin(boolean v)     { this.isMajin     = v; }
    public void setLegendary(boolean v) { this.isLegendary = v; }
    public void setFlyEnabled(boolean v)  { this.flyEnabled  = v; }
    public void setChargingKi(boolean v)  { this.chargingKi  = v; }
    public void setFlyBoosting(boolean v) { this.flyBoosting = v; }
    public void setBoostSizeApplied(boolean v) { this.boostSizeApplied = v; }
    public void setInOtherworld(boolean v) { this.inOtherworld = v; }
    public void setOtherworldSince(long t) { this.otherworldSince = t; }
    public void setDowned(boolean v)     { this.downed = v; }
    public void setDownedUntil(long t)   { this.downedUntil = t; }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isImmortal",  isImmortal);
        tag.putBoolean("isDivine",    isDivine);
        tag.putBoolean("isMajin",     isMajin);
        tag.putBoolean("isLegendary", isLegendary);
        tag.putBoolean("flyEnabled",  flyEnabled);
        tag.putBoolean("chargingKi",  chargingKi);
        tag.putBoolean("inOtherworld", inOtherworld);
        tag.putLong("otherworldSince", otherworldSince);
        tag.putBoolean("downed", downed);
        tag.putLong("downedUntil", downedUntil);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.isImmortal  = tag.getBoolean("isImmortal");
        this.isDivine    = tag.getBoolean("isDivine");
        this.isMajin     = tag.getBoolean("isMajin");
        this.isLegendary = tag.getBoolean("isLegendary");
        this.flyEnabled  = tag.getBoolean("flyEnabled");
        this.chargingKi  = tag.getBoolean("chargingKi");
        this.inOtherworld = tag.getBoolean("inOtherworld");
        this.otherworldSince = tag.getLong("otherworldSince");
        this.downed = tag.getBoolean("downed");
        this.downedUntil = tag.getLong("downedUntil");
    }
}