package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.Dbrattributes;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.Style;
import com.hmc.zenkai.core.network.feature.ki.KiAttackDefinition;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * Coordinador principal de los datos del jugador.
 * No contiene lógica propia: delega a los cuatro sub-módulos.
 *
 * Sub-módulos:
 *   - PlayerRaceStats    → raza, estilo, atributos, TP
 *   - PlayerResourcePools → body, stamina, energy, movimiento
 *   - PlayerKiAttacks    → definiciones y cálculos de Ki Attacks
 *   - PlayerStateFlags   → flags especiales (inmortal, divino, etc.)
 */
public class PlayerStatsAttachment {

    private final PlayerRaceStats     raceStats = new PlayerRaceStats();
    private final PlayerResourcePools pools     = new PlayerResourcePools();
    private final PlayerKiAttacks     kiAttacks = new PlayerKiAttacks();
    private final PlayerStateFlags    flags     = new PlayerStateFlags();

    public PlayerStatsAttachment() {
        // Calcular los máximos iniciales y llenar los pools
        applyRecalc();
        pools.refillAll();
    }

    // ── Acceso estático ──────────────────────────────────────────────────────
    public static PlayerStatsAttachment get(Player p) {
        return p.getData(DataAttachments.PLAYER_STATS.get());
    }

    // ── Acceso a sub-módulos completos (cuando se necesita más que un getter) ─
    public PlayerRaceStats     raceStats()  { return raceStats; }
    public PlayerResourcePools pools()      { return pools; }
    public PlayerKiAttacks     kiAttacks()  { return kiAttacks; }
    public PlayerStateFlags    flags()      { return flags; }

    // ────────────────────────────────────────────────────────────────────────
    // API de compatibilidad — mantiene todas las llamadas existentes sin cambios
    // ────────────────────────────────────────────────────────────────────────

    // ── Raza / Estilo ────────────────────────────────────────────────────────
    public Race  getRace()  { return raceStats.getRace(); }
    public Style getStyle() { return raceStats.getStyle(); }

    public void setRace(Race r)   { raceStats.setRace(r);   applyRecalc(); }
    public void setStyle(Style s) { raceStats.setStyle(s);  applyRecalc(); }

    public boolean isRaceChosen()       { return raceStats.isRaceChosen(); }
    public boolean isStyleChosen()      { return raceStats.isStyleChosen(); }
    public void setRaceChosen(boolean v)  { raceStats.setRaceChosen(v); }
    public void setStyleChosen(boolean v) { raceStats.setStyleChosen(v); }

    public void applyRaceBaseAttributes() { raceStats.applyRaceBaseAttributes(); applyRecalc(); }

    // ── Atributos / TP ───────────────────────────────────────────────────────
    public int  getAttribute(Dbrattributes a)        { return raceStats.getAttribute(a); }
    public void setAttribute(Dbrattributes a, int v) { raceStats.setAttribute(a, v); applyRecalc(); }

    public int  getTP()              { return raceStats.getTP(); }
    public void addTP(int amount)    { raceStats.addTP(amount); }

    public boolean spendTP(Dbrattributes attr, int points) {
        boolean ok = raceStats.spendTP(attr, points);
        if (ok) applyRecalc();
        return ok;
    }

    public int  previewTpCost(Dbrattributes attr, int points) {
        return raceStats.previewTpCost(attr, points);
    }

    public void respec() { raceStats.respec(); applyRecalc(); }

    // ── Stats de combate ─────────────────────────────────────────────────────
    public double getMeleeBonus()       { return raceStats.getMeleeBonus(); }
    public double computeMeleeFinal()   { return raceStats.computeMeleeFinal(); }
    public double computeDefenseFinal() { return raceStats.computeDefenseFinal(); }
    public double computeSpeedFinal()   { return raceStats.computeSpeedFinal(); }
    public double computeFlyFinal()     { return raceStats.computeFlyFinal(); }
    public double computeKiPowerFinal() { return raceStats.computeKiPowerFinal(); }
    public double computeKiPoolFinal()  { return raceStats.computeKiPoolFinal(); }

    // ── Body ─────────────────────────────────────────────────────────────────
    public int  getBody()            { return pools.getBody(); }
    public int  getBodyMax()         { return pools.getBodyMax(); }
    public void addBody(int delta)   { pools.addBody(delta); }

