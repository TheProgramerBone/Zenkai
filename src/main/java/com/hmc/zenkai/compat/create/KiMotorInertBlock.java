package com.hmc.zenkai.compat.create;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Motor de Ki SIN Create: un bloque de vanilla, inerte, con la misma propiedad {@code facing} que
 * {@link KiMotorBlock}. Antes era un {@code Block} pelado y blockstates/ki_motor.json (que solo
 * tiene variantes {@code facing=...}) llenaba el log de «Unknown blockstate property: 'facing'»
 * y el bloque salía sin modelo si se abría sin Create un mundo guardado con él.
 * No extiende nada de Create, así que {@link KiMotorFactory} sí puede nombrarlo.
 */
public final class KiMotorInertBlock extends DirectionalBlock {

    public static final MapCodec<KiMotorInertBlock> CODEC = simpleCodec(KiMotorInertBlock::new);

    public KiMotorInertBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
    }
}
