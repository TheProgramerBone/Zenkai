package com.hmc.db_renewed.content.blockentity;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.content.block.ModBlocks;
import com.hmc.db_renewed.content.blockentity.AllDragonBalls.AllDragonBallsEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DragonBlockRenewed.MOD_ID);

    public static final Supplier<BlockEntityType<AllDragonBallsEntity>> ALL_DRAGON_BALLS_ENTITY = BLOCK_ENTITIES.register(
            "all_dragon_balls",
            () -> new BlockEntityType<>(
                    AllDragonBallsEntity::new,
                    Set.of(ModBlocks.ALL_DRAGON_BALLS.get()),
                    null
            ));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
