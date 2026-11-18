package com.hmc.zenkai.compat.create;

import com.hmc.zenkai.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Bloque del Motor de Ki. Sigue el patrón del motor creativo de Create ({@code CreativeMotorBlock}):
 * un bloque direccional cuyo FACING es la cara del eje. Solo se instancia con Create cargado.
 */
public class KiMotorBlock extends DirectionalKineticBlock implements IBE<KiMotorBlockEntity> {

    public KiMotorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hideStressImpact() {
        return false;
    }

    @Override
    public Class<KiMotorBlockEntity> getBlockEntityClass() {
        return KiMotorBlockEntity.class;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public BlockEntityType<? extends KiMotorBlockEntity> getBlockEntityType() {
        return (BlockEntityType) ModBlockEntities.KI_MOTOR.get();
    }

    /**
     * Mano vacía abre la pantalla del motor (modo, velocidad, sentido y puertos). Se abre en el
     * CLIENTE sin menú: no hay inventario que sincronizar, el estado llega por sendData del block
     * entity y cada ajuste viaja como {@link KiMotorActionPacket}, que el servidor valida.
     * La llamada al cliente va en una clase aparte del paquete client: aquí solo se nombra, y la
     * JVM no la carga en un servidor dedicado porque esta rama nunca corre allí.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            com.hmc.zenkai.client.gui.screens.KiMotorScreenOpener.open(pos);
        }
        return InteractionResult.SUCCESS;
    }
}
