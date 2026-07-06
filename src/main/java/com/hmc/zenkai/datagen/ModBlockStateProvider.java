package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, Zenkai.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        blockWithItem(ModBlocks.TERRAGEM_BLOCK);
        blockWithItem(ModBlocks.TERRAGEM_ORE);
        blockWithItem(ModBlocks.DEEPSLATE_TERRAGEM_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_DIRT);
        blockWithItem(ModBlocks.NAMEKIAN_STONE);
        blockWithItem(ModBlocks.NAMEKIAN_COBBLESTONE);
        blockWithItem(ModBlocks.ROCKY_BLOCK);
        blockWithItem(ModBlocks.HTC_BLOCK);
        blockWithItem(ModBlocks.HTC_PORTAL);
        blockWithItem(ModBlocks.NAMEKIAN_STRUCTURE_BLOCK);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_BLACK);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_BLUE);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_BROWN);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_CYAN);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_GRAY);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_LIGHT_BLUE);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_LIGHT_GRAY);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_DARK_GREEN);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_RED);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_YELLOW);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_MAGENTA);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_WHITE);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_PURPLE);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_GREEN);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_PINK);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_ORANGE);
        blockWithItem(ModBlocks.STRUCTURAL_CONCRETE_DARK_RED);
        blockWithItem(ModBlocks.OTHERWORLD_CLOUD);
    }

    private void blockWithItem(DeferredBlock<?> deferredBlock) {
        simpleBlockWithItem(deferredBlock.get(), cubeAll(deferredBlock.get()));
    }
}