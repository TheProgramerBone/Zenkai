package com.hmc.db_renewed.item;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.item.custom.ModFoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DragonBlockRenewed.MOD_ID);

    public static final DeferredItem<Item> SENZU_BEAN = ITEMS.registerItem("senzu_bean",
            Item::new,
            new Item.Properties().food(ModFoodProperties.SENZU_BEAN));

    public static final DeferredItem<Item> WARENAI_CRYSTAL = ITEMS.registerItem("warenai_crystal",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<Item> WARENAI_CRYSTAL_DUST = ITEMS.registerItem("warenai_crystal_dust",
            Item::new,
            new Item.Properties());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
