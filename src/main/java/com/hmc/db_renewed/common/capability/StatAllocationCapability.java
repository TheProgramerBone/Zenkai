package com.hmc.db_renewed.common.capability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.EntityCapability;

public class StatAllocationCapability {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("db_renewed", "stat_allocation");

    public static final EntityCapability<StatAllocation, Void> INSTANCE =
            EntityCapability.createVoid(ID, StatAllocation.class);

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerEntity(
                INSTANCE,
                EntityType.PLAYER,
                (player, context) -> new StatAllocation(10,10,10,10,10,10)
        );
    }
}