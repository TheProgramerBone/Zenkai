package com.hmc.db_renewed.common.player;

import com.hmc.db_renewed.common.race.ModRaces;
import com.hmc.db_renewed.common.race.Race;
import com.hmc.db_renewed.common.race.RaceStats;
import net.minecraft.nbt.CompoundTag;

public class RaceData implements IRaceData {

    private Race race = Race.HUMAN;
    private boolean selected = false;
    private RaceStats stats = ModRaces.DEFAULT_STATS.get(race);

    @Override
    public Race getRace() {
        return race;
    }

    @Override
    public void setRace(Race race) {
        this.race = race;
        this.stats = ModRaces.DEFAULT_STATS.get(race);
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
    public RaceStats getStats() {
        return stats;
    }

    @Override
    public void setStats(RaceStats stats) {
        this.stats = stats;
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Race", race.name());
        tag.putBoolean("Selected", selected);
        // puedes extenderlo para guardar cada stat también si se modifican dinámicamente
        return tag;
    }

    public void loadNBT(CompoundTag tag) {
        this.race = Race.valueOf(tag.getString("Race"));
        this.selected = tag.getBoolean("Selected");
        this.stats = ModRaces.DEFAULT_STATS.get(race);
    }
}