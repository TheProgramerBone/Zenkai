package com.hmc.db_renewed.common.capability;

import com.hmc.db_renewed.common.player.RaceDataHandler;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class ModCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerEntity(
                RaceDataHandler.CAPABILITY,
                EntityType.PLAYER,
                (player, ctx) -> new RaceDataHandler()
        );
    }
}