    // ── Stamina ──────────────────────────────────────────────────────────────
    public int  getStamina()                  { return pools.getStamina(); }
    public int  getStaminaMax()               { return pools.getStaminaMax(); }
    public void addStamina(int delta)         { pools.addStamina(delta); }
    public void consumeStamina(int amount)    { pools.consumeStamina(amount); }

    // ── Energy / Ki ──────────────────────────────────────────────────────────
    public int  getEnergy()            { return pools.getEnergy(); }
    public int  getEnergyMax()         { return pools.getEnergyMax(); }
    public void addEnergy(int delta)   { pools.addEnergy(delta); }
    public void addKi(double delta)    { pools.addKi(delta); }
    public int  getKiCurrent()         { return pools.getKiCurrent(); }
    public int  getKiPool()            { return pools.getKiPool(); }

    // ── Movimiento ───────────────────────────────────────────────────────────
    public double getSpeedStat()    { return pools.getSpeedStat(); }
    public double getFlySpeedStat() { return pools.getFlySpeedStat(); }
    public double getFlySpeed()     { return pools.getFlySpeed(); }

    public double getFlyMultiplier() {
        return pools.getFlyMultiplier(StatsConfig.flyMultiplierCap());
    }
    public double getMoveMultiplier() {
        return pools.getMoveMultiplier();
    }
    public double getRegenEnergyPerTick() { return 1.0; }

    // ── Flags ────────────────────────────────────────────────────────────────
    public boolean isFlyEnabled()    { return flags.isFlyEnabled(); }
    public boolean isChargingKi()    { return flags.isChargingKi(); }
    public boolean isImmortal()      { return flags.isImmortal(); }
    public boolean isDivine()        { return flags.isDivine(); }
    public boolean isMajin()         { return flags.isMajin(); }
    public boolean isLegendary()     { return flags.isLegendary(); }

    public void setFlyEnabled(boolean v)  { flags.setFlyEnabled(v); }
    public void setChargingKi(boolean v)  { flags.setChargingKi(v); }
    public void setImmortal(boolean v)    { flags.setImmortal(v); }
    public void setDivine(boolean v)      { flags.setDivine(v); }
    public void setMajin(boolean v)       { flags.setMajin(v); }
    public void setLegendary(boolean v)   { flags.setLegendary(v); }

    // ── Ki Attacks ───────────────────────────────────────────────────────────
    public KiAttackDefinition getKiAttack(String id)    { return kiAttacks.getAttack(id); }
    public void addOrUpdateKiAttack(KiAttackDefinition def) { kiAttacks.addOrUpdate(def); }
    public void setKiAttackColor(String id, int rgb)    { kiAttacks.setColor(id, rgb); }
    public void setSelectedKiAttackId(String id)        { kiAttacks.setSelectedId(id); }
    public String getSelectedKiAttackId()               { return kiAttacks.getSelectedId(); }
    public Map<String, KiAttackDefinition> getKiAttacksReadonly() { return kiAttacks.getReadonly(); }

    public int computeKiAttackCost(KiAttackDefinition def, double chargeRatio) {
        return kiAttacks.computeCost(def, chargeRatio, raceStats.computeKiPowerFinal());
    }
    public float computeKiAttackDamage(KiAttackDefinition def, double chargeRatio) {
        return kiAttacks.computeDamage(def, chargeRatio, raceStats.computeKiPowerFinal());
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────
    public void refillOnRespawn() { pools.refillAll(); }

    // ── Recalc interno ───────────────────────────────────────────────────────
    /** Propaga los máximos calculados por PlayerRaceStats a PlayerResourcePools. */
    private void applyRecalc() {
        PlayerRaceStats.RecalcResult res = raceStats.recalcAll();
        pools.setBodyMax(res.bodyMax());
        pools.setStaminaMax(res.staminaMax());
        pools.setEnergyMax(res.energyMax());
        pools.setSpeed(res.speed());
        pools.setFlySpeed(res.flySpeed());
        pools.clampToCurrent();
    }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("race",      raceStats.save());
        tag.put("pools",     pools.save());
        tag.put("kiAttacks", kiAttacks.save());
        tag.put("flags",     flags.save());
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("race"))      raceStats.load(tag.getCompound("race"));
        if (tag.contains("pools"))     pools.load(tag.getCompound("pools"));
        if (tag.contains("kiAttacks")) kiAttacks.load(tag.getCompound("kiAttacks"));
        if (tag.contains("flags"))     flags.load(tag.getCompound("flags"));
        // Recalc por si los atributos cambiaron al cargar
        applyRecalc();
    }
}