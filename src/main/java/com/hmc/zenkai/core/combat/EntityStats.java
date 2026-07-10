package com.hmc.zenkai.core.combat;

import com.hmc.zenkai.core.network.feature.Dbrattributes;
import com.hmc.zenkai.util.MathUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;

/**
 * Stats de combate RESUELTOS de una entidad (runtime). Implementa el mismo contrato que el
 * jugador, así que el pipeline los trata igual. Se resuelve desde un {@link EntityStatDef}:
 * PL + arquetipo -> atributos (back-solve) -> overrides -> pools.
 *
 * Para entidades el stat efectivo = atributo × 1 (la "forma" del arquetipo ya define la
 * personalidad); los multiplicadores de body/ki afinan cuánto aguanta/pega por encima del PL.
 *
 * El body es la VIDA REAL (esquiva el cap de MC). Serializable: una entidad herida conserva su
 * body tras guardar/recargar (los máximos se recalculan de atributos+mults).
 */
public final class EntityStats implements ZenkaiCombatStats {

    /** Recompensa de TP "auto" = PL × esto (placeholder tuneable; el "mundo TP" real va después). */
    private static final double TP_PER_PL = 0.05;

    private final EnumMap<Dbrattributes, Integer> attr = new EnumMap<>(Dbrattributes.class);
    private double bodyMult = 1.0;
    private double kiMult   = 1.0;

    private int body,    bodyMax;
    private int stamina, staminaMax;
    private int energy,  energyMax;
    private int tpReward = 0;
    private boolean initialized = false;

    /** Constructor por defecto (attachment sin poblar). isCombatActive()=false hasta applyDef/load. */
    public EntityStats() {
        for (Dbrattributes a : Dbrattributes.values()) attr.put(a, 0);
        recalc();
    }

    /** Resuelve los stats desde el plano JSON (spawn). */
    public void applyDef(EntityStatDef def) {
        Archetype arch = Archetype.get(def.archetype());
        EnumMap<Dbrattributes, Integer> solved = PowerLevel.solveAttributes(def.powerLevel(), arch);

        // Overrides de atributos (absolutos o en %).
        for (var e : def.attributeOverrides().entrySet()) {
            EntityStatDef.AttrOverride ov = e.getValue();
            int base = solved.getOrDefault(e.getKey(), 0);
            int val  = ov.percent()
                    ? (int) Math.round(base * (1.0 + ov.value() / 100.0))
                    : (int) Math.round(ov.value());
            solved.put(e.getKey(), Math.max(0, val));
        }

        attr.clear();
        attr.putAll(solved);
        bodyMult = arch.bodyMult() * def.bodyMultOverride();
        kiMult   = arch.kiMult()   * def.kiMultOverride();

        recalc();
        body = bodyMax; stamina = staminaMax; energy = energyMax;
        tpReward = resolveReward(def.rewardTp(), getPowerLevel());
        initialized = true;
    }

    private static int resolveReward(String raw, long pl) {
        int auto = (int) Math.max(1, Math.round(pl * TP_PER_PL));
        if (raw == null || raw.equalsIgnoreCase("auto")) return auto;
        try { return Math.max(0, Integer.parseInt(raw.trim())); }
        catch (Exception ex) { return auto; }
    }

    // ── Pools: MISMAS fórmulas y escalas de config que el jugador (simetría del pipeline),
    //    con los multiplicadores del arquetipo encima (body/ki). ──
    private void recalc() {
        double con = getAttr(Dbrattributes.CONSTITUTION);
        double spi = getAttr(Dbrattributes.SPIRIT);
        this.bodyMax    = (int) Math.max(1, Math.round(10 + con * bodyMult * com.hmc.zenkai.core.config.StatsConfig.bodyScale()));
        this.staminaMax = (int) Math.max(1, Math.round(90 + con * com.hmc.zenkai.core.config.StatsConfig.staminaScale()));
        this.energyMax  = (int) Math.max(1, Math.round(90 + spi * kiMult * com.hmc.zenkai.core.config.StatsConfig.energyScale()));
    }

    public int getAttr(Dbrattributes a) { return attr.getOrDefault(a, 0); }
    public boolean isInitialized()       { return initialized; }
    public int getTpReward()             { return tpReward; }

    // ── ZenkaiCombatStats ─────────────────────────────────────────────────────
    @Override public boolean isCombatActive()     { return initialized; }
    @Override public double computeMeleeFinal()   { return getAttr(Dbrattributes.STRENGTH); }
    @Override public double computeDefenseFinal() { return getAttr(Dbrattributes.DEXTERITY); }
    @Override public double computeKiPowerFinal() { return getAttr(Dbrattributes.WILLPOWER); }
    @Override public double computeKiPoolFinal()  { return getAttr(Dbrattributes.SPIRIT); }
    @Override public double computeConFinal()     { return getAttr(Dbrattributes.CONSTITUTION); }

    @Override public int  getBody()          { return body; }
    @Override public int  getBodyMax()       { return bodyMax; }
    @Override public void addBody(int delta) { body = MathUtil.clamp(body + delta, 0, bodyMax); }

    @Override public int  getStamina()          { return stamina; }
    @Override public int  getStaminaMax()       { return staminaMax; }
    @Override public void consumeStamina(int a) { stamina = MathUtil.clamp(stamina - a, 0, staminaMax); }
    @Override public int  getEnergy()           { return energy; }
    @Override public int  getEnergyMax()        { return energyMax; }

    // ── NBT ────────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("init", initialized);
        CompoundTag a = new CompoundTag();
        for (var e : attr.entrySet()) a.putInt(e.getKey().name(), e.getValue());
        t.put("attr", a);
        t.putDouble("bodyMult", bodyMult);
        t.putDouble("kiMult",   kiMult);
        t.putInt("body",    body);
        t.putInt("stamina", stamina);
        t.putInt("energy",  energy);
        t.putInt("tpReward", tpReward);
        return t;
    }

    public void load(CompoundTag t) {
        this.initialized = t.getBoolean("init");
        CompoundTag a = t.getCompound("attr");
        for (Dbrattributes x : Dbrattributes.values()) attr.put(x, a.getInt(x.name()));
        this.bodyMult = t.contains("bodyMult") ? t.getDouble("bodyMult") : 1.0;
        this.kiMult   = t.contains("kiMult")   ? t.getDouble("kiMult")   : 1.0;
        this.tpReward = t.getInt("tpReward");
        recalc();
        this.body    = MathUtil.clamp(t.getInt("body"),    0, bodyMax);
        this.stamina = MathUtil.clamp(t.getInt("stamina"), 0, staminaMax);
        this.energy  = MathUtil.clamp(t.getInt("energy"),  0, energyMax);
    }
}