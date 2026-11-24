package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.AllDragonBalls.AllDragonBallsEntity;
import com.hmc.zenkai.content.blockentity.EnergyGeneratorBlockEntity;
import com.hmc.zenkai.content.blockentity.NpcMarkerBlockEntity;
import com.hmc.zenkai.content.blockentity.PieceConnectorBlockEntity;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Zenkai.MOD_ID);

    public static final Supplier<BlockEntityType<AllDragonBallsEntity>> ALL_DRAGON_BALLS_ENTITY = BLOCK_ENTITIES.register(
            "all_dragon_balls",
            () -> new BlockEntityType<>(
                    AllDragonBallsEntity::new,
                    Set.of(ModBlocks.ALL_DRAGON_BALLS.get(), ModBlocks.NAMEK_ALL_DRAGON_BALLS.get()),
                    null));

    public static final Supplier<BlockEntityType<NpcMarkerBlockEntity>> NPC_MARKER =
            BLOCK_ENTITIES.register("npc_marker", () -> BlockEntityType.Builder
                    .of(NpcMarkerBlockEntity::new, ModBlocks.NPC_MARKER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<PieceConnectorBlockEntity>> PIECE_CONNECTOR =
            BLOCK_ENTITIES.register("piece_connector", () -> BlockEntityType.Builder
                    .of(PieceConnectorBlockEntity::new, ModBlocks.PIECE_CONNECTOR.get())
                    .build(null));

    public static final Supplier<BlockEntityType<ScouterBenchBlockEntity>> SCOUTER_BENCH =
            BLOCK_ENTITIES.register("scouter_bench", () -> BlockEntityType.Builder
                    .of(ScouterBenchBlockEntity::new, ModBlocks.SCOUTER_BENCH.get())
                    .build(null));

    public static final Supplier<BlockEntityType<EnergyGeneratorBlockEntity>> ENERGY_GENERATOR =
            BLOCK_ENTITIES.register("energy_generator", () -> BlockEntityType.Builder
                    .of(EnergyGeneratorBlockEntity::new, ModBlocks.ENERGY_GENERATOR.get())
                    .build(null));

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity>> ARMOR_FABRICATOR =
            BLOCK_ENTITIES.register("armor_fabricator", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity::new, ModBlocks.ARMOR_FABRICATOR.get())
                    .build(null));

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.CrusherBlockEntity>> CRUSHER =
            BLOCK_ENTITIES.register("crusher", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.CrusherBlockEntity::new, ModBlocks.CRUSHER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.ImbuingStationBlockEntity>> IMBUING_STATION =
            BLOCK_ENTITIES.register("imbuing_station", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.ImbuingStationBlockEntity::new, ModBlocks.IMBUING_STATION.get())
                    .build(null));

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.KiInfuserBlockEntity>> KI_INFUSER =
            BLOCK_ENTITIES.register("ki_infuser", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.KiInfuserBlockEntity::new, ModBlocks.KI_INFUSER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity>> KI_CONTAINMENT_CHAMBER =
            BLOCK_ENTITIES.register("ki_containment_chamber", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity::new,
                            ModBlocks.KI_CONTAINMENT_CHAMBER.get())
                    .build(null));

    /** Motor de Ki (compat Create). El factory solo se invoca al colocar el bloque real, que solo existe con Create. */
    public static final Supplier<BlockEntityType<net.minecraft.world.level.block.entity.BlockEntity>> KI_MOTOR =
            BLOCK_ENTITIES.register("ki_motor", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.compat.create.KiMotorFactory::blockEntity, ModBlocks.KI_MOTOR.get())
                    .build(null));

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.EnergyTransportBlockEntity>> ENERGY_TRANSPORT =
            BLOCK_ENTITIES.register("energy_transport", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.EnergyTransportBlockEntity::new,
                            ModBlocks.ENERGY_CABLE.get(), ModBlocks.ENERGY_TANK.get())
                    .build(null));

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.FluidTransportBlockEntity>> FLUID_TRANSPORT =
            BLOCK_ENTITIES.register("fluid_transport", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.FluidTransportBlockEntity::new,
                            ModBlocks.FLUID_PIPE.get(), ModBlocks.FLUID_TANK.get())
                    .build(null));

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity>> GRAVITY_CHAMBER =
            BLOCK_ENTITIES.register("gravity_chamber", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity::new,
                            ModBlocks.GRAVITY_CHAMBER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.GravityChamberPortBlockEntity>> GRAVITY_CHAMBER_PORT =
            BLOCK_ENTITIES.register("gravity_chamber_port", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.GravityChamberPortBlockEntity::new,
                            ModBlocks.GRAVITY_CHAMBER_PORT.get())
                    .build(null));

    /** Cápsula Hoi-Poi colocada (Fase 6): guarda el stack y el modo (contorno / armada / llena). */
    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.CapsuleBlockEntity>> CAPSULE =
            BLOCK_ENTITIES.register("capsule", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.CapsuleBlockEntity::new, ModBlocks.CAPSULE.get())
                    .build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
