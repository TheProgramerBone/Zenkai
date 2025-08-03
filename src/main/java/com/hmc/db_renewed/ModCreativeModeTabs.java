package com.hmc.db_renewed;

import com.hmc.db_renewed.block.ModBlocks;
import com.hmc.db_renewed.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DragonBlockRenewed.MOD_ID);

    public static final Supplier<CreativeModeTab> CREATIVE_MODE_ITEMS = CREATIVE_MODE_TAB.register("db_renewed_tab",
            () -> CreativeModeTab.builder()
            .title(Component.translatable("creativetab.db_renewed"))
                    .icon(() -> new ItemStack(ModItems.SENZU_BEAN.get()))
                    .displayItems((params, output) -> {
                        ModItems.ITEMS.getEntries().forEach(supplier -> {
                            Item item = supplier.get();
                            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                            if (id.getNamespace().equals(DragonBlockRenewed.MOD_ID)) {
                                output.accept(item);
                            }
                        });
                    })
                    .build()
    );

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
