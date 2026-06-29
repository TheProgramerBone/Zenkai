package com.hmc.zenkai;

import com.hmc.zenkai.content.item.ModArmorMaterials;
import com.hmc.zenkai.content.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Zenkai.MOD_ID);

    public static final Supplier<CreativeModeTab> CREATIVE_MODE_ITEMS = CREATIVE_MODE_TAB.register("zenkai_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.zenkai"))
                    .icon(() -> new ItemStack(ModItems.SENZU_BEAN.get()))
                    .displayItems((params, output) -> {

                        // Items de material RACE que SÍ quieres mostrar (excepciones a la auto-exclusión).
                        Set<Item> raceMaterialExceptions = new HashSet<>(List.of(
                                ModItems.HALO.get()
                        ));

                        // Items que NO son de material RACE pero igual quieres ocultar (red de seguridad).
                        Set<Item> extraExcluded = new HashSet<>(List.of(
                                ModItems.HAIR_1.get(),
                                ModItems.SSJ1_HAIR1.get()
                        ));

                        ModItems.ITEMS.getEntries().forEach(supplier -> {
                            Item item = supplier.get();
                            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                            if (!id.getNamespace().equals(Zenkai.MOD_ID)) return;
                            boolean hideByMaterial = isRaceArmor(item) && !raceMaterialExceptions.contains(item);

                            if (hideByMaterial || extraExcluded.contains(item)) return;

                            output.accept(item);
                        });
                    })
                    .build()
    );

    /** True si el item es una armadura cuyo material es RACE_ARMOR_MATERIAL. */
    private static boolean isRaceArmor(Item item) {
        if (!(item instanceof ArmorItem armor)) return false;
        return armor.getMaterial().equals(ModArmorMaterials.RACE_ARMOR_MATERIAL);
    }

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TAB.register(eventBus);
    }
}