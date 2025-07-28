package com.hmc.db_renewed;

import com.hmc.db_renewed.block.ModBlocks;
import com.hmc.db_renewed.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DragonBlockRenewed.MOD_ID);

    public static final Supplier<CreativeModeTab> CREATIVE_MODE_ITEMS = CREATIVE_MODE_TAB.register("db_renewed_items_tab",
            () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.db_renewed.items"))
            .icon(() -> new ItemStack(ModItems.SENZU_BEAN.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.SENZU_BEAN.get());
                output.accept(ModItems.DRAGON_BALL_RADAR.get());
                output.accept(ModItems.BASIC_CIRCUIT.get());
                output.accept(ModItems.ADVANCED_CIRCUIT.get());
                output.accept(ModItems.ULTIMATE_CIRCUIT.get());
                output.accept(ModItems.WARENAI_CRYSTAL.get());
                output.accept(ModItems.WARENAI_CRYSTAL_DUST.get());
                output.accept(ModItems.WARENAI_CRYSTAL_SWORD.get());
                output.accept(ModItems.WARENAI_CRYSTAL_PICKAXE.get());
                output.accept(ModItems.WARENAI_CRYSTAL_AXE.get());
                output.accept(ModItems.WARENAI_CRYSTAL_SHOVEL.get());
                output.accept(ModItems.WARENAI_CRYSTAL_HOE.get());
                output.accept(ModItems.WARENAI_CRYSTAL_HELMET.get());
                output.accept(ModItems.WARENAI_CRYSTAL_CHESTPLATE.get());
                output.accept(ModItems.WARENAI_CRYSTAL_LEGGINGS.get());
                output.accept(ModItems.WARENAI_CRYSTAL_BOOTS.get());
            })
            .build()
    );

    public static final Supplier<CreativeModeTab> CREATIVE_MODE_BLOCKS = CREATIVE_MODE_TAB.register("db_renewed_blocks_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.db_renewed.blocks"))
                    .icon(() -> new ItemStack(ModBlocks.WARENAI_CRYSTAL_BLOCK.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.DRAGON_BALL_1.get());
                        output.accept(ModBlocks.DRAGON_BALL_2.get());
                        output.accept(ModBlocks.DRAGON_BALL_3.get());
                        output.accept(ModBlocks.DRAGON_BALL_4.get());
                        output.accept(ModBlocks.DRAGON_BALL_5.get());
                        output.accept(ModBlocks.DRAGON_BALL_6.get());
                        output.accept(ModBlocks.DRAGON_BALL_7.get());
                        output.accept(ModBlocks.NAMEK_DRAGON_BALL_1.get());
                        output.accept(ModBlocks.NAMEK_DRAGON_BALL_2.get());
                        output.accept(ModBlocks.NAMEK_DRAGON_BALL_3.get());
                        output.accept(ModBlocks.NAMEK_DRAGON_BALL_4.get());
                        output.accept(ModBlocks.NAMEK_DRAGON_BALL_5.get());
                        output.accept(ModBlocks.NAMEK_DRAGON_BALL_6.get());
                        output.accept(ModBlocks.NAMEK_DRAGON_BALL_7.get());
                        output.accept(ModBlocks.WARENAI_CRYSTAL_BLOCK.get());
                        output.accept(ModBlocks.WARENAI_CRYSTAL_ORE.get());
                        output.accept(ModBlocks.DEEPSLATE_WARENAI_CRYSTAL_ORE.get());
                        output.accept(ModBlocks.NAMEKIAN_GRASS_BLOCK.get());
                        output.accept(ModBlocks.NAMEKIAN_DIRT_BLOCK.get());
                    })
                    .build()
    );

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
