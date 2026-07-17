package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.core.combat.ZenkaiCombatStats;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.ZenkaiAttributes;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.Style;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coordinador principal de los datos del jugador.
 * No contiene lógica propia: delega a los submódulos.
 * Submódulos:
 *   - PlayerRaceStats     → raza, estilo, atributos, TP
 *   - PlayerResourcePools → body, stamina, energy, movimiento
 *   - PlayerStateFlags    → flags especiales (inmortal, divino, etc.)
 *   - PlayerSkills        → habilidades desbloqueadas (MIND)
 *   - PlayerTechniques    → técnicas ki (tipos desbloqueados + slots)
 */
public class PlayerStatsAttachment implements ZenkaiCombatStats {

    private final PlayerRaceStats     raceStats  = new PlayerRaceStats();
    private final PlayerResourcePools pools      = new PlayerResourcePools();
    private final PlayerStateFlags    flags      = new PlayerStateFlags();
    private final PlayerSkills        skills     = new PlayerSkills();
    private final PlayerTechniques    techniques = new PlayerTechniques();

    /** Último tick (gameTime) en que este jugador invocó a Shenlong. Cooldown por jugador. */
    private long lastSummonTick = Long.MIN_VALUE;

    /** NBT completo de las mascotas muertas del jugador (para el deseo de revivir). Más reciente al final. */
    private static final int MAX_DEAD_PETS = 6;
    private final List<CompoundTag> deadPets = new ArrayList<>();

    public PlayerStatsAttachment() {
        // Calcular los máximos iniciales y llenar los pools
        applyRecalc();
        pools.refillAll();
    }

    // ── Acceso estático ──────────────────────────────────────────────────────
    public static PlayerStatsAttachment get(Player p) {
        return p.getData(DataAttachments.PLAYER_STATS.get());
    }

    // ── Acceso a submódulos completos (cuando se necesita más que un getter) ─
    public PlayerRaceStats     raceStats()  { return raceStats; }
    public PlayerResourcePools pools()      { return pools; }
    public PlayerStateFlags    flags()      { return flags; }
    public PlayerSkills        skills()     { return skills; }
    public PlayerTechniques    techniques() { return techniques; }

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
    public int  getAttribute(ZenkaiAttributes a)        { return raceStats.getAttribute(a); }
    public void setAttribute(ZenkaiAttributes a, int v) { raceStats.setAttribute(a, v); applyRecalc(); }

    public int  getTP()              { return raceStats.getTP(); }
    public void addTP(int amount)    { raceStats.addTP(amount); }

    public boolean spendTP(ZenkaiAttributes attr, int points) {
        boolean ok = raceStats.spendTP(attr, points);
        if (ok) applyRecalc();
        return ok;
    }

    public int  previewTpCost(ZenkaiAttributes attr, int points) {
        return raceStats.previewTpCost(attr, points);
    }

    /** Respec: devuelve el TP invertido en atributos Y en habilidades COMPRADAS (y las
     *  limpia). Las otorgadas por maestros/comando no se tocan. */
    public void respec() {
        int skillRefund = 0;
        for (String id : skills.boughtAll()) {
            com.hmc.zenkai.core.skills.SkillDef def = com.hmc.zenkai.core.skills.SkillDef.get(id);
            if (def != null) skillRefund += def.tpCost();
        }
        skills.clearBought();
        raceStats.respec();
        raceStats.addTP(skillRefund);
        applyRecalc();
    }

    // ── Stats de combate ─────────────────────────────────────────────────────
    public double getMeleeBonus()       { return raceStats.getMeleeBonus(); }
    public double computeMeleeFinal()   { return raceStats.computeMeleeFinal(); }
    public double computeDefenseFinal() { return raceStats.computeDefenseFinal(); }
    public double computeSpeedFinal()   { return raceStats.computeSpeedFinal(); }
    public double computeFlyFinal()     { return raceStats.computeFlyFinal(); }
    public double computeKiPowerFinal() { return raceStats.computeKiPowerFinal(); }
    public double computeKiPoolFinal()  { return raceStats.computeKiPoolFinal(); }
    public double computeConFinal()     { return raceStats.computeConFinal(); }
    public boolean isCombatActive() { return isRaceChosen(); }

    // ── Body ─────────────────────────────────────────────────────────────────
    public int  getBody()            { return pools.getBody(); }
    public int  getBodyMax()         { return pools.getBodyMax(); }
    public void addBody(int delta)   { pools.addBody(delta); }
    public void setBody(int value)   { pools.setBody(value); }

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

