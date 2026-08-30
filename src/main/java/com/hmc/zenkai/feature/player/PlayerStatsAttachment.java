package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.feature.combat.PowerLevel;
import com.hmc.zenkai.feature.combat.ZenkaiCombatStats;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.SuperForms;
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

    /** % de poder en uso (Ki Control): 50 maxPowerPercent. Multiplica STR/DEX-def/WIL. */
    private int powerPercent = 50;

    /** Último tick (gameTime) en que este jugador invocó a Shenlong. Cooldown por jugador. */
    private long lastSummonTick = Long.MIN_VALUE;

    /** Último tick (gameTime) en que este jugador usó "Descender" en la rueda. Cooldown corto
     *  (2-3s) contra spam, mismo patrón que lastSummonTick. */
    private long lastDescendTick = Long.MIN_VALUE;

    /** NBT completo de las mascotas muertas del jugador (para el deseo de revivir). Más reciente al final. */
    private static final int MAX_DEAD_PETS = 6;
    private final List<CompoundTag> deadPets = new ArrayList<>();

    public PlayerStatsAttachment() {
        // Calcular los máximos iniciales y llenar los pools
        applyRecalc();
        pools.refillAll();
    }

    /** Multiplicador de forma + kaioken + majin. DERIVADO: no va a NBT, lo recalcula
     *  FormSystem cada tick. Un solo punto de escritura, así ningún camino se queda sin él. */
    private double statMultiplier = 1.0;

    /** Penalización de las pesas de entrenamiento. DERIVADO igual que statMultiplier, pero
     *  APARTE a propósito: el PL limpio (capacidad de carga, TP) necesita poder mirar los
     *  stats sin este factor, y si estuviera fusionado en statMultiplier sería irrecuperable. */
    private double weightFactor = 1.0;
    /** Carga relativa r = toneladas / capacidad. Derivado; lo escribe WeightLoadSystem. */
    private double weightLoad = 0.0;

    public void setStatMultiplier(double m) { this.statMultiplier = Math.max(0.0, m); }
    public double getStatMultiplier() { return statMultiplier; }

    public void setWeightFactor(double f) { this.weightFactor = Math.max(0.0, f); }
    public double getWeightFactor() { return weightFactor; }
    public void setWeightLoad(double r) { this.weightLoad = Math.max(0.0, r); }
    public double getWeightLoad() { return weightLoad; }

    // ── Acceso estático ──────────────────────────────────────────────────────
    public static PlayerStatsAttachment get(Player p) {
        return p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
    }

    // ── Acceso a submódulos completos (cuando se necesita más que un getter) ─
    public PlayerRaceStats     raceStats()  { return raceStats; }
    public PlayerResourcePools pools()      { return pools; }
    public PlayerStateFlags    flags()      { return flags; }
    public PlayerSkills        skills()     { return skills; }
    public PlayerTechniques    techniques() { return techniques; }

    // ────────────────────────────────────────────────────────────────────────
    // API de compatibilidad — mantiene el conjunto de llamadas existentes sin cambios
    // ────────────────────────────────────────────────────────────────────────

    // ── Raza / Estilo ────────────────────────────────────────────────────────
    public Race  getRace()  { return raceStats.getRace(); }
    public Style getStyle() { return raceStats.getStyle(); }

    /** Coste de ki de esta combinación raza/estilo (columna KI_COST del datapack). */
    @Override
    public double kiCostMult() {
        return com.hmc.zenkai.feature.RaceStatTable.kiCostMult(getRace(), getStyle());
    }

    /** Coste de estamina de esta combinación raza/estilo (columna STAM_COST). */
    @Override
    public double staminaCostMult() {
        return com.hmc.zenkai.feature.RaceStatTable.staminaCostMult(getRace(), getStyle());
    }

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

    public int refundPoint(ZenkaiAttributes attr) {
        int given = raceStats.refundPoint(attr);
        if (given >= 0) applyRecalc();
        return given;
    }

    public int refundPoints(ZenkaiAttributes attr, int points) {
        int given = raceStats.refundPoints(attr, points);
        if (given > 0) applyRecalc();
        return given;
    }

    public int  previewTpCost(ZenkaiAttributes attr, int points) {
        return raceStats.previewTpCost(attr, points);
    }

    /** Respec: devuelve el TP invertido en atributos Y en los NIVELES de habilidad comprados
     *  (cada habilidad baja hasta el nivel que le otorgó un maestro; si no tenía, desaparece).
     *  Los niveles otorgados por maestros/comando no se tocan ni se devuelven. */
    public void respec() {
        int skillRefund = 0;
        for (String id : skills.allLevels().keySet()) {
            SkillDef def = SkillDef.get(id);
            if (def == null) continue;
            int bought = skills.boughtLevels(id);
            if (bought <= 0) continue;

            if (def.levelsFromForms()) {
                // Coste por nivel derivado de las formas: hay que sumar nivel a nivel. Con
                // def.tpCost() (que aquí es 0) el respec se quedaría con el TP invertido.
                int top = skills.level(id);
                for (int lvl = top - bought + 1; lvl <= top; lvl++) {
                    int c = SuperForms.tpCostForLevel(getRace(), lvl);
                    if (c != Integer.MAX_VALUE) skillRefund += c;
                }
            } else {
                skillRefund += def.tpCost() * bought;
            }
        }
        skills.clearBought();
        raceStats.respec();
        raceStats.addTP(skillRefund);
        applyRecalc();
    }

    // ── Stats de combate ─────────────────────────────────────────────────────
    // El multiplicador de forma escala SOLO STR, DEX y WIL (como DBC). CON y SPI se quedan
    // crudos: transformarse no te da más vida ni más pool de ki, te hace pegar más fuerte.
    // Las pesas (weightFactor) montan encima y siguen la MISMA regla: castigan lo ofensivo
    // y lo defensivo, no el pool ni el body.
    public double getMeleeBonus()       { return raceStats.getMeleeBonus(); }

    public double computeMeleeUnweighted()   { return raceStats.computeMeleeFinal()   * powerFraction() * statMultiplier; }
    public double computeDefenseUnweighted() { return raceStats.computeDefenseFinal() * powerFraction() * statMultiplier; }
    public double computeKiPowerUnweighted() { return raceStats.computeKiPowerFinal() * powerFraction() * statMultiplier; }

    public double computeMeleeFinal()   { return computeMeleeUnweighted()   * weightFactor; }
    public double computeDefenseFinal() { return computeDefenseUnweighted() * weightFactor; }
    public double computeKiPowerFinal() { return computeKiPowerUnweighted() * weightFactor; }
    // Stats SIN supresión: el % de Ki Control es una máscara hacia fuera, no una rebaja de lo
    // que eres. Lo que mide poder de verdad (PL real, capacidad de carga, TP) usa estos.
    private double meleeUnsuppressed()   { return raceStats.computeMeleeFinal()   * statMultiplier * weightFactor; }
    private double defenseUnsuppressed() { return raceStats.computeDefenseFinal() * statMultiplier * weightFactor; }
    private double kiPowerUnsuppressed() { return raceStats.computeKiPowerFinal() * statMultiplier * weightFactor; }
    public double computeKiPoolFinal()  { return raceStats.computeKiPoolFinal(); }
    public double computeConFinal()     { return raceStats.computeConFinal(); }
    /** Escala con forma y % de poder igual que el melee: Ki Fist se beneficia de transformarse. */
    public double computeSpiritMeleeFinal() { return raceStats.computeSpiritMeleeFinal() * powerFraction() * statMultiplier * weightFactor; }
    /** Escala con forma, % de poder y pesas igual que el melee: el Black Flash se beneficia
     *  de transformarse, como lo demás. */
    @Override
    public double computeBestMeleeFinal() {
        return raceStats.computeBestMeleeFinal() * powerFraction() * statMultiplier * weightFactor;
    }
    public boolean isCombatActive() { return isRaceChosen(); }

    /** PL SIN la penalización de las pesas Y SIN supresión. Lo consumen la capacidad de carga
     *  y el TP.
     *  Sin pesas: si usaran el PL penalizado, ponerte pesas bajaría tu capacidad, lo que
     *  subiría r, lo que bajaría más el PL... bucle.
     *  Sin supresión: llevaba powerFraction dentro, así que bajar el % de Ki Control te
     *  recortaba la capacidad de carga y el TP. Esconder el ki no te hace más débil. */
    public long getPowerLevelRaw() {
        return PowerLevel.compute(
                raceStats.computeMeleeFinal()   * statMultiplier,
                computeConFinal(),
                raceStats.computeDefenseFinal() * statMultiplier,
                raceStats.computeKiPowerFinal() * statMultiplier,
                computeKiPoolFinal());
    }

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

    @Override
    public void consumeEnergy(int amount) { pools.addEnergy(-Math.max(0, amount)); }

    // ── Movimiento ───────────────────────────────────────────────────────────
    public double getSpeedStat()    { return pools.getSpeedStat(); }
    public double getFlySpeedStat() { return pools.getFlySpeedStat(); }
    public double getFlySpeed()     { return pools.getFlySpeed(); }

    public double getFlyMultiplier() {
        return pools.getFlyMultiplier(CommonConfig.flyMultiplierCap(),
                CommonConfig.flyScaling(), statMultiplier);
    }

    public double getMoveMultiplier() {
        return pools.getMoveMultiplier(CommonConfig.speedMultiplierCap(),
                CommonConfig.movementScaling(), statMultiplier);
    }

    // ── Flags ────────────────────────────────────────────────────────────────
    public boolean isFlyEnabled()    { return flags.isFlyEnabled(); }
    public boolean isChargingKi()    { return flags.isChargingKi(); }
    public boolean isOverdriveCharging()   { return flags.isOverdriveCharging(); }
    public boolean hasBrokenOverdriveOnce() { return flags.hasBrokenOverdriveOnce(); }
    public boolean isImmortal()      { return flags.isImmortal(); }
    public boolean isDivine()        { return flags.isDivine(); }
    public boolean isMajin()         { return flags.isMajin(); }
    public boolean isLegendary()     { return flags.isLegendary(); }
    public boolean hasTail()         { return flags.hasTail(); }
    public boolean hasSsj4Ritual()   { return flags.hasSsj4Ritual(); }
    public boolean hasReceivedKaioWeights() { return flags.hasReceivedKaioWeights(); }

    public void setHasTail(boolean v) { flags.setHasTail(v); }
    public void setHasSsj4Ritual(boolean v) { flags.setHasSsj4Ritual(v); }
    public void setReceivedKaioWeights(boolean v) { flags.setReceivedKaioWeights(v); }
    public void setFlyEnabled(boolean v)  { flags.setFlyEnabled(v); }
    public void setChargingKi(boolean v)  { flags.setChargingKi(v); }
    public void setOverdriveCharging(boolean v)    { flags.setOverdriveCharging(v); }
    public void setHasBrokenOverdriveOnce(boolean v) { flags.setHasBrokenOverdriveOnce(v); }
    public void setImmortal(boolean v)    { flags.setImmortal(v); }
    public void setDivine(boolean v)      { flags.setDivine(v); }
    public void setMajin(boolean v)       { flags.setMajin(v); }
    public void setLegendary(boolean v)   { flags.setLegendary(v); }

    public boolean isInOtherworld()        { return flags.isInOtherworld(); }
    public void setInOtherworld(boolean v) { flags.setInOtherworld(v); }
    public long getOtherworldSince()       { return flags.getOtherworldSince(); }
    public void setOtherworldSince(long t) { flags.setOtherworldSince(t); }
    /** Murió en un mundo hardcore. Lo consulta Yemma para negarse a revivirlo; solo las
     *  esferas del dragón lo deshacen. Ver OtherworldManager#markPendingOtherworld. */
    public boolean isHardcoreDeath()        { return flags.isHardcoreDeath(); }
    public void setHardcoreDeath(boolean v) { flags.setHardcoreDeath(v); }

    // ── Ciclo de vida ────────────────────────────────────────────────────────
    public void refillOnRespawn() { pools.refillAll(); }

    // ── Cooldown de invocación (por jugador) ─────────────────────────────────
    /** Long.MIN_VALUE => nunca ha invocado (sin cooldown). */
    public long getLastSummonTick()       { return lastSummonTick; }
    public void setLastSummonTick(long t) { this.lastSummonTick = t; }

    // ── Cooldown de "Descender" (rueda) ──────────────────────────────────────
    public long getLastDescendTick()       { return lastDescendTick; }
    public void setLastDescendTick(long t) { this.lastDescendTick = t; }

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

    /** Reset full: la maestría de técnica se pierde igual que la de forma/kaioken
     *  (PlayerFormAttachment.clearProgression()). No hay equivalente en el respec de stats:
     *  ese solo devuelve TP, no borra progreso de uso. */
    public void clearTechniqueMastery() { techMastery.clear(); }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("race",       raceStats.save());
        tag.put("pools",      pools.save());
        tag.put("flags",      flags.save());
        tag.put("skills",     skills.save());
        tag.put("techniques", techniques.save());
        tag.putLong("lastSummonTick", lastSummonTick);
        tag.putLong("lastDescendTick", lastDescendTick);
        ListTag pets = new ListTag();
        pets.addAll(deadPets);
        tag.put("deadPets", pets);
        tag.putInt("alignment", alignment);
        CompoundTag tm = new CompoundTag();
        for (var e : techMastery.entrySet()) tm.putFloat(e.getKey(), e.getValue());
        tag.put("techMastery", tm);
        tag.putInt("powerPercent", powerPercent);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("race"))       raceStats.load(tag.getCompound("race"));
        if (tag.contains("pools"))      pools.load(tag.getCompound("pools"));
        if (tag.contains("flags"))      flags.load(tag.getCompound("flags"));
        if (tag.contains("skills"))     skills.load(tag.getCompound("skills"));
        if (tag.contains("techniques")) techniques.load(tag.getCompound("techniques"));
        lastSummonTick = tag.contains("lastSummonTick") ? tag.getLong("lastSummonTick") : Long.MIN_VALUE;
        lastDescendTick = tag.contains("lastDescendTick") ? tag.getLong("lastDescendTick") : Long.MIN_VALUE;
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
        powerPercent = tag.contains("powerPercent") ? Math.max(0, tag.getInt("powerPercent")) : 50;
        // Recalc por si los atributos cambiaron al cargar
        applyRecalc();
    }

    public int getPowerPercent() { return powerPercent; }

    public double powerFraction() { return powerPercent / 100.0; }

    /** Clampa a [0, techo por skill]. Devuelve true si cambió. */
    public boolean setPowerPercent(int pct, int maxAllowed) {
        int clamped = Math.max(0, Math.min(maxAllowed, pct));
        if (clamped == powerPercent) return false;
        powerPercent = clamped;
        return true;
    }

    /**
     * Atributo con el boost de forma aplicado. SOLO para mostrar y para cálculos de combate.s
     * Para costes de TP, requisitos de MND y el cap se usa getAttribute() (el crudo): si no,
     * transformarse te desbloquearía habilidades y abarataría subidas.
     */
    public int getEffectiveAttribute(ZenkaiAttributes a) {
        int raw = raceStats.getAttribute(a);
        return switch (a) {
            case STRENGTH, DEXTERITY, WILLPOWER -> (int) Math.round(raw * statMultiplier * weightFactor);
            default -> raw; // CON y SPI no escalan con la forma; MIND tampoco
        };
    }

    /** MIND ocupada por habilidades Y técnicas. Ver MindBudget: es el único sitio donde se
     *  suman los tres orígenes. */
    public int mindUsed() { return MindBudget.used(this); }

    /** MIND libre. Puede salir NEGATIVA y no se corrige a propósito — ver MindBudget#free. */
    public int mindFree() { return MindBudget.free(this); }

    /** PL REAL. No lo baja esconder el ki. */
    @Override
    public long getPowerLevel() {
        return PowerLevel.compute(meleeUnsuppressed(), computeConFinal(), defenseUnsuppressed(),
                kiPowerUnsuppressed(), computeKiPoolFinal());
    }

    /** PL APARENTE: el real por el % de Ki Control, con suelo. Es lo único que ven los demás. */
    @Override
    public long getApparentPowerLevel() {
        return PowerLevel.suppress(getPowerLevel(), powerFraction());
    }

    /**
     * PL LIBERABLE: lo máximo que este jugador puede sacar HOY, o sea el crudo por el techo
     * de Ki Control. No es ninguno de los otros tres:
     *   - getPowerLevelRaw()      = potencial total, aunque no puedas usarlo
     *   - getApparentPowerLevel() = lo que estás mostrando ahora mismo (el slider)
     *   - este                    = tu tope real
     * Lo consumen los maestros y los logros de PL. Con el crudo, un jugador con 1600 de
     * potencial y Ki Control al 50% desbloqueaba el logro de 1000 teniendo 800 usables.
     * Sin pesas, por la misma razón que getPowerLevelRaw: entrenar con lastre no debe
     * alejarte de los hitos.
     */
    public long getReleasablePowerLevel() {
        int cap = Math.min(100, SkillEffects.maxPowerPercent(this));
        return Math.round(getPowerLevelRaw() * (cap / 100.0));
    }
}