package com.hmc.db_renewed.common.player;

import com.hmc.db_renewed.common.capability.StatAllocation;
import com.hmc.db_renewed.common.race.ModRaces;
import com.hmc.db_renewed.common.race.ModRacesStats;
import net.minecraft.nbt.CompoundTag;

public class RaceData implements IRaceData {

    private ModRaces modRaces = ModRaces.HUMAN;
    private boolean selected = false;
    private StatAllocation stats = ModRacesStats.DEFAULT_STATS.get(modRaces);

    @Override
    public ModRaces getRace() {
        return modRaces;
    }

    @Override
    public void setRace(ModRaces modRaces) {
        this.modRaces = modRaces;
        this.stats = ModRacesStats.DEFAULT_STATS.get(modRaces);
    }

    @Override
    public boolean hasSelectedRace() {
        return selected;
    }

    @Override
    public void setSelectedRace(boolean selected) {
        this.selected = selected;
    }

    @Override
    public StatAllocation getStats() {
        return stats;
    }

    @Override
    public void setStats(StatAllocation stats) {
        this.stats = stats;
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Race", modRaces.name());
        tag.putBoolean("Selected", selected);
        // puedes extenderlo para guardar cada stat también si se modifican dinámicamente
        return tag;
    }

    public void loadNBT(CompoundTag tag) {
        this.modRaces = ModRaces.valueOf(tag.getString("Race"));
        this.selected = tag.getBoolean("Selected");
        this.stats = ModRacesStats.DEFAULT_STATS.get(modRaces);
    }
}