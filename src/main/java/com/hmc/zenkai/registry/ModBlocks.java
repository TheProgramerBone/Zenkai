package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.HtcPortalBlock;
import com.hmc.zenkai.content.blockentity.AllDragonBalls.AllDragonBallsBlock;
import com.hmc.zenkai.content.blockentity.DragonBalls.DragonBalls;
import com.hmc.zenkai.content.blockentity.DragonBalls.NamekDragonBalls;
import com.hmc.zenkai.content.blockentity.DragonBalls.NamekianGrassBlock;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks MOD_BLOCKS =
            DeferredRegister.createBlocks(Zenkai.MOD_ID);

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = MOD_BLOCKS.register(name,block);
        registerBlockItem(name,toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties()));
    }

    public static final DeferredBlock<Block> DRAGON_BALL_STONE = registerBlock("dragon_ball_stone",
            ()-> new DragonBalls(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(0f,100f)
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)));

    public static final DeferredBlock<Block> DRAGON_BALL_1 = registerBlock("dragon_ball_1",
            ()-> new DragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> DRAGON_BALL_2 = registerBlock("dragon_ball_2",
            ()-> new DragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> DRAGON_BALL_3 = registerBlock("dragon_ball_3",
            ()-> new DragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> DRAGON_BALL_4 = registerBlock("dragon_ball_4",
            ()-> new DragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> DRAGON_BALL_5 = registerBlock("dragon_ball_5",
            ()-> new DragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> DRAGON_BALL_6 = registerBlock("dragon_ball_6",
            ()-> new DragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> DRAGON_BALL_7 = registerBlock("dragon_ball_7",
            ()-> new DragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> ALL_DRAGON_BALLS = MOD_BLOCKS.register("all_dragon_balls",
            () -> new AllDragonBallsBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> NAMEK_DRAGON_BALL_STONE = registerBlock("namek_dragon_ball_stone",
            ()-> new NamekDragonBalls(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(0f,100f)
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)));

    public static final DeferredBlock<Block> NAMEK_DRAGON_BALL_1 = registerBlock("namek_dragon_ball_1",
            ()-> new NamekDragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> NAMEK_DRAGON_BALL_2 = registerBlock("namek_dragon_ball_2",
            ()-> new NamekDragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> NAMEK_DRAGON_BALL_3 = registerBlock("namek_dragon_ball_3",
            ()-> new NamekDragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> NAMEK_DRAGON_BALL_4 = registerBlock("namek_dragon_ball_4",
            ()-> new NamekDragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> NAMEK_DRAGON_BALL_5 = registerBlock("namek_dragon_ball_5",
            ()-> new NamekDragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> NAMEK_DRAGON_BALL_6 = registerBlock("namek_dragon_ball_6",
            ()-> new NamekDragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> NAMEK_DRAGON_BALL_7 = registerBlock("namek_dragon_ball_7",
            ()-> new NamekDragonBalls(BlockBehaviour.Properties.of()
                    .lightLevel(state -> 15)
                    .strength(0f,100f)
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> TERRAGEM_BLOCK = registerBlock("terragem_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(2f,3f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .mapColor(MapColor.COLOR_BROWN)));

    public static final DeferredBlock<Block> TERRAGEM_ORE = registerBlock("terragem_ore",
            ()-> new DropExperienceBlock(UniformInt.of(2,4),
                    BlockBehaviour.Properties.of()
                            .strength(2f,3f)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.STONE)
                            .mapColor(MapColor.STONE)));

    public static final DeferredBlock<Block> DEEPSLATE_TERRAGEM_ORE = registerBlock("deepslate_terragem_ore",
            ()-> new DropExperienceBlock(UniformInt.of(3,5),
                    BlockBehaviour.Properties.of()
                            .strength(3f,3f)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.DEEPSLATE)
                            .mapColor(MapColor.DEEPSLATE)));


    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_BLACK = registerBlock("structural_concrete_black",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    .requiresCorrectToolForDrops()
                    
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_BLACK)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_BLUE = registerBlock("structural_concrete_blue",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_BLUE)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_BROWN = registerBlock("structural_concrete_brown",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_BROWN)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_CYAN = registerBlock("structural_concrete_cyan",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_CYAN)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_DARK_GREEN = registerBlock("structural_concrete_dark_green",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.TERRACOTTA_GREEN)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_DARK_RED = registerBlock("structural_concrete_dark_red",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.NETHER)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_GRAY = registerBlock("structural_concrete_gray",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_GRAY)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_GREEN = registerBlock("structural_concrete_green",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_GREEN)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_LIGHT_BLUE = registerBlock("structural_concrete_light_blue",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_LIGHT_GRAY = registerBlock("structural_concrete_light_gray",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_MAGENTA = registerBlock("structural_concrete_magenta",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_MAGENTA)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_ORANGE = registerBlock("structural_concrete_orange",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_PINK = registerBlock("structural_concrete_pink",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_PINK)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_PURPLE = registerBlock("structural_concrete_purple",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_PURPLE)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_RED = registerBlock("structural_concrete_red",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_RED)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_WHITE = registerBlock("structural_concrete_white",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.TERRACOTTA_WHITE)));

    public static final DeferredBlock<Block> STRUCTURAL_CONCRETE_YELLOW = registerBlock("structural_concrete_yellow",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f,100f)
                    
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.COLOR_YELLOW)));

    public static final DeferredBlock<Block> ROCKY_BLOCK = registerBlock("rocky_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.SAND)));

    public static final DeferredBlock<Block> NAMEKIAN_DIRT = registerBlock("namekian_dirt",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5f)
                    .sound(SoundType.GRAVEL)
                    .mapColor(MapColor.DIRT)));

    public static final DeferredBlock<Block> NAMEKIAN_GRASS_BLOCK = registerBlock("namekian_grass_block",
            ()-> new NamekianGrassBlock(Block.Properties.of()
                    .strength(0.5f)
                    .sound(SoundType.GRAVEL)
                    .mapColor(MapColor.DIRT),
                    ModBlocks.NAMEKIAN_DIRT));

    public static final DeferredBlock<Block> NAMEKIAN_STONE = registerBlock("namekian_stone",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.SAND)));

    public static final DeferredBlock<Block> NAMEKIAN_COBBLESTONE = registerBlock("namekian_cobblestone",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.SAND)));

    public static final DeferredBlock<Block> NAMEKIAN_SAND = registerBlock("namekian_sand",
            ()-> new ColoredFallingBlock(new ColorRGBA(12109722), BlockBehaviour.Properties.of()   // ⚠ API
                    .strength(0.5f)
                    .sound(SoundType.SAND)
                    .mapColor(MapColor.SAND)));

    public static final DeferredBlock<Block> NAMEKIAN_GRAVEL = registerBlock("namekian_gravel",
            ()-> new ColoredFallingBlock(new ColorRGBA(8030834), BlockBehaviour.Properties.of()
                    .strength(0.6f)
                    .sound(SoundType.GRAVEL)
                    .mapColor(MapColor.STONE)));

    public static final DeferredBlock<Block> NAMEKIAN_COAL_ORE = registerBlock("namekian_coal_ore",
            ()-> new DropExperienceBlock(UniformInt.of(0, 2), oreProps()));

    public static final DeferredBlock<Block> NAMEKIAN_IRON_ORE = registerBlock("namekian_iron_ore",
            ()-> new Block(oreProps()));

    public static final DeferredBlock<Block> NAMEKIAN_COPPER_ORE = registerBlock("namekian_copper_ore",
            ()-> new Block(oreProps()));

    public static final DeferredBlock<Block> NAMEKIAN_GOLD_ORE = registerBlock("namekian_gold_ore",
            ()-> new Block(oreProps()));

    public static final DeferredBlock<Block> NAMEKIAN_REDSTONE_ORE = registerBlock("namekian_redstone_ore",
            ()-> new DropExperienceBlock(UniformInt.of(1, 5), oreProps()));

    public static final DeferredBlock<Block> NAMEKIAN_LAPIS_ORE = registerBlock("namekian_lapis_ore",
            ()-> new DropExperienceBlock(UniformInt.of(2, 5), oreProps()));

    public static final DeferredBlock<Block> NAMEKIAN_DIAMOND_ORE = registerBlock("namekian_diamond_ore",
            ()-> new DropExperienceBlock(UniformInt.of(3, 7), oreProps()));

    public static final DeferredBlock<Block> NAMEKIAN_STRUCTURE_BLOCK = registerBlock("namekian_structure_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.QUARTZ)));

    // ── Ajisa (madera de Namek) ──────────────────────────────────────────────

    /** Generador del sapling. La ConfiguredFeature se registra en la fase 2b; hasta
     *  entonces el hueso no hace nada, pero el bloque ya es válido. */
    public static final TreeGrower AJISA_GROWER = new TreeGrower(              // ⚠ firma
            Zenkai.MOD_ID + ":ajisa",
            Optional.empty(),
            Optional.of(ModConfiguredFeatures.AJISA_TREE),
            Optional.empty());

    public static final DeferredBlock<Block> AJISA_LOG = registerBlock("ajisa_log",
            ()-> new RotatedPillarBlock(logProps()));

    public static final DeferredBlock<Block> AJISA_WOOD = registerBlock("ajisa_wood",
            ()-> new RotatedPillarBlock(logProps()));

    public static final DeferredBlock<Block> STRIPPED_AJISA_LOG = registerBlock("stripped_ajisa_log",
            ()-> new RotatedPillarBlock(logProps()));

    public static final DeferredBlock<Block> STRIPPED_AJISA_WOOD = registerBlock("stripped_ajisa_wood",
            ()-> new RotatedPillarBlock(logProps()));

    public static final DeferredBlock<Block> AJISA_PLANKS = registerBlock("ajisa_planks",
            ()-> new Block(plankProps()));

    public static final DeferredBlock<Block> AJISA_LEAVES = registerBlock("ajisa_leaves",
            ()-> new LeavesBlock(BlockBehaviour.Properties.of()
                    .strength(0.2f)
                    .randomTicks()
                    .sound(SoundType.GRASS)
                    .noOcclusion()
                    .isValidSpawn((s, l, p, e) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)
                    .mapColor(MapColor.PLANT)));

    public static final DeferredBlock<Block> AJISA_SAPLING = registerBlock("ajisa_sapling",
            ()-> new SaplingBlock(AJISA_GROWER, BlockBehaviour.Properties.of()
                    .noCollission()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.GRASS)
                    .pushReaction(PushReaction.DESTROY)
                    .mapColor(MapColor.PLANT)));

    public static final DeferredBlock<Block> AJISA_SLAB = registerBlock("ajisa_slab",
            ()-> new SlabBlock(plankProps()));

    public static final DeferredBlock<Block> AJISA_STAIRS = registerBlock("ajisa_stairs",
            ()-> new StairBlock(AJISA_PLANKS.get().defaultBlockState(), plankProps()));

    public static final DeferredBlock<Block> AJISA_FENCE = registerBlock("ajisa_fence",
            ()-> new FenceBlock(plankProps()));

    public static final DeferredBlock<Block> AJISA_FENCE_GATE = registerBlock("ajisa_fence_gate",
            ()-> new FenceGateBlock(ModWoodTypes.AJISA, plankProps()));

    public static final DeferredBlock<Block> AJISA_DOOR = registerBlock("ajisa_door",
            ()-> new DoorBlock(ModWoodTypes.AJISA_SET, plankProps().noOcclusion()));

    public static final DeferredBlock<Block> AJISA_TRAPDOOR = registerBlock("ajisa_trapdoor",
            ()-> new TrapDoorBlock(ModWoodTypes.AJISA_SET, plankProps().noOcclusion()));

    public static final DeferredBlock<Block> AJISA_BUTTON = registerBlock("ajisa_button",
            ()-> new ButtonBlock(ModWoodTypes.AJISA_SET, 30,
                    BlockBehaviour.Properties.of().noCollission().strength(0.5f).sound(SoundType.WOOD)));

    public static final DeferredBlock<Block> AJISA_PRESSURE_PLATE = registerBlock("ajisa_pressure_plate",
            ()-> new PressurePlateBlock(ModWoodTypes.AJISA_SET,
                    BlockBehaviour.Properties.of().noCollission().strength(0.5f).sound(SoundType.WOOD)));

    // ── Vegetación de Namek ──────────────────────────────────────────────────

    public static final DeferredBlock<Block> AJISA_FLOWER = registerBlock("ajisa_flower",
            ()-> new FlowerBlock(MobEffects.REGENERATION, 8, BlockBehaviour.Properties.of()   // ⚠ firma
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
                    .pushReaction(PushReaction.DESTROY)
                    .offsetType(BlockBehaviour.OffsetType.XZ)
                    .mapColor(MapColor.PLANT)));

    // ── Menas exclusivas de Namek ────────────────────────────────────────────

    public static final DeferredBlock<Block> NAMEK_CRYSTAL_ORE = registerBlock("namek_crystal_ore",
            ()-> new DropExperienceBlock(UniformInt.of(2, 5), oreProps()));

    /** Emite luz 6: en una cueva a oscuras se insinúa a lo lejos sin iluminar la sala.
     *  Ojo, la luz también impide que aparezcan mobs justo encima. */
    public static final DeferredBlock<Block> ENERGY_CRYSTAL_ORE = registerBlock("energy_crystal_ore",
            ()-> new DropExperienceBlock(UniformInt.of(3, 7), oreProps().lightLevel(s -> 6)));

    public static final DeferredBlock<Block> SACRED_STONE_ORE = registerBlock("sacred_stone_ore",
            ()-> new DropExperienceBlock(UniformInt.of(1, 3), oreProps()));

    // ── Bloques de almacenamiento ────────────────────────────────────────────

    public static final DeferredBlock<Block> NAMEK_CRYSTAL_BLOCK = registerBlock("namek_crystal_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f, 6f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)));

    public static final DeferredBlock<Block> ENERGY_CRYSTAL_BLOCK = registerBlock("energy_crystal_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f, 6f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST)
                    .lightLevel(s -> 10)
                    .mapColor(MapColor.COLOR_CYAN)));

    // ── Piedra Sagrada (set de construcción de templos) ──────────────────────

    public static final DeferredBlock<Block> SACRED_STONE_BLOCK = registerBlock("sacred_stone_block",
            ()-> new Block(sacredProps()));
    public static final DeferredBlock<Block> SACRED_STONE_STAIRS = registerBlock("sacred_stone_stairs",
            ()-> new StairBlock(SACRED_STONE_BLOCK.get().defaultBlockState(), sacredProps()));
    public static final DeferredBlock<Block> SACRED_STONE_SLAB = registerBlock("sacred_stone_slab",
            ()-> new SlabBlock(sacredProps()));
    public static final DeferredBlock<Block> SACRED_STONE_WALL = registerBlock("sacred_stone_wall",
            ()-> new WallBlock(sacredProps()));

    public static final DeferredBlock<Block> POLISHED_SACRED_STONE = registerBlock("polished_sacred_stone",
            ()-> new Block(sacredProps()));
    public static final DeferredBlock<Block> POLISHED_SACRED_STONE_STAIRS = registerBlock("polished_sacred_stone_stairs",
            ()-> new StairBlock(POLISHED_SACRED_STONE.get().defaultBlockState(), sacredProps()));
    public static final DeferredBlock<Block> POLISHED_SACRED_STONE_SLAB = registerBlock("polished_sacred_stone_slab",
            ()-> new SlabBlock(sacredProps()));
    public static final DeferredBlock<Block> POLISHED_SACRED_STONE_WALL = registerBlock("polished_sacred_stone_wall",
            ()-> new WallBlock(sacredProps()));

    public static final DeferredBlock<Block> SACRED_STONE_BRICKS = registerBlock("sacred_stone_bricks",
            ()-> new Block(sacredProps()));
    public static final DeferredBlock<Block> SACRED_STONE_BRICK_STAIRS = registerBlock("sacred_stone_brick_stairs",
            ()-> new StairBlock(SACRED_STONE_BRICKS.get().defaultBlockState(), sacredProps()));
    public static final DeferredBlock<Block> SACRED_STONE_BRICK_SLAB = registerBlock("sacred_stone_brick_slab",
            ()-> new SlabBlock(sacredProps()));
    public static final DeferredBlock<Block> SACRED_STONE_BRICK_WALL = registerBlock("sacred_stone_brick_wall",
            ()-> new WallBlock(sacredProps()));

    private static BlockBehaviour.Properties sacredProps() {
        return BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .mapColor(MapColor.QUARTZ);
    }

    private static BlockBehaviour.Properties logProps() {
        return BlockBehaviour.Properties.of()
                .strength(2.0f)
                .sound(SoundType.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .ignitedByLava()
                .mapColor(MapColor.PLANT);
    }

    private static BlockBehaviour.Properties plankProps() {
        return BlockBehaviour.Properties.of()
                .strength(2.0f, 3.0f)
                .sound(SoundType.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .ignitedByLava()
                .mapColor(MapColor.PLANT);
    }

    public static final DeferredBlock<Block> OTHERWORLD_CLOUD = registerBlock("otherworld_cloud",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .sound(SoundType.WOOL)
                    .strength(-1.0f, 3600000)
                    .noCollission()
                    .noLootTable()));

    public static final DeferredBlock<Block> HTC_BLOCK = registerBlock("htc_block",
            ()  -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .sound(SoundType.STONE)
                    .strength(-1f,3600000)
                    .noLootTable()));

    public static final DeferredBlock<Block> HTC_PORTAL = registerBlock("htc_portal",
            ()-> new HtcPortalBlock(BlockBehaviour.Properties.of()
                    .strength(50f, 1200f)
                    .sound(SoundType.WOOD)
                    .noLootTable()
                    .lightLevel(s -> 10)
                    .noOcclusion()));

    public static void register(IEventBus eventBus) {
        MOD_BLOCKS.register(eventBus);
    }

    private static BlockBehaviour.Properties oreProps() {
        return BlockBehaviour.Properties.of()
                .strength(3f, 3f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .mapColor(MapColor.SAND);
    }
}
