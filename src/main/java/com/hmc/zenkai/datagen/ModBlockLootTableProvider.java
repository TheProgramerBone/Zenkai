package com.hmc.zenkai.datagen;

import com.hmc.zenkai.content.block.NamekianHerbCropBlock;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
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
        dropSelf(ModBlocks.SCOUTER_BENCH.get());
        add(ModBlocks.ALL_DRAGON_BALLS.get(), noDrop());
        dropSelf(ModBlocks.NAMEKIAN_GRASS_BLOCK.get());
        dropSelf(ModBlocks.NAMEKIAN_DIRT.get());
        dropSelf(ModBlocks.ROCKY_BLOCK.get());
        dropSelf(ModBlocks.HFIL_SCORCHED_STONE.get());
        dropSelf(ModBlocks.HFIL_SPIKE_ROCK.get());
        dropSelf(ModBlocks.HFIL_CINDER_SAND.get());
        dropSelf(ModBlocks.HFIL_CINDER_SANDSTONE.get());
        dropSelf(ModBlocks.NAMEKIAN_COBBLESTONE.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_BLACK.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_BLUE.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_BROWN.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_CYAN.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_GRAY.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_LIGHT_BLUE.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_LIGHT_GRAY.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_DARK_GREEN.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_RED.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_YELLOW.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_MAGENTA.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_WHITE.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_PURPLE.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_GREEN.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_PINK.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_ORANGE.get());
        dropSelf(ModBlocks.STRUCTURAL_CONCRETE_DARK_RED.get());
        dropSelf(ModBlocks.KATCHIN_BLOCK.get());
        dropSelf(ModBlocks.CUT_KATCHIN.get());
        dropSelf(ModBlocks.CUT_KATCHIN_STAIRS.get());
        dropSelf(ModBlocks.CUT_KATCHIN_WALL.get());
        dropSelf(ModBlocks.KATCHIN_PILLAR.get());
        add(ModBlocks.CUT_KATCHIN_SLAB.get(), this::createSlabItemTable);
        dropSelf(ModBlocks.NAMEKIAN_LAMP.get());
        dropSelf(ModBlocks.ENERGY_GENERATOR.get());

        for (var fam : ModBlocks.STRUCTURAL_CONCRETE_FAMILIES) {
            dropSelf(fam.block().get());
            dropSelf(fam.stairs().get());
            dropSelf(fam.wall().get());
            add(fam.slab().get(), this::createSlabItemTable);
        }

        add(ModBlocks.NAMEKIAN_STONE.get(),
                createSingleItemTableWithSilkTouch(ModBlocks.NAMEKIAN_STONE.get(), ModBlocks.NAMEKIAN_COBBLESTONE));
        add(ModBlocks.NAMEKIAN_GRASS_BLOCK.get(),
                createSingleItemTableWithSilkTouch(ModBlocks.NAMEKIAN_GRASS_BLOCK.get(), ModBlocks.NAMEKIAN_DIRT));
        dropSelf(ModBlocks.NAMEKIAN_SAND.get());
        dropSelf(ModBlocks.NAMEKIAN_GRAVEL.get());
        add(ModBlocks.NAMEKIAN_COAL_ORE.get(),
                b -> createOreDrop(b, Items.COAL));
        add(ModBlocks.NAMEKIAN_IRON_ORE.get(),
                b -> createOreDrop(b, Items.RAW_IRON));
        add(ModBlocks.NAMEKIAN_GOLD_ORE.get(),
                b -> createOreDrop(b, Items.RAW_GOLD));
        add(ModBlocks.NAMEKIAN_DIAMOND_ORE.get(),
                b -> createOreDrop(b, Items.DIAMOND));
        add(ModBlocks.NAMEKIAN_COPPER_ORE.get(),
                this::createCopperOreDrops);
        add(ModBlocks.NAMEKIAN_REDSTONE_ORE.get(),
                this::createRedstoneOreDrops);
        add(ModBlocks.NAMEKIAN_LAPIS_ORE.get(),
                this::createLapisOreDrops);
        add(ModBlocks.NAMEKIAN_DIRT_PATH.get(),
                b -> createSingleItemTable(ModBlocks.NAMEKIAN_DIRT.get()));
        add(ModBlocks.KATCHIN_ORE.get(),
                b -> createOreDrop(b, ModItems.RAW_KATCHIN.get()));
        add(ModBlocks.DEEPSLATE_KATCHIN_ORE.get(),
                b -> createOreDrop(b, ModItems.RAW_KATCHIN.get()));

        dropSelf(ModBlocks.AJISA_LOG.get());
        dropSelf(ModBlocks.AJISA_WOOD.get());
        dropSelf(ModBlocks.STRIPPED_AJISA_LOG.get());
        dropSelf(ModBlocks.STRIPPED_AJISA_WOOD.get());
        dropSelf(ModBlocks.AJISA_PLANKS.get());
        dropSelf(ModBlocks.AJISA_SAPLING.get());
        dropSelf(ModBlocks.AJISA_STAIRS.get());
        dropSelf(ModBlocks.AJISA_FENCE.get());
        dropSelf(ModBlocks.AJISA_FENCE_GATE.get());
        dropSelf(ModBlocks.AJISA_TRAPDOOR.get());
        dropSelf(ModBlocks.AJISA_BUTTON.get());
        dropSelf(ModBlocks.AJISA_PRESSURE_PLATE.get());
        dropSelf(ModBlocks.AJISA_FLOWER.get());

        // La losa suelta dos si estaba doble; la puerta solo una vez pese a ocupar dos bloques.
        add(ModBlocks.AJISA_SLAB.get(), this::createSlabItemTable);
        add(ModBlocks.AJISA_DOOR.get(), this::createDoorTable);

        // Hojas: sapling con la probabilidad de vainilla y palos con la suya.
        add(ModBlocks.AJISA_LEAVES.get(),
                b -> createLeavesDrops(b, ModBlocks.AJISA_SAPLING.get(), NORMAL_LEAVES_SAPLING_CHANCES));

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

        // Madura suelta 1-2 hierbas y 1-2 semillas; verde solo la semilla.
        LootItemCondition.Builder grown = LootItemBlockStatePropertyCondition
                .hasBlockStateProperties(ModBlocks.NAMEKIAN_HERB_CROP.get())
                .setProperties(StatePropertiesPredicate.Builder.properties()
                        .hasProperty(NamekianHerbCropBlock.AGE, NamekianHerbCropBlock.MAX_AGE));

        add(ModBlocks.NAMEKIAN_HERB_CROP.get(),
                createCropDrops(ModBlocks.NAMEKIAN_HERB_CROP.get(),
                        ModItems.NAMEKIAN_HERB.get(),
                        ModItems.NAMEKIAN_HERB_SEEDS.get(),
                        grown));

        // Cristal de Namek: 1 con fortuna, como el diamante. Ciclo cortísimo mena -> moneda.
        add(ModBlocks.NAMEK_CRYSTAL_ORE.get(),
                b -> createOreDrop(b, ModItems.NAMEK_CRYSTAL.get()));

        // Cristal Energético: 4-5 con fortuna, como la redstone. Es el "redstone de Namek",
        // así que tiene que salir a puñados o no da para fabricar nada.
        add(ModBlocks.ENERGY_CRYSTAL_ORE.get(),
                b -> createSilkTouchDispatchTable(b, applyExplosionDecay(b,
                        LootItem.lootTableItem(ModItems.ENERGY_CRYSTAL.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(4.0F, 5.0F)))
                                .apply(ApplyBonusCount.addOreBonusCount(
                                        registries.holderOrThrow(Enchantments.FORTUNE))))));

        // Piedra Sagrada: 4-9 con fortuna, tabla idéntica a la del lapislázuli.
        add(ModBlocks.SACRED_STONE_ORE.get(),
                b -> createSilkTouchDispatchTable(b, applyExplosionDecay(b,
                        LootItem.lootTableItem(ModItems.SACRED_STONE.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(4.0F, 9.0F)))
                                .apply(ApplyBonusCount.addOreBonusCount(
                                        registries.holderOrThrow(Enchantments.FORTUNE))))));



        dropSelf(ModBlocks.NAMEK_CRYSTAL_BLOCK.get());
        dropSelf(ModBlocks.ENERGY_CRYSTAL_BLOCK.get());
        dropSelf(ModBlocks.SACRED_STONE_BLOCK.get());
        dropSelf(ModBlocks.SACRED_STONE_STAIRS.get());
        dropSelf(ModBlocks.SACRED_STONE_WALL.get());
        dropSelf(ModBlocks.POLISHED_SACRED_STONE.get());
        dropSelf(ModBlocks.POLISHED_SACRED_STONE_STAIRS.get());
        dropSelf(ModBlocks.POLISHED_SACRED_STONE_WALL.get());
        dropSelf(ModBlocks.SACRED_STONE_BRICKS.get());
        dropSelf(ModBlocks.SACRED_STONE_BRICK_STAIRS.get());
        dropSelf(ModBlocks.SACRED_STONE_BRICK_WALL.get());

        add(ModBlocks.SACRED_STONE_SLAB.get(), this::createSlabItemTable);
        add(ModBlocks.POLISHED_SACRED_STONE_SLAB.get(), this::createSlabItemTable);
        add(ModBlocks.SACRED_STONE_BRICK_SLAB.get(), this::createSlabItemTable);

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