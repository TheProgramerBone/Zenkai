package com.hmc.db_renewed.common.capability;

import com.hmc.db_renewed.api.PlayerStatData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.EntityCapability;

public class ModCapabilities {
    public static final EntityCapability<PlayerStatData, Void> PLAYER_STATS =
            EntityCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("db_renewed","player_stats"),
                    PlayerStatData.class
            );
}
