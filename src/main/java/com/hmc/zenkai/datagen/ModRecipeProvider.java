package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.DRAGON_BALL_1.get(), 1)
                .requires(ModBlocks.DRAGON_BALL_7)
                .unlockedBy("has_dragon_ball_7", has(ModBlocks.DRAGON_BALL_7)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.DRAGON_BALL_7.get(), 1)
                .requires(ModBlocks.DRAGON_BALL_6)
                .unlockedBy("has_dragon_ball_6", has(ModBlocks.DRAGON_BALL_6)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.DRAGON_BALL_6.get(), 1)
                .requires(ModBlocks.DRAGON_BALL_5)
                .unlockedBy("has_dragon_ball_5", has(ModBlocks.DRAGON_BALL_5)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.DRAGON_BALL_5.get(), 1)
                .requires(ModBlocks.DRAGON_BALL_4)
                .unlockedBy("has_dragon_ball_4", has(ModBlocks.DRAGON_BALL_4)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.DRAGON_BALL_4.get(), 1)
                .requires(ModBlocks.DRAGON_BALL_3)
                .unlockedBy("has_dragon_ball_3", has(ModBlocks.DRAGON_BALL_3)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.DRAGON_BALL_3.get(), 1)
                .requires(ModBlocks.DRAGON_BALL_2)
                .unlockedBy("has_dragon_ball_2", has(ModBlocks.DRAGON_BALL_2)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.DRAGON_BALL_2.get(), 1)
                .requires(ModBlocks.DRAGON_BALL_1)
                .unlockedBy("has_dragon_ball_1", has(ModBlocks.DRAGON_BALL_1)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.NAMEK_DRAGON_BALL_1.get(), 1)
                .requires(ModBlocks.NAMEK_DRAGON_BALL_7)
                .unlockedBy("has_namek_dragon_ball_7", has(ModBlocks.NAMEK_DRAGON_BALL_7)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.NAMEK_DRAGON_BALL_7.get(), 1)
                .requires(ModBlocks.NAMEK_DRAGON_BALL_6)
                .unlockedBy("has_namek_dragon_ball_6", has(ModBlocks.NAMEK_DRAGON_BALL_6)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.NAMEK_DRAGON_BALL_6.get(), 1)
                .requires(ModBlocks.NAMEK_DRAGON_BALL_5)
                .unlockedBy("has_namek_dragon_ball_5", has(ModBlocks.NAMEK_DRAGON_BALL_5)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.NAMEK_DRAGON_BALL_5.get(), 1)
                .requires(ModBlocks.NAMEK_DRAGON_BALL_4)
                .unlockedBy("has_namek_dragon_ball_4", has(ModBlocks.NAMEK_DRAGON_BALL_4)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.NAMEK_DRAGON_BALL_4.get(), 1)
                .requires(ModBlocks.NAMEK_DRAGON_BALL_3)
                .unlockedBy("has_namek_dragon_ball_3", has(ModBlocks.NAMEK_DRAGON_BALL_3)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.NAMEK_DRAGON_BALL_3.get(), 1)
                .requires(ModBlocks.NAMEK_DRAGON_BALL_2)
                .unlockedBy("has_namek_dragon_ball_2", has(ModBlocks.NAMEK_DRAGON_BALL_2)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.NAMEK_DRAGON_BALL_2.get(), 1)
                .requires(ModBlocks.NAMEK_DRAGON_BALL_1)
                .unlockedBy("has_namek_dragon_ball_1", has(ModBlocks.NAMEK_DRAGON_BALL_1)).save(recipeOutput);

        List<ItemLike> NAMEKIAN_COBBLESTONE = List.of(ModBlocks.NAMEKIAN_COBBLESTONE);
        oreSmelting(recipeOutput, NAMEKIAN_COBBLESTONE, RecipeCategory.MISC, ModBlocks.NAMEKIAN_STONE.get(), 0.1f, 200, "namekian");
        oreBlasting(recipeOutput, List.of(ModItems.RAW_KATCHIN), RecipeCategory.MISC,
                ModItems.KATCHIN_INGOT.get(), 1.5f, 400, "katchin");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.HALO.get(),1)
                .pattern("YYY")
                .pattern("Y Y")
                .pattern("YYY")
                .define('Y', Items.YELLOW_WOOL)
                .unlockedBy("has_wool", has(Items.YELLOW_WOOL)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.SCOUTER.get(),1)
                .pattern("III")
                .pattern("IGI")
                .pattern("RCR")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('G',Tags.Items.GLASS_BLOCKS)
                .define('C',ModItems.ADVANCED_CIRCUIT)
                .define('R',Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_advanced_circuit", has(ModItems.ADVANCED_CIRCUIT)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SCOUTER_RADAR_UPGRADE.get(),1)
                .pattern("IEI")
                .pattern("GDG")
                .pattern("ICI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('G',Tags.Items.GLASS_BLOCKS)
                .define('D', ModTags.Items.DRAGON_BALLS_ITEM)
                .define('C',ModItems.ELITE_CIRCUIT)
                .define('E',Items.EMERALD)
                .unlockedBy("has_scouter", has(ModItems.SCOUTER)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.DRAGON_BALL_RADAR.get(),1)
                .pattern("ICI")
                .pattern("ADA")
                .pattern("ICI")
                .define('D', Items.DIAMOND)
                .define('I', Items.IRON_INGOT)
                .define('A', Items.AMETHYST_SHARD)
                .define('C', itemtag("c:circuits/advanced"))
                .unlockedBy("has_amethyst",has(Items.AMETHYST_SHARD)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.NAMEKIAN_LAMP.get(), 1)
                .pattern(" S ")
                .pattern("SCS")
                .pattern(" S ")
                .define('S', ModItems.SACRED_STONE.get())
                .define('C', ModItems.ENERGY_CRYSTAL.get())
                .unlockedBy("has_energy_crystal", has(ModItems.ENERGY_CRYSTAL.get())).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.HEALING_WATER_BOTTLE.get(), 1)
                .requires(Items.POTION)
                .requires(ModItems.NAMEKIAN_HERB.get(), 2)
                .unlockedBy("has_namekian_herb", has(ModItems.NAMEKIAN_HERB.get())).save(recipeOutput);


        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.SPACE_POD_ITEM.get(),1)
                .pattern("IRI")
                .pattern("BCB")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('B', itemtag("c:circuits/basic"))
                .define('R', Items.RED_DYE)
                .define('C', itemtag("c:circuits/advanced"))
                .unlockedBy("has_amethyst",has(Items.IRON_INGOT)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.BASIC_CIRCUIT.get(),1)
                .pattern(" C ")
                .pattern("CRC")
                .pattern(" C ")
                .define('R', Items.REDSTONE)
                .define('C', itemtag("c:ingots/copper"))
                .unlockedBy("has_redstone",has(Items.REDSTONE)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.ADVANCED_CIRCUIT.get(),1)
                .pattern(" G ")
                .pattern("ICI")
                .pattern(" G ")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GOLD_INGOT)
                .define('C', itemtag("c:circuits/basic"))
                .unlockedBy("has_basic",has(ModItems.BASIC_CIRCUIT)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.ELITE_CIRCUIT.get(),1)
                .pattern(" L ")
                .pattern("DCD")
                .pattern(" L ")
                .define('D', Items.DIAMOND)
                .define('L', Items.LAPIS_LAZULI)
                .define('C', itemtag("c:circuits/advanced"))
                .unlockedBy("has_advanced",has(ModItems.ADVANCED_CIRCUIT)).save(recipeOutput);

        // ── Katchin ──────────────────────────────────────────────────────────
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.KATCHIN_BLOCK.get(), 1)
                .pattern("KK").pattern("KK")
                .define('K', ModItems.KATCHIN_INGOT.get())
                .unlockedBy("has_katchin_ingot", has(ModItems.KATCHIN_INGOT)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.KATCHIN_INGOT.get(), 4)
                .requires(ModBlocks.KATCHIN_BLOCK)
                .unlockedBy("has_katchin_block", has(ModBlocks.KATCHIN_BLOCK))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "katchin_ingot_from_block"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.CUT_KATCHIN.get(), 4)
                .pattern("KK")
                .pattern("KK")
                .define('K', ModBlocks.KATCHIN_BLOCK.get())
                .unlockedBy("has_katchin_block", has(ModBlocks.KATCHIN_BLOCK.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.KATCHIN_PILLAR.get(), 2)
                .pattern("K")
                .pattern("K")
                .define('K', ModBlocks.CUT_KATCHIN.get())
                .unlockedBy("has_cut_katchin", has(ModBlocks.CUT_KATCHIN.get())).save(recipeOutput);

        sacredFamily(recipeOutput, ModBlocks.CUT_KATCHIN.get(),
                ModBlocks.CUT_KATCHIN_STAIRS.get(), ModBlocks.CUT_KATCHIN_SLAB.get(), ModBlocks.CUT_KATCHIN_WALL.get());
        stonecut(recipeOutput, ModBlocks.KATCHIN_BLOCK.get(), ModBlocks.CUT_KATCHIN.get(), 4);
        stonecut(recipeOutput, ModBlocks.KATCHIN_BLOCK.get(), ModBlocks.KATCHIN_PILLAR.get(), 4);

        // Las pesas: el lastre es katchin. Es lo que le da su primer uso al material.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.WEIGHTED_STRAPS.get(), 1)
                .pattern("LKL").pattern("K K").pattern("LKL")
                .define('K', ModItems.KATCHIN_INGOT.get())
                .define('L', Items.LEATHER)
                .unlockedBy("has_katchin_ingot", has(ModItems.KATCHIN_INGOT)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.WEIGHTED_CAPE.get(), 1)
                .pattern("WKW").pattern("KKK").pattern("WKW")
                .define('K', ModItems.KATCHIN_INGOT.get())
                .define('W', Items.WHITE_WOOL)
                .unlockedBy("has_weighted_straps", has(ModItems.WEIGHTED_STRAPS)).save(recipeOutput);

        // ── Ajisa ────────────────────────────────────────────────────────────

        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.AJISA_PLANKS.get(), 4)
                .requires(ModTags.Items.AJISA_LOGS)
                .unlockedBy("has_ajisa_log", has(ModTags.Items.AJISA_LOGS)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.AJISA_WOOD.get(), 3)
                .pattern("LL").pattern("LL")
                .define('L', ModBlocks.AJISA_LOG.get())
                .unlockedBy("has_ajisa_log", has(ModBlocks.AJISA_LOG.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.STRIPPED_AJISA_WOOD.get(), 3)
                .pattern("LL").pattern("LL")
                .define('L', ModBlocks.STRIPPED_AJISA_LOG.get())
                .unlockedBy("has_stripped_ajisa_log", has(ModBlocks.STRIPPED_AJISA_LOG.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.AJISA_STAIRS.get(), 4)
                .pattern("P  ").pattern("PP ").pattern("PPP")
                .define('P', ModBlocks.AJISA_PLANKS.get())
                .unlockedBy("has_ajisa_planks", has(ModBlocks.AJISA_PLANKS.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.AJISA_SLAB.get(), 6)
                .pattern("PPP")
                .define('P', ModBlocks.AJISA_PLANKS.get())
                .unlockedBy("has_ajisa_planks", has(ModBlocks.AJISA_PLANKS.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.AJISA_FENCE.get(), 3)
                .pattern("PSP").pattern("PSP")
                .define('P', ModBlocks.AJISA_PLANKS.get())
                .define('S', Items.STICK)
                .unlockedBy("has_ajisa_planks", has(ModBlocks.AJISA_PLANKS.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.AJISA_FENCE_GATE.get(), 1)
                .pattern("SPS").pattern("SPS")
                .define('P', ModBlocks.AJISA_PLANKS.get())
                .define('S', Items.STICK)
                .unlockedBy("has_ajisa_planks", has(ModBlocks.AJISA_PLANKS.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.AJISA_DOOR.get(), 3)
                .pattern("PP").pattern("PP").pattern("PP")
                .define('P', ModBlocks.AJISA_PLANKS.get())
                .unlockedBy("has_ajisa_planks", has(ModBlocks.AJISA_PLANKS.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.AJISA_TRAPDOOR.get(), 2)
                .pattern("PPP").pattern("PPP")
                .define('P', ModBlocks.AJISA_PLANKS.get())
                .unlockedBy("has_ajisa_planks", has(ModBlocks.AJISA_PLANKS.get())).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, ModBlocks.AJISA_BUTTON.get(), 1)
                .requires(ModBlocks.AJISA_PLANKS.get())
                .unlockedBy("has_ajisa_planks", has(ModBlocks.AJISA_PLANKS.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.AJISA_PRESSURE_PLATE.get(), 1)
                .pattern("PP")
                .define('P', ModBlocks.AJISA_PLANKS.get())
                .unlockedBy("has_ajisa_planks", has(ModBlocks.AJISA_PLANKS.get())).save(recipeOutput);

        // ── Menas de Namek ───────────────────────────────────────────────────
        // ── Compactado de cristales ──────────────────────────────────────────

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.NAMEK_CRYSTAL_BLOCK.get(), 1)
                .pattern("CCC").pattern("CCC").pattern("CCC")
                .define('C', ModItems.NAMEK_CRYSTAL.get())
                .unlockedBy("has_namek_crystal", has(ModItems.NAMEK_CRYSTAL.get())).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.NAMEK_CRYSTAL.get(), 9)
                .requires(ModBlocks.NAMEK_CRYSTAL_BLOCK.get())
                .unlockedBy("has_namek_crystal_block", has(ModBlocks.NAMEK_CRYSTAL_BLOCK.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "namek_crystal_from_block"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.ENERGY_CRYSTAL_BLOCK.get(), 1)
                .pattern("CCC").pattern("CCC").pattern("CCC")
                .define('C', ModItems.ENERGY_CRYSTAL.get())
                .unlockedBy("has_energy_crystal", has(ModItems.ENERGY_CRYSTAL.get())).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ENERGY_CRYSTAL.get(), 9)
                .requires(ModBlocks.ENERGY_CRYSTAL_BLOCK.get())
                .unlockedBy("has_energy_crystal_block", has(ModBlocks.ENERGY_CRYSTAL_BLOCK.get()))
                .save(recipeOutput, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "energy_crystal_from_block"));

        // ── Piedra Sagrada ───────────────────────────────────────────────────
        // 4 items -> 1 bloque, no 9: es material de construcción y a 9 por bloque no daría
        // para levantar un templo con lo que suelta una veta.

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.SACRED_STONE_BLOCK.get(), 1)
                .pattern("SS").pattern("SS")
                .define('S', ModItems.SACRED_STONE.get())
                .unlockedBy("has_sacred_stone", has(ModItems.SACRED_STONE.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.POLISHED_SACRED_STONE.get(), 4)
                .pattern("SS").pattern("SS")
                .define('S', ModBlocks.SACRED_STONE_BLOCK.get())
                .unlockedBy("has_sacred_stone_block", has(ModBlocks.SACRED_STONE_BLOCK.get())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.SACRED_STONE_BRICKS.get(), 4)
                .pattern("SS").pattern("SS")
                .define('S', ModBlocks.POLISHED_SACRED_STONE.get())
                .unlockedBy("has_polished_sacred_stone", has(ModBlocks.POLISHED_SACRED_STONE.get())).save(recipeOutput);

        sacredFamily(recipeOutput, ModBlocks.SACRED_STONE_BLOCK.get(),
                ModBlocks.SACRED_STONE_STAIRS.get(), ModBlocks.SACRED_STONE_SLAB.get(), ModBlocks.SACRED_STONE_WALL.get());
        sacredFamily(recipeOutput, ModBlocks.POLISHED_SACRED_STONE.get(),
                ModBlocks.POLISHED_SACRED_STONE_STAIRS.get(), ModBlocks.POLISHED_SACRED_STONE_SLAB.get(), ModBlocks.POLISHED_SACRED_STONE_WALL.get());
        sacredFamily(recipeOutput, ModBlocks.SACRED_STONE_BRICKS.get(),
                ModBlocks.SACRED_STONE_BRICK_STAIRS.get(), ModBlocks.SACRED_STONE_BRICK_SLAB.get(), ModBlocks.SACRED_STONE_BRICK_WALL.get());

        // Cantero: cada variante puede saltar directamente a las siguientes de la cadena,
        // igual que la piedra vanilla. Ahorra pasos intermedios y es lo que espera el jugador.
        stonecut(recipeOutput, ModBlocks.SACRED_STONE_BLOCK.get(), ModBlocks.POLISHED_SACRED_STONE.get(), 1);
        stonecut(recipeOutput, ModBlocks.SACRED_STONE_BLOCK.get(), ModBlocks.SACRED_STONE_BRICKS.get(), 1);
        stonecut(recipeOutput, ModBlocks.POLISHED_SACRED_STONE.get(), ModBlocks.SACRED_STONE_BRICKS.get(), 1);

        oreSmelting(recipeOutput, List.of(ModBlocks.NAMEKIAN_IRON_ORE.get()),   RecipeCategory.MISC, Items.IRON_INGOT,   0.7f, 200, "namekian_iron");
        oreBlasting(recipeOutput, List.of(ModBlocks.NAMEKIAN_IRON_ORE.get()),   RecipeCategory.MISC, Items.IRON_INGOT,   0.7f, 100, "namekian_iron");
        oreSmelting(recipeOutput, List.of(ModBlocks.NAMEKIAN_GOLD_ORE.get()),   RecipeCategory.MISC, Items.GOLD_INGOT,   1.0f, 200, "namekian_gold");
        oreBlasting(recipeOutput, List.of(ModBlocks.NAMEKIAN_GOLD_ORE.get()),   RecipeCategory.MISC, Items.GOLD_INGOT,   1.0f, 100, "namekian_gold");
        oreSmelting(recipeOutput, List.of(ModBlocks.NAMEKIAN_COPPER_ORE.get()), RecipeCategory.MISC, Items.COPPER_INGOT, 0.7f, 200, "namekian_copper");
        oreBlasting(recipeOutput, List.of(ModBlocks.NAMEKIAN_COPPER_ORE.get()), RecipeCategory.MISC, Items.COPPER_INGOT, 0.7f, 100, "namekian_copper");
        oreSmelting(recipeOutput, List.of(ModBlocks.NAMEKIAN_COAL_ORE.get()),   RecipeCategory.MISC, Items.COAL,         0.1f, 200, "namekian_coal");
        oreBlasting(recipeOutput, List.of(ModBlocks.NAMEKIAN_COAL_ORE.get()),   RecipeCategory.MISC, Items.COAL,         0.1f, 100, "namekian_coal");
        oreSmelting(recipeOutput, List.of(ModBlocks.NAMEKIAN_LAPIS_ORE.get()),  RecipeCategory.MISC, Items.LAPIS_LAZULI, 0.2f, 200, "namekian_lapis");
        oreBlasting(recipeOutput, List.of(ModBlocks.NAMEKIAN_LAPIS_ORE.get()),  RecipeCategory.MISC, Items.LAPIS_LAZULI, 0.2f, 100, "namekian_lapis");
        oreSmelting(recipeOutput, List.of(ModBlocks.NAMEKIAN_REDSTONE_ORE.get()), RecipeCategory.MISC, Items.REDSTONE,   0.7f, 200, "namekian_redstone");
        oreBlasting(recipeOutput, List.of(ModBlocks.NAMEKIAN_REDSTONE_ORE.get()), RecipeCategory.MISC, Items.REDSTONE,   0.7f, 100, "namekian_redstone");
        oreSmelting(recipeOutput, List.of(ModBlocks.NAMEKIAN_DIAMOND_ORE.get()),  RecipeCategory.MISC, Items.DIAMOND,    1.0f, 200, "namekian_diamond");
        oreBlasting(recipeOutput, List.of(ModBlocks.NAMEKIAN_DIAMOND_ORE.get()),  RecipeCategory.MISC, Items.DIAMOND,    1.0f, 100, "namekian_diamond");
        oreSmelting(recipeOutput, List.of(ModBlocks.NAMEK_CRYSTAL_ORE.get()),  RecipeCategory.MISC, ModItems.NAMEK_CRYSTAL.get(),  1.0f, 200, "namek_crystal");
        oreBlasting(recipeOutput, List.of(ModBlocks.NAMEK_CRYSTAL_ORE.get()),  RecipeCategory.MISC, ModItems.NAMEK_CRYSTAL.get(),  1.0f, 100, "namek_crystal");
        oreSmelting(recipeOutput, List.of(ModBlocks.ENERGY_CRYSTAL_ORE.get()), RecipeCategory.MISC, ModItems.ENERGY_CRYSTAL.get(), 1.0f, 200, "energy_crystal");
        oreBlasting(recipeOutput, List.of(ModBlocks.ENERGY_CRYSTAL_ORE.get()), RecipeCategory.MISC, ModItems.ENERGY_CRYSTAL.get(), 1.0f, 100, "energy_crystal");
        oreSmelting(recipeOutput, List.of(ModBlocks.SACRED_STONE_ORE.get()),   RecipeCategory.MISC, ModItems.SACRED_STONE.get(),   0.2f, 200, "sacred_stone");
        oreBlasting(recipeOutput, List.of(ModBlocks.SACRED_STONE_ORE.get()),   RecipeCategory.MISC, ModItems.SACRED_STONE.get(),   0.2f, 100, "sacred_stone");
    }

    protected static void oreSmelting(@NotNull RecipeOutput recipeOutput, List<ItemLike> pIngredients, @NotNull RecipeCategory pCategory, @NotNull ItemLike pResult,
                                      float pExperience, int pCookingTIme, @NotNull String pGroup) {
        oreCooking(recipeOutput, RecipeSerializer.SMELTING_RECIPE, SmeltingRecipe::new, pIngredients, pCategory, pResult,
                pExperience, pCookingTIme, pGroup, "_from_smelting");
    }

    protected static void oreBlasting(@NotNull RecipeOutput recipeOutput, List<ItemLike> pIngredients, @NotNull RecipeCategory pCategory, @NotNull ItemLike pResult,
                                      float pExperience, int pCookingTime, @NotNull String pGroup) {
        oreCooking(recipeOutput, RecipeSerializer.BLASTING_RECIPE, BlastingRecipe::new, pIngredients, pCategory, pResult,
                pExperience, pCookingTime, pGroup, "_from_blasting");
    }

    private static TagKey<Item> itemtag(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        return TagKey.create(Registries.ITEM, rl);
    }

    protected static <T extends AbstractCookingRecipe> void oreCooking(@NotNull RecipeOutput recipeOutput, RecipeSerializer<T> pCookingSerializer, AbstractCookingRecipe.@NotNull Factory<T> factory,
                                                                       List<ItemLike> pIngredients, @NotNull RecipeCategory pCategory, @NotNull ItemLike pResult, float pExperience, int pCookingTime, @NotNull String pGroup, String pRecipeName) {
        for(ItemLike itemlike : pIngredients) {
            SimpleCookingRecipeBuilder.generic(Ingredient.of(itemlike), pCategory, pResult, pExperience, pCookingTime, pCookingSerializer, factory).group(pGroup).unlockedBy(getHasName(itemlike), has(itemlike))
                    .save(recipeOutput, Zenkai.MOD_ID + ":" + getItemName(pResult) + pRecipeName + "_" + getItemName(itemlike));
        }
    }

    /** Escalera, losa y muro de una variante, con la geometría de vainilla. */
    private static void sacredFamily(RecipeOutput out, Block base, Block stairs, Block slab, Block wall) {
        String baseName = BuiltInRegistries.BLOCK.getKey(base).getPath();

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, stairs, 4)
                .pattern("B  ").pattern("BB ").pattern("BBB")
                .define('B', base)
                .unlockedBy("has_" + baseName, has(base)).save(out);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, slab, 6)
                .pattern("BBB")
                .define('B', base)
                .unlockedBy("has_" + baseName, has(base)).save(out);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, wall, 6)
                .pattern("BBB").pattern("BBB")
                .define('B', base)
                .unlockedBy("has_" + baseName, has(base)).save(out);

        // Cantero: la losa sale a 2 por bloque, el resto a 1. Igual que la piedra vanilla.
        stonecut(out, base, stairs, 1);
        stonecut(out, base, slab, 2);
        stonecut(out, base, wall, 1);
    }

    private static void stonecut(RecipeOutput out, ItemLike input, ItemLike result, int count) {
        String in  = BuiltInRegistries.ITEM.getKey(input.asItem()).getPath();
        String res = BuiltInRegistries.ITEM.getKey(result.asItem()).getPath();
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(input), RecipeCategory.BUILDING_BLOCKS, result, count)
                .unlockedBy("has_" + in, has(input))
                .save(out, ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, res + "_from_" + in + "_stonecutting"));
    }
}