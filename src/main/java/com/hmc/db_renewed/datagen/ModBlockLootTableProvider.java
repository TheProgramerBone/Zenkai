package com.hmc.db_renewed.datagen;

import com.hmc.db_renewed.block.ModBlocks;
import com.hmc.db_renewed.item.ModItems;
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
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.jetbrains.annotations.NotNull;

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
        dropSelf(ModBlocks.WARENAI_CRYSTAL_BLOCK.get());
        dropSelf(ModBlocks.ALL_DRAGON_BALLS.get());
        dropSelf(ModBlocks.NAMEKIAN_GRASS_BLOCK.get());
        dropSelf(ModBlocks.NAMEKIAN_DIRT_BLOCK.get());
        dropSelf(ModBlocks.ROCKY_BLOCK.get());

        add(ModBlocks.WARENAI_CRYSTAL_ORE.get(),
                block -> createMultipleOreDrops(ModBlocks.WARENAI_CRYSTAL_ORE.get(), ModItems.WARENAI_CRYSTAL.get(), 1, 4));
        add(ModBlocks.DEEPSLATE_WARENAI_CRYSTAL_ORE.get(),
                block -> createMultipleOreDrops(ModBlocks.DEEPSLATE_WARENAI_CRYSTAL_ORE.get(), ModItems.WARENAI_CRYSTAL.get(), 1, 4));

    }

    protected LootTable.Builder createMultipleOreDrops(Block pBlock, Item item, float minDrops, float maxDrops) {
        HolderLookup.RegistryLookup<Enchantment> registrylookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(pBlock,
                this.applyExplosionDecay(pBlock, LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(minDrops, maxDrops)))
                        .apply(ApplyBonusCount.addOreBonusCount(registrylookup.getOrThrow(Enchantments.FORTUNE)))));
    }


    @Override
    protected @NotNull Iterable<Block> getKnownBlocks() {
        return ModBlocks.MOD_BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}