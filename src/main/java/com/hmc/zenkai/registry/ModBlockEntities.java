package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.AllDragonBalls.AllDragonBallsEntity;
import com.hmc.zenkai.content.blockentity.NpcMarkerBlockEntity;
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
                    Set.of(ModBlocks.ALL_DRAGON_BALLS.get()),
                    null));

    public static final Supplier<BlockEntityType<NpcMarkerBlockEntity>> NPC_MARKER =
            BLOCK_ENTITIES.register("npc_marker", () -> BlockEntityType.Builder
                    .of(NpcMarkerBlockEntity::new, ModBlocks.NPC_MARKER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<ScouterBenchBlockEntity>> SCOUTER_BENCH =
            BLOCK_ENTITIES.register("scouter_bench", () -> BlockEntityType.Builder
                    .of(ScouterBenchBlockEntity::new, ModBlocks.SCOUTER_BENCH.get())
                    .build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
