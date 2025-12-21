package com.hmc.db_renewed.datagen;

import com.hmc.db_renewed.content.block.ModBlocks;
import com.hmc.db_renewed.content.item.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class ModBlockLootTableProvider extends BlockLootSubProvider {
    protected ModBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.DRAGON_BALL_1.get());
        dropSelf(ModBlocks.DRAGON_BALL_2.get());
        dropSelf(ModBlocks.DRAGON_BALL_3.get());
        dropSelf(ModBlocks.DRAGON_BALL_4.get());
        dropSelf(ModBlocks.DRAGON_BALL_5.get());
        dropSelf(ModBlocks.DRAGON_BALL_6.get());
        dropSelf(ModBlocks.DRAGON_BALL_7.get());
        dropSelf(ModBlocks.NAMEK_DRAGON_BALL_1.get());
        dropSelf(ModBlocks.NAMEK_DRAGON_BALL_2.get());
        dropSelf(ModBlocks.NAMEK_DRAGON_BALL_3.get());
        dropSelf(ModBlocks.NAMEK_DRAGON_BALL_4.get());
        dropSelf(ModBlocks.NAMEK_DRAGON_BALL_5.get());
        dropSelf(ModBlocks.NAMEK_DRAGON_BALL_6.get());
        dropSelf(ModBlocks.NAMEK_DRAGON_BALL_7.get());
        dropSelf(ModBlocks.TERRAGEM_BLOCK.get());
        dropSelf(ModBlocks.ALL_DRAGON_BALLS.get());
        dropSelf(ModBlocks.NAMEKIAN_GRASS_BLOCK.get());
        dropSelf(ModBlocks.NAMEKIAN_DIRT.get());
        dropSelf(ModBlocks.ROCKY_BLOCK.get());
        dropSelf(ModBlocks.NAMEKIAN_COBBLESTONE.get());
        dropSelf(ModBlocks.NAMEKIAN_STRUCTURE_BLOCK.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_BLACK.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_BLUE.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_BROWN.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_CYAN.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_GRAY.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_LIGHT_BLUE.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_LIGHT_GRAY.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_DARK_GREEN.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_RED.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_YELLOW.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_MAGENTA.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_WHITE.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_PURPLE.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_GREEN.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_PINK.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_ORANGE.get());
        dropSelf(ModBlocks.TERRAGEM_CONCRETE_DARK_RED.get());

        add(ModBlocks.NAMEKIAN_STONE.get(),
                createSingleItemTableWithSilkTouch(ModBlocks.NAMEKIAN_STONE.get(), ModBlocks.NAMEKIAN_COBBLESTONE));
        add(ModBlocks.NAMEKIAN_GRASS_BLOCK.get(),
                createSingleItemTableWithSilkTouch(ModBlocks.NAMEKIAN_GRASS_BLOCK.get(), ModBlocks.NAMEKIAN_DIRT));


        add(ModBlocks.TERRAGEM_ORE.get(),
                block -> createMultipleOreDrops(ModBlocks.TERRAGEM_ORE.get(), ModItems.TERRAGEM.get(), 1, 4));
        add(ModBlocks.DEEPSLATE_TERRAGEM_ORE.get(),
                block -> createMultipleOreDrops(ModBlocks.DEEPSLATE_TERRAGEM_ORE.get(), ModItems.TERRAGEM.get(), 1, 4));

        addRandomDrops(ModBlocks.DRAGON_BALL_STONE.get(), List.of(
                ModBlocks.DRAGON_BALL_1.get(),
                ModBlocks.DRAGON_BALL_2.get(),
                ModBlocks.DRAGON_BALL_3.get(),
                ModBlocks.DRAGON_BALL_4.get(),
                ModBlocks.DRAGON_BALL_5.get(),
                ModBlocks.DRAGON_BALL_6.get(),
                ModBlocks.DRAGON_BALL_7.get()
        ));

        addRandomDrops(ModBlocks.NAMEK_DRAGON_BALL_STONE.get(), List.of(
                ModBlocks.NAMEK_DRAGON_BALL_1.get(),
                ModBlocks.NAMEK_DRAGON_BALL_2.get(),
                ModBlocks.NAMEK_DRAGON_BALL_3.get(),
                ModBlocks.NAMEK_DRAGON_BALL_4.get(),
                ModBlocks.NAMEK_DRAGON_BALL_5.get(),
                ModBlocks.NAMEK_DRAGON_BALL_6.get(),
                ModBlocks.NAMEK_DRAGON_BALL_7.get()
        ));
    }

    protected LootTable.Builder createMultipleOreDrops(Block pBlock, Item item, float minDrops, float maxDrops) {
        HolderLookup.RegistryLookup<Enchantment> registrylookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(pBlock,
                this.applyExplosionDecay(pBlock, LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(minDrops, maxDrops)))
                        .apply(ApplyBonusCount.addOreBonusCount(registrylookup.getOrThrow(Enchantments.FORTUNE)))));
    }

    private void addRandomDrops(Block block, List<Block> drops) {
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1));

        for (Block block1 : drops) {
            pool.add(LootItem.lootTableItem(block1).setWeight(1));
        }

        this.add(block, LootTable.lootTable().withPool(pool));
    }

    @Override
    protected @NotNull Iterable<Block> getKnownBlocks() {
        return ModBlocks.MOD_BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}