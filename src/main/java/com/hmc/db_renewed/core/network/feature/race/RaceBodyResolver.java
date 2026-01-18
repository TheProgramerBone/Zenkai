package com.hmc.db_renewed.core.network.feature.race;


import com.hmc.db_renewed.core.network.feature.forms.FormSkinResolver;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class RaceBodyResolver {

    private RaceBodyResolver() {}

    public static ItemStack resolve(Player player, EquipmentSlot slot) {
        // 1) Override por forma (si existe)
        ItemStack override = FormSkinResolver.resolveBodyOverride(player, slot);
        if (!override.isEmpty()) return override;

        // 2) Fallback: base por raza
        return RaceSkinSlots.getVirtualRaceArmor(player, slot);
    }
}