package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.ImbuingStationBlockEntity;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Estación de imbuido (2026-11-23, Fase 5d de pendientes-0.7.0: la parte de la antigua máquina de fabricación que
 * modifica piezas ya hechas; pestañas Atributos e Infundir). Comportamiento de bloque común en {@link KiMachineBlock};
 * al abrir escribe la pestaña recordada, que ImbuingStationMenu lee tras la posición.
 */
public class ImbuingStationBlock extends KiMachineBlock<ImbuingStationBlockEntity> {

    public static final MapCodec<ImbuingStationBlock> CODEC = simpleCodec(ImbuingStationBlock::new);

    public ImbuingStationBlock(Properties props) {
        super(props, ImbuingStationBlockEntity.class, ModBlockEntities.IMBUING_STATION);
    }

    @Override protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected ImbuingStationBlockEntity create(BlockPos pos, BlockState state) { return new ImbuingStationBlockEntity(pos, state); }

    @Override
    protected BlockEntityTicker<ImbuingStationBlockEntity> serverTicker() { return ImbuingStationBlockEntity::serverTick; }

    @Override
    protected void writeOpenData(ImbuingStationBlockEntity be, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(be.lastTab());
    }
}
