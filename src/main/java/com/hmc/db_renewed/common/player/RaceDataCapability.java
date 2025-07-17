package com.hmc.db_renewed.common.player;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.EntityCapability;

public class RaceDataCapability {
    public static final EntityCapability<RaceDataHandler, Void> INSTANCE =
            EntityCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("db_renewed", "race_data"),
                    RaceDataHandler.class
            );
}