    public boolean isInOtherworld()        { return flags.isInOtherworld(); }
    public void setInOtherworld(boolean v) { flags.setInOtherworld(v); }
    public long getOtherworldSince()       { return flags.getOtherworldSince(); }
    public void setOtherworldSince(long t) { flags.setOtherworldSince(t); }

    // ── Ciclo de vida ────────────────────────────────────────────────────────
    public void refillOnRespawn() { pools.refillAll(); }

    // ── Cooldown de invocación (por jugador) ─────────────────────────────────
    /** Long.MIN_VALUE => nunca ha invocado (sin cooldown). */
    public long getLastSummonTick()       { return lastSummonTick; }
    public void setLastSummonTick(long t) { this.lastSummonTick = t; }

    // ── Mascotas muertas (deseo de revivir) ──────────────────────────────────
    /** Lista de solo lectura del NBT de mascotas muertas (la más reciente al final). */
    public List<CompoundTag> getDeadPets() { return Collections.unmodifiableList(deadPets); }

    /** Añade una mascota muerta (NBT ya serializado). Respeta el tope, descartando la más antigua. */
    public void addDeadPet(CompoundTag petNbt) {
        if (petNbt == null || petNbt.isEmpty()) return;
        deadPets.add(petNbt);
        while (deadPets.size() > MAX_DEAD_PETS) deadPets.remove(0);
    }

    /** Saca y devuelve la mascota en el índice dado (para revivirla); null si el índice no es válido. */
    public CompoundTag removeDeadPet(int index) {
        if (index < 0 || index >= deadPets.size()) return null;
        return deadPets.remove(index);
    }

    /** Borra el historial de mascotas muertas. */
    public void clearDeadPets() { deadPets.clear(); }

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

    // ── Alineamiento (-100..+100) ────────────────────────────────────────────
    private int alignment = 0;

    public int  getAlignment()          { return alignment; }
    public void setAlignment(int v)     { this.alignment = Math.max(-100, Math.min(100, v)); }
    public void addAlignment(int delta) { setAlignment(alignment + delta); }

    // ── Maestría por técnica (clave = nombre del tipo, 0..100%) ──────────────
    private final java.util.Map<String, Float> techMastery = new java.util.HashMap<>();

    public float getTechniqueMastery(String key) {
        return techMastery.getOrDefault(key, 0f);
    }
    public void addTechniqueMastery(String key, float delta) {
        if (key == null || key.isEmpty() || delta <= 0) return;
        techMastery.merge(key, delta, Float::sum);
        techMastery.computeIfPresent(key, (k, v) -> Math.min(100f, v));
    }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("race",       raceStats.save());
        tag.put("pools",      pools.save());
        tag.put("flags",      flags.save());
        tag.put("skills",     skills.save());
        tag.put("techniques", techniques.save());
        tag.putLong("lastSummonTick", lastSummonTick);
        ListTag pets = new ListTag();
        pets.addAll(deadPets);
        tag.put("deadPets", pets);
        tag.putInt("alignment", alignment);
        CompoundTag tm = new CompoundTag();
        for (var e : techMastery.entrySet()) tm.putFloat(e.getKey(), e.getValue());
        tag.put("techMastery", tm);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("race"))       raceStats.load(tag.getCompound("race"));
        if (tag.contains("pools"))      pools.load(tag.getCompound("pools"));
        if (tag.contains("flags"))      flags.load(tag.getCompound("flags"));
        if (tag.contains("skills"))     skills.load(tag.getCompound("skills"));
        if (tag.contains("techniques")) techniques.load(tag.getCompound("techniques"));
        lastSummonTick = tag.contains("lastSummonTick") ? tag.getLong("lastSummonTick") : Long.MIN_VALUE;
        setAlignment(tag.contains("alignment") ? tag.getInt("alignment") : 0);
        techMastery.clear();
        if (tag.contains("techMastery")) {
            CompoundTag tm = tag.getCompound("techMastery");
            for (String k : tm.getAllKeys()) techMastery.put(k, Math.min(100f, Math.max(0f, tm.getFloat(k))));
        }
        deadPets.clear();
        if (tag.contains("deadPets")) {
            ListTag pets = tag.getList("deadPets", Tag.TAG_COMPOUND);
            for (int i = 0; i < pets.size(); i++) deadPets.add(pets.getCompound(i));
        }
        // Recalc por si los atributos cambiaron al cargar
        applyRecalc();
    }
}