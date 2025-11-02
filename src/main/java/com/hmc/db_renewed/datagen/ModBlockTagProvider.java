package com.hmc.db_renewed.datagen;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.block.ModBlocks;
import com.hmc.db_renewed.util.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, DragonBlockRenewed.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.TERRAGEM_BLOCK.get())
                .add(ModBlocks.TERRAGEM_ORE.get())
                .add(ModBlocks.DEEPSLATE_TERRAGEM_ORE.get())
                .add(ModBlocks.ROCKY_BLOCK.get())
                .add(ModBlocks.NAMEKIAN_STONE.get())
                .add(ModBlocks.NAMEKIAN_COBBLESTONE.get())
                .add(ModBlocks.NAMEKIAN_STRUCTURE_BLOCK.get());

        tag(BlockTags.DEAD_BUSH_MAY_PLACE_ON)
                .add(ModBlocks.ROCKY_BLOCK.get());

        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.TERRAGEM_BLOCK.get())
                .add(ModBlocks.TERRAGEM_ORE.get())
                .add(ModBlocks.DEEPSLATE_TERRAGEM_ORE.get());

        tag(ModTags.Blocks.NEEDS_TERRAGEM_TOOL)
                .addTag(BlockTags.NEEDS_IRON_TOOL);

        tag(ModTags.Blocks.INCORRECT_FOR_TERRAGEM_TOOL)
                .addTag(BlockTags.INCORRECT_FOR_IRON_TOOL)
                .remove(ModTags.Blocks.NEEDS_TERRAGEM_TOOL);

        tag(BlockTags.MINEABLE_WITH_SHOVEL)
                .add(ModBlocks.NAMEKIAN_DIRT.get())
                .add(ModBlocks.NAMEKIAN_GRASS_BLOCK.get());

        tag(BlockTags.DIRT)
                .add(ModBlocks.NAMEKIAN_DIRT.get())
                .add(ModBlocks.NAMEKIAN_GRASS_BLOCK.get());

        tag(ModTags.Blocks.DRAGON_BALLS)
                .add(ModBlocks.DRAGON_BALL_STONE.get())
                .add(ModBlocks.DRAGON_BALL_1.get())
                .add(ModBlocks.DRAGON_BALL_2.get())
                .add(ModBlocks.DRAGON_BALL_3.get())
                .add(ModBlocks.DRAGON_BALL_4.get())
                .add(ModBlocks.DRAGON_BALL_5.get())
                .add(ModBlocks.DRAGON_BALL_6.get())
                .add(ModBlocks.DRAGON_BALL_7.get())
                .add(ModBlocks.NAMEK_DRAGON_BALL_STONE.get())
                .add(ModBlocks.NAMEK_DRAGON_BALL_1.get())
                .add(ModBlocks.NAMEK_DRAGON_BALL_2.get())
                .add(ModBlocks.NAMEK_DRAGON_BALL_3.get())
                .add(ModBlocks.NAMEK_DRAGON_BALL_4.get())
                .add(ModBlocks.NAMEK_DRAGON_BALL_5.get())
                .add(ModBlocks.NAMEK_DRAGON_BALL_6.get())
                .add(ModBlocks.NAMEK_DRAGON_BALL_7.get())
                .add(ModBlocks.ALL_DRAGON_BALLS.get());
    }
}