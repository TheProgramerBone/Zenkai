package com.hmc.db_renewed.common.stats;

import com.hmc.db_renewed.common.capability.StatAllocation;
import com.hmc.db_renewed.common.player.RaceDataHandler;
import com.hmc.db_renewed.common.race.ModRacesStats;
import net.minecraft.world.entity.player.Player;

public class StatInitializer {
    public static void initializeBaseStats(Player player) {
        RaceDataHandler data = player.getCapability(RaceDataHandler.CAPABILITY, null);
        if (data != null) {
            StatAllocation alloc = data.getStatAllocation();
            StatAllocation base = ModRacesStats.DEFAULT_STATS.get(data.getRace());
            if (base != null) {
                alloc.strength = base.strength;
                alloc.dexterity = base.dexterity;
                alloc.constitution = base.constitution;
                alloc.willpower = base.willpower;
                alloc.mind = base.mind;
                alloc.spirit = base.spirit;

                alloc.setTpStrength(0);
                alloc.setTpDexterity(0);
                alloc.setTpConstitution(0);
                alloc.setTpWillpower(0);
                alloc.setTpMind(0);
                alloc.setTpSpirit(0);
            }
        }
    }
}
