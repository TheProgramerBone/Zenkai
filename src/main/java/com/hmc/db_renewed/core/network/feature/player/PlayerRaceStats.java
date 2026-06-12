package com.hmc.db_renewed.core.network.feature.player;

import com.hmc.db_renewed.core.config.StatsConfig;
import com.hmc.db_renewed.core.network.feature.Dbrattributes;
import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.Style;
import com.hmc.db_renewed.util.BalanceUtil;
import com.hmc.db_renewed.util.MathUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

public class PlayerRaceStats {

    private Race  race  = Race.HUMAN;
    private Style style = Style.MARTIAL_ARTIST;

    private boolean raceChosen  = false;
    private boolean styleChosen = false;

    private int tp = 0;
    private final EnumMap<Dbrattributes, Integer> attributes = new EnumMap<>(Dbrattributes.class);
    private final EnumMap<Dbrattributes, Integer> invested   = new EnumMap<>(Dbrattributes.class);

    public PlayerRaceStats() {
        for (Dbrattributes a : Dbrattributes.values()) {
            attributes.put(a, 0);
            invested.put(a, 0);
        }
        applyRaceBaseAttributes();
    }

    // ── Raza y Estilo ─────────────────────────────────────────────────────────
    public Race  getRace()  { return race; }
    public Style getStyle() { return style; }

    public boolean isRaceChosen()  { return raceChosen; }
    public boolean isStyleChosen() { return styleChosen; }

    public void setRaceChosen(boolean v)  { this.raceChosen  = v; }
    public void setStyleChosen(boolean v) { this.styleChosen = v; }

    public void setRace(Race r) {
        this.race = r;
        applyRaceBaseAttributes();
    }

    public void setStyle(Style s) {
        this.style = s;
    }

    // ── Atributos base ────────────────────────────────────────────────────────
    public void applyRaceBaseAttributes() {
        int[] base = StatsConfig.raceBaseAttributes(this.race);
        BalanceUtil.setBase(attributes,
                base[0], // STR
                base[1], // CON
                base[2], // DEX
                base[3], // WIL
                base[4], // SPI
                base[5]  // MND
        );
        capAll();
    }

    private void capAll() {
        int cap = StatsConfig.globalAttributeCap();
        for (Map.Entry<Dbrattributes, Integer> e : attributes.entrySet()) {
            e.setValue(Math.min(e.getValue(), cap));
        }
    }

    public int getAttribute(Dbrattributes a) { return attributes.getOrDefault(a, 0); }

    public void setAttribute(Dbrattributes a, int v) {
        attributes.put(a, MathUtil.clamp(v, 0, StatsConfig.globalAttributeCap()));
    }

    // ── TP ───────────────────────────────────────────────────────────────────
    public int  getTP() { return tp; }
    public void addTP(int amount) { this.tp = Math.max(0, this.tp + amount); }

    public boolean spendTP(Dbrattributes attr, int points) {
        if (points <= 0) return false;
        double coeff    = StatsConfig.tpCoefficient();
        int    totalInv = invested.values().stream().mapToInt(Integer::intValue).sum();
        int    cap      = StatsConfig.globalAttributeCap();
        int    cur      = attributes.get(attr);
        int    add      = Math.min(points, cap - cur);
        if (add <= 0) return false;

        int totalCost = 0;
        for (int k = 0; k < add; k++) {
            totalCost += (int) Math.ceil(1 + (totalInv + k) * coeff);
        }
        if (tp < totalCost) return false;

        attributes.put(attr, cur + add);
        invested.compute(attr, (k, v) -> v + add);
        tp -= totalCost;
        return true;
    }

    public int previewTpCost(Dbrattributes attr, int points) {
        if (points <= 0) return 0;
        double coeff    = StatsConfig.tpCoefficient();
        int    totalInv = invested.values().stream().mapToInt(Integer::intValue).sum();
        int    cap      = StatsConfig.globalAttributeCap();
        int    cur      = attributes.get(attr);
        int    add      = Math.min(points, cap - cur);
        if (add <= 0) return 0;

        int totalCost = 0;
        for (int k = 0; k < add; k++) {
            totalCost += (int) Math.ceil(1 + (totalInv + k) * coeff);
        }
        return totalCost;
    }

    public void respec() {
        int refund = invested.values().stream().mapToInt(i -> i).sum();
        tp += refund;
        invested.replaceAll((k, v) -> 0);
        applyRaceBaseAttributes();
    }

    // ── Recalc — devuelve los máximos para que PlayerResourcePools los aplique ──
    public record RecalcResult(int bodyMax, int staminaMax, int energyMax,
                               double speed, double flySpeed) {}

    public RecalcResult recalcAll() {
        double[] r = StatsConfig.raceMultipliers(this.race);
        double[] s = StatsConfig.styleMultipliers(this.style);

        double CON = attributes.get(Dbrattributes.CONSTITUTION) * r[1] * s[1];
        double DEX = attributes.get(Dbrattributes.DEXTERITY)    * r[2] * s[2];
        double SPI = attributes.get(Dbrattributes.SPIRIT)       * r[4] * s[4];

        int bodyMax    = (int) Math.max(1, Math.round(10 + CON));
        int staminaMax = (int) Math.max(1, Math.round(90 + CON));
        int energyMax  = (int) Math.max(1, Math.round(90 + SPI));

        return new RecalcResult(bodyMax, staminaMax, energyMax, DEX, DEX);
    }

    // ── Stats de combate ─────────────────────────────────────────────────────
    public double computeMeleeFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.STRENGTH),   race, style, Dbrattributes.STRENGTH);
    }
    public double computeDefenseFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.DEXTERITY),  race, style, Dbrattributes.DEXTERITY);
    }
    public double computeSpeedFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.DEXTERITY),  race, style, Dbrattributes.DEXTERITY);
    }
    public double computeFlyFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.DEXTERITY),  race, style, Dbrattributes.DEXTERITY);
    }
    public double computeKiPowerFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.WILLPOWER),  race, style, Dbrattributes.WILLPOWER);
    }
    public double computeKiPoolFinal() {
        return BalanceUtil.computeStat(attributes.get(Dbrattributes.SPIRIT),     race, style, Dbrattributes.SPIRIT);
    }
    public double getMeleeBonus() {
        return attributes.get(Dbrattributes.STRENGTH);
    }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("race",         race.name());
        tag.putString("style",        style.name());
        tag.putBoolean("raceChosen",  raceChosen);
        tag.putBoolean("styleChosen", styleChosen);
        tag.putInt("tp", tp);

        CompoundTag attrs = new CompoundTag();
        for (var e : attributes.entrySet()) attrs.putInt(e.getKey().name(), e.getValue());
        tag.put("attributes", attrs);

        CompoundTag inv = new CompoundTag();
        for (var e : invested.entrySet()) inv.putInt(e.getKey().name(), e.getValue());
        tag.put("invested", inv);

        return tag;
    }

    public void load(CompoundTag tag) {
        try {
            this.race  = Race.valueOf(tag.getString("race"));
            this.style = Style.valueOf(tag.getString("style"));
        } catch (Exception ignored) {}

        this.raceChosen  = tag.getBoolean("raceChosen");
        this.styleChosen = tag.getBoolean("styleChosen");
        this.tp          = tag.getInt("tp");

        CompoundTag attrs = tag.getCompound("attributes");
        for (Dbrattributes a : Dbrattributes.values()) attributes.put(a, attrs.getInt(a.name()));

        CompoundTag inv = tag.getCompound("invested");
        for (Dbrattributes a : Dbrattributes.values()) invested.put(a, inv.getInt(a.name()));
    }
}