package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.CrusherBlockEntity;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Triturador (Fase 5d de la 0.7.0). Comportamiento de bloque común en {@link KiMachineBlock}.
 */
public class CrusherBlock extends KiMachineBlock<CrusherBlockEntity> {

    public static final MapCodec<CrusherBlock> CODEC = simpleCodec(CrusherBlock::new);

    public CrusherBlock(Properties props) {
        super(props, CrusherBlockEntity.class, ModBlockEntities.CRUSHER);
    }

    @Override protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected CrusherBlockEntity create(BlockPos pos, BlockState state) { return new CrusherBlockEntity(pos, state); }

    @Override
    protected BlockEntityTicker<CrusherBlockEntity> serverTicker() { return CrusherBlockEntity::serverTick; }
}
