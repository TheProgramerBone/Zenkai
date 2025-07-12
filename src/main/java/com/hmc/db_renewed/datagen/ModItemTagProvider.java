package com.hmc.db_renewed.datagen;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends ItemTagsProvider {
    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                              CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, DragonBlockRenewed.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        tag(ItemTags.SWORDS)
                .add(ModItems.WARENAI_CRYSTAL_SWORD.get());
        tag(ItemTags.PICKAXES)
                .add(ModItems.WARENAI_CRYSTAL_PICKAXE.get());
        tag(ItemTags.AXES)
                .add(ModItems.WARENAI_CRYSTAL_AXE.get());
        tag(ItemTags.SHOVELS)
                .add(ModItems.WARENAI_CRYSTAL_SHOVEL.get());
        tag(ItemTags.HOES)
                .add(ModItems.WARENAI_CRYSTAL_HOE.get());

        this.tag(ItemTags.TRIMMABLE_ARMOR)
                .add(ModItems.WARENAI_CRYSTAL_HELMET.get())
                .add(ModItems.WARENAI_CRYSTAL_CHESTPLATE.get())
                .add(ModItems.WARENAI_CRYSTAL_LEGGINGS.get())
                .add(ModItems.WARENAI_CRYSTAL_BOOTS.get());

    }
}