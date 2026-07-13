package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.ModBlocks;
import com.hmc.zenkai.content.item.ModItems;
import com.hmc.zenkai.util.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends ItemTagsProvider {
    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                              CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, Zenkai.MOD_ID, existingFileHelper);
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

        this.tag(ItemTags.DYEABLE)
                .add(ModItems.SCOUTER.get());

        this.tag(ModTags.Items.DRAGON_BALLS_ITEM)
                .add(ModBlocks.DRAGON_BALL_1.get().asItem())
                .add(ModBlocks.DRAGON_BALL_2.get().asItem())
                .add(ModBlocks.DRAGON_BALL_3.get().asItem())
                .add(ModBlocks.DRAGON_BALL_4.get().asItem())
                .add(ModBlocks.DRAGON_BALL_5.get().asItem())
                .add(ModBlocks.DRAGON_BALL_6.get().asItem())
                .add(ModBlocks.DRAGON_BALL_7.get().asItem())
                .add(ModBlocks.NAMEK_DRAGON_BALL_1.get().asItem())
                .add(ModBlocks.NAMEK_DRAGON_BALL_2.get().asItem())
                .add(ModBlocks.NAMEK_DRAGON_BALL_3.get().asItem())
                .add(ModBlocks.NAMEK_DRAGON_BALL_4.get().asItem())
                .add(ModBlocks.NAMEK_DRAGON_BALL_5.get().asItem())
                .add(ModBlocks.NAMEK_DRAGON_BALL_6.get().asItem())
                .add(ModBlocks.NAMEK_DRAGON_BALL_7.get().asItem());
    }
}