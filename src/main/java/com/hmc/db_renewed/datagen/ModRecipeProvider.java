package com.hmc.db_renewed.datagen;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.block.ModBlocks;
import com.hmc.db_renewed.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
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

        List<ItemLike> WARENAI_SMELTABLES = List.of(ModItems.WARENAI_CRYSTAL_DUST,
                ModBlocks.WARENAI_CRYSTAL_ORE, ModBlocks.DEEPSLATE_WARENAI_CRYSTAL_ORE);
        List<ItemLike> NAMEKIAN_COBBLESTONE = List.of(ModBlocks.NAMEKIAN_COBBLESTONE);

        oreSmelting(recipeOutput, NAMEKIAN_COBBLESTONE, RecipeCategory.MISC, ModBlocks.NAMEKIAN_STONE.get(), 0.1f, 200, "namekian");
        oreSmelting(recipeOutput, WARENAI_SMELTABLES, RecipeCategory.MISC, ModItems.WARENAI_CRYSTAL.get(), 0.25f, 200, "warenai");
        oreBlasting(recipeOutput, WARENAI_SMELTABLES, RecipeCategory.MISC, ModItems.WARENAI_CRYSTAL.get(), 0.25f, 100, "warenai");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.WARENAI_CRYSTAL_BLOCK.get(),1)
                .pattern("WWW")
                .pattern("WWW")
                .pattern("WWW")
                .define('W', ModItems.WARENAI_CRYSTAL.get())
                .unlockedBy("has_warenai", has(ModItems.WARENAI_CRYSTAL)).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.WARENAI_CRYSTAL.get(), 9)
                .requires(ModBlocks.WARENAI_CRYSTAL_BLOCK)
                .unlockedBy("has_warenai_block", has(ModBlocks.WARENAI_CRYSTAL_BLOCK)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.WARENAI_TEMPLATE.get(),2)
                .pattern("WTW")
                .pattern("WRW")
                .pattern("WWW")
                .define('W', ModItems.WARENAI_CRYSTAL.get())
                .define('R', ModBlocks.ROCKY_BLOCK.get())
                .define('T',ModItems.WARENAI_TEMPLATE.get())
                .unlockedBy("has_warenai",has(ModItems.WARENAI_TEMPLATE)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.WARENAI_CRYSTAL_HAMMER.get(),1)
                .pattern(" WP")
                .pattern(" SW")
                .pattern("S  ")
                .define('S', Items.STICK)
                .define('W', ModItems.WARENAI_CRYSTAL)
                .define('P', ModItems.WARENAI_CRYSTAL_PICKAXE)
                .unlockedBy("has_warenai",has(ModItems.WARENAI_CRYSTAL)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,ModBlocks.NAMEKIAN_STRUCTURE_BLOCK.get(),8)
                .pattern("SSS")
                .pattern("SQS")
                .pattern("SSS")
                .define('S', ModBlocks.NAMEKIAN_STONE)
                .define('Q',Items.QUARTZ)
                .unlockedBy("has_warenai",has(ModItems.WARENAI_CRYSTAL)).save(recipeOutput);


        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.DRAGON_BALL_RADAR.get(),1)
                .pattern("ICI")
                .pattern("ADA")
                .pattern("ICI")
                .define('D', Items.DIAMOND)
                .define('I', Items.IRON_INGOT)
                .define('A', Items.AMETHYST_SHARD)
                .define('C', itemtag("c:circuits/advanced"))
                .unlockedBy("has_warenai",has(ModItems.WARENAI_CRYSTAL)).save(recipeOutput);


        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.SPACE_POD_ITEM.get(),1)
                .pattern("IRI")
                .pattern("BCB")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('B', itemtag("c:circuits/basic"))
                .define('R', Items.RED_DYE)
                .define('C', itemtag("c:circuits/advanced"))
                .unlockedBy("has_warenai",has(ModItems.WARENAI_CRYSTAL)).save(recipeOutput);

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
                    .save(recipeOutput, DragonBlockRenewed.MOD_ID + ":" + getItemName(pResult) + pRecipeName + "_" + getItemName(itemlike));
        }
    }

}