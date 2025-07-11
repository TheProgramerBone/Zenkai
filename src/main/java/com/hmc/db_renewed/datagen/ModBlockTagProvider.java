package com.hmc.db_renewed.datagen;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.block.ModBlocks;
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
                .add(ModBlocks.WARENAI_CRYSTAL_BLOCK.get())
                .add(ModBlocks.WARENAI_CRYSTAL_ORE.get())
                .add(ModBlocks.DEEPSLATE_WARENAI_CRYSTAL_ORE.get());

        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.WARENAI_CRYSTAL_BLOCK.get())
                .add(ModBlocks.WARENAI_CRYSTAL_ORE.get())
                .add(ModBlocks.DEEPSLATE_WARENAI_CRYSTAL_ORE.get());
    }
}