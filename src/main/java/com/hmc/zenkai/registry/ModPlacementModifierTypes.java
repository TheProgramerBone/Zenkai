package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.worldgen.placement.ClampedHeightmapPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registro de PlacementModifierType propios. Mismo patrón que ModFeatures — llamar a
 * ModPlacementModifierTypes.register(modEventBus) en el constructor del mod.
 */
public final class ModPlacementModifierTypes {
    private ModPlacementModifierTypes() {}

    public static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Zenkai.MOD_ID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ClampedHeightmapPlacement>> CLAMPED_HEIGHTMAP =
            TYPES.register("clamped_heightmap", () -> () -> ClampedHeightmapPlacement.CODEC);

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
