package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.*;
import com.hmc.zenkai.feature.race.layer.GeoLayerArmorItem;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

    public static final DeferredItem<Item> ALL_DRAGON_BALLS_ITEM = ITEMS.registerItem("all_dragon_balls",
            props -> new BlockItem(ModBlocks.ALL_DRAGON_BALLS.get(), props),
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> BASIC_CIRCUIT = ITEMS.registerItem("basic_circuit",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> ADVANCED_CIRCUIT = ITEMS.registerItem("advanced_circuit",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> ELITE_CIRCUIT = ITEMS.registerItem("elite_circuit",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> SCOUTER_RADAR_UPGRADE = ITEMS.registerItem("scouter_radar_upgrade",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> SPACE_POD_ITEM = ITEMS.registerItem("space_pod_item",
            SpacePodItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> KINTOUN_ITEM = ITEMS.registerItem("kintoun_item",
            KintounItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> SHADOW_KINTOUN_ITEM = ITEMS.registerItem("kintoun_shadow_item",
            ShadowKintounItem::new, new Item.Properties().stacksTo(1));

    /** Mineral en bruto. Solo la fundición lo convierte en lingote. */
    public static final DeferredItem<Item> RAW_KATCHIN = ITEMS.registerItem("raw_katchin",
            Item::new, new Item.Properties());

    /** El metal más duro del Universo 7. No es un tier de herramienta: es material
     *  estructural y de equipo. Ver PowerMining para por qué no se pica con un pico. */
    public static final DeferredItem<Item> KATCHIN_INGOT = ITEMS.registerItem("katchin_ingot",
            Item::new, new Item.Properties().rarity(Rarity.RARE));

    // ── Recursos exclusivos de Namek ─────────────────────────────────────────
    public static final DeferredItem<Item> NAMEK_CRYSTAL = ITEMS.registerItem("namek_crystal",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> ENERGY_CRYSTAL = ITEMS.registerItem("energy_crystal",
            Item::new, new Item.Properties());

    public static final DeferredItem<Item> SACRED_STONE = ITEMS.registerItem("sacred_stone",
            Item::new, new Item.Properties());

    /** Semilla. ItemNameBlockItem hace que el item se llame como él y coloque el cultivo. */
    public static final DeferredItem<Item> NAMEKIAN_HERB_SEEDS = ITEMS.registerItem("namekian_herb_seeds",
            props -> new ItemNameBlockItem(ModBlocks.NAMEKIAN_HERB_CROP.get(), props),
            new Item.Properties());

    /** Producto de la cosecha. Ingrediente del líquido curativo. */
    public static final DeferredItem<Item> NAMEKIAN_HERB = ITEMS.registerItem("namekian_herb",
            Item::new, new Item.Properties());

    /** El agua curativa de la saga de Namek. Se bebe, regeneración media, devuelve botella. */
    public static final DeferredItem<Item> HEALING_WATER_BOTTLE = ITEMS.registerItem("healing_water_bottle",
            HealingWaterItem::new, new Item.Properties()
                    .stacksTo(16)
                    .food(new FoodProperties.Builder()
                            .nutrition(0)
                            .saturationModifier(0f)
                            .alwaysEdible()
                            .effect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), 1.0f)
                            .build()));

    public static final DeferredItem<Item> WARRIOR_SPAWN_EGG = ITEMS.register("namekian_warrior_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NAMEKIAN_WARRIOR,0x28ad1b ,0x26b9fe, new Item.Properties()));

    public static final DeferredItem<Item> NAMEKIAN_SPAWN_EGG = ITEMS.register("namekian_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NAMEKIAN,0x28ad1b ,0xfdfefe, new Item.Properties()));

    public static final DeferredItem<Item> YEMMA_SPAWN_EGG = ITEMS.register("yemma_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.YEMMA,0xc96a6f,0x565f97, new Item.Properties()));

    public static final DeferredItem<Item> SAIBAMAN_SPAWN_EGG = ITEMS.register("saibaman_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SAIBAMAN,0xd3cf5d,0x18460d, new Item.Properties()));

    public static final DeferredItem<Item> SHENLONG_SPAWN_EGG = ITEMS.register("shenlong_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SHENLONG,0x2E8B57,0xD2B48C,new Item.Properties()));

    public static final DeferredItem<Item> ISAAC_SPAWN_EGG = ITEMS.register("isaac_spawn_egg",
    () -> new DeferredSpawnEggItem(ModEntities.ISAAC,0xe4c7c5,0x9c716f, new Item.Properties()));

    // ── Pesas de entrenamiento ───────────────────────────────────────────────
    // Los rangos son los que definen la escalera: el máximo de las de Goku es el mínimo de
    // las de Piccolo, así que llevar las dos a tope da 2100 t y no hay hueco entre tramos.
    public static final DeferredItem<WeightArmorItem> WEIGHTED_STRAPS =
            ITEMS.register("weighted_straps", () -> new WeightArmorItem(
                    "geo/weighted_straps.geo.json",
                    "textures/models/armor/weighted_straps.png",
                    5.0, 100.0));

    public static final DeferredItem<WeightArmorItem> WEIGHTED_CAPE =
            ITEMS.register("weighted_cape", () -> new WeightArmorItem(
                    "geo/weighted_cape.geo.json",
                    "textures/models/armor/weighted_cape.png",
                    100.0, 2000.0));


    /** Espada de ki. Equilibrada: alcance +1, velocidad de espada. */
    public static final DeferredItem<Item> KI_BLADE = ITEMS.registerItem("ki_blade",
            p -> new KiWeaponItem(p, SkillEffects.KI_BLADE,"ki_blade"), new Item.Properties()
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_blade_dmg"),
                                            3.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED, new AttributeModifier(
                                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_blade_spd"),
                                            -2.4, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_blade_reach"),
                                            1.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND).build()));

        /** Guadaña de ki. Más daño y alcance, más lenta y mucho más cara en ki. */
        public static final DeferredItem<Item> KI_SCYTHE = ITEMS.registerItem("ki_scythe",
                        p -> new KiWeaponItem(p, SkillEffects.KI_SCYTHE,"ki_scythe"), new Item.Properties()
                        .attributes(ItemAttributeModifiers.builder()
                                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                                        ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_scythe_dmg"),
                                                4.0, AttributeModifier.Operation.ADD_VALUE),
                                        EquipmentSlotGroup.MAINHAND)
                                .add(Attributes.ATTACK_SPEED, new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_scythe_spd"),
                                                -3.0, AttributeModifier.Operation.ADD_VALUE),
                                        EquipmentSlotGroup.MAINHAND)
                                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                                        ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_scythe_reach"),
                                                2.0, AttributeModifier.Operation.ADD_VALUE),
                                        EquipmentSlotGroup.MAINHAND).build()));



    //Razas
    public static final DeferredItem<GeoLayerArmorItem> NAMEKIAN_RACE_HELMET =
            ITEMS.register("namekian_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_CHESTPLATE =
            ITEMS.register("namekian_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_LEGGINGS =
            ITEMS.register("namekian_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_BOOTS =
            ITEMS.register("namekian_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_HELMET_COLORABLE =
            ITEMS.register("namekian_race_helmet_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_CHESTPLATE_COLORABLE =
            ITEMS.register("namekian_race_chestplate_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_LEGGINGS_COLORABLE =
            ITEMS.register("namekian_race_leggings_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_BOOTS_COLORABLE =
            ITEMS.register("namekian_race_boots_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET =
            ITEMS.register("human_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE =
            ITEMS.register("human_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS =
            ITEMS.register("human_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS =
            ITEMS.register("human_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET_COLORABLE =
            ITEMS.register("human_race_helmet_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE_COLORABLE =
            ITEMS.register("human_race_chestplate_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS_COLORABLE =
            ITEMS.register("human_race_leggings_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS_COLORABLE =
            ITEMS.register("human_race_boots_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    // ── Human/Saiyan FEMENINO (modelo + textura femeninos) ─────────────────────
    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET_FEMALE =
            ITEMS.register("human_race_helmet_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE_FEMALE =
            ITEMS.register("human_race_chestplate_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS_FEMALE =
            ITEMS.register("human_race_leggings_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS_FEMALE =
            ITEMS.register("human_race_boots_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET_COLORABLE_FEMALE =
            ITEMS.register("human_race_helmet_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE_COLORABLE_FEMALE =
            ITEMS.register("human_race_chestplate_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS_COLORABLE_FEMALE =
            ITEMS.register("human_race_leggings_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS_COLORABLE_FEMALE =
            ITEMS.register("human_race_boots_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_HELMET =
            ITEMS.register("arcosian_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/arcosian_first_form_player.geo.json",
                            "textures/models/races/arcosian_first_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_CHESTPLATE =
            ITEMS.register("arcosian_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/arcosian_first_form_player.geo.json",
                            "textures/models/races/arcosian_first_form_player_layer_0.png",
                            "animations/freezer.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_LEGGINGS =
            ITEMS.register("arcosian_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/arcosian_first_form_player.geo.json",
                            "textures/models/races/arcosian_first_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_BOOTS =
            ITEMS.register("arcosian_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/arcosian_first_form_player.geo.json",
                            "textures/models/races/arcosian_first_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    //Halo
    public static final DeferredItem<GeoLayerArmorItem> HALO =
            ITEMS.register("halo", () -> new GeoLayerArmorItem(
                    ArmorMaterials.LEATHER, ArmorItem.Type.HELMET,
                    new Item.Properties(),
                    "geo/halo.geo.json",
                    "textures/models/races/halo.png",
                    ""));

    public static final DeferredItem<ScouterItem> SCOUTER =
            ITEMS.register("scouter", () -> new ScouterItem(
                    ArmorMaterials.IRON,
                    new Item.Properties(),
                    "geo/scouter.geo.json",
                    "textures/models/armor/scouter.png"));

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