package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.content.item.ModItems;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.core.network.feature.Race;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public final class RaceSkinSlots {
    private RaceSkinSlots() {}

    public static ItemStack getVirtualRaceArmor(Player player, EquipmentSlot slot) {
        Race race = PlayerStatsAttachment.get(player).getRace();

        if (race == Race.NAMEKIAN) {
            boolean custom = PlayerVisualAttachment.get(player).isCustomSkinColor();
            return switch (slot) {
                case HEAD  -> (custom ? ModItems.NAMEKIAN_RACE_HELMET_COLORABLE     : ModItems.NAMEKIAN_RACE_HELMET).get().getDefaultInstance();
                case CHEST -> (custom ? ModItems.NAMEKIAN_RACE_CHESTPLATE_COLORABLE : ModItems.NAMEKIAN_RACE_CHESTPLATE).get().getDefaultInstance();
                case LEGS  -> (custom ? ModItems.NAMEKIAN_RACE_LEGGINGS_COLORABLE   : ModItems.NAMEKIAN_RACE_LEGGINGS).get().getDefaultInstance();
                case FEET  -> (custom ? ModItems.NAMEKIAN_RACE_BOOTS_COLORABLE      : ModItems.NAMEKIAN_RACE_BOOTS).get().getDefaultInstance();
                default    -> ItemStack.EMPTY;
            };
        }

        // Human / Saiyan: género (M/F) × color (natural / custom-tinte)
        if (race == Race.SAIYAN || race == Race.HUMAN) {
            PlayerVisualAttachment vis = PlayerVisualAttachment.get(player);
            boolean custom = vis.isCustomSkinColor();
            boolean female = vis.getGender() == PlayerVisualAttachment.Gender.FEMALE;
            Supplier<GeoLayerArmorItem> item = humanBody(slot, female, custom);
            return item == null ? ItemStack.EMPTY : item.get().getDefaultInstance();
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

        // Majin: pendiente de items propios → sin skin de raza por ahora
        return ItemStack.EMPTY;
    }

    /** Selecciona el item de cuerpo Human/Saiyan según género y modo de color. null = slot no aplica. */
    private static Supplier<GeoLayerArmorItem> humanBody(EquipmentSlot slot, boolean female, boolean custom) {
        if (female) {
            if (custom) {
                return switch (slot) {
                    case HEAD  -> ModItems.HUMAN_RACE_HELMET_COLORABLE_FEMALE;
                    case CHEST -> ModItems.HUMAN_RACE_CHESTPLATE_COLORABLE_FEMALE;
                    case LEGS  -> ModItems.HUMAN_RACE_LEGGINGS_COLORABLE_FEMALE;
                    case FEET  -> ModItems.HUMAN_RACE_BOOTS_COLORABLE_FEMALE;
                    default    -> null;
                };
            }
            return switch (slot) {
                case HEAD  -> ModItems.HUMAN_RACE_HELMET_FEMALE;
                case CHEST -> ModItems.HUMAN_RACE_CHESTPLATE_FEMALE;
                case LEGS  -> ModItems.HUMAN_RACE_LEGGINGS_FEMALE;
                case FEET  -> ModItems.HUMAN_RACE_BOOTS_FEMALE;
                default    -> null;
            };
        }
        // Masculino
        if (custom) {
            return switch (slot) {
                case HEAD  -> ModItems.HUMAN_RACE_HELMET_COLORABLE;
                case CHEST -> ModItems.HUMAN_RACE_CHESTPLATE_COLORABLE;
                case LEGS  -> ModItems.HUMAN_RACE_LEGGINGS_COLORABLE;
                case FEET  -> ModItems.HUMAN_RACE_BOOTS_COLORABLE;
                default    -> null;
            };
        }
        return switch (slot) {
            case HEAD  -> ModItems.HUMAN_RACE_HELMET;
            case CHEST -> ModItems.HUMAN_RACE_CHESTPLATE;
            case LEGS  -> ModItems.HUMAN_RACE_LEGGINGS;
            case FEET  -> ModItems.HUMAN_RACE_BOOTS;
            default    -> null;
        };
    }
}