package com.hmc.zenkai.datagen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModTags;
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

        List<ItemLike> TERRAGEM_SMELTABLES = List.of(ModItems.TERRAGEM_DUST,
                ModBlocks.TERRAGEM_ORE, ModBlocks.DEEPSLATE_TERRAGEM_ORE);
        List<ItemLike> NAMEKIAN_COBBLESTONE = List.of(ModBlocks.NAMEKIAN_COBBLESTONE);

        oreSmelting(recipeOutput, NAMEKIAN_COBBLESTONE, RecipeCategory.MISC, ModBlocks.NAMEKIAN_STONE.get(), 0.1f, 200, "namekian");
        oreSmelting(recipeOutput, TERRAGEM_SMELTABLES, RecipeCategory.MISC, ModItems.TERRAGEM.get(), 0.25f, 200, "terragem");
        oreBlasting(recipeOutput, TERRAGEM_SMELTABLES, RecipeCategory.MISC, ModItems.TERRAGEM.get(), 0.25f, 100, "terragem");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.TERRAGEM_BLOCK.get(),1)
                .pattern("WWW")
                .pattern("WWW")
                .pattern("WWW")
                .define('W', ModItems.TERRAGEM.get())
                .unlockedBy("has_terragem", has(ModItems.TERRAGEM)).save(recipeOutput);

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

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.TERRAGEM.get(), 9)
                .requires(ModBlocks.TERRAGEM_BLOCK)
                .unlockedBy("has_terragem_block", has(ModBlocks.TERRAGEM_BLOCK)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.TERRAGEM_TEMPLATE.get(),2)
                .pattern("WTW")
                .pattern("WRW")
                .pattern("WWW")
                .define('W', ModItems.TERRAGEM.get())
                .define('R', ModBlocks.ROCKY_BLOCK.get())
                .define('T',ModItems.TERRAGEM_TEMPLATE.get())
                .unlockedBy("has_terragem",has(ModItems.TERRAGEM_TEMPLATE)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.TERRAGEM_HAMMER.get(),1)
                .pattern(" WP")
                .pattern(" SW")
                .pattern("S  ")
                .define('S', Items.STICK)
                .define('W', ModItems.TERRAGEM)
                .define('P', ModItems.TERRAGEM_PICKAXE)
                .unlockedBy("has_terragem",has(ModItems.TERRAGEM)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,ModBlocks.NAMEKIAN_STRUCTURE_BLOCK.get(),8)
                .pattern("SSS")
                .pattern("SQS")
                .pattern("SSS")
                .define('S', ModBlocks.NAMEKIAN_STONE)
                .define('Q',Items.QUARTZ)
                .unlockedBy("has_terragem",has(ModItems.TERRAGEM)).save(recipeOutput);


        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.DRAGON_BALL_RADAR.get(),1)
                .pattern("ICI")
                .pattern("ADA")
                .pattern("ICI")
                .define('D', Items.DIAMOND)
                .define('I', Items.IRON_INGOT)
                .define('A', Items.AMETHYST_SHARD)
                .define('C', itemtag("c:circuits/advanced"))
                .unlockedBy("has_terragem",has(ModItems.TERRAGEM)).save(recipeOutput);


        ShapedRecipeBuilder.shaped(RecipeCategory.MISC,ModItems.SPACE_POD_ITEM.get(),1)
                .pattern("IRI")
                .pattern("BCB")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('B', itemtag("c:circuits/basic"))
                .define('R', Items.RED_DYE)
                .define('C', itemtag("c:circuits/advanced"))
                .unlockedBy("has_terragem",has(ModItems.TERRAGEM)).save(recipeOutput);

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
        // Solo hacen falta para el bloque con Toque de Seda: al picarlas normal sueltan ya
        // el item vanilla, que tiene sus propias recetas de fundido.

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

}