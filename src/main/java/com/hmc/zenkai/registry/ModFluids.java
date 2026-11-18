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

    /**
     * Agua hirviendo azul cobalto de las pozas del HFIL ("Umi Jigoku", el "infierno del mar" de
     * Beppu: una fuente termal real a ~98 °C, de color azul lechoso). Quema por contacto (ver
     * HfilBoilingWaterBlock). Tiene cubo desde 2026-10-04 (petición del usuario): se recoge y se
     * coloca como el agua vanilla.
     */
    public static final DeferredHolder<FluidType, FluidType> HFIL_BOILING_WATER_TYPE = FLUID_TYPES.register(
            "hfil_boiling_water",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.zenkai.hfil_boiling_water")
                    .canSwim(true)
                    .canDrown(true)
                    .canExtinguish(true)
                    .canConvertToSource(false) // no se "cultiva" como el agua: es de la poza
                    .density(1000)
                    .viscosity(1000)
                    .temperature(370)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HFIL_BOILING_WATER = FLUIDS.register(
            "hfil_boiling_water", () -> new BaseFlowingFluid.Source(boilingWaterProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> HFIL_BOILING_WATER_FLOWING = FLUIDS.register(
            "flowing_hfil_boiling_water", () -> new BaseFlowingFluid.Flowing(boilingWaterProperties()));

    private static BaseFlowingFluid.Properties boilingWaterProperties() {
        return new BaseFlowingFluid.Properties(HFIL_BOILING_WATER_TYPE, HFIL_BOILING_WATER, HFIL_BOILING_WATER_FLOWING)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .block(ModBlocks.HFIL_BOILING_WATER_BLOCK)
                .bucket(ModItems.HFIL_BOILING_WATER_BUCKET);
    }

    // ── Fluidos de Ki (Fase 2a de la 0.7.0) ──────────────────────────────────────────────
    // Cuatro tipos de energía (ver feature.kichamber.KiEnergyType). Hasta el 2026-11-17 no tenían
    // bloque ni cubo (vivían solo en tanques, como los gases de Mekanism); el usuario pidió cubos para
    // llenarlos desde el tanque de fluidos, así que ahora son líquidos normales: bloque KiFluidBlock
    // (efectos al tocarlos), cubo en ModItems e interacciones con agua, lava y entre ellos en
    // KiFluidInteractions. No se convierten en fuente (canConvertToSource=false): nada de ki infinito.
    // Densidad POSITIVA desde entonces: con -100 los mods con tanques de gas lo trataban como gas.
    // Sus sprites son textures/block/ki_<tipo>_still|flow.png (tools/gen_ki_fluids.py).

    /** Un fluido de Ki registrado: su tipo y sus dos estados. */
    public record KiFluid(DeferredHolder<FluidType, FluidType> type,
                          DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
                          DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing) {}

    public static final KiFluid KI_BASE      = kiFluid("ki_base");
    public static final KiFluid KI_SUPER     = kiFluid("ki_super");
    public static final KiFluid KI_DIVINE    = kiFluid("ki_divine");
    public static final KiFluid KI_LEGENDARY = kiFluid("ki_legendary");

    private static KiFluid kiFluid(String name) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name,
                () -> new FluidType(FluidType.Properties.create()
                        .descriptionId("fluid.zenkai." + name)
                        .canSwim(false)
                        .canDrown(false)
                        .canExtinguish(false)
                        .canConvertToSource(false)
                        .lightLevel(12)      // es energía: los tanques de otros mods lo pintan luminoso
                        .density(800)        // un poco más ligero que el agua; positivo = líquido para los mods
                        .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                        .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)
                        .viscosity(500)
                        .temperature(300)));
        // Los dos estados se refieren el uno al otro: el array hace de caja para la referencia
        // circular, y ninguno se resuelve fuera de la lambda de registro.
        KiFluid[] self = new KiFluid[1];
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source = FLUIDS.register(name,
                () -> new BaseFlowingFluid.Source(kiProperties(self[0], name)));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing = FLUIDS.register("flowing_" + name,
                () -> new BaseFlowingFluid.Flowing(kiProperties(self[0], name)));
        self[0] = new KiFluid(type, source, flowing);
        return self[0];
    }

    /** Bloque y cubo se resuelven por nombre y PEREZOSAMENTE (las suppliers solo se llaman tras el
     *  registro): ModBlocks y ModItems nombran a su vez estos fluidos, y así ningún orden de carga
     *  de clases entre los tres archivos importa. */
    private static BaseFlowingFluid.Properties kiProperties(KiFluid f, String name) {
        return new BaseFlowingFluid.Properties(f.type(), f.source(), f.flowing())
                .levelDecreasePerBlock(2)   // se extiende menos que el agua: 4 bloques
                .block(() -> switch (name) {
                    case "ki_base" -> ModBlocks.KI_BASE_BLOCK.get();
                    case "ki_super" -> ModBlocks.KI_SUPER_BLOCK.get();
                    case "ki_divine" -> ModBlocks.KI_DIVINE_BLOCK.get();
                    default -> ModBlocks.KI_LEGENDARY_BLOCK.get();
                })
                .bucket(() -> switch (name) {
                    case "ki_base" -> ModItems.KI_BASE_BUCKET.get();
                    case "ki_super" -> ModItems.KI_SUPER_BUCKET.get();
                    case "ki_divine" -> ModItems.KI_DIVINE_BUCKET.get();
                    default -> ModItems.KI_LEGENDARY_BUCKET.get();
                });
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
