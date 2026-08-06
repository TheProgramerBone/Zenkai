package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModTags;
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
        super(output, lookupProvider, blockTags, Zenkai.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {

        // ==========================================
        // 1. HERRAMIENTAS Y ARMADURAS
        // ==========================================


        // ==========================================
        // 2. ÍTEMS ESPECIALES Y COSMÉTICOS
        // ==========================================

        tag(ItemTags.DYEABLE).add(
                ModItems.SCOUTER.get()
        );

        // ==========================================
        // 3. SET DE MADERA DE AJISA Y VEGETACIÓN
        // ==========================================

        tag(ItemTags.LOGS_THAT_BURN).add(
                ModBlocks.AJISA_LOG.get().asItem(),
                ModBlocks.AJISA_WOOD.get().asItem(),
                ModBlocks.STRIPPED_AJISA_LOG.get().asItem(),
                ModBlocks.STRIPPED_AJISA_WOOD.get().asItem()
        );

        tag(ItemTags.PLANKS).add(ModBlocks.AJISA_PLANKS.get().asItem());
        tag(ItemTags.LEAVES).add(ModBlocks.AJISA_LEAVES.get().asItem());
        tag(ItemTags.SAPLINGS).add(ModBlocks.AJISA_SAPLING.get().asItem());
        tag(ItemTags.WOODEN_SLABS).add(ModBlocks.AJISA_SLAB.get().asItem());
        tag(ItemTags.WOODEN_STAIRS).add(ModBlocks.AJISA_STAIRS.get().asItem());
        tag(ItemTags.WOODEN_FENCES).add(ModBlocks.AJISA_FENCE.get().asItem());
        tag(ItemTags.FENCE_GATES).add(ModBlocks.AJISA_FENCE_GATE.get().asItem());
        tag(ItemTags.WOODEN_DOORS).add(ModBlocks.AJISA_DOOR.get().asItem());
        tag(ItemTags.WOODEN_TRAPDOORS).add(ModBlocks.AJISA_TRAPDOOR.get().asItem());
        tag(ItemTags.WOODEN_BUTTONS).add(ModBlocks.AJISA_BUTTON.get().asItem());
        tag(ItemTags.WOODEN_PRESSURE_PLATES).add(ModBlocks.AJISA_PRESSURE_PLATE.get().asItem());
        tag(ItemTags.SMALL_FLOWERS).add(ModBlocks.AJISA_FLOWER.get().asItem());
        tag(ItemTags.VILLAGER_PLANTABLE_SEEDS).add(ModItems.NAMEKIAN_HERB_SEEDS.get());

        // ==========================================
        // 4. BLOQUES DE CONSTRUCCIÓN (ÍTEMS)
        // ==========================================

        tag(ItemTags.WALLS).add(
                ModBlocks.SACRED_STONE_WALL.get().asItem(),
                ModBlocks.POLISHED_SACRED_STONE_WALL.get().asItem(),
                ModBlocks.SACRED_STONE_BRICK_WALL.get().asItem()
        );

        tag(ItemTags.STAIRS).add(
                ModBlocks.SACRED_STONE_STAIRS.get().asItem(),
                ModBlocks.POLISHED_SACRED_STONE_STAIRS.get().asItem(),
                ModBlocks.SACRED_STONE_BRICK_STAIRS.get().asItem()
        );

        tag(ItemTags.SLABS).add(
                ModBlocks.SACRED_STONE_SLAB.get().asItem(),
                ModBlocks.POLISHED_SACRED_STONE_SLAB.get().asItem(),
                ModBlocks.SACRED_STONE_BRICK_SLAB.get().asItem()
        );

        // ==========================================
        // 5. TAGS PERSONALIZADOS DEL MOD (ZENKAI)
        // ==========================================

        tag(ModTags.Items.KEEPS_HAIR).add(
                ModItems.HALO.get(),
                ModItems.SCOUTER.get()
        );

        tag(ModTags.Items.AJISA_LOGS).add(
                ModBlocks.AJISA_LOG.get().asItem(),
                ModBlocks.AJISA_WOOD.get().asItem(),
                ModBlocks.STRIPPED_AJISA_LOG.get().asItem(),
                ModBlocks.STRIPPED_AJISA_WOOD.get().asItem()
        );

        tag(ModTags.Items.DRAGON_BALLS_ITEM).add(
                ModBlocks.DRAGON_BALL_1.get().asItem(),
                ModBlocks.DRAGON_BALL_2.get().asItem(),
                ModBlocks.DRAGON_BALL_3.get().asItem(),
                ModBlocks.DRAGON_BALL_4.get().asItem(),
                ModBlocks.DRAGON_BALL_5.get().asItem(),
                ModBlocks.DRAGON_BALL_6.get().asItem(),
                ModBlocks.DRAGON_BALL_7.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_1.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_2.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_3.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_4.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_5.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_6.get().asItem(),
                ModBlocks.NAMEK_DRAGON_BALL_7.get().asItem()
        );
    }
}