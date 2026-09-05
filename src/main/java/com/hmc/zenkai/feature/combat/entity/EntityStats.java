package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.config.ServerConfig;
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
    public void applyDef(EntityStatDef def, LivingEntity le) {
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

        // El pool arranca EN PROPORCIÓN a la vida vanilla actual, no siempre lleno. En un spawn
        // normal la entidad viene entera y esto da body = bodyMax, igual que antes. Donde
        // importa es en las entidades que YA estaban en el mundo y entran aquí sin inicializar:
        // mundo anterior al mod, tipo que no tenía JSON hasta ahora, o el fallback recién
        // encendido. Con `body = bodyMax` a secas, un mob que dejaste herido estrenaba pool
        // completo con la barra a la mitad.
        float maxHp = le.getMaxHealth();
        float hpRatio = maxHp > 0f ? Math.min(1.0f, le.getHealth() / maxHp) : 1.0f;
        body    = Math.max(1, Math.round(bodyMax * hpRatio));
        stamina = staminaMax;
        energy  = energyMax;

        tpReward = resolveReward(def.rewardTp(), getPowerLevel());
        initialized = true;

        // Y el espejo en la otra dirección, para que vida y pool arranquen de acuerdo aunque el
        // redondeo del ratio los haya separado un punto. Aquí es donde `le` deja de ser un
        // parámetro sin usar: hasta ahora se pasaba y no se tocaba.
        mirrorToVanilla(le);
    }

    private static int resolveReward(String raw, long pl) {
        int auto = (int) Math.max(1, Math.round(pl * ServerConfig.tpPerPl()));
        if (raw == null || raw.equalsIgnoreCase("auto")) return auto;
        try { return Math.max(0, Integer.parseInt(raw.trim())); }
        catch (Exception ex) { return auto; }
    }

    // ── Pools: MISMAS fórmulas y escalas de config que el jugador (simetría del pipeline),
    //    con los multiplicadores del arquetipo encima (body/ki). ──
    private void recalc() {
        double con = getAttr(ZenkaiAttributes.CONSTITUTION);
        double spi = getAttr(ZenkaiAttributes.SPIRIT);
        this.bodyMax    = (int) Math.max(1, Math.round(10 + con * bodyMult * ServerConfig.bodyScale()));
        this.staminaMax = (int) Math.max(1, Math.round(90 + con * ServerConfig.staminaScale()));
        this.energyMax  = (int) Math.max(1, Math.round(90 + spi * kiMult * ServerConfig.energyScale()));
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
        double dmgF  = f * ServerConfig.vanillaDamageRatio();

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
        // Mismo criterio que applyDef: el pool arranca EN PROPORCIÓN a la vida vanilla actual,
        // no siempre lleno. En un spawn normal la entidad viene entera y esto da body = bodyMax.
        // Donde importa es en las entidades que YA estaban en el mundo y entran aquí sin
        // inicializar: mundo anterior al mod, tipo que no tenía JSON hasta ahora, o el fallback
        // recién encendido. Con `body = bodyMax` a secas, un zombi que dejaste a dos corazones
        // estrenaba pool COMPLETO con la barra a dos corazones.
        float maxHp = (float) hp;
        float hpRatio = maxHp > 0f ? Math.min(1.0f, le.getHealth() / maxHp) : 1.0f;
        body    = Math.max(1, Math.round(bodyMax * hpRatio));
        stamina = staminaMax;
        energy  = energyMax;

        tpReward = (int) Math.max(0, Math.round(getPowerLevel() * ServerConfig.tpPerPl()
                * ServerConfig.vanillaTpRewardFactor()));
        initialized = true;
        mirrorToVanilla(le);
    }

    /**
     * Un aldeano y un warden no pueden compartir factor: con uno global, el aldeano salía
     * con PL 330 (en DBZ un humano corriente marca 5). La categoría lo resuelve sin listar
     * mobs uno a uno, y zenkai_entities/*.json sigue mandando por encima de esto.
     */
    private static double categoryFactor(LivingEntity le) {
        if (le.getType().is(ModTags.EntityTypes.BOSSES)) return ServerConfig.vanillaBossFactor();
        return le.getType().getCategory() == MobCategory.MONSTER
                ? ServerConfig.vanillaHostileFactor()
                : ServerConfig.vanillaPassiveFactor();
    }

    private static double attrOr(LivingEntity le, Holder<Attribute> a, double fallback) {
        var inst = le.getAttribute(a);   // ATTACK_DAMAGE no existe en pasivos
        return inst == null ? fallback : inst.getValue();
    }

