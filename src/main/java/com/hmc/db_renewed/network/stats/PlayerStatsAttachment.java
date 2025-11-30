package com.hmc.db_renewed.network.stats;

import com.hmc.db_renewed.config.StatsConfig;
import com.hmc.db_renewed.network.ki.KiAttackDefinition;
import com.hmc.db_renewed.network.ki.KiAttackType;
import com.hmc.db_renewed.util.BalanceUtil;
import com.hmc.db_renewed.util.MathUtil;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;


public class PlayerStatsAttachment {
    // Campos principales
    private Race race = Race.HUMAN;
    private Style style = Style.MARTIAL_ARTIST;

    private boolean raceChosen = false;

    private int tp = 0; // puntos libres
    private final EnumMap<Dbrattributes, Integer> attributes = new EnumMap<>(Dbrattributes.class);
    private final EnumMap<Dbrattributes, Integer> invested = new EnumMap<>(Dbrattributes.class); // TP invertido por atributo

    // Derivados / pools actuales
    private int body;        // HP interna opcional (placeholder)
    private int bodyMax;
    private int stamina;
    private int staminaMax;
    private int energy;      // KI actual
    private int energyMax;   // KI máximo
    private double speed;    // stat lineal
    private double flySpeed; // stat lineal
    private boolean flyEnabled;
    private boolean chargingKi;

    // === Sistema de Ki Attacks (mínimo necesario) ===
    private final Map<String, KiAttackDefinition> kiAttacks = new HashMap<>(); // id -> definición
    private String selectedKiAttackId = "";                                     // ataque seleccionado
    private int auraColorRgb = 0x33CCFF;                                        // color de aura/por defecto

    // Temp overrides (simple)
    private final Map<String, TempStat> tempStats = new HashMap<>();

    public PlayerStatsAttachment() {
        for (Dbrattributes a : Dbrattributes.values()) {
            attributes.put(a, 0);
            invested.put(a, 0);
        }
        applyRaceStyle(); // setea bases por raza
        recalcAll();      // deriva pools
        this.body = this.bodyMax;
        this.stamina = this.staminaMax;
        this.energy = this.energyMax;

        // Semilla de un ataque básico por si el jugador aún no crea uno
        if (!kiAttacks.containsKey("basic_blast")) {
            kiAttacks.put("basic_blast", new KiAttackDefinition(
                    "basic_blast", "Basic Blast", KiAttackType.BLAST,
                    4.0,    // basePower
                    1.0,    // speed (blocks/tick)
                    20,     // cooldownTicks
                    20,     // chargeTimeTicks (llega al 100% en 1s aprox)
                    1,      // density
                    0x33CCFF // color inicial
            ));
            selectedKiAttackId = "basic_blast";
        }
    }

    // === Acceso estático seguro ===
    public static PlayerStatsAttachment get(Player p) {
        // Usa el AttachmentType<...> registrado; la mayoría de implementaciones exponen .get()
        return p.getData(DataAttachments.PLAYER_STATS.get());
    }

    // === Raza/estilo y multiplicadores ===
    public void setRace(Race r) {
        this.race = r;
        this.raceChosen = true;
        applyRaceStyle();
        recalcAll();
    }

    public void setStyle(Style s) {
        this.style = s;
        recalcAll();
    }

    public Race getRace() { return race; }
    public Style getStyle() { return style; }

    public void applyRaceStyle() {
        switch (race) {
            case HUMAN -> BalanceUtil.setBase(attributes, 10,10,10,10,10,10);
            case SAIYAN -> BalanceUtil.setBase(attributes, 14,10,12,8,6,10);
            case NAMEKIAN -> BalanceUtil.setBase(attributes, 8,8,10,11,13,10);
            case ARCOSIAN -> BalanceUtil.setBase(attributes, 8,8,10,12,12,10);
            case MAJIN -> BalanceUtil.setBase(attributes, 10,8,14,8,10,10);
        }
        capAll();
    }

    private void capAll() {
        int cap = StatsConfig.globalAttributeCap();
        for (Map.Entry<Dbrattributes,Integer> e : attributes.entrySet()) {
            e.setValue(Math.min(e.getValue(), cap));
        }
    }

