package com.hmc.db_renewed.common.stats;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;


public class StatAttributeApplier {

    public static void apply(Player player, PlayerStats stats) {
        applyAttribute(player, (Attribute) Attributes.ATTACK_DAMAGE, getAttackDamage(stats.getStrength()));
        applyAttribute(player, (Attribute) Attributes.MAX_HEALTH, getMaxHealth(stats.getConstitution()));
        applyAttribute(player, (Attribute) Attributes.MOVEMENT_SPEED, getMovementSpeed(stats.getDexterity()));
        applyAttribute(player, (Attribute) Attributes.FLYING_SPEED, getFlyingSpeed(stats.getDexterity()));
        applyAttribute(player, (Attribute) Attributes.ARMOR, getArmor(stats.getDexterity()));

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void applyAttribute(Player player, Attribute attribute, double value) {

        ResourceLocation attributeId = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
        if (attributeId == null) return;


        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
        if (holder == null) return;


        AttributeInstance instance = player.getAttributes().getInstance(holder);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    // Fórmulas de conversión
    private static double getAttackDamage(int strength) {
        return 1.0 + (strength - 10) * 0.25;
    }

    private static double getMaxHealth(int constitution) {
        return 20.0 + (constitution - 10) * 0.5;
    }

    private static double getMovementSpeed(int dexterity) {
        return 0.1 + (dexterity - 10) * 0.005;
    }

    private static double getFlyingSpeed(int dexterity) {
        return 0.05 + (dexterity - 10) * 0.003;
    }

    private static double getArmor(int dexterity) {
        return 0.5 + (dexterity - 10) * 0.25;
    }
}