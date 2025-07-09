package com.hmc.db_renewed.block;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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

    //Bloques aquí:
    public static final DeferredBlock<Block> WARENAI_CRYSTAL_BLOCK = registerBlock("warenai_crystal_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(2f,3f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .mapColor(MapColor.COLOR_BROWN)));

    public static void register(IEventBus eventBus) {
        MOD_BLOCKS.register(eventBus);
    }
}