    // === Recalcular derivados ===
    public void recalcAll() {
        // Multiplicadores por raza
        double mSTR = switch (race) {
            case HUMAN -> 1.0; case SAIYAN -> 1.3; case NAMEKIAN -> 0.8; case ARCOSIAN -> 0.9; case MAJIN -> 0.9;
        };
        double mCON = switch (race) {
            case HUMAN -> 1.0; case SAIYAN -> 1.0; case NAMEKIAN -> 0.9; case ARCOSIAN -> 0.9; case MAJIN -> 1.3;
        };
        double mDEX = switch (race) {
            case HUMAN -> 1.0; case SAIYAN -> 1.2; case NAMEKIAN -> 0.9; case ARCOSIAN -> 1.0; case MAJIN -> 0.9;
        };
        double mWIL = switch (race) {
            case HUMAN -> 1.0; case SAIYAN -> 0.8; case NAMEKIAN -> 1.1; case ARCOSIAN -> 1.2; case MAJIN -> 1.1;
        };
        double mSPI = switch (race) {
            case HUMAN -> 1.0; case SAIYAN -> 0.7; case NAMEKIAN -> 1.3; case ARCOSIAN -> 1.1; case MAJIN -> 0.8;
        };
        double mMIND = 1.0;

        // Multiplicadores por estilo
        double sSTR = switch (style) {
            case WARRIOR -> 1.2; case MARTIAL_ARTIST -> 1.1; case SPIRITUALIST -> 0.9;
        };
        double sCON = switch (style) {
            case WARRIOR -> 1.1; case MARTIAL_ARTIST -> 1.0; case SPIRITUALIST -> 0.9;
        };
        double sDEX = switch (style) {
            case WARRIOR -> 1.3; case MARTIAL_ARTIST -> 1.0; case SPIRITUALIST -> 0.9;
        };
        double sWIL = switch (style) {
            case WARRIOR -> 0.8; case MARTIAL_ARTIST -> 1.1; case SPIRITUALIST -> 1.3;
        };
        double sSPI = switch (style) {
            case WARRIOR -> 0.8; case MARTIAL_ARTIST -> 1.0; case SPIRITUALIST -> 1.2;
        };
        double sMIND = 1.0;

        // Estadística = Atributo × MultRaza × MultEstilo
        double STR = attributes.get(Dbrattributes.STRENGTH) * mSTR * sSTR;
        double CON = attributes.get(Dbrattributes.CONSTITUTION) * mCON * sCON;
        double DEX = attributes.get(Dbrattributes.DEXTERITY) * mDEX * sDEX;
        double WIL = attributes.get(Dbrattributes.WILLPOWER) * mWIL * sWIL;
        double SPI = attributes.get(Dbrattributes.SPIRIT) * mSPI * sSPI;
        double MND = attributes.get(Dbrattributes.MIND) * mMIND * sMIND;

        // Mapeos lineales a derivados
        // (dejamos comentarios para claridad por si los usas luego)
        double melee = STR;              // Melee ← STRENGTH
        double defense = DEX;            // Defense ← DEXTERITY
        double bodyStat = CON;           // Body ← CON
        double staminaStat = CON;        // Stamina ← CON
        double kiPower = WIL;            // KiPower ← WILLPOWER
        double kiPool = SPI;             // KiPool ← SPIRIT
        double speedStat = DEX;          // Speed ← DEXTERITY
        double regenBody = CON;          // RegenRateBody ← CON
        double regenStamina = CON;       // RegenRateStamina ← CON
        double regenEnergy = SPI;        // RegenRateEnergy ← SPIRIT
        double flyStat = DEX;            // FlySpeed ← DEXTERITY

        // Pools máximos
        this.bodyMax = (int)Math.max(1, Math.round(100 + bodyStat));
        this.staminaMax = (int)Math.max(1, Math.round(100 + staminaStat));
        this.energyMax  = (int)Math.max(1, Math.round(100 + kiPool));

        // Stats de movimiento (lineales)
        this.speed = speedStat;
        this.flySpeed = flyStat;

        // Clamps de pools actuales
        this.body = Math.min(this.body, this.bodyMax);
        this.stamina = Math.min(this.stamina, this.staminaMax);
        this.energy = Math.min(this.energy, this.energyMax);

        // Limpieza temp stats
        tempStats.values().removeIf(TempStat::expired);
    }

    // === TP / progresión ===
    public boolean spendTP(Dbrattributes attr, int points) {
        if (points <= 0) return false;
        int have = this.tp;
        int inv = this.invested.get(attr);
        double coeff = StatsConfig.tpCoefficient();

        int totalCost = 0;
        for (int k=0;k<points;k++) totalCost += (int)Math.ceil(1 + (inv + k) * coeff);
        if (have < totalCost) return false;

        int cap = StatsConfig.globalAttributeCap();
        int cur = attributes.get(attr);
        int add = Math.min(points, cap - cur);
        if (add <= 0) return false;

        attributes.put(attr, cur + add);
        invested.put(attr, inv + add);
        this.tp = have - totalCost;
        recalcAll();
        return true;
    }

