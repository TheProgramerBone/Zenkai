package com.hmc.db_renewed.datagen;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.block.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, DragonBlockRenewed.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        blockWithItem(ModBlocks.WARENAI_CRYSTAL_BLOCK);
        blockWithItem(ModBlocks.WARENAI_CRYSTAL_ORE);
        blockWithItem(ModBlocks.DEEPSLATE_WARENAI_CRYSTAL_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_DIRT_BLOCK);
        blockWithItem(ModBlocks.ROCKY_BLOCK);
    }

    private void blockWithItem(DeferredBlock<?> deferredBlock) {
        simpleBlockWithItem(deferredBlock.get(), cubeAll(deferredBlock.get()));
    }
}