package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModTags;
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
        super(output, lookupProvider, Zenkai.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {

        // ==========================================
        // 1. HERRAMIENTAS Y HERRAMIENTAS REQUERIDAS
        // ==========================================

        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                // Bloques base y de construcción
                ModBlocks.TERRAGEM_BLOCK.get(),
                ModBlocks.TERRAGEM_ORE.get(),
                ModBlocks.DEEPSLATE_TERRAGEM_ORE.get(),
                ModBlocks.ROCKY_BLOCK.get(),
                ModBlocks.NAMEKIAN_STONE.get(),
                ModBlocks.NAMEKIAN_COBBLESTONE.get(),

                // Minerales de Namek
                ModBlocks.NAMEKIAN_COAL_ORE.get(),
                ModBlocks.NAMEKIAN_IRON_ORE.get(),
                ModBlocks.NAMEKIAN_COPPER_ORE.get(),
                ModBlocks.NAMEKIAN_GOLD_ORE.get(),
                ModBlocks.NAMEKIAN_REDSTONE_ORE.get(),
                ModBlocks.NAMEKIAN_LAPIS_ORE.get(),
                ModBlocks.NAMEKIAN_DIAMOND_ORE.get(),

                // Cristal y Piedra Sagrada
                ModBlocks.NAMEK_CRYSTAL_ORE.get(),
                ModBlocks.ENERGY_CRYSTAL_ORE.get(),
                ModBlocks.SACRED_STONE_ORE.get(),
                ModBlocks.NAMEK_CRYSTAL_BLOCK.get(),
                ModBlocks.ENERGY_CRYSTAL_BLOCK.get(),
                ModBlocks.SACRED_STONE_BLOCK.get(),
                ModBlocks.SACRED_STONE_STAIRS.get(),
                ModBlocks.SACRED_STONE_SLAB.get(),
                ModBlocks.SACRED_STONE_WALL.get(),
                ModBlocks.POLISHED_SACRED_STONE.get(),
                ModBlocks.POLISHED_SACRED_STONE_STAIRS.get(),
                ModBlocks.POLISHED_SACRED_STONE_SLAB.get(),
                ModBlocks.POLISHED_SACRED_STONE_WALL.get(),
                ModBlocks.SACRED_STONE_BRICKS.get(),
                ModBlocks.SACRED_STONE_BRICK_STAIRS.get(),
                ModBlocks.SACRED_STONE_BRICK_SLAB.get(),
                ModBlocks.SACRED_STONE_BRICK_WALL.get(),
                ModBlocks.NAMEKIAN_LAMP.get()
        );

        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(
                ModBlocks.NAMEKIAN_DIRT.get(),
                ModBlocks.NAMEKIAN_GRASS_BLOCK.get(),
                ModBlocks.NAMEKIAN_SAND.get(),
                ModBlocks.NAMEKIAN_GRAVEL.get()
        );

        tag(BlockTags.MINEABLE_WITH_AXE).add(
                ModBlocks.AJISA_LOG.get(),
                ModBlocks.AJISA_WOOD.get(),
                ModBlocks.STRIPPED_AJISA_LOG.get(),
                ModBlocks.STRIPPED_AJISA_WOOD.get(),
                ModBlocks.AJISA_PLANKS.get(),
                ModBlocks.AJISA_SLAB.get(),
                ModBlocks.AJISA_STAIRS.get(),
                ModBlocks.AJISA_FENCE.get(),
                ModBlocks.AJISA_FENCE_GATE.get(),
                ModBlocks.AJISA_DOOR.get(),
                ModBlocks.AJISA_TRAPDOOR.get(),
                ModBlocks.AJISA_BUTTON.get(),
                ModBlocks.AJISA_PRESSURE_PLATE.get()
        );

        tag(BlockTags.MINEABLE_WITH_HOE).add(
                ModBlocks.AJISA_LEAVES.get()
        );

        // ==========================================
        // 2. NIVELES DE HERRAMIENTA (TIERS)
        // ==========================================

        tag(BlockTags.NEEDS_STONE_TOOL).add(
                ModBlocks.NAMEKIAN_IRON_ORE.get(),
                ModBlocks.NAMEKIAN_COPPER_ORE.get(),
                ModBlocks.NAMEKIAN_LAPIS_ORE.get(),
                ModBlocks.SACRED_STONE_ORE.get()
        );

        tag(BlockTags.NEEDS_IRON_TOOL).add(
                ModBlocks.TERRAGEM_BLOCK.get(),
                ModBlocks.TERRAGEM_ORE.get(),
                ModBlocks.DEEPSLATE_TERRAGEM_ORE.get(),
                ModBlocks.NAMEKIAN_GOLD_ORE.get(),
                ModBlocks.NAMEKIAN_REDSTONE_ORE.get(),
                ModBlocks.NAMEKIAN_DIAMOND_ORE.get(),
                ModBlocks.NAMEK_CRYSTAL_ORE.get(),
                ModBlocks.NAMEK_CRYSTAL_BLOCK.get()
        );

        tag(BlockTags.NEEDS_DIAMOND_TOOL).add(
                ModBlocks.ENERGY_CRYSTAL_ORE.get(),
                ModBlocks.ENERGY_CRYSTAL_BLOCK.get()
        );

        // ==========================================
        // 3. SET DE MADERA DE AJISA Y VEGETACIÓN
        // ==========================================

        tag(BlockTags.LOGS_THAT_BURN).add(
                ModBlocks.AJISA_LOG.get(),
                ModBlocks.AJISA_WOOD.get(),
                ModBlocks.STRIPPED_AJISA_LOG.get(),
                ModBlocks.STRIPPED_AJISA_WOOD.get()
        );

        tag(BlockTags.PLANKS).add(ModBlocks.AJISA_PLANKS.get());
        tag(BlockTags.LEAVES).add(ModBlocks.AJISA_LEAVES.get());
        tag(BlockTags.SAPLINGS).add(ModBlocks.AJISA_SAPLING.get());
        tag(BlockTags.WOODEN_SLABS).add(ModBlocks.AJISA_SLAB.get());
        tag(BlockTags.WOODEN_STAIRS).add(ModBlocks.AJISA_STAIRS.get());
        tag(BlockTags.WOODEN_FENCES).add(ModBlocks.AJISA_FENCE.get());
        tag(BlockTags.FENCE_GATES).add(ModBlocks.AJISA_FENCE_GATE.get());
        tag(BlockTags.WOODEN_DOORS).add(ModBlocks.AJISA_DOOR.get());
        tag(BlockTags.WOODEN_TRAPDOORS).add(ModBlocks.AJISA_TRAPDOOR.get());
        tag(BlockTags.WOODEN_BUTTONS).add(ModBlocks.AJISA_BUTTON.get());
        tag(BlockTags.WOODEN_PRESSURE_PLATES).add(ModBlocks.AJISA_PRESSURE_PLATE.get());
        tag(BlockTags.SMALL_FLOWERS).add(ModBlocks.AJISA_FLOWER.get());

        // ==========================================
        // 4. BLOQUES DE CONSTRUCCIÓN Y ESTRUCTURALES
        // ==========================================

        tag(BlockTags.DIRT).add(
                ModBlocks.NAMEKIAN_DIRT.get(),
                ModBlocks.NAMEKIAN_GRASS_BLOCK.get()
        );

        tag(BlockTags.WALLS).add(
                ModBlocks.SACRED_STONE_WALL.get(),
                ModBlocks.POLISHED_SACRED_STONE_WALL.get(),
                ModBlocks.SACRED_STONE_BRICK_WALL.get()
        );

        tag(BlockTags.STAIRS).add(
                ModBlocks.SACRED_STONE_STAIRS.get(),
                ModBlocks.POLISHED_SACRED_STONE_STAIRS.get(),
                ModBlocks.SACRED_STONE_BRICK_STAIRS.get()
        );

        tag(BlockTags.SLABS).add(
                ModBlocks.SACRED_STONE_SLAB.get(),
                ModBlocks.POLISHED_SACRED_STONE_SLAB.get(),
                ModBlocks.SACRED_STONE_BRICK_SLAB.get()
        );

        // ==========================================
        // 5. GENERACIÓN DE MUNDO Y ENTORNO
        // ==========================================

        tag(BlockTags.SCULK_REPLACEABLE).add(
                ModBlocks.ROCKY_BLOCK.get(),
                ModBlocks.NAMEKIAN_GRASS_BLOCK.get(),
                ModBlocks.NAMEKIAN_STONE.get(),
                ModBlocks.NAMEKIAN_COBBLESTONE.get(),
                ModBlocks.NAMEKIAN_DIRT.get()
        );

        tag(BlockTags.CROPS).add(
                ModBlocks.NAMEKIAN_HERB_CROP.get());

        tag(BlockTags.DEAD_BUSH_MAY_PLACE_ON).add(
                ModBlocks.ROCKY_BLOCK.get()
        );

        tag(BlockTags.OVERWORLD_CARVER_REPLACEABLES).add(
                ModBlocks.NAMEKIAN_STONE.get(),
                ModBlocks.NAMEKIAN_DIRT.get(),
                ModBlocks.NAMEKIAN_GRASS_BLOCK.get(),
                ModBlocks.NAMEKIAN_SAND.get(),
                ModBlocks.NAMEKIAN_GRAVEL.get()
        );

        // ==========================================
        // 6. TAGS PERSONALIZADOS DEL MOD (ZENKAI)
        // ==========================================

        tag(ModTags.Blocks.NEEDS_TERRAGEM_TOOL)
                .addTag(BlockTags.NEEDS_IRON_TOOL);

        tag(ModTags.Blocks.INCORRECT_FOR_TERRAGEM_TOOL)
                .addTag(BlockTags.INCORRECT_FOR_IRON_TOOL)
                .remove(ModTags.Blocks.NEEDS_TERRAGEM_TOOL);

        tag(ModTags.Blocks.NAMEKIAN_ORE_REPLACEABLES).add(
                ModBlocks.NAMEKIAN_STONE.get()
        );

        tag(ModTags.Blocks.DRAGON_BALLS_BLOCK).add(
                ModBlocks.DRAGON_BALL_1.get(),
                ModBlocks.DRAGON_BALL_2.get(),
                ModBlocks.DRAGON_BALL_3.get(),
                ModBlocks.DRAGON_BALL_4.get(),
                ModBlocks.DRAGON_BALL_5.get(),
                ModBlocks.DRAGON_BALL_6.get(),
                ModBlocks.DRAGON_BALL_7.get(),
                ModBlocks.NAMEK_DRAGON_BALL_1.get(),
                ModBlocks.NAMEK_DRAGON_BALL_2.get(),
                ModBlocks.NAMEK_DRAGON_BALL_3.get(),
                ModBlocks.NAMEK_DRAGON_BALL_4.get(),
                ModBlocks.NAMEK_DRAGON_BALL_5.get(),
                ModBlocks.NAMEK_DRAGON_BALL_6.get(),
                ModBlocks.NAMEK_DRAGON_BALL_7.get(),
                ModBlocks.ALL_DRAGON_BALLS.get()
        );
    }
}