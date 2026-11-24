package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.KiInfuserBlockEntity;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Infusora de Ki (Fase 5c de la 0.7.0). Comportamiento de bloque común en {@link KiMachineBlock}.
 */
public class KiInfuserBlock extends KiMachineBlock<KiInfuserBlockEntity> {

    public static final MapCodec<KiInfuserBlock> CODEC = simpleCodec(KiInfuserBlock::new);

    public KiInfuserBlock(Properties props) {
        super(props, KiInfuserBlockEntity.class, ModBlockEntities.KI_INFUSER);
    }

    @Override protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected KiInfuserBlockEntity create(BlockPos pos, BlockState state) { return new KiInfuserBlockEntity(pos, state); }

    @Override
    protected BlockEntityTicker<KiInfuserBlockEntity> serverTicker() { return KiInfuserBlockEntity::serverTick; }
}
