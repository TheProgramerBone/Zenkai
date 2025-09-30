package com.hmc.db_renewed.block;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.block.entity.AllDragonBalls.AllDragonBallsBlock;
import com.hmc.db_renewed.block.special.DragonBalls;
import com.hmc.db_renewed.block.special.NamekDragonBalls;
import com.hmc.db_renewed.block.special.NamekianGrassBlock;
import com.hmc.db_renewed.item.ModItems;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks MOD_BLOCKS =
            DeferredRegister.createBlocks(DragonBlockRenewed.MOD_ID);

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

    public static final DeferredBlock<Block> WARENAI_CRYSTAL_BLOCK = registerBlock("warenai_crystal_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(2f,3f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .mapColor(MapColor.COLOR_BROWN)));

    public static final DeferredBlock<Block> WARENAI_CRYSTAL_ORE = registerBlock("warenai_crystal_ore",
            ()-> new DropExperienceBlock(UniformInt.of(2,4),
                    BlockBehaviour.Properties.of()
                            .strength(2f,3f)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.STONE)
                            .mapColor(MapColor.STONE)));

    public static final DeferredBlock<Block> DEEPSLATE_WARENAI_CRYSTAL_ORE = registerBlock("deepslate_warenai_crystal_ore",
            ()-> new DropExperienceBlock(UniformInt.of(3,5),
                    BlockBehaviour.Properties.of()
                            .strength(3f,3f)
                            .requiresCorrectToolForDrops()
                            .sound(SoundType.DEEPSLATE)
                            .mapColor(MapColor.DEEPSLATE)));

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

    public static final DeferredBlock<Block> NAMEKIAN_STRUCTURE_BLOCK = registerBlock("namekian_structure_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(3f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .mapColor(MapColor.QUARTZ)));


    public static void register(IEventBus eventBus) {
        MOD_BLOCKS.register(eventBus);
    }
}
