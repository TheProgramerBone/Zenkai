package com.hmc.db_renewed.core.network.feature.race;

import com.hmc.db_renewed.content.item.ModItems;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.Race;
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

        if (race == Race.SAIYAN || race == Race.HUMAN){
            return switch (slot) {
                case HEAD  -> ModItems.HUMAN_RACE_HELMET.get().getDefaultInstance();
                case CHEST -> ModItems.HUMAN_RACE_CHESTPLATE.get().getDefaultInstance();
                case LEGS  -> ModItems.HUMAN_RACE_LEGGINGS.get().getDefaultInstance();
                case FEET  -> ModItems.HUMAN_RACE_BOOTS.get().getDefaultInstance();
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