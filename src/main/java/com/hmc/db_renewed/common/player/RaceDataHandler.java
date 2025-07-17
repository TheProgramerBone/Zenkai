package com.hmc.db_renewed.common.player;

import com.hmc.db_renewed.common.capability.StatAllocation;
import com.hmc.db_renewed.common.race.ModRaces;
import com.hmc.db_renewed.common.style.CombatStyle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class RaceDataHandler {
    private ModRaces modRaces = ModRaces.HUMAN;
    private CombatStyle style = CombatStyle.WARRIOR;
    private final StatAllocation allocation = new StatAllocation(10,10,10,10,10,10);

    public ModRaces getRace() {
        return modRaces;
    }

    public void setRace(ModRaces modRaces) {
        this.modRaces = modRaces;
    }

    public CombatStyle getCombatStyle() {
        return style;
    }

    public void setCombatStyle(CombatStyle style) {
        this.style = style;
    }

    public StatAllocation getStatAllocation() {
        return allocation;
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Race", modRaces.getSerializedName());
        tag.putString("CombatStyle", style.getSerializedName());
        tag.put("StatAllocation", allocation.saveNBT());
        return tag;
    }

    public void loadNBT(CompoundTag tag) {
        if (tag.contains("Race")) {
            this.modRaces = ModRaces.byName(tag.getString("Race"));
        }
        if (tag.contains("CombatStyle")) {
            this.style = CombatStyle.byName(tag.getString("CombatStyle"));
        }
        if (tag.contains("StatAllocation")) {
            this.allocation.loadNBT(tag.getCompound("StatAllocation"));
        }
    }

    public static final net.neoforged.neoforge.capabilities.EntityCapability<RaceDataHandler, Void> CAPABILITY =
            net.neoforged.neoforge.capabilities.EntityCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("db_renewed", "race_data"),
                    RaceDataHandler.class
            );

}