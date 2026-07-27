package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> NEEDS_TERRAGEM_TOOL = createTag("needs_terragem_tool");
        public static final TagKey<Block> INCORRECT_FOR_TERRAGEM_TOOL = createTag("incorrect_for_terragem_tool");
        public static final TagKey<Block> DRAGON_BALLS_BLOCK = createTag("dragon_balls_block");

        private static TagKey<Block> createTag(String name) {
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,name));
        }

    }

    public static class Items{

        public static final TagKey<Item> DRAGON_BALLS_ITEM = createTag("dragon_balls_item");
        public static final TagKey<Item> KEEPS_HAIR = createTag("keeps_hair");

        private static TagKey<Item> createTag(String name) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,name));
        }

    }

    public static class Structures {
        public static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> DRAGON_BALLS =
                TagKey.create(Registries.STRUCTURE,
                        ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "dragon_balls"));
    }

    public static class EntityTypes {
        /** Mobs que merecen escala de jefe. El resto se reparte por MobCategory. */
        public static final TagKey<EntityType<?>> BOSSES = createTag("bosses");

        private static TagKey<EntityType<?>> createTag(String name) {
            return TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
        }
    }
}
