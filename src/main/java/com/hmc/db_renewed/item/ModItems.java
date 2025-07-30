package com.hmc.db_renewed.item;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.item.custom.DragonRadarItem;
import com.hmc.db_renewed.item.custom.HammerItem;
import com.hmc.db_renewed.item.custom.SpacePodItem;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DragonBlockRenewed.MOD_ID);

    public static final DeferredItem<Item> SENZU_BEAN = ITEMS.registerItem("senzu_bean",
            Item::new,
            new Item.Properties().food(ModFoodProperties.SENZU_BEAN));

    public static final DeferredItem<Item> DRAGON_BALL_RADAR = ITEMS.registerItem("dragon_ball_radar",
            DragonRadarItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> BASIC_CIRCUIT = ITEMS.registerItem("basic_circuit",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<Item> ADVANCED_CIRCUIT = ITEMS.registerItem("advanced_circuit",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<Item> ULTIMATE_CIRCUIT = ITEMS.registerItem("ultimate_circuit",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<Item> WARENAI_CRYSTAL = ITEMS.registerItem("warenai_crystal",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<Item> WARENAI_CRYSTAL_DUST = ITEMS.registerItem("warenai_crystal_dust",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<SwordItem> WARENAI_CRYSTAL_SWORD = ITEMS.register("warenai_crystal_sword",
            () -> new SwordItem(ModToolTiers.WARENAI_CRYSTAL,
            new Item.Properties().attributes(SwordItem.createAttributes(ModToolTiers.WARENAI_CRYSTAL,3,-2.4f))));

    public static final DeferredItem<PickaxeItem> WARENAI_CRYSTAL_PICKAXE = ITEMS.register("warenai_crystal_pickaxe",
            () -> new PickaxeItem(ModToolTiers.WARENAI_CRYSTAL,
                    new Item.Properties().attributes(PickaxeItem.createAttributes(ModToolTiers.WARENAI_CRYSTAL,1,-2.8f))));

    public static final DeferredItem<ShovelItem> WARENAI_CRYSTAL_SHOVEL = ITEMS.register("warenai_crystal_shovel",
            () -> new ShovelItem(ModToolTiers.WARENAI_CRYSTAL,
                    new Item.Properties().attributes(ShovelItem.createAttributes(ModToolTiers.WARENAI_CRYSTAL,1.5f,-3.0f))));

    public static final DeferredItem<AxeItem> WARENAI_CRYSTAL_AXE = ITEMS.register("warenai_crystal_axe",
            () -> new AxeItem(ModToolTiers.WARENAI_CRYSTAL,
                    new Item.Properties().attributes(AxeItem.createAttributes(ModToolTiers.WARENAI_CRYSTAL,5f,-3.2f))));

    public static final DeferredItem<HoeItem> WARENAI_CRYSTAL_HOE = ITEMS.register("warenai_crystal_hoe",
            () -> new HoeItem(ModToolTiers.WARENAI_CRYSTAL,
                    new Item.Properties().attributes(HoeItem.createAttributes(ModToolTiers.WARENAI_CRYSTAL,-2.5f,-1f))));

    public static final DeferredItem<HammerItem> WARENAI_CRYSTAL_HAMMER = ITEMS.register("warenai_crystal_hammer",
            () -> new HammerItem(ModToolTiers.WARENAI_CRYSTAL, new Item.Properties()
                    .attributes(PickaxeItem.createAttributes(ModToolTiers.WARENAI_CRYSTAL,5.5f,-3.5f))
                    .attributes(ShovelItem.createAttributes(ModToolTiers.WARENAI_CRYSTAL,5.5f,-3.5f))
                    .attributes(AxeItem.createAttributes(ModToolTiers.WARENAI_CRYSTAL,5.5f,-3.5f))));

    public static final DeferredItem<Item> WARENAI_TEMPLATE = ITEMS.registerItem("warenai_template",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<ArmorItem> WARENAI_CRYSTAL_HELMET = ITEMS.register("warenai_crystal_helmet",
            () -> new ArmorItem(ModArmorMaterials.WARENAI_CRYSTAL_ARMOR_MATERIAL,ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(25))));

    public static final DeferredItem<ArmorItem> WARENAI_CRYSTAL_CHESTPLATE = ITEMS.register("warenai_crystal_chestplate",
            () -> new ArmorItem(ModArmorMaterials.WARENAI_CRYSTAL_ARMOR_MATERIAL,ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(25))));

    public static final DeferredItem<ArmorItem> WARENAI_CRYSTAL_LEGGINGS = ITEMS.register("warenai_crystal_leggings",
            () -> new ArmorItem(ModArmorMaterials.WARENAI_CRYSTAL_ARMOR_MATERIAL,ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(25))));

    public static final DeferredItem<ArmorItem> WARENAI_CRYSTAL_BOOTS = ITEMS.register("warenai_crystal_boots",
            () -> new ArmorItem(ModArmorMaterials.WARENAI_CRYSTAL_ARMOR_MATERIAL,ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(25))));

    public static final DeferredItem<Item> SPACE_POD_ITEM = ITEMS.registerItem("space_pod_item",
            SpacePodItem::new,
            new Item.Properties()
                    .stacksTo(1));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
