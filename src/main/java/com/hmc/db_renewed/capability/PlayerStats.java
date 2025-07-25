package com.hmc.db_renewed.capability;

import com.hmc.db_renewed.config.RaceConfig;
import com.hmc.db_renewed.config.StyleConfig;
import com.hmc.db_renewed.data.EnumRace;
import com.hmc.db_renewed.data.EnumStyle;
import com.hmc.db_renewed.data.StatSet;
import com.hmc.db_renewed.data.StatType;
import com.hmc.db_renewed.util.TPUtils;
import net.minecraft.nbt.CompoundTag;
import java.util.EnumMap;
import java.util.Map;

public class PlayerStats {

    private EnumRace race;
    private EnumStyle style;

    private int totalTP = 0;
    private final EnumMap<StatType, Integer> tpSpent = new EnumMap<>(StatType.class); // TP invertido por stat
    private final StatSet baseStats = new StatSet(0f); // Valores base

    public PlayerStats() {
        this(EnumRace.HUMAN, EnumStyle.MARTIAL_ARTIST); // Default
    }

    public PlayerStats(EnumRace race, EnumStyle style) {
        this.race = race;
        this.style = style;
        for (StatType type : StatType.values()) {
            tpSpent.put(type, 0);
        }
    }

    // ====== Raza & estilo ======
    public EnumRace getRace() { return race; }
    public void setRace(EnumRace race) { this.race = race; }

    public EnumStyle getStyle() { return style; }
    public void setStyle(EnumStyle style) { this.style = style; }

    // ====== TP ======
    public int getTotalTP() { return totalTP; }

    public void addTP(int amount) {
        this.totalTP += amount;
    }

    public int getTPSPent(StatType type) {
        return tpSpent.getOrDefault(type, 0);
    }

    public boolean investTP(StatType type) {
        int spent = tpSpent.getOrDefault(type, 0);
        int cost = TPUtils.getTPCost(spent); // Costo progresivo
        if (totalTP >= cost) {
            totalTP -= cost;
            tpSpent.put(type, spent + 1);
            return true;
        }
        return false;
    }

    public int getVisualLevel() {
        return tpSpent.values().stream().mapToInt(Integer::intValue).sum() / 5;
    }

    // ====== Stats base ======
    public StatSet getBaseStats() {
        return baseStats;
    }

    public void setBaseStat(StatType type, float value) {
        baseStats.set(type, value);
    }

    // ====== Stats finales ======
    public StatSet getFinalStats() {
        StatSet result = new StatSet(0f);
        for (StatType type : StatType.values()) {
            float base = baseStats.get(type);
            int tp = tpSpent.getOrDefault(type, 0);
            float styleMult = StyleConfig.getStyleMultiplier(style, type);
            float raceMult = RaceConfig.getRaceMultiplier(race, type);
            float finalValue = base + (tp * styleMult * raceMult);
            result.set(type, finalValue);
        }
        return result;
    }

    // ====== Serialización NBT ======
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Race", race.name());
        tag.putString("Style", style.name());
        tag.putInt("TotalTP", totalTP);

        CompoundTag tpTag = new CompoundTag();
        for (Map.Entry<StatType, Integer> entry : tpSpent.entrySet()) {
            tpTag.putInt(entry.getKey().name(), entry.getValue());
        }
        tag.put("TPSpent", tpTag);

        CompoundTag baseTag = new CompoundTag();
        for (StatType type : StatType.values()) {
            baseTag.putFloat(type.name(), baseStats.get(type));
        }
        tag.put("BaseStats", baseTag);

        return tag;
    }

    public void load(CompoundTag tag) {
        this.race = EnumRace.valueOf(tag.getString("Race"));
        this.style = EnumStyle.valueOf(tag.getString("Style"));
        this.totalTP = tag.getInt("TotalTP");

        CompoundTag tpTag = tag.getCompound("TPSpent");
        for (StatType type : StatType.values()) {
            tpSpent.put(type, tpTag.getInt(type.name()));
        }

        CompoundTag baseTag = tag.getCompound("BaseStats");
        for (StatType type : StatType.values()) {
            baseStats.set(type, baseTag.getFloat(type.name()));
        }
    }
}