package com.hmc.db_renewed.content.item;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.content.entity.ModEntities;
import com.hmc.db_renewed.core.network.feature.race.NamekainRaceArmorItem;
import com.hmc.db_renewed.content.item.special.*;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DragonBlockRenewed.MOD_ID);

    public static final DeferredItem<Item> SENZU_BEAN = ITEMS.registerItem("senzu_bean",
            SenzuBean::new,
            new SenzuBean.Properties());

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

    public static final DeferredItem<Item> ELITE_CIRCUIT = ITEMS.registerItem("elite_circuit",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<Item> SPACE_POD_ITEM = ITEMS.registerItem("space_pod_item",
            SpacePodItem::new,
            new Item.Properties()
                    .stacksTo(1));

    public static final DeferredItem<Item> KINTOUN_ITEM = ITEMS.registerItem("kintoun_item",
            KintounItem::new,
            new Item.Properties()
                    .stacksTo(1));

    public static final DeferredItem<Item> SHADOW_KINTOUN_ITEM = ITEMS.registerItem("kintoun_shadow_item",
            ShadowKintounItem::new,
            new Item.Properties()
                    .stacksTo(1));

    public static final DeferredItem<Item> TERRAGEM = ITEMS.registerItem("terragem",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<Item> TERRAGEM_DUST = ITEMS.registerItem("terragem_dust",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<SwordItem> TERRAGEM_SWORD = ITEMS.register("terragem_sword",
            () -> new SwordItem(ModToolTiers.TERRAGEM,
            new Item.Properties().attributes(SwordItem.createAttributes(ModToolTiers.TERRAGEM,3,-2.4f))));

    public static final DeferredItem<PickaxeItem> TERRAGEM_PICKAXE = ITEMS.register("terragem_pickaxe",
            () -> new PickaxeItem(ModToolTiers.TERRAGEM,
                    new Item.Properties().attributes(PickaxeItem.createAttributes(ModToolTiers.TERRAGEM,1,-2.8f))));

    public static final DeferredItem<ShovelItem> TERRAGEM_SHOVEL = ITEMS.register("terragem_shovel",
            () -> new ShovelItem(ModToolTiers.TERRAGEM,
                    new Item.Properties().attributes(ShovelItem.createAttributes(ModToolTiers.TERRAGEM,1.5f,-3.0f))));

    public static final DeferredItem<AxeItem> TERRAGEM_AXE = ITEMS.register("terragem_axe",
            () -> new AxeItem(ModToolTiers.TERRAGEM,
                    new Item.Properties().attributes(AxeItem.createAttributes(ModToolTiers.TERRAGEM,5f,-3.2f))));

    public static final DeferredItem<HoeItem> TERRAGEM_HOE = ITEMS.register("terragem_hoe",
            () -> new HoeItem(ModToolTiers.TERRAGEM,
                    new Item.Properties().attributes(HoeItem.createAttributes(ModToolTiers.TERRAGEM,-2.5f,-1f))));

    public static final DeferredItem<HammerItem> TERRAGEM_HAMMER = ITEMS.register("terragem_hammer",
            () -> new HammerItem(ModToolTiers.TERRAGEM, new Item.Properties()
                    .attributes(PickaxeItem.createAttributes(ModToolTiers.TERRAGEM,5.5f,-3.5f))
                    .attributes(ShovelItem.createAttributes(ModToolTiers.TERRAGEM,5.5f,-3.5f))
                    .attributes(AxeItem.createAttributes(ModToolTiers.TERRAGEM,5.5f,-3.5f))));

    public static final DeferredItem<Item> TERRAGEM_TEMPLATE = ITEMS.registerItem("terragem_template",
            Item::new,
            new Item.Properties());

    public static final DeferredItem<ArmorItem> TERRAGEM_HELMET = ITEMS.register("terragem_helmet",
            () -> new ArmorItem(ModArmorMaterials.TERRAGEM_ARMOR_MATERIAL,ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(25))));

    public static final DeferredItem<ArmorItem> TERRAGEM_CHESTPLATE = ITEMS.register("terragem_chestplate",
            () -> new ArmorItem(ModArmorMaterials.TERRAGEM_ARMOR_MATERIAL,ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(25))));

    public static final DeferredItem<ArmorItem> TERRAGEM_LEGGINGS = ITEMS.register("terragem_leggings",
            () -> new ArmorItem(ModArmorMaterials.TERRAGEM_ARMOR_MATERIAL,ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(25))));

    public static final DeferredItem<ArmorItem> TERRAGEM_BOOTS = ITEMS.register("terragem_boots",
            () -> new ArmorItem(ModArmorMaterials.TERRAGEM_ARMOR_MATERIAL,ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(25))));

    public static final DeferredItem<Item> WARRIOR_SPAWN_EGG = ITEMS.register("namekian_warrior_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NAMEKIAN_WARRIOR,0x28ad1b ,0x26b9fe,
                    new Item.Properties()));

    public static final DeferredItem<Item> NAMEKIAN_SPAWN_EGG = ITEMS.register("namekian_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NAMEKIAN,0x28ad1b ,0xfdfefe,
                    new Item.Properties()));

    public static final Supplier<NamekainRaceArmorItem> NAMEKIAN_RACE_HELMET = ITEMS.register("namekian_race_helmet",
            () -> new NamekainRaceArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL,
                    ArmorItem.Type.HELMET,new Item.Properties()));

    public static final Supplier<NamekainRaceArmorItem> NAMEKIAN_RACE_CHESTPLATE = ITEMS.register("namekian_race_chestplate",
            () -> new NamekainRaceArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL,
                    ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    public static final Supplier<NamekainRaceArmorItem> NAMEKIAN_RACE_LEGGINGS = ITEMS.register("namekian_race_leggings",
            () -> new NamekainRaceArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL,
                    ArmorItem.Type.LEGGINGS, new Item.Properties()));

    public static final Supplier<NamekainRaceArmorItem> NAMEKIAN_RACE_BOOTS = ITEMS.register("namekian_race_boots",
            () -> new NamekainRaceArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL,
                    ArmorItem.Type.BOOTS, new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
