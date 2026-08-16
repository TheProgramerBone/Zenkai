package com.hmc.zenkai.client.gui;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.menu.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, Zenkai.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<StackWishMenu>> STACK_WISH =
            MENUS.register("stack_wish",
                    () -> new MenuType<>(
                            StackWishMenu::new,
                            FeatureFlags.VANILLA_SET
                    ));

    public static final DeferredHolder<MenuType<?>, MenuType<ScouterBenchMenu>> SCOUTER_BENCH =
            MENUS.register("scouter_bench",
                    () -> IMenuTypeExtension.create(ScouterBenchMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<EnergyGeneratorMenu>> ENERGY_GENERATOR =
            MENUS.register("energy_generator",
                    () -> IMenuTypeExtension.create(EnergyGeneratorMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
