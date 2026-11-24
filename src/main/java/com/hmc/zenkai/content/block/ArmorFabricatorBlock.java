package com.hmc.zenkai.content.block;

import com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Fabricadora de armaduras (Fase 5b de la 0.7.0; desde el 2026-11-23 solo fabrica y duplica: imbuir e infundir están en
 * la estación de imbuido, ImbuingStationBlock). Comportamiento de bloque común en {@link KiMachineBlock}.
 */
public class ArmorFabricatorBlock extends KiMachineBlock<ArmorFabricatorBlockEntity> {

    public static final MapCodec<ArmorFabricatorBlock> CODEC = simpleCodec(ArmorFabricatorBlock::new);

    public ArmorFabricatorBlock(Properties props) {
        super(props, ArmorFabricatorBlockEntity.class, ModBlockEntities.ARMOR_FABRICATOR);
    }

    @Override protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected ArmorFabricatorBlockEntity create(BlockPos pos, BlockState state) { return new ArmorFabricatorBlockEntity(pos, state); }

    @Override
    protected BlockEntityTicker<ArmorFabricatorBlockEntity> serverTicker() { return ArmorFabricatorBlockEntity::serverTick; }
}
