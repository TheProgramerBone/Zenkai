package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.util.MathUtil;
import net.minecraft.nbt.CompoundTag;

public class PlayerResourcePools {

    private int body     = 1;
    private int bodyMax  = 1;
    private int stamina    = 1;
    private int staminaMax = 1;
    private int energy     = 1;
    private int energyMax  = 1;
    /**
     * Sobrecarga del ki Legendario (0.7.0): hueco EXTRA por encima de energyMax que deja llenar el
     * fluido de Ki Legendario. Mientras se está dentro se renueva; al salir, {@link #tickOvercharge}
     * lo encoge en línea recta hasta 0 y el ki sobrante se pierde con él. Se guarda en el NBT del
     * attachment, que es también lo que viaja al cliente, así la HUD ve «345 / 276» sin packet propio.
     */
    private int overchargeRoom = 0;
    /** Hueco al empezar a desvanecerse y segundos que quedan, para encogerlo en línea recta. */
    private int overchargePeak = 0;
    private int overchargeFadeLeft = 0;
    private double speed    = 0;
    private double flySpeed = 0;

    // ── Body ─────────────────────────────────────────────────────────────────
    public int getBody()    { return body; }
    public int getBodyMax() { return bodyMax; }

    public void setBodyMax(int max) { this.bodyMax = Math.max(1, max); }

    public void addBody(int delta) {
        body = MathUtil.clamp(body + delta, 0, bodyMax);
    }

    /** Fija el body a un valor absoluto (con clamp a [0, bodyMax]). */
    public void setBody(int value) {
        body = MathUtil.clamp(value, 0, bodyMax);
    }

    // ── Stamina ──────────────────────────────────────────────────────────────
    public int getStamina()    { return stamina; }
    public int getStaminaMax() { return staminaMax; }

    public void setStaminaMax(int max) { this.staminaMax = Math.max(1, max); }

    public void addStamina(int delta) {
        stamina = MathUtil.clamp(stamina + delta, 0, staminaMax);
    }

    public void consumeStamina(int amount) {
        if (amount <= 0 || stamina <= 0) return;
        stamina -= Math.min(amount, stamina);
    }

    // ── Energy / Ki ──────────────────────────────────────────────────────────
    public int getEnergy()    { return energy; }
    public int getEnergyMax() { return energyMax; }

    public int getKiCurrent() { return energy; }
    public int getKiPool()    { return energyMax; }

    public void setEnergyMax(int max) { this.energyMax = Math.max(1, max); }

    /** Techo real del ki: el pool más la sobrecarga Legendaria que quede. */
    private int energyCap() { return energyMax + overchargeRoom; }

    public void addEnergy(int delta) {
        energy = MathUtil.clamp(energy + delta, 0, energyCap());
    }

    public void addKi(double delta) {
        energy = MathUtil.clamp((int) Math.round(energy + delta), 0, energyCap());
    }

    public int getOverchargeRoom() { return overchargeRoom; }

    /**
     * Dentro del fluido Legendario: abre (o mantiene) el hueco de sobrecarga y reinicia el
     * desvanecimiento, que empezará cuando deje de renovarse.
     */
    public void holdOvercharge(int room, int fadeSeconds) {
        overchargeRoom = Math.max(overchargeRoom, Math.max(0, room));
        overchargePeak = overchargeRoom;
        overchargeFadeLeft = Math.max(1, fadeSeconds) + 1;   // +1: el segundo en curso no cuenta
    }

    /** Una vez por segundo (RegenSystem): encoge el hueco y recorta el ki que ya no cabe. */
    public void tickOvercharge() {
        if (overchargeRoom <= 0) return;
        overchargeFadeLeft--;
        if (overchargeFadeLeft <= 0) overchargeRoom = 0;
        else overchargeRoom = Math.min(overchargeRoom,
                (int) Math.ceil(overchargePeak * (overchargeFadeLeft / (double) (overchargeFadeLeft + 1))));
        energy = Math.min(energy, energyCap());
    }

    // ── Movimiento ───────────────────────────────────────────────────────────
    public double getSpeedStat()    { return speed; }
    public double getFlySpeedStat() { return flySpeed; }
    public double getFlySpeed()     { return flySpeed; }

    public void setSpeed(double v)    { this.speed    = v; }
    public void setFlySpeed(double v) { this.flySpeed = v; }

    /** El boost de forma se aplica a la DEX efectiva ANTES del cap, así el techo de config
     *  sigue siendo el límite real de seguridad. */
    public double getFlyMultiplier(double cap, double scaling, double statMult) {
        return Math.min(cap, Math.max(0.0, 1.0 + (flySpeed * statMult / 100.0) * scaling));
    }

    /** El 2.0 cableado que había aquí ignoraba speedMultiplierCap y movementScaling, así que
     *  la pantalla de stats mostraba un número y el movimiento aplicaba otro. */
    public double getMoveMultiplier(double cap, double scaling, double statMult) {
        return Math.min(cap, Math.max(0.0, 1.0 + (speed * statMult / 100.0) * scaling));
    }

    // ── Clamp y refill ───────────────────────────────────────────────────────
    public void clampToCurrent() {
        body    = Math.min(body,    bodyMax);
        stamina = Math.min(stamina, staminaMax);
        energy  = Math.min(energy,  energyCap());
    }

    public void refillAll() {
        body    = bodyMax;
        stamina = staminaMax;
        energy  = energyMax;
    }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("body",       body);
        tag.putInt("bodyMax",    bodyMax);
        tag.putInt("stamina",    stamina);
        tag.putInt("staminaMax", staminaMax);
        tag.putInt("energy",     energy);
        tag.putInt("energyMax",  energyMax);
        if (overchargeRoom > 0) {
            tag.putInt("overchargeRoom", overchargeRoom);
            tag.putInt("overchargePeak", overchargePeak);
            tag.putInt("overchargeFade", overchargeFadeLeft);
        }
        tag.putDouble("speed",    speed);
        tag.putDouble("flySpeed", flySpeed);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.body       = tag.getInt("body");
        this.bodyMax    = tag.getInt("bodyMax");
        this.stamina    = tag.getInt("stamina");
        this.staminaMax = tag.getInt("staminaMax");
        this.energy     = tag.getInt("energy");
        this.energyMax  = tag.getInt("energyMax");
        this.overchargeRoom     = tag.getInt("overchargeRoom");
        this.overchargePeak     = tag.getInt("overchargePeak");
        this.overchargeFadeLeft = tag.getInt("overchargeFade");
        this.speed      = tag.getDouble("speed");
        this.flySpeed   = tag.getDouble("flySpeed");
    }
}