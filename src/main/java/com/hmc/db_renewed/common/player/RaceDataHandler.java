package com.hmc.db_renewed.common.player;

import com.hmc.db_renewed.common.race.ModRaces;
import com.hmc.db_renewed.common.race.Race;
import com.hmc.db_renewed.common.race.RaceStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public class RaceDataHandler {
    private static final String TAG = "DragonBlockRace";

    public static void save(Player player, Race race, boolean selected) {
        CompoundTag tag = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag raceTag = new CompoundTag();

        raceTag.putString("Race", race.name());
        raceTag.putBoolean("Selected", selected);

        tag.put(TAG, raceTag);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    public static Race loadRace(Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (tag.contains(TAG)) {
            String raceName = tag.getCompound(TAG).getString("Race");
            try {
                return Race.valueOf(raceName);
            } catch (IllegalArgumentException e) {
                return Race.HUMAN; // valor por defecto
            }
        }
        return Race.HUMAN;
    }

    public static boolean wasRaceSelected(Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return tag.contains(TAG) && tag.getCompound(TAG).getBoolean("Selected");
    }

    public static RaceStats getStats(Player player) {
        return ModRaces.DEFAULT_STATS.get(loadRace(player));
    }
}