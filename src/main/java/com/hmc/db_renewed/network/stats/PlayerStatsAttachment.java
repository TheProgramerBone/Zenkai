package com.hmc.db_renewed.network.stats;

import com.hmc.db_renewed.config.StatsConfig;
import com.hmc.db_renewed.util.BalanceUtil;
import com.hmc.db_renewed.util.MathUtil;
import net.minecraft.world.entity.player.Player;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class PlayerStatsAttachment {
    // Campos principales
    private Race race = Race.HUMAN;
    private Style style = Style.MARTIAL_ARTIST;

    private int tp = 0; // puntos libres
    private final EnumMap<Dbrattributes, Integer> attributes = new EnumMap<>(Dbrattributes.class);
    private final EnumMap<Dbrattributes, Integer> invested = new EnumMap<>(Dbrattributes.class); // TP invertido por atributo (para coste creciente)

    // Derivados / pools actuales
    private int body;        // "HP interna" opcional (placeholder simple)
    private int bodyMax;
    private int stamina;
    private int staminaMax;
    private int energy;
    private int energyMax;
    private double speed;    // stat (lineal)
    private double flySpeed; // stat (lineal)

    // Temp overrides (placeholder simple)
    private final Map<String, TempStat> tempStats = new HashMap<>();

    public PlayerStatsAttachment() {
        for (Dbrattributes a : Dbrattributes.values()) {
            attributes.put(a, 0);
            invested.put(a, 0);
        }
        // Inicializar con base de raza
        applyRaceStyle(); // setea bases y multipliers
        recalcAll();      // deriva pools
        this.body = this.bodyMax;
        this.stamina = this.staminaMax;
        this.energy = this.energyMax;
    }

    // === Acceso estático seguro ===
    public static PlayerStatsAttachment get(Player p) {
        return p.getData(DataAttachments.PLAYER_STATS);
    }

    // === Raza/estilo y multiplicadores ===
    public void setRace(Race r) {
        this.race = r;
        applyRaceStyle();
        recalcAll();
    }

    public void setStyle(Style s) {
        this.style = s;
        recalcAll();
    }

    public Race getRace() { return race; }
    public Style getStyle() { return style; }

    // Aplicar base de raza y reset opcional de atributos si es necesario
    public void applyRaceStyle() {
        // Cargar bases por raza (default hardcoded guiado por config)
        // Si quisieras 100% configurable, expón cada base/mult en el serverconfig con listas.
        switch (race) {
            case HUMAN -> BalanceUtil.setBase(attributes, 10,10,10,10,10,10);
            case SAIYAN -> BalanceUtil.setBase(attributes, 14,10,12,8,6,10);
            case NAMEKIAN -> BalanceUtil.setBase(attributes, 8,8,10,11,13,10);
            case ARCOSIAN -> BalanceUtil.setBase(attributes, 8,8,10,12,12,10);
            case MAJIN -> BalanceUtil.setBase(attributes, 10,8,14,8,10,10);
        }
        // Aplicar cap global
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

        // Pools máximos (puedes ajustar fórmulas si quieres curvas)
        this.bodyMax = (int)Math.max(1, Math.round(100 + bodyStat));      // base 100 + CON
        this.staminaMax = (int)Math.max(1, Math.round(100 + staminaStat));
        this.energyMax  = (int)Math.max(1, Math.round(100 + kiPool));

        // Stats de movimiento: guardamos stats lineales; el multiplicador se aplica en el tick
        this.speed = speedStat;
        this.flySpeed = flyStat;

        // Clamps de pools actuales
        this.body = Math.min(this.body, this.bodyMax);
        this.stamina = Math.min(this.stamina, this.staminaMax);
        this.energy = Math.min(this.energy, this.energyMax);

        // Temp stats expirables (placeholder: limpieza ligera)
        tempStats.values().removeIf(TempStat::expired);
    }

    // === TP / progresión ===
    public boolean spendTP(Dbrattributes attr, int points) {
        if (points <= 0) return false;
        int have = this.tp;
        int inv = this.invested.get(attr);
        double coeff = StatsConfig.tpCoefficient();

        // coste total sum_{k=0}^{points-1} (1 + (inv + k) * coeff)
        int totalCost = 0;
        for (int k=0;k<points;k++) {
            totalCost += (int)Math.ceil(1 + (inv + k) * coeff);
        }
        if (have < totalCost) return false;

        // Aplicar incremento respetando cap global
        int cap = StatsConfig.globalAttributeCap();
        int cur = attributes.get(attr);
        int add = Math.min(points, cap - cur);
        if (add <= 0) return false;

        attributes.put(attr, cur + add);
        invested.put(attr, inv + add);
        this.tp = have - totalCost; // consume TP
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
        // Devolver TP invertido (simple: suma de investidos) y reset a bases por raza
        int refund = invested.values().stream().mapToInt(i -> {
            // costo aproximado no lineal; para simplicidad devolvemos 1:1 puntos invertidos
            return i;
        }).sum();
        this.tp += refund;
        invested.replaceAll((k,v)->0);
        applyRaceStyle();
        recalcAll();
    }

    // === Daño / defensa / consumo stamina ===
    public double getMeleeBonus() {
        // Melee ← STR => aquí devolvemos STR multiplicado ya aplicado
        return attributes.get(Dbrattributes.STRENGTH); // el bonus final lo computa BalanceUtil con mults
    }
    public double computeMeleeFinal() {
        // devolver el "Melee" final con multiplicadores de raza/estilo
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

    // Stamina / Energy manipulación
    public int getStamina() { return stamina; }
    public int getStaminaMax() { return staminaMax; }
    public void addStamina(int delta) { stamina = MathUtil.clamp(stamina + delta, 0, staminaMax); }
    public boolean consumeStamina(int amount) {
        if (amount <= 0) return true;
        if (stamina <= 0) return false;
        int use = Math.min(amount, stamina);
        stamina -= use;
        return use == amount;
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

    // === Serialización NBT (para Attachment y red) ===
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
        return tag;
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
    }
}