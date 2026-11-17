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

    public static final Supplier<BlockEntityType<com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity>> KI_CONTAINMENT_CHAMBER =
            BLOCK_ENTITIES.register("ki_containment_chamber", () -> BlockEntityType.Builder
                    .of(com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity::new,
                            ModBlocks.KI_CONTAINMENT_CHAMBER.get())
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

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
