package com.hmc.db_renewed.common.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class StatAllocation {

    public int strength;
    public int dexterity;
    public int constitution;
    public int willpower;
    public int mind;
    public int spirit;

    private int tpStrength = 0;
    private int tpDexterity = 0;
    private int tpConstitution = 0;
    private int tpWillpower = 0;
    private int tpMind = 0;
    private int tpSpirit = 0;

    public StatAllocation(int strength, int dexterity, int constitution, int willpower, int mind, int spirit) {
        this.strength = strength;
        this.dexterity = dexterity;
        this.constitution = constitution;
        this.willpower = willpower;
        this.mind = mind;
        this.spirit = spirit;
    }

    // Getters
    public int getTpStrength() { return tpStrength; }
    public int getTpDexterity() { return tpDexterity; }
    public int getTpConstitution() { return tpConstitution; }
    public int getTpWillpower() { return tpWillpower; }
    public int getTpMind() { return tpMind; }
    public int getTpSpirit() { return tpSpirit; }

    // Setters
    public void setTpStrength(int value) { tpStrength = value; }
    public void setTpDexterity(int value) { tpDexterity = value; }
    public void setTpConstitution(int value) { tpConstitution = value; }
    public void setTpWillpower(int value) { tpWillpower = value; }
    public void setTpMind(int value) { tpMind = value; }
    public void setTpSpirit(int value) { tpSpirit = value; }

    // Modifiers
    public void addTpStrength(int amount) { tpStrength += amount; }
    public void addTpDexterity(int amount) { tpDexterity += amount; }
    public void addTpConstitution(int amount) { tpConstitution += amount; }
    public void addTpWillpower(int amount) { tpWillpower += amount; }
    public void addTpMind(int amount) { tpMind += amount; }
    public void addTpSpirit(int amount) { tpSpirit += amount; }

    // Capability instance
    public static final EntityCapability<StatAllocation, Void> INSTANCE = EntityCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("db_renewed", "stat_allocation"),
            StatAllocation.class
    );

    // Register capability to Player
    public static void registerCapability(RegisterCapabilitiesEvent event) {
        event.registerEntity(
                INSTANCE,
                EntityType.PLAYER,
                (player, context) -> new StatAllocation(10,10,10,10,10,10)
        );
    }

    // Save to NBT
    public Tag saveToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tpStrength", tpStrength);
        tag.putInt("tpDexterity", tpDexterity);
        tag.putInt("tpConstitution", tpConstitution);
        tag.putInt("tpWillpower", tpWillpower);
        tag.putInt("tpMind", tpMind);
        tag.putInt("tpSpirit", tpSpirit);
        return tag;
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Strength", this.strength);
        tag.putInt("Dexterity", this.dexterity);
        tag.putInt("Constitution", this.constitution);
        tag.putInt("Willpower", this.willpower);
        tag.putInt("Spirit",this.spirit);
        tag.putInt("Mind", this.mind);
        return tag;
    }

    // Load from NBT
    public void loadFromNBT(Tag nbt) {
        if (nbt instanceof CompoundTag tag) {
            tpStrength = tag.getInt("tpStrength");
            tpDexterity = tag.getInt("tpDexterity");
            tpConstitution = tag.getInt("tpConstitution");
            tpWillpower = tag.getInt("tpWillpower");
            tpSpirit = tag.getInt("tpSpirit");
            tpMind = tag.getInt("tpMind");
        }
    }

    public void loadNBT(CompoundTag tag) {
        this.strength = tag.getInt("Strength");
        this.dexterity = tag.getInt("Dexterity");
        this.constitution = tag.getInt("Constitution");
        this.willpower = tag.getInt("Willpower");
        this.spirit = tag.getInt("Spirit");
        this.mind = tag.getInt("Mind");
    }
}

