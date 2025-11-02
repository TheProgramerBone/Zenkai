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
                .add(ModItems.TERRAGEM_SWORD.get());
        tag(ItemTags.PICKAXES)
                .add(ModItems.TERRAGEM_PICKAXE.get());
        tag(ItemTags.AXES)
                .add(ModItems.TERRAGEM_AXE.get());
        tag(ItemTags.SHOVELS)
                .add(ModItems.TERRAGEM_SHOVEL.get());
        tag(ItemTags.HOES)
                .add(ModItems.TERRAGEM_HOE.get());

        this.tag(ItemTags.TRIMMABLE_ARMOR)
                .add(ModItems.TERRAGEM_HELMET.get())
                .add(ModItems.TERRAGEM_CHESTPLATE.get())
                .add(ModItems.TERRAGEM_LEGGINGS.get())
                .add(ModItems.TERRAGEM_BOOTS.get());

        this.tag(ItemTags.ARMOR_ENCHANTABLE)
                .add(ModItems.TERRAGEM_HELMET.get())
                .add(ModItems.TERRAGEM_CHESTPLATE.get())
                .add(ModItems.TERRAGEM_LEGGINGS.get())
                .add(ModItems.TERRAGEM_BOOTS.get());
    }
}