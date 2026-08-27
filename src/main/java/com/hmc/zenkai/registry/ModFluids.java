package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Primer fluido del mod. Agua de Namek en forma de cubo/bloque: se coloca y se recoge
 * exactamente como agua vanilla (ver ModItems.HEALING_WATER_BUCKET, un BucketItem vanilla
 * sin subclase), pero cura de forma pasiva por contacto (ver HealingWaterBlock.entityInside)
 * y, si se recoge con una botella de vidrio, da el healing_water_bottle YA EXISTENTE (ver
 * HealingWaterBlock.useItemOn) en vez de una poción de agua vanilla.
 *
 * Referencias circulares fluid<->block<->bucket resueltas con Supplier perezoso: ninguna
 * de las tres llama .get() fuera de una lambda de registro, así que el orden de declaración
 * entre este archivo, ModBlocks y ModItems no importa (mismo principio que ya usa
 * ModBlocks.AJISA_GROWER con ModConfiguredFeatures.AJISA_TREE).
 */
public final class ModFluids {
    private ModFluids() {}

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Zenkai.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Zenkai.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> HEALING_WATER_TYPE = FLUID_TYPES.register(
            "healing_water",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.zenkai.healing_water")
                    .canSwim(true)
                    .canDrown(false)          // es curativa, no un obstáculo que ahogue
                    .canExtinguish(true)
                    .canConvertToSource(true) // igual que el agua vanilla
                    .density(1000)
                    .viscosity(1000)
                    .temperature(300)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HEALING_WATER = FLUIDS.register(
            "healing_water", () -> new BaseFlowingFluid.Source(fluidProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> HEALING_WATER_FLOWING = FLUIDS.register(
            "flowing_healing_water", () -> new BaseFlowingFluid.Flowing(fluidProperties()));

    private static BaseFlowingFluid.Properties fluidProperties() {
        return new BaseFlowingFluid.Properties(HEALING_WATER_TYPE, HEALING_WATER, HEALING_WATER_FLOWING)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .block(ModBlocks.HEALING_WATER_BLOCK)
                .bucket(ModItems.HEALING_WATER_BUCKET);
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
