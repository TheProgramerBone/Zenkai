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

    public static final DeferredHolder<MenuType<?>, MenuType<PlayerBodyMenu>> PLAYER_BODY =
            MENUS.register("player_body",
                    () -> IMenuTypeExtension.create(PlayerBodyMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.hmc.zenkai.client.gui.menu.KiChamberMenu>> KI_CHAMBER =
            MENUS.register("ki_chamber",
                    () -> IMenuTypeExtension.create(com.hmc.zenkai.client.gui.menu.KiChamberMenu::new));

    /** Tanques de energía y de fluidos (0.7.0): un menú para los dos, el tipo viaja en el buffer. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.hmc.zenkai.client.gui.menu.TankMenu>> TANK =
            MENUS.register("tank",
                    () -> IMenuTypeExtension.create(com.hmc.zenkai.client.gui.menu.TankMenu::new));

    /** Consola de la cámara de gravedad (0.7.0, Fase 3). */
    /** Máquina de fabricación de armaduras (0.7.0, Fase 5b). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.hmc.zenkai.client.gui.menu.ArmorFabricatorMenu>> ARMOR_FABRICATOR =
            MENUS.register("armor_fabricator",
                    () -> IMenuTypeExtension.create(com.hmc.zenkai.client.gui.menu.ArmorFabricatorMenu::new));

    /** Infusora de Ki (0.7.0, Fase 5c). */
    public static final DeferredHolder<MenuType<?>, MenuType<com.hmc.zenkai.client.gui.menu.KiInfuserMenu>> KI_INFUSER =
            MENUS.register("ki_infuser",
                    () -> IMenuTypeExtension.create(com.hmc.zenkai.client.gui.menu.KiInfuserMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<GravityChamberMenu>> GRAVITY_CHAMBER =
            MENUS.register("gravity_chamber",
                    () -> IMenuTypeExtension.create(GravityChamberMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
