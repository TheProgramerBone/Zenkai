package com.hmc.zenkai.content.item;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.ModEntities;
import com.hmc.zenkai.core.network.feature.race.GeoLayerArmorItem;
import com.hmc.zenkai.content.item.special.*;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Zenkai.MOD_ID);

    public static final DeferredItem<Item> SENZU_BEAN = ITEMS.registerItem("senzu_bean",
            SenzuBean::new,
            new SenzuBean.Properties());

    public static final DeferredItem<Item> DRAGON_BALL_RADAR = ITEMS.registerItem("dragon_ball_radar",
            DragonRadarItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> BASIC_CIRCUIT = ITEMS.registerItem("basic_circuit",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> ADVANCED_CIRCUIT = ITEMS.registerItem("advanced_circuit",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> ELITE_CIRCUIT = ITEMS.registerItem("elite_circuit",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> SPACE_POD_ITEM = ITEMS.registerItem("space_pod_item",
            SpacePodItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> KINTOUN_ITEM = ITEMS.registerItem("kintoun_item",
            KintounItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> SHADOW_KINTOUN_ITEM = ITEMS.registerItem("kintoun_shadow_item",
            ShadowKintounItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> TERRAGEM = ITEMS.registerItem("terragem",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> TERRAGEM_DUST = ITEMS.registerItem("terragem_dust",
            Item::new, new Item.Properties());

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
            Item::new, new Item.Properties());

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
            () -> new DeferredSpawnEggItem(ModEntities.NAMEKIAN_WARRIOR,0x28ad1b ,0x26b9fe, new Item.Properties()));

    public static final DeferredItem<Item> NAMEKIAN_SPAWN_EGG = ITEMS.register("namekian_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NAMEKIAN,0x28ad1b ,0xfdfefe, new Item.Properties()));

    //Razas

    public static final DeferredItem<GeoLayerArmorItem> NAMEKIAN_RACE_HELMET =
            ITEMS.register("namekian_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_CHESTPLATE =
            ITEMS.register("namekian_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_LEGGINGS =
            ITEMS.register("namekian_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_BOOTS =
            ITEMS.register("namekian_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_HELMET_COLORABLE =
            ITEMS.register("namekian_race_helmet_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_CHESTPLATE_COLORABLE =
            ITEMS.register("namekian_race_chestplate_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_LEGGINGS_COLORABLE =
            ITEMS.register("namekian_race_leggings_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_BOOTS_COLORABLE =
            ITEMS.register("namekian_race_boots_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET =
            ITEMS.register("human_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE =
            ITEMS.register("human_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS =
            ITEMS.register("human_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS =
            ITEMS.register("human_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET_COLORABLE =
            ITEMS.register("human_race_helmet_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE_COLORABLE =
            ITEMS.register("human_race_chestplate_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS_COLORABLE =
            ITEMS.register("human_race_leggings_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS_COLORABLE =
            ITEMS.register("human_race_boots_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    // ── Human/Saiyan FEMENINO (modelo + textura femeninos) ─────────────────────
    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET_FEMALE =
            ITEMS.register("human_race_helmet_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE_FEMALE =
            ITEMS.register("human_race_chestplate_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS_FEMALE =
            ITEMS.register("human_race_leggings_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS_FEMALE =
            ITEMS.register("human_race_boots_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET_COLORABLE_FEMALE =
            ITEMS.register("human_race_helmet_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE_COLORABLE_FEMALE =
            ITEMS.register("human_race_chestplate_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS_COLORABLE_FEMALE =
            ITEMS.register("human_race_leggings_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS_COLORABLE_FEMALE =
            ITEMS.register("human_race_boots_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female_colorable.png",
                            "animations/namekian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_HELMET =
            ITEMS.register("arcosian_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/arcosian_final_form_player.geo.json",
                            "textures/models/races/arcosian_final_form_player.png",
                            "animations/arcosian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_CHESTPLATE =
            ITEMS.register("arcosian_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/arcosian_final_form_player.geo.json",
                            "textures/models/races/arcosian_final_form_player.png",
                            "animations/arcosian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_LEGGINGS =
            ITEMS.register("arcosian_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/arcosian_final_form_player.geo.json",
                            "textures/models/races/arcosian_final_form_player.png",
                            "animations/arcosian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_BOOTS =
            ITEMS.register("arcosian_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/arcosian_final_form_player.geo.json",
                            "textures/models/races/arcosian_final_form_player.png",
                            "animations/arcosian_default.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    //Halo
    public static final DeferredItem<GeoLayerArmorItem> HALO =
            ITEMS.register("halo", () -> new GeoLayerArmorItem(
                    ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET, new Item.Properties(),
                    "geo/halo.geo.json",
                    "textures/models/races/halo.png",
                    ""));

    //Cabellos Común
    public static final Supplier<GeoLayerArmorItem> HAIR_1 =
            ITEMS.register("hair_1",() ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/hair/hair_1.geo.json",
                            "textures/customization/hair/hair_1.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.HAIR));

    //Transformaciones Saiyan
    public static final Supplier<GeoLayerArmorItem> SSJ1_HAIR1 =
            ITEMS.register("ssj_hair1",() ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/hair/ssj_hair_1.geo.json",
                            "textures/customization/hair/ssj_hair_1.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.HAIR));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}