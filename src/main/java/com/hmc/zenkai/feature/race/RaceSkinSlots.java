package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.Race;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public final class RaceSkinSlots {
    private RaceSkinSlots() {}

    public static ItemStack getVirtualRaceArmor(Player player, EquipmentSlot slot) {
        return backedOrEmpty(resolveRaw(player, slot));
    }

    /**
     * ¿Tiene esta raza AL MENOS un cuerpo con modelo GeckoLib real? Se comprueba con una sola
     * ranura de referencia (CHEST): las 4 de una misma raza se registran o faltan juntas en
     * este mod, así que basta con mirar una. Usado por RaceSkinHideBasePlayerHooks para no
     * ocultar el cuerpo vanilla cuando no hay nada real que ponga en su lugar (ver el mismo
     * "sistema general" que backedOrEmpty aplica aquí abajo).
     */
    public static boolean raceHasBackedBody(Player player) {
        return !getVirtualRaceArmor(player, EquipmentSlot.CHEST).isEmpty();
    }

    /** Resuelve el ítem "en teoría" para esa raza/ranura, SIN comprobar si su modelo existe
     *  de verdad — eso lo hace backedOrEmpty() en el único punto de salida de arriba. */
    private static ItemStack resolveRaw(Player player, EquipmentSlot slot) {
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
        if (race == Race.MAJIN) {
            boolean female = PlayerVisualAttachment.get(player).getGender()
                    == PlayerVisualAttachment.Gender.FEMALE;
            return switch (slot) {
                case HEAD  -> (female ? ModItems.MAJIN_RACE_HELMET_FEMALE     : ModItems.MAJIN_RACE_HELMET).get().getDefaultInstance();
                case CHEST -> (female ? ModItems.MAJIN_RACE_CHESTPLATE_FEMALE : ModItems.MAJIN_RACE_CHESTPLATE).get().getDefaultInstance();
                case LEGS  -> (female ? ModItems.MAJIN_RACE_LEGGINGS_FEMALE   : ModItems.MAJIN_RACE_LEGGINGS).get().getDefaultInstance();
                case FEET  -> (female ? ModItems.MAJIN_RACE_BOOTS_FEMALE      : ModItems.MAJIN_RACE_BOOTS).get().getDefaultInstance();
                default    -> ItemStack.EMPTY;
            };
        }

        return ItemStack.EMPTY;
    }

    /**
     * SISTEMA GENERAL "sin modelo GeckoLib -> vuelve al cuerpo por defecto": un ítem puede
     * estar perfectamente registrado en ModItems (para que el código ya exista mientras se
     * modela con calma) sin que su .geo.json/textura existan todavía en disco. A diferencia
     * de una textura ausente (GeckoLib la sustituye por el checkerboard de "missing texture",
     * sin crashear), un .geo.json ausente hace que GeckoLib lance una RuntimeException sin
     * capturar en pleno render — así es como Majin (MAJIN_RACE_*, sin modelo real todavía)
     * crasheaba el juego en cuanto se le veía en la vista previa de RaceSelectionScreen.
     * Este filtro es el único punto de salida de getVirtualRaceArmor(): protege CUALQUIER
     * raza (no solo Majin) contra un futuro registro cuyo archivo aún no exista.
     */
    private static ItemStack backedOrEmpty(ItemStack stack) {
        if (stack.getItem() instanceof GeoLayerArmorItem geo
                && !RaceTextureUtil.resourceExists(geo.getModelPath())) {
            return ItemStack.EMPTY;
        }
        return stack;
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