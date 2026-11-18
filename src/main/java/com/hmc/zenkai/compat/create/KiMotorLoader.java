package com.hmc.zenkai.compat.create;

import com.hmc.zenkai.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Aquí SÍ se nombran las clases que extienden Create. Solo se carga desde {@link KiMotorFactory} con Create presente. */
final class KiMotorLoader {
    private KiMotorLoader() {}

    static Block block(Block.Properties properties) {
        return new KiMotorBlock(properties);
    }

    static BlockEntity blockEntity(BlockPos pos, BlockState state) {
        return new KiMotorBlockEntity(ModBlockEntities.KI_MOTOR.get(), pos, state);
    }

    @SuppressWarnings("unchecked")
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        BlockEntityType<KiMotorBlockEntity> type = (BlockEntityType<KiMotorBlockEntity>) (BlockEntityType<?>) ModBlockEntities.KI_MOTOR.get();
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energyAt(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> be.fluidAt(side));
    }
}