    public void addTP(int amount) { this.tp = Math.max(0, this.tp + amount); }
    public int getTP() { return tp; }

    public void setAttribute(Dbrattributes a, int v) {
        attributes.put(a, MathUtil.clamp(v, 0, StatsConfig.globalAttributeCap()));
        recalcAll();
    }
    public int getAttribute(Dbrattributes a) { return attributes.getOrDefault(a, 0); }

    public void respec() {
        int refund = invested.values().stream().mapToInt(i -> i).sum();
        this.tp += refund;
        invested.replaceAll((k,v)->0);
        applyRaceStyle();
        recalcAll();
    }

    // === Daño / defensa / consumo stamina ===
    public double getMeleeBonus() {
        return attributes.get(Dbrattributes.STRENGTH);
    }
    public double computeMeleeFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.STRENGTH), race, style, Dbrattributes.STRENGTH);
    }
    public double computeDefenseFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.DEXTERITY), race, style, Dbrattributes.DEXTERITY);
    }
    public double computeSpeedFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.DEXTERITY), race, style, Dbrattributes.DEXTERITY);
    }
    public double computeFlyFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.DEXTERITY), race, style, Dbrattributes.DEXTERITY);
    }


    /** Ki Power final (útil para escalar daño/costo de ataques). */
    public double computeKiPowerFinal() {
        return BalanceUtil.computeStat(
                attributes.get(Dbrattributes.WILLPOWER),
                race,
                style,
                Dbrattributes.WILLPOWER
        );
    }

    public double computeKiPoolFinal() {
        return BalanceUtil.computeStat(
                attributes.get(Dbrattributes.SPIRIT),
                race,
                style,
                Dbrattributes.SPIRIT
        );
    }

    /**
     * Calcula el coste de KI de un ataque dado su definición y el ratio de carga.
     *
     * @param def          Definición del ataque (basePower, chargeTime, etc.)
     * @param chargeRatio  0.0 = sin cargar, 1.0 = carga al 100%, 2.0 = overcharge al 200%
     * @return             Coste de KI en puntos (entero, ya redondeado)
     */

    public int computeKiAttackCost(KiAttackDefinition def, double chargeRatio) {
        if (def == null) return 1;

        // Aseguramos 0.0–2.0
        double r = Math.max(0.0, Math.min(2.0, chargeRatio));

        // KiPowerFinal = escala con WILLPOWER (puede ser grande)
        double kiPower = computeKiPowerFinal(); // ya usa BalanceUtil

        // Coste base según el ataque (muy bajo)
        double base = 3.0 + def.basePower() * 0.3;   // blast más fuerte => un poquito más caro

        // Penalización SUAVE por kiPower: usamos raíz para no explotar
        double byPower = Math.sqrt(Math.max(0.0, kiPower)) * 0.5; // si kiPower=100 → ~5

        // La carga (0–2) incrementa cost pero suave: 0→0.5, 1→1.25, 2→2.0
        double byCharge = 0.5 + 0.75 * r;

        double cost = base + byPower * byCharge;

        // Coste siempre entre 1 y 80 (ajusta si quieres)
        return MathUtil.clamp((int) Math.round(cost), 1, 80);
    }

    /**
     * Ejemplo de cálculo de daño escalado con atributos y carga.
     * Si ya tienes tu propia fórmula de daño en la entidad, puedes ignorar esto.
     */
    public float computeKiAttackDamage(KiAttackDefinition def, double chargeRatio) {
        if (def == null) return 0f;

        double r = Math.max(0.0, Math.min(2.0, chargeRatio)); // 0.0–2.0
        double basePower = def.basePower();

        double kiPower = computeKiPowerFinal();
        // Escala suave con WILLPOWER
        double scale = 1.0 + kiPower / 200.0; // si KiPower=100 → x1.5

        // daño crece con la carga (0→0.5x, 1→2.0x, 2→3.5x) y con el kiPower
        double dmg = basePower * (0.5 + 1.5 * r) * scale;

        return (float) Math.max(0.5, dmg);
    }

    // Stamina / Energy manipulación
    public int getStamina() { return stamina; }
    public int getStaminaMax() { return staminaMax; }
    public void addStamina(int delta) { stamina = MathUtil.clamp(stamina + delta, 0, staminaMax); }
    public void consumeStamina(int amount) {
        if (amount <= 0 || stamina <= 0) return;
        int use = Math.min(amount, stamina);
        stamina -= use;
    }

    public int getEnergy() { return energy; }
    public int getEnergyMax() { return energyMax; }
    public void addEnergy(int delta) { energy = MathUtil.clamp(energy + delta, 0, energyMax); }

    public int getBody() { return body; }
    public int getBodyMax() { return bodyMax; }
    public void addBody(int delta) { body = MathUtil.clamp(body + delta, 0, bodyMax); }

    public double getSpeedStat() { return speed; }
    public double getFlySpeedStat() { return flySpeed; }

    // === Temp stats (simple) ===
    public void setTempStat(String key, double value, int ticks) {
        tempStats.put(key, new TempStat(value, System.currentTimeMillis() + ticks * 50L));
    }
    public Double getTempStat(String key) {
        TempStat t = tempStats.get(key);
        return (t == null || t.expired()) ? null : t.value;
    }
    record TempStat(double value, long expireMs) { boolean expired(){ return System.currentTimeMillis()>expireMs; } }

    // === Serialización NBT (Attachment y red) ===
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("race", race.name());
        tag.putString("style", style.name());
        tag.putInt("tp", tp);

        CompoundTag attrs = new CompoundTag();
        for (var e : attributes.entrySet()) attrs.putInt(e.getKey().name(), e.getValue());
        tag.put("attributes", attrs);

        CompoundTag inv = new CompoundTag();
        for (var e : invested.entrySet()) inv.putInt(e.getKey().name(), e.getValue());
        tag.put("invested", inv);

        tag.putInt("body", body);
        tag.putInt("bodyMax", bodyMax);
        tag.putInt("stamina", stamina);
        tag.putInt("staminaMax", staminaMax);
        tag.putInt("energy", energy);
        tag.putInt("energyMax", energyMax);
        tag.putDouble("speed", speed);
        tag.putDouble("flySpeed", flySpeed);

        // Flags
        tag.putBoolean("flyEnabled", flyEnabled);
        tag.putBoolean("chargingKi", chargingKi);
        tag.putBoolean("raceChosen",raceChosen);

        // Ki Attacks
        ListTag list = getTags();
        tag.put("kiAttacks", list);
        tag.putString("selectedKiAttackId", selectedKiAttackId);
        tag.putInt("auraColorRgb", auraColorRgb);

        return tag;
    }

    private @NotNull ListTag getTags() {
        ListTag list = new ListTag();
        for (var def : kiAttacks.values()) {
            CompoundTag d = new CompoundTag();
            d.putString("id", def.id());
            d.putString("display", def.displayName());
            d.putString("type", def.type().name());
            d.putDouble("basePower", def.basePower());
            d.putDouble("speed", def.speed());
            d.putInt("cooldown", def.cooldownTicks());
            d.putInt("chargeTime", def.chargeTimeTicks());
            d.putInt("density", def.density());
            d.putInt("rgb", def.rgbColor());
            list.add(d);
        }
        return list;
    }

    public void load(CompoundTag tag) {
        try {
            this.race = Race.valueOf(tag.getString("race"));
            this.style = Style.valueOf(tag.getString("style"));
        } catch (Exception ignored) {}

        this.tp = tag.getInt("tp");

        CompoundTag attrs = tag.getCompound("attributes");
        for (Dbrattributes a : Dbrattributes.values()) {
            attributes.put(a, attrs.getInt(a.name()));
        }

        CompoundTag inv = tag.getCompound("invested");
        for (Dbrattributes a : Dbrattributes.values()) {
            invested.put(a, inv.getInt(a.name()));
        }

        this.body = tag.getInt("body");
        this.bodyMax = tag.getInt("bodyMax");
        this.stamina = tag.getInt("stamina");
        this.staminaMax = tag.getInt("staminaMax");
        this.energy = tag.getInt("energy");
        this.energyMax = tag.getInt("energyMax");
        this.speed = tag.getDouble("speed");
        this.flySpeed = tag.getDouble("flySpeed");

        // Flags
        this.flyEnabled = tag.getBoolean("flyEnabled");
        this.chargingKi = tag.getBoolean("chargingKi");
        this.raceChosen = tag.getBoolean("raceChosen");

        // Ki Attacks
        this.kiAttacks.clear();
        if (tag.contains("kiAttacks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("kiAttacks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag d = list.getCompound(i);
                String id = d.getString("id");
                KiAttackDefinition def = new KiAttackDefinition(
                        id,
                        d.getString("display"),
                        safeType(d.getString("type")),
                        d.getDouble("basePower"),
                        d.getDouble("speed"),
                        d.getInt("cooldown"),
                        d.getInt("chargeTime"),
                        d.getInt("density"),
                        d.getInt("rgb")
                );
                kiAttacks.put(id, def);
            }
        }
        this.selectedKiAttackId = tag.getString("selectedKiAttackId");
        this.auraColorRgb = tag.contains("auraColorRgb", Tag.TAG_INT) ? tag.getInt("auraColorRgb") : this.auraColorRgb;
    }

    private static KiAttackType safeType(String name) {
        try { return KiAttackType.valueOf(name); }
        catch (Exception e) { return KiAttackType.BLAST; }
    }

    // === Vuelo / KI usando tus campos existentes ===
    public boolean isFlyEnabled() { return flyEnabled; }
    public void setFlyEnabled(boolean v) { this.flyEnabled = v; }

    public boolean isChargingKi() { return chargingKi; }
    public void setChargingKi(boolean v) { this.chargingKi = v; }

    /** Añade/quita KI usando energy/energyMax (clamp). */
    public void addKi(double delta) {
        int newVal = (int)Math.round(this.energy + delta);
        this.energy = MathUtil.clamp(newVal, 0, this.energyMax);
    }

    /** “Ki actual”. */
    public int getKiCurrent() { return this.energy; }

    /** “Ki pool”. */
    public int getKiPool() { return this.energyMax; }

    /** Stat lineal de vuelo ya es flySpeed. */
    public double getFlySpeed() { return this.flySpeed; }

    /** Multiplicador de vuelo vanilla: 1.0 + FlySpeed/100 con cap 2.0. */
    public double getFlyMultiplier() {
        double mult = 2.0 + (this.flySpeed / 100.0);
        return Math.min(3.0, Math.max(0.0, mult));
    }

    /** (Opcional) Multiplicador de movimiento terrestre con cap 2.0. */
    public double getMoveMultiplier() {
        double mult = 1.0 + (this.speed / 100.0);
        return Math.min(2.0, Math.max(0.0, mult));
    }

    /** Regeneración base de energía/ki por tick. Configurable si ya lo cableaste. */
    public double getRegenEnergyPerTick() {
        // return StatsConfig.REGEN_ENERGY_BASE_PER_TICK.get().doubleValue();
        return 1.0;
    }

    // === API mínima de Ki Attacks (para UI/packets/servidor) ===

    /** Devuelve la definición por id o null. */
    public KiAttackDefinition getKiAttack(String id) {
        return kiAttacks.get(id);
    }

    /** Añade o actualiza una definición (por id). */
    public void addOrUpdateKiAttack(KiAttackDefinition def) {
        if (def == null || def.id() == null || def.id().isEmpty()) return;
        kiAttacks.put(def.id(), def);
        if (selectedKiAttackId == null || selectedKiAttackId.isEmpty()) {
            selectedKiAttackId = def.id();
        }
    }

    /** Cambia solo el color del ataque (manteniendo el resto). */
    public void setKiAttackColor(String id, int rgb) {
        var old = kiAttacks.get(id);
        if (old == null) return;
        kiAttacks.put(id, new KiAttackDefinition(
                old.id(), old.displayName(), old.type(),
                old.basePower(), old.speed(), old.cooldownTicks(),
                old.chargeTimeTicks(), old.density(), rgb
        ));
    }

    /** Selecciona ataque actual (para disparar). */
    public void setSelectedKiAttackId(String id) {
        if (id != null && kiAttacks.containsKey(id)) {
            this.selectedKiAttackId = id;
        }
    }

    public String getSelectedKiAttackId() { return selectedKiAttackId; }

    /** Color del aura configurable (y por defecto para ataques nuevos). */
    public int getAuraColorRgb() { return auraColorRgb; }
    public void setAuraColorRgb(int rgb) { this.auraColorRgb = rgb; }

    /** Vista de solo-lectura de ataques (para UI). */
    public Map<String, KiAttackDefinition> getKiAttacksReadonly() {
        return Collections.unmodifiableMap(kiAttacks);
    }

    public boolean isRaceChosen() {
        return raceChosen;
    }

    public void setRaceChosen(boolean raceChosen) {
        this.raceChosen = raceChosen;
    }

    public void refillOnRespawn() {
        this.body    = this.bodyMax;

        this.stamina = this.staminaMax;
        this.energy  = this.energyMax;
    }
}