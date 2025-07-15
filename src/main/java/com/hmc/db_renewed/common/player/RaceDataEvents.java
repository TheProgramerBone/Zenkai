package com.hmc.db_renewed.common.player;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public class RaceDataEvents {

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player clone = event.getEntity();

        CompoundTag originalTag = original.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        clone.getPersistentData().put(Player.PERSISTED_NBT_TAG, originalTag.copy());
    }
}
