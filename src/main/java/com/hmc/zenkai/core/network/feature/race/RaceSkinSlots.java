package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.content.item.ModItems;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.core.network.feature.Race;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class RaceSkinSlots {
    private RaceSkinSlots() {}

    public static ItemStack getVirtualRaceArmor(Player player, EquipmentSlot slot) {
        Race race = PlayerStatsAttachment.get(player).getRace();

        if (race == Race.NAMEKIAN) {
            return switch (slot) {
                case HEAD  -> ModItems.NAMEKIAN_RACE_HELMET.get().getDefaultInstance();
                case CHEST -> ModItems.NAMEKIAN_RACE_CHESTPLATE.get().getDefaultInstance();
                case LEGS  -> ModItems.NAMEKIAN_RACE_LEGGINGS.get().getDefaultInstance();
                case FEET  -> ModItems.NAMEKIAN_RACE_BOOTS.get().getDefaultInstance();
                default    -> ItemStack.EMPTY;
            };
        }

        // Human / Saiyan (y Majin): color custom → item colorable (tinte); natural → item normal
        if (race == Race.SAIYAN || race == Race.HUMAN) {
            boolean custom = PlayerVisualAttachment.get(player).isCustomSkinColor();
            return switch (slot) {
                case HEAD  -> (custom ? ModItems.HUMAN_RACE_HELMET_COLORABLE     : ModItems.HUMAN_RACE_HELMET).get().getDefaultInstance();
                case CHEST -> (custom ? ModItems.HUMAN_RACE_CHESTPLATE_COLORABLE : ModItems.HUMAN_RACE_CHESTPLATE).get().getDefaultInstance();
                case LEGS  -> (custom ? ModItems.HUMAN_RACE_LEGGINGS_COLORABLE   : ModItems.HUMAN_RACE_LEGGINGS).get().getDefaultInstance();
                case FEET  -> (custom ? ModItems.HUMAN_RACE_BOOTS_COLORABLE      : ModItems.HUMAN_RACE_BOOTS).get().getDefaultInstance();
                default    -> ItemStack.EMPTY;
            };
        }

        if (race == Race.ARCOSIAN) {
            return switch (slot) {
                case HEAD  -> ModItems.ARCOSIAN_RACE_HELMET.get().getDefaultInstance();
                case CHEST -> ModItems.ARCOSIAN_RACE_CHESTPLATE.get().getDefaultInstance();
                case LEGS  -> ModItems.ARCOSIAN_RACE_LEGGINGS.get().getDefaultInstance();
                case FEET  -> ModItems.ARCOSIAN_RACE_BOOTS.get().getDefaultInstance();
                default    -> ItemStack.EMPTY;
            };
        }

        return ItemStack.EMPTY;
    }
}