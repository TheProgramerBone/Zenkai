package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.combat.PowerLevel;
import com.hmc.zenkai.feature.combat.ZenkaiCombatStats;
import com.hmc.zenkai.registry.ModTags;
import com.hmc.zenkai.util.MathUtil;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.EnumMap;

/**
 * Stats de combate RESUELTOS de una entidad (runtime). Implementa el mismo contrato que el
 * jugador, así que el pipeline los trata igual. Se resuelve desde un {@link EntityStatDef}:
 * PL + arquetipo -> atributos (back-solve) -> overrides -> pools.
 * Para entidades el stat efectivo = atributo × 1 (la "forma" del arquetipo ya define la
 * personalidad); los multiplicadores de body/ki afinan cuánto aguanta/pega por encima del PL.
 * El body es la VIDA REAL (esquiva el cap de MC). Serializable: una entidad herida conserva su
 * body tras guardar/recargar (los máximos se recalculan de atributos+mults).
 */
public final class EntityStats implements ZenkaiCombatStats {

    /** Recompensa de TP "auto" = PL × esto (placeholder tuneable; el "mundo TP" real va después). */
    private static final double TP_PER_PL = 0.05;

    private final EnumMap<ZenkaiAttributes, Integer> attr = new EnumMap<>(ZenkaiAttributes.class);
    private double bodyMult = 1.0;
    private double kiMult   = 1.0;

    private int body,    bodyMax;
    private int stamina, staminaMax;
    private int energy,  energyMax;
    private int tpReward = 0;
    private boolean initialized = false;

    /** Constructor por defecto (attachment sin poblar). isCombatActive()=false hasta applyDef/load. */
    public EntityStats() {
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) attr.put(a, 0);
        recalc();
    }

    /** Resuelve los stats desde el plano JSON (spawn). */
    public void applyDef(EntityStatDef def) {
        EntityArchetype arch = EntityArchetype.get(def.archetype());
        EnumMap<ZenkaiAttributes, Integer> solved = PowerLevel.solveAttributes(def.powerLevel(), arch);

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
        double con = getAttr(ZenkaiAttributes.CONSTITUTION);
        double spi = getAttr(ZenkaiAttributes.SPIRIT);
        this.bodyMax    = (int) Math.max(1, Math.round(10 + con * bodyMult * CommonConfig.bodyScale()));
        this.staminaMax = (int) Math.max(1, Math.round(90 + con * CommonConfig.staminaScale()));
        this.energyMax  = (int) Math.max(1, Math.round(90 + spi * kiMult * CommonConfig.energyScale()));
    }

    public int getAttr(ZenkaiAttributes a) { return attr.getOrDefault(a, 0); }
    public boolean isInitialized()       { return initialized; }
    public int getTpReward()             { return tpReward; }

    // ── ZenkaiCombatStats ─────────────────────────────────────────────────────
    @Override public boolean isCombatActive()     { return initialized; }
    @Override public double computeMeleeFinal()   { return getAttr(ZenkaiAttributes.STRENGTH); }
    @Override public double computeDefenseFinal() { return getAttr(ZenkaiAttributes.DEXTERITY); }
    @Override public double computeKiPowerFinal() { return getAttr(ZenkaiAttributes.WILLPOWER); }
    @Override public double computeKiPoolFinal()  { return getAttr(ZenkaiAttributes.SPIRIT); }
    @Override public double computeConFinal()     { return getAttr(ZenkaiAttributes.CONSTITUTION); }

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
        for (ZenkaiAttributes x : ZenkaiAttributes.values()) attr.put(x, a.getInt(x.name()));
        this.bodyMult = t.contains("bodyMult") ? t.getDouble("bodyMult") : 1.0;
        this.kiMult   = t.contains("kiMult")   ? t.getDouble("kiMult")   : 1.0;
        this.tpReward = t.getInt("tpReward");
        recalc();
        this.body    = MathUtil.clamp(t.getInt("body"),    0, bodyMax);
        this.stamina = MathUtil.clamp(t.getInt("stamina"), 0, staminaMax);
        this.energy  = MathUtil.clamp(t.getInt("energy"),  0, energyMax);
    }

    /**
     * Fallback para mobs SIN JSON: traduce sus atributos vanilla a la escala del mod.
     * Sin esto, cualquier mob no listado queda fuera del pipeline y el PvE se vuelve
     * irrelevante (un jugador nuevo pega 113 efectivos contra 20 de vida).
     * Vida y daño usan factores DISTINTOS a propósito: con el mismo, las explosiones y el
     * warden pasaban a matar de un golpe.
     */
    public void applyVanilla(LivingEntity le) {
        double f     = categoryFactor(le);
        double hp    = le.getMaxHealth();
        double atk   = attrOr(le, Attributes.ATTACK_DAMAGE, 1.0);
        double armor = attrOr(le, Attributes.ARMOR, 0.0);
        double dmgF  = f * CommonConfig.vanillaDamageRatio();

        attr.clear();
        attr.put(ZenkaiAttributes.CONSTITUTION, (int) Math.max(1, Math.round(hp * f)));
        attr.put(ZenkaiAttributes.STRENGTH,     (int) Math.max(0, Math.round(atk * dmgF)));
        attr.put(ZenkaiAttributes.DEXTERITY,    (int) Math.max(0, Math.round(armor * dmgF)));
        attr.put(ZenkaiAttributes.WILLPOWER, 0);
        attr.put(ZenkaiAttributes.SPIRIT, 0);
        attr.put(ZenkaiAttributes.MIND, 0);

        bodyMult = 1.0;
        kiMult   = 1.0;
        recalc();
        body = bodyMax; stamina = staminaMax; energy = energyMax;
        tpReward = (int) Math.max(0, Math.round(getPowerLevel() * TP_PER_PL
                * CommonConfig.vanillaTpRewardFactor()));
        initialized = true;
    }

    /**
     * Un aldeano y un warden no pueden compartir factor: con uno global, el aldeano salía
     * con PL 330 (en DBZ un humano corriente marca 5). La categoría lo resuelve sin listar
     * mobs uno a uno, y zenkai_entities/*.json sigue mandando por encima de esto.
     */
    private static double categoryFactor(LivingEntity le) {
        if (le.getType().is(ModTags.EntityTypes.BOSSES)) return CommonConfig.vanillaBossFactor();
        return le.getType().getCategory() == MobCategory.MONSTER
                ? CommonConfig.vanillaHostileFactor()
                : CommonConfig.vanillaPassiveFactor();
    }

    private static double attrOr(LivingEntity le, Holder<Attribute> a, double fallback) {
        var inst = le.getAttribute(a);   // ATTACK_DAMAGE no existe en pasivos
        return inst == null ? fallback : inst.getValue();
    }

    /**
     * Refleja el pool del mod en la vida vanilla, conservando el RATIO. Sin esto, las
     * entidades con lógica propia sobre getHealth() (dragón, wither, warden) no morían
     * nunca: su barra se quedaba llena mientras el body bajaba en paralelo.
     * No baja de 1: matarlas es cosa del pipeline de daño, no de este espejo.
     */
    public void mirrorToVanilla(LivingEntity le) {
        if (bodyMax <= 0) return;
        float target = le.getMaxHealth() * (body / (float) bodyMax);
        le.setHealth(Math.max(1.0F, Math.min(le.getMaxHealth(), target)));
    }
}