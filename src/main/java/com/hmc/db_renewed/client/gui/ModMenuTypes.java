package com.hmc.db_renewed.client.gui;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, DragonBlockRenewed.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<StackWishMenu>> STACK_WISH =
            MENUS.register("stack_wish",
                    () -> new MenuType<>(
                            StackWishMenu::new,
                            FeatureFlags.VANILLA_SET
                    ));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
