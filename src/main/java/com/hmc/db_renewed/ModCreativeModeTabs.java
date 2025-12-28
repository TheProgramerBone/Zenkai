package com.hmc.db_renewed;

import com.hmc.db_renewed.content.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DragonBlockRenewed.MOD_ID);

    public static final Supplier<CreativeModeTab> CREATIVE_MODE_ITEMS = CREATIVE_MODE_TAB.register("db_renewed_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.db_renewed"))
                    .icon(() -> new ItemStack(ModItems.SENZU_BEAN.get()))
                    .displayItems((params, output) -> {

                        Set<Item> excludedItems = new HashSet<>(Arrays.asList(
                                ModItems.NAMEKIAN_RACE_HELMET.get(),
                                ModItems.NAMEKIAN_RACE_CHESTPLATE.get(),
                                ModItems.NAMEKIAN_RACE_LEGGINGS.get(),
                                ModItems.NAMEKIAN_RACE_BOOTS.get(),
                                ModItems.ARCOSIAN_RACE_HELMET.get(),
                                ModItems.ARCOSIAN_RACE_CHESTPLATE.get(),
                                ModItems.ARCOSIAN_RACE_LEGGINGS.get(),
                                ModItems.ARCOSIAN_RACE_BOOTS.get(),
                                ModItems.HAIR_1.get(),
                                ModItems.SSJ1_HAIR1.get()
                        ));

                        ModItems.ITEMS.getEntries().forEach(supplier -> {
                            Item item = supplier.get();
                            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

                            if (id.getNamespace().equals(DragonBlockRenewed.MOD_ID) && !excludedItems.contains(item)) {
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
