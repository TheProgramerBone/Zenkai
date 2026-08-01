package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.NamekianHerbCropBlock;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
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
        simpleBlockWithItem(ModBlocks.NAMEKIAN_DIRT_PATH.get(),
                models().withExistingParent("namekian_dirt_path", mcLoc("block/dirt_path"))
                        .texture("top",  modLoc("block/namekian_dirt_path_top"))
                        .texture("side", modLoc("block/namekian_dirt_path_side")));
        blockWithItem(ModBlocks.NAMEKIAN_STONE);
        blockWithItem(ModBlocks.NAMEKIAN_COBBLESTONE);
        blockWithItem(ModBlocks.NAMEKIAN_SAND);
        blockWithItem(ModBlocks.NAMEKIAN_GRAVEL);
        blockWithItem(ModBlocks.NAMEKIAN_COAL_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_IRON_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_COPPER_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_GOLD_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_REDSTONE_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_LAPIS_ORE);
        blockWithItem(ModBlocks.NAMEKIAN_DIAMOND_ORE);
        blockWithItem(ModBlocks.ROCKY_BLOCK);
        blockWithItem(ModBlocks.HTC_BLOCK);
        blockWithItem(ModBlocks.HTC_PORTAL);
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
        blockWithItem(ModBlocks.NAMEKIAN_LAMP);
        blockWithItem(ModBlocks.NPC_MARKER);
        makeCrop((CropBlock) ModBlocks.NAMEKIAN_HERB_CROP.get(), "namekian_herb_crop_stage", "namekian_herb_crop_stage");
        logBlock((RotatedPillarBlock) ModBlocks.AJISA_LOG.get());
        logBlock((RotatedPillarBlock) ModBlocks.STRIPPED_AJISA_LOG.get());
        axisBlock((RotatedPillarBlock) ModBlocks.AJISA_WOOD.get(),
                modLoc("block/ajisa_log"), modLoc("block/ajisa_log"));
        axisBlock((RotatedPillarBlock) ModBlocks.STRIPPED_AJISA_WOOD.get(),
                modLoc("block/stripped_ajisa_log"), modLoc("block/stripped_ajisa_log"));

        blockWithItem(ModBlocks.AJISA_PLANKS);

        simpleBlockWithItem(ModBlocks.AJISA_LEAVES.get(),
                models().cubeAll("ajisa_leaves", modLoc("block/ajisa_leaves")).renderType("cutout_mipped"));

        simpleBlockWithItem(ModBlocks.AJISA_SAPLING.get(),
                models().cross("ajisa_sapling", modLoc("block/ajisa_sapling")).renderType("cutout"));
        simpleBlockWithItem(ModBlocks.AJISA_FLOWER.get(),
                models().cross("ajisa_flower", modLoc("block/ajisa_flower")).renderType("cutout"));

        stairsBlock((StairBlock) ModBlocks.AJISA_STAIRS.get(), modLoc("block/ajisa_planks"));
        slabBlock((SlabBlock) ModBlocks.AJISA_SLAB.get(), modLoc("block/ajisa_planks"), modLoc("block/ajisa_planks"));
        fenceBlock((FenceBlock) ModBlocks.AJISA_FENCE.get(), modLoc("block/ajisa_planks"));
        fenceGateBlock((FenceGateBlock) ModBlocks.AJISA_FENCE_GATE.get(), modLoc("block/ajisa_planks"));
        doorBlockWithRenderType((DoorBlock) ModBlocks.AJISA_DOOR.get(),
                modLoc("block/ajisa_door_bottom"), modLoc("block/ajisa_door_top"), "cutout");
        trapdoorBlockWithRenderType((TrapDoorBlock) ModBlocks.AJISA_TRAPDOOR.get(),
                modLoc("block/ajisa_trapdoor"), true, "cutout");
        buttonBlock((ButtonBlock) ModBlocks.AJISA_BUTTON.get(), modLoc("block/ajisa_planks"));
        pressurePlateBlock((PressurePlateBlock) ModBlocks.AJISA_PRESSURE_PLATE.get(), modLoc("block/ajisa_planks"));

        blockWithItem(ModBlocks.NAMEK_CRYSTAL_ORE);
        blockWithItem(ModBlocks.ENERGY_CRYSTAL_ORE);
        blockWithItem(ModBlocks.SACRED_STONE_ORE);
        blockWithItem(ModBlocks.NAMEK_CRYSTAL_BLOCK);
        blockWithItem(ModBlocks.ENERGY_CRYSTAL_BLOCK);
        blockWithItem(ModBlocks.SACRED_STONE_BLOCK);
        blockWithItem(ModBlocks.POLISHED_SACRED_STONE);
        blockWithItem(ModBlocks.SACRED_STONE_BRICKS);

        stairsBlock((StairBlock) ModBlocks.SACRED_STONE_STAIRS.get(), modLoc("block/sacred_stone_block"));
        slabBlock((SlabBlock) ModBlocks.SACRED_STONE_SLAB.get(), modLoc("block/sacred_stone_block"), modLoc("block/sacred_stone_block"));
        wallBlock((WallBlock) ModBlocks.SACRED_STONE_WALL.get(), modLoc("block/sacred_stone_block"));

        stairsBlock((StairBlock) ModBlocks.POLISHED_SACRED_STONE_STAIRS.get(), modLoc("block/polished_sacred_stone"));
        slabBlock((SlabBlock) ModBlocks.POLISHED_SACRED_STONE_SLAB.get(), modLoc("block/polished_sacred_stone"), modLoc("block/polished_sacred_stone"));
        wallBlock((WallBlock) ModBlocks.POLISHED_SACRED_STONE_WALL.get(), modLoc("block/polished_sacred_stone"));

        stairsBlock((StairBlock) ModBlocks.SACRED_STONE_BRICK_STAIRS.get(), modLoc("block/sacred_stone_bricks"));
        slabBlock((SlabBlock) ModBlocks.SACRED_STONE_BRICK_SLAB.get(), modLoc("block/sacred_stone_bricks"), modLoc("block/sacred_stone_bricks"));
        wallBlock((WallBlock) ModBlocks.SACRED_STONE_BRICK_WALL.get(), modLoc("block/sacred_stone_bricks"));
    }

    private void blockWithItem(DeferredBlock<?> deferredBlock) {
        simpleBlockWithItem(deferredBlock.get(), cubeAll(deferredBlock.get()));
    }

    private void makeCrop(CropBlock block, String modelName, String textureName) {
        getVariantBuilder(block).forAllStates(state -> {
            int age = state.getValue(NamekianHerbCropBlock.AGE);
            return ConfiguredModel.builder().modelFile(
                    models().crop(modelName + age, modLoc("block/" + textureName + age)).renderType("cutout")).build();
        });
    }
}