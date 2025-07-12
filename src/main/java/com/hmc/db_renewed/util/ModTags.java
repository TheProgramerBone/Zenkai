package com.hmc.db_renewed.util;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> NEEDS_WARENAI_CRYSTAL_TOOL = createTag("needs_warenai_crystal_tool");
        public static final TagKey<Block> INCORRECT_FOR_WARENAI_CRYSTAL_TOOL = createTag("incorrect_for_warenai_crystal_tool");

        private static TagKey<Block> createTag(String name) {
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,name));
        }

    }

    public static class Items{

        private static TagKey<Item> createTag(String name) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID,name));
        }

    }
}