// ── ESPEJO A LA VIDA VANILLA ──────────────────────────────────────────────

    /** Vida vanilla mínima mientras quede body. Solo tiene que ser > 0. */
    private static final float MIN_MIRROR_HEALTH = 0.01f;

    /**
     * Última vida que escribimos NOSOTROS y el maxHealth con el que se calculó. Con estos dos
     * reconcile() distingue "esta vida la puso el espejo" de "esta vida la ha tocado otro".
     * No van a NBT a propósito: al cargar valen NaN y el primer reconcile los resiembra.
     */
    private float lastMirrored  = Float.NaN;
    private float lastMaxHealth = Float.NaN;

    /**
     * Proyecta el body sobre la vida vanilla conservando el RATIO. Lo que lea
     * getHealth()/getMaxHealth() (barra del Wither, del dragón, corazones de montura, HUDs de
     * terceros) queda correcto sin tocar nada suyo.
     * EL SUELO YA NO ES 1.0. Con 1.0, un mob de 20 de vida no podía enseñar menos del 5% de
     * barra, y reconcile leía ese 5% como curación externa y le devolvía body: los mobs
     * pequeños se curaban solos al borde de la muerte. Basta con que sea > 0 para que
     * isDeadOrDying() siga siendo false; matar sigue siendo cosa del pipeline de daño.
     */
    public void mirrorToVanilla(LivingEntity le) {
        if (bodyMax <= 0) return;
        float maxHp = le.getMaxHealth();
        if (maxHp <= 0f) return;

        float target = maxHp * (body / (float) bodyMax);
        le.setHealth(Math.max(MIN_MIRROR_HEALTH, Math.min(maxHp, target)));

        lastMirrored  = le.getHealth();   // se relee: setHealth clampa por su cuenta
        lastMaxHealth = maxHp;
    }

    /**
     * Reconciliación por tick. Traduce a body las escrituras de vida que NO hemos hecho
     * nosotros (cristales del End, /data, otro mod) y vuelve a proyectar.
     * NO SE RECONCILIA A UN MUERTO. En el tick en el que la entidad muere, su vida vanilla es 0
     * y lastMirrored todavía es la de antes del golpe: el delta se leería como una escritura
     * externa enorme, el suelo de 1 punto devolvería el pool a 1 y la proyección le pondría
     * vida otra vez. Y eso no es solo un parpadeo — die() ya ha corrido, pero al volver
     * isDeadOrDying() a false, baseTick deja de llamar a tickDeath() y la entidad no se elimina
     * NUNCA. Es exactamente el bug de "el mob no muere".
     * Las otras dos reglas siguen igual: umbral RELATIVO al maxHealth (el ruido de coma
     * flotante no cuenta como curación) y un delta que redondea a 0 puntos de body se descarta
     * en vez de forzarse a ±1.
     */
    public void reconcile(LivingEntity le) {
        if (!initialized || bodyMax <= 0) return;
        if (body <= 0 || le.isDeadOrDying() || le.isRemoved()) return;

        float maxHp = le.getMaxHealth();
        if (maxHp <= 0f) return;

        // Primera pasada tras cargar del disco, o alguien ha cambiado MAX_HEALTH: no hay delta
        // que interpretar, solo se resiembra la proyección.
        if (Float.isNaN(lastMirrored) || lastMaxHealth != maxHp) {
            mirrorToVanilla(le);
            return;
        }

        float delta = le.getHealth() - lastMirrored;
        float minDelta = Math.max(0.05f, maxHp * 0.005f);
        if (Math.abs(delta) < minDelta) return;

        int bodyDelta = (int) Math.round(delta * (bodyMax / maxHp));
        if (bodyDelta == 0) return;

        // MATAR NO ES COSA DE AQUÍ: una escritura externa puede vaciar el pool pero no rematar.
        // El suelo es 1 y el pipeline de daño se encarga del golpe final.
        body = MathUtil.clamp(body + bodyDelta, 1, bodyMax);
        mirrorToVanilla(le);
    }
}