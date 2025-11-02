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
        public static final TagKey<Block> NEEDS_TERRAGEM_TOOL = createTag("needs_terragem_tool");
        public static final TagKey<Block> INCORRECT_FOR_TERRAGEM_TOOL = createTag("incorrect_for_terragem_tool");
        public static final TagKey<Block> DRAGON_BALLS = createTag("dragon_balls");

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
