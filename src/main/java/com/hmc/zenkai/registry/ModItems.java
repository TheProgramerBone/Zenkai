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

    public static final DeferredItem<Item> NAMEK_ALL_DRAGON_BALLS_ITEM = ITEMS.registerItem("namek_all_dragon_balls",
            props -> new BlockItem(ModBlocks.NAMEK_ALL_DRAGON_BALLS.get(), props),
            new Item.Properties().stacksTo(1));

    /** Tijeras: abren solo la pestaña Pelo de la apariencia (ScissorsItem). */
    public static final DeferredItem<Item> SCISSORS = ITEMS.registerItem("scissors",
            com.hmc.zenkai.content.item.ScissorsItem::new, new Item.Properties().stacksTo(1));

    /**
     * Alma: la sueltan los ogros del HFIL (25 % por tabla de botín, con Looting) y Uranai Baba cobra
     * {@code permadeath.visit_soul_cost} por cada visita a la Tierra. Textura animada 16x16 (tools/gen_soul_item.py).
     */
    public static final DeferredItem<Item> SOUL = ITEMS.registerItem("soul",
            Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> BASIC_CIRCUIT =ITEMS.registerItem("basic_circuit",
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

    /** Intermedio de la cadena de Mekanism: mena machacada, todavía con roca pegada.
     *  NO es polvo de katchin — el metal no se pulveriza; lo que se rompe es la piedra que
     *  lo envuelve, igual que la escoria antigua en la cadena de netherita. */
    public static final DeferredItem<Item> DIRTY_RAW_KATCHIN = ITEMS.registerItem("dirty_raw_katchin",
            Item::new, new Item.Properties());

    /** El metal más duro del Universo 7. No es un tier de herramienta: es material
     *  estructural y de equipo. Ver PowerMining para por qué no se pica con un pico. */
    // ── Mejoras de máquina y batería (0.7.0, Fase 2b) ────────────────────────
    // La tolerancia a Ki Base viene de serie en las máquinas: solo hay mejora para los otros tres tipos.
    public static final DeferredItem<Item> KI_TOLERANCE_SUPER = ITEMS.registerItem("ki_tolerance_super",
            com.hmc.zenkai.content.item.MachineUpgradeItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> KI_TOLERANCE_DIVINE = ITEMS.registerItem("ki_tolerance_divine",
            com.hmc.zenkai.content.item.MachineUpgradeItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> KI_TOLERANCE_LEGENDARY = ITEMS.registerItem("ki_tolerance_legendary",
            com.hmc.zenkai.content.item.MachineUpgradeItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    public static final DeferredItem<Item> MACHINE_SPEED_UPGRADE = ITEMS.registerItem("machine_speed_upgrade",
            com.hmc.zenkai.content.item.MachineUpgradeItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> MACHINE_STORAGE_UPGRADE = ITEMS.registerItem("machine_storage_upgrade",
            com.hmc.zenkai.content.item.MachineUpgradeItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> ENERGY_BATTERY = ITEMS.registerItem("energy_battery",
            com.hmc.zenkai.content.item.EnergyBatteryItem::new, new Item.Properties());

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
                            .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 200, 1), 1.0f)
                            .build()));

    /** Cubo del agua curativa en forma de fluido (ver ModFluids/HealingWaterBlock). Se
     *  coloca/recoge igual que un cubo de agua normal: BucketItem vanilla ya cubre eso, no
     *  hace falta subclase propia. */
    public static final DeferredItem<Item> HEALING_WATER_BUCKET = ITEMS.registerItem("healing_water_bucket",
            p -> new BucketItem(ModFluids.HEALING_WATER.get(), p),
            new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET));

    /** Cubo del agua hirviendo del HFIL (ver ModFluids.HFIL_BOILING_WATER): BucketItem vanilla, el
     *  fluido quema por contacto al colocarla (HfilBoilingWaterBlock). */
    public static final DeferredItem<Item> HFIL_BOILING_WATER_BUCKET = ITEMS.registerItem("hfil_boiling_water_bucket",
            p -> new BucketItem(ModFluids.HFIL_BOILING_WATER.get(), p),
            new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET));

    public static final DeferredItem<Item> WARRIOR_SPAWN_EGG = ITEMS.register("namekian_warrior_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NAMEKIAN_WARRIOR,0x28ad1b ,0x26b9fe, new Item.Properties()));

    public static final DeferredItem<Item> NAMEKIAN_SPAWN_EGG = ITEMS.register("namekian_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.NAMEKIAN,0x28ad1b ,0xfdfefe, new Item.Properties()));

    public static final DeferredItem<Item> YEMMA_SPAWN_EGG = ITEMS.register("yemma_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.YEMMA,0xc96a6f,0x565f97, new Item.Properties()));

    public static final DeferredItem<Item> SAIBAMAN_SPAWN_EGG = ITEMS.register("saibaman_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SAIBAMAN,0xd3cf5d,0x18460d, new Item.Properties()));

    /** Ogros del HFIL: piel y camiseta blanca de cada uno. */
    public static final DeferredItem<Item> RED_OGRE_SPAWN_EGG = ITEMS.register("red_ogre_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.RED_OGRE, 0xC8241C, 0xE8E2DC, new Item.Properties()));
    public static final DeferredItem<Item> BLUE_OGRE_SPAWN_EGG = ITEMS.register("blue_ogre_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BLUE_OGRE, 0x6EC8DC, 0x7A5AC8, new Item.Properties()));
    /** Ángel del capullo de Freezer: túnica blanca y aro dorado. */
    public static final DeferredItem<Item> ANGEL_SPAWN_EGG = ITEMS.register("angel_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ANGEL, 0xF2F2F0, 0xF0C966, new Item.Properties()));

    public static final DeferredItem<Item> SHENLONG_SPAWN_EGG = ITEMS.register("shenlong_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SHENLONG,0x2E8B57,0xD2B48C,new Item.Properties()));

    public static final DeferredItem<Item> ISAAC_SPAWN_EGG = ITEMS.register("isaac_spawn_egg",
    () -> new DeferredSpawnEggItem(ModEntities.ISAAC,0xe4c7c5,0x9c716f, new Item.Properties()));

    /** Maestros: color dominante de su modelo y el de su detalle más reconocible. Kami: túnica
     *  blanca y piel namek; Korin: pelo blanco y bastón; Tori-Bot: chapa gris azulada y rojo. */
    public static final DeferredItem<Item> KAMI_SPAWN_EGG = ITEMS.register("kami_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.KAMI, 0xE5E5E5, 0x2DC31E, new Item.Properties()));
    public static final DeferredItem<Item> KORIN_SPAWN_EGG = ITEMS.register("korin_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.KORIN, 0xEAEEEF, 0x5C3A22, new Item.Properties()));
    public static final DeferredItem<Item> URANAI_BABA_SPAWN_EGG = ITEMS.register("uranai_baba_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.URANAI_BABA, 0x1E1E24, 0x82F7CB, new Item.Properties()));
    public static final DeferredItem<Item> TORIBOT_SPAWN_EGG = ITEMS.register("toribot_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.TORIBOT, 0xC0C5E2, 0xCC2C2A, new Item.Properties()));

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


    //Razas
    public static final DeferredItem<GeoLayerArmorItem> NAMEKIAN_RACE_HELMET =
            ITEMS.register("namekian_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian/namekian_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint()
                            .hideOnHelmet("antena1", "antena2", "orejaiz", "orejade")
                            .hideOnEarGear("orejaiz"));

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_CHESTPLATE =
            ITEMS.register("namekian_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian/namekian_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_LEGGINGS =
            ITEMS.register("namekian_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian/namekian_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_BOOTS =
            ITEMS.register("namekian_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian/namekian_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_HELMET_COLORABLE =
            ITEMS.register("namekian_race_helmet_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian/namekian_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint()
                            .hideOnHelmet("antena1", "antena2", "orejaiz", "orejade")
                            .hideOnEarGear("orejaiz"));

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_CHESTPLATE_COLORABLE =
            ITEMS.register("namekian_race_chestplate_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian/namekian_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_LEGGINGS_COLORABLE =
            ITEMS.register("namekian_race_leggings_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian/namekian_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEKIAN_RACE_BOOTS_COLORABLE =
            ITEMS.register("namekian_race_boots_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/namekian_player.geo.json",
                            "textures/models/races/namekian/namekian_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    // Namekiano Naranja (namek_orange). A diferencia de namek_giant/namek_god (que SÍ reusan
    // el cuerpo base a propósito, ver body_items:{} en su JSON), esta forma cambia de modelo
    // propio, solo que ligeramente más grande que el namekiano base (el "scale" real vive en
    // namek_orange.json, no aquí). Pendiente: sin .geo.json/textura todavía, ver
    // .claude/pendiente/arte-y-modelos.md — el sistema de RaceSkinSlots.backedOrEmpty
    // (mismo que protege a Majin) evita que crashee o se vea nada raro mientras tanto.
    public static final Supplier<GeoLayerArmorItem> NAMEK_ORANGE_HELMET =
            ITEMS.register("namek_orange_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/forms/namek_orange_player.geo.json",
                            "textures/models/races/namekian/orange_form/namek_orange_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEK_ORANGE_CHESTPLATE =
            ITEMS.register("namek_orange_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/forms/namek_orange_player.geo.json",
                            "textures/models/races/namekian/orange_form/namek_orange_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEK_ORANGE_LEGGINGS =
            ITEMS.register("namek_orange_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/forms/namek_orange_player.geo.json",
                            "textures/models/races/namekian/orange_form/namek_orange_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> NAMEK_ORANGE_BOOTS =
            ITEMS.register("namek_orange_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/forms/namek_orange_player.geo.json",
                            "textures/models/races/namekian/orange_form/namek_orange_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET =
            ITEMS.register("human_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human/human_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE =
            ITEMS.register("human_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human/human_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS =
            ITEMS.register("human_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human/human_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS =
            ITEMS.register("human_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human/human_player.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET_COLORABLE =
            ITEMS.register("human_race_helmet_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human/human_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE_COLORABLE =
            ITEMS.register("human_race_chestplate_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human/human_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS_COLORABLE =
            ITEMS.register("human_race_leggings_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human/human_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS_COLORABLE =
            ITEMS.register("human_race_boots_colorable", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player.geo.json",
                            "textures/models/races/human/human_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    // ── Human/Saiyan FEMENINO (modelo + textura femeninos) ─────────────────────
    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET_FEMALE =
            ITEMS.register("human_race_helmet_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human/human_player_female.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE_FEMALE =
            ITEMS.register("human_race_chestplate_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human/human_player_female.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS_FEMALE =
            ITEMS.register("human_race_leggings_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human/human_player_female.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS_FEMALE =
            ITEMS.register("human_race_boots_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human/human_player_female.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.NONE).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_HELMET_COLORABLE_FEMALE =
            ITEMS.register("human_race_helmet_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human/human_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_CHESTPLATE_COLORABLE_FEMALE =
            ITEMS.register("human_race_chestplate_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human/human_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_LEGGINGS_COLORABLE_FEMALE =
            ITEMS.register("human_race_leggings_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human/human_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> HUMAN_RACE_BOOTS_COLORABLE_FEMALE =
            ITEMS.register("human_race_boots_colorable_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/human_player_female.geo.json",
                            "textures/models/races/human/human_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_HELMET =
            ITEMS.register("arcosian_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/arcosian_first_form_player.geo.json",
                            "textures/models/races/arcosian/first_form/arcosian_first_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint()
                            .hideOnHelmet("casco", "cacho", "cacho2", "cacho3"));

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_CHESTPLATE =
            ITEMS.register("arcosian_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/arcosian_first_form_player.geo.json",
                            "textures/models/races/arcosian/first_form/arcosian_first_form_player_layer_0.png",
                            "animations/freezer.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_LEGGINGS =
            ITEMS.register("arcosian_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/arcosian_first_form_player.geo.json",
                            "textures/models/races/arcosian/first_form/arcosian_first_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_RACE_BOOTS =
            ITEMS.register("arcosian_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/arcosian_first_form_player.geo.json",
                            "textures/models/races/arcosian/first_form/arcosian_first_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    // Transformaciones arcosianas (second/third/final/golden/black). A diferencia de
    // namek_giant/namek_god/human_*/majin_* (que reusan a propósito el cuerpo de su raza, ver
    // body_items:{} en su JSON), CADA forma arcosiana tiene su propio modelo geo distinto —
    // no hay ninguna que reuse el cuerpo de arcosian_first_form_player. second/third/final ya
    // tienen geo y textura en geo/races + textures/models/races (capas con
    // tools/gen_arcosian_form_layers.py); golden/black/fifth siguen registradas sin
    // .geo.json/textura (rutas geo/forms), ver .claude/pendiente/arte-y-modelos.md — el sistema
    // de RaceSkinSlots.backedOrEmpty evita que crashee o se vea nada raro mientras tanto.
    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_SECOND_FORM_HELMET =
            ITEMS.register("arcosian_second_form_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/arcosian_second_form_player.geo.json",
                            "textures/models/races/arcosian/second_form/arcosian_second_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint()
                            .hideOnHelmet("casco", "cacho", "cacho2", "cacho3"));

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_SECOND_FORM_CHESTPLATE =
            ITEMS.register("arcosian_second_form_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/arcosian_second_form_player.geo.json",
                            "textures/models/races/arcosian/second_form/arcosian_second_form_player_layer_0.png",
                            "animations/freezer.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_SECOND_FORM_LEGGINGS =
            ITEMS.register("arcosian_second_form_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/arcosian_second_form_player.geo.json",
                            "textures/models/races/arcosian/second_form/arcosian_second_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_SECOND_FORM_BOOTS =
            ITEMS.register("arcosian_second_form_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/arcosian_second_form_player.geo.json",
                            "textures/models/races/arcosian/second_form/arcosian_second_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_THIRD_FORM_HELMET =
            ITEMS.register("arcosian_third_form_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/arcosian_third_form_player.geo.json",
                            "textures/models/races/arcosian/third_form/arcosian_third_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint()
                            .hideOnHelmet("casco", "cacho", "cacho2", "cacho3", "cacho4"));

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_THIRD_FORM_CHESTPLATE =
            ITEMS.register("arcosian_third_form_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/arcosian_third_form_player.geo.json",
                            "textures/models/races/arcosian/third_form/arcosian_third_form_player_layer_0.png",
                            "animations/freezer.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_THIRD_FORM_LEGGINGS =
            ITEMS.register("arcosian_third_form_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/arcosian_third_form_player.geo.json",
                            "textures/models/races/arcosian/third_form/arcosian_third_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_THIRD_FORM_BOOTS =
            ITEMS.register("arcosian_third_form_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/arcosian_third_form_player.geo.json",
                            "textures/models/races/arcosian/third_form/arcosian_third_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_FINAL_FORM_HELMET =
            ITEMS.register("arcosian_final_form_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/arcosian_final_form_player.geo.json",
                            "textures/models/races/arcosian/final_form/arcosian_final_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint()
                            .hideOnHelmet("casco"));

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_FINAL_FORM_CHESTPLATE =
            ITEMS.register("arcosian_final_form_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/arcosian_final_form_player.geo.json",
                            "textures/models/races/arcosian/final_form/arcosian_final_form_player_layer_0.png",
                            "animations/freezer.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_FINAL_FORM_LEGGINGS =
            ITEMS.register("arcosian_final_form_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/arcosian_final_form_player.geo.json",
                            "textures/models/races/arcosian/final_form/arcosian_final_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_FINAL_FORM_BOOTS =
            ITEMS.register("arcosian_final_form_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/arcosian_final_form_player.geo.json",
                            "textures/models/races/arcosian/final_form/arcosian_final_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_GOLDEN_FORM_HELMET =
            ITEMS.register("arcosian_golden_form_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/arcosian_golden_form_player.geo.json",
                            "textures/models/races/arcosian/golden_form/arcosian_golden_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint()
                            .hideOnHelmet("casco"));

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_GOLDEN_FORM_CHESTPLATE =
            ITEMS.register("arcosian_golden_form_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/arcosian_golden_form_player.geo.json",
                            "textures/models/races/arcosian/golden_form/arcosian_golden_form_player_layer_0.png",
                            "animations/freezer.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_GOLDEN_FORM_LEGGINGS =
            ITEMS.register("arcosian_golden_form_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/arcosian_golden_form_player.geo.json",
                            "textures/models/races/arcosian/golden_form/arcosian_golden_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_GOLDEN_FORM_BOOTS =
            ITEMS.register("arcosian_golden_form_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/arcosian_golden_form_player.geo.json",
                            "textures/models/races/arcosian/golden_form/arcosian_golden_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_BLACK_FORM_HELMET =
            ITEMS.register("arcosian_black_form_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/arcosian_black_form_player.geo.json",
                            "textures/models/races/arcosian/black_form/arcosian_black_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint()
                            .hideOnHelmet("casco"));

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_BLACK_FORM_CHESTPLATE =
            ITEMS.register("arcosian_black_form_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/arcosian_black_form_player.geo.json",
                            "textures/models/races/arcosian/black_form/arcosian_black_form_player_layer_0.png",
                            "animations/freezer.animation.json"
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_BLACK_FORM_LEGGINGS =
            ITEMS.register("arcosian_black_form_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/arcosian_black_form_player.geo.json",
                            "textures/models/races/arcosian/black_form/arcosian_black_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_BLACK_FORM_BOOTS =
            ITEMS.register("arcosian_black_form_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/arcosian_black_form_player.geo.json",
                            "textures/models/races/arcosian/black_form/arcosian_black_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    // Oozaru / Super Oozaru: cuerpo de mono propio (tools/gen_oozaru.py), el mismo geo para las
    // dos formas y solo cambia la textura (pelaje marrón / dorado). Sin channel/faceOverlays/
    // bodyTint: el color del pelaje es fijo y la cara va horneada en el modelo (por eso
    // FaceOverlayGeoLayer, TailResolver y HairResolver se apagan en estas dos formas). La
    // animación (respiración, cola, mandíbula, orejas) va en HEAD y CHEST: cada ranura solo
    // anima los huesos que pinta. FEET no pinta nada (sus huesos armor*Boot están vacíos), pero
    // tiene que existir: sin él, FormSkinResolver dejaría los pies de la raza x3 dentro de los
    // del mono.
    public static final Supplier<GeoLayerArmorItem> OOZARU_HELMET =
            ITEMS.register("oozaru_helmet", () -> oozaruBody(ArmorItem.Type.HELMET, "oozaru", true));
    public static final Supplier<GeoLayerArmorItem> OOZARU_CHESTPLATE =
            ITEMS.register("oozaru_chestplate", () -> oozaruBody(ArmorItem.Type.CHESTPLATE, "oozaru", true));
    public static final Supplier<GeoLayerArmorItem> OOZARU_LEGGINGS =
            ITEMS.register("oozaru_leggings", () -> oozaruBody(ArmorItem.Type.LEGGINGS, "oozaru", false));
    public static final Supplier<GeoLayerArmorItem> OOZARU_BOOTS =
            ITEMS.register("oozaru_boots", () -> oozaruBody(ArmorItem.Type.BOOTS, "oozaru", false));
    public static final Supplier<GeoLayerArmorItem> SUPER_OOZARU_HELMET =
            ITEMS.register("super_oozaru_helmet", () -> oozaruBody(ArmorItem.Type.HELMET, "super_oozaru", true));
    public static final Supplier<GeoLayerArmorItem> SUPER_OOZARU_CHESTPLATE =
            ITEMS.register("super_oozaru_chestplate", () -> oozaruBody(ArmorItem.Type.CHESTPLATE, "super_oozaru", true));
    public static final Supplier<GeoLayerArmorItem> SUPER_OOZARU_LEGGINGS =
            ITEMS.register("super_oozaru_leggings", () -> oozaruBody(ArmorItem.Type.LEGGINGS, "super_oozaru", false));
    public static final Supplier<GeoLayerArmorItem> SUPER_OOZARU_BOOTS =
            ITEMS.register("super_oozaru_boots", () -> oozaruBody(ArmorItem.Type.BOOTS, "super_oozaru", false));

    private static GeoLayerArmorItem oozaruBody(ArmorItem.Type type, String texture, boolean animated) {
        return new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, type, new Item.Properties(),
                "geo/races/oozaru_player.geo.json",
                "textures/models/races/saiyan/oozaru/" + texture + "_player.png",
                animated ? "animations/oozaru.animation.json" : "");
    }

    // Fifth Form Path (0.3.0, .claude/docs/jugador-razas-formas.md): dos
    // formas nuevas tras black_form — golden_fifth_form (extensión mortal) y black_fifth_form
    // (primera forma divina del Arcosiano). Mismo patrón exacto que las 5 formas Arcosian ya
    // existentes: sin .geo.json/textura todavía, cae limpio al cuerpo de black_form vía el
    // mismo fallback (RaceSkinSlots.backedOrEmpty) — no crashea, ver
    // .claude/pendiente/arte-y-modelos.md.
    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_GOLDEN_FIFTH_FORM_HELMET =
            ITEMS.register("arcosian_golden_fifth_form_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/forms/arcosian_golden_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/golden_fifth_form/arcosian_golden_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_GOLDEN_FIFTH_FORM_CHESTPLATE =
            ITEMS.register("arcosian_golden_fifth_form_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/forms/arcosian_golden_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/golden_fifth_form/arcosian_golden_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_GOLDEN_FIFTH_FORM_LEGGINGS =
            ITEMS.register("arcosian_golden_fifth_form_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/forms/arcosian_golden_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/golden_fifth_form/arcosian_golden_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_GOLDEN_FIFTH_FORM_BOOTS =
            ITEMS.register("arcosian_golden_fifth_form_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/forms/arcosian_golden_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/golden_fifth_form/arcosian_golden_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_BLACK_FIFTH_FORM_HELMET =
            ITEMS.register("arcosian_black_fifth_form_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/forms/arcosian_black_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/black_fifth_form/arcosian_black_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_BLACK_FIFTH_FORM_CHESTPLATE =
            ITEMS.register("arcosian_black_fifth_form_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/forms/arcosian_black_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/black_fifth_form/arcosian_black_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_BLACK_FIFTH_FORM_LEGGINGS =
            ITEMS.register("arcosian_black_fifth_form_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/forms/arcosian_black_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/black_fifth_form/arcosian_black_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_BLACK_FIFTH_FORM_BOOTS =
            ITEMS.register("arcosian_black_fifth_form_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/forms/arcosian_black_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/black_fifth_form/arcosian_black_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    // fifth_form (0.3.0): nodo central del que cuelgan golden_fifth_form y black_fifth_form.
    // Mismo patrón y mismo fallback limpio que las demás formas Arcosian (sin .geo.json/textura
    // todavía cae al cuerpo de la forma anterior, RaceSkinSlots.backedOrEmpty).
    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_FIFTH_FORM_HELMET =
            ITEMS.register("arcosian_fifth_form_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/forms/arcosian_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/fifth_form/arcosian_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_FIFTH_FORM_CHESTPLATE =
            ITEMS.register("arcosian_fifth_form_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/forms/arcosian_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/fifth_form/arcosian_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_FIFTH_FORM_LEGGINGS =
            ITEMS.register("arcosian_fifth_form_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/forms/arcosian_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/fifth_form/arcosian_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> ARCOSIAN_FIFTH_FORM_BOOTS =
            ITEMS.register("arcosian_fifth_form_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/forms/arcosian_fifth_form_player.geo.json",
                            "textures/models/races/arcosian/fifth_form/arcosian_fifth_form_player_layer_0.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    // Majin. Cuatro variantes como Human/Saiyan (género x color) porque comparte con ellos
    // la silueta humanoide y el selector de género de AppearanceScreen, que ya lo incluye en
    // isTintRace. A diferencia de ellos NO tiene pelo: HairResolver filtra por raza y el
    // majin no entra, que es lo correcto — su "pelo" será una capa propia, no un peinado.
    public static final Supplier<GeoLayerArmorItem> MAJIN_RACE_HELMET =
            ITEMS.register("majin_race_helmet", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/majin_player.geo.json",
                            "textures/models/races/majin/majin_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> MAJIN_RACE_CHESTPLATE =
            ITEMS.register("majin_race_chestplate", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/majin_player.geo.json",
                            "textures/models/races/majin/majin_player_colorable.png",
                            // "" hasta que exista animations/majin.animation.json: con una ruta
                            // no vacía apuntando a un archivo ausente, GeckoLib lanza al
                            // intentar cargar el controlador de animación — el mismo tipo de
                            // crash que el modelo pendiente (ver RaceSkinSlots.backedOrEmpty),
                            // pero ESTE no lo cubre ese filtro porque no depende del .geo.json.
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> MAJIN_RACE_LEGGINGS =
            ITEMS.register("majin_race_leggings", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/majin_player.geo.json",
                            "textures/models/races/majin/majin_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> MAJIN_RACE_BOOTS =
            ITEMS.register("majin_race_boots", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/majin_player.geo.json",
                            "textures/models/races/majin/majin_player_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> MAJIN_RACE_HELMET_FEMALE =
            ITEMS.register("majin_race_helmet_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/races/majin_player_female.geo.json",
                            "textures/models/races/majin/majin_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> MAJIN_RACE_CHESTPLATE_FEMALE =
            ITEMS.register("majin_race_chestplate_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/races/majin_player_female.geo.json",
                            "textures/models/races/majin/majin_player_female_colorable.png",
                            "" // ver el comentario en MAJIN_RACE_CHESTPLATE
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> MAJIN_RACE_LEGGINGS_FEMALE =
            ITEMS.register("majin_race_leggings_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                            new Item.Properties(),
                            "geo/races/majin_player_female.geo.json",
                            "textures/models/races/majin/majin_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    public static final Supplier<GeoLayerArmorItem> MAJIN_RACE_BOOTS_FEMALE =
            ITEMS.register("majin_race_boots_female", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                            new Item.Properties(),
                            "geo/races/majin_player_female.geo.json",
                            "textures/models/races/majin/majin_player_female_colorable.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.SKIN).faceOverlays().bodyTint());

    //Halo
    public static final DeferredItem<GeoLayerArmorItem> HALO =
            ITEMS.register("halo", () -> new GeoLayerArmorItem(
                    ArmorMaterials.LEATHER, ArmorItem.Type.HELMET,
                    new Item.Properties(),
                    "geo/halo.geo.json",
                    "textures/models/races/common/halo.png",
                    ""));

    public static final DeferredItem<ScouterItem> SCOUTER =
            ITEMS.register("scouter", () -> new ScouterItem(
                    ArmorMaterials.IRON,
                    new Item.Properties(),
                    "geo/scouter.geo.json",
                    "textures/models/armor/scouter.png"));

    // Pelo GENÉRICO (S5, 2026-10-01): UN item para cualquier peinado del catálogo de datapack
    // (HairCatalog). El peinado viaja en el componente HAIR_STYLE del stack virtual que construye
    // HairResolver, y de ahí salen modelo y textura en cada render. Un peinado nuevo = geo + PNG +
    // JSON en zenkai_hair_styles, sin registrar nada aquí. La geo/textura de abajo son solo el
    // respaldo si el componente falta o apunta a un peinado que no existe.
    public static final Supplier<GeoLayerArmorItem> HAIR =
            ITEMS.register("hair", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                            new Item.Properties(),
                            "geo/hair/classic.geo.json",
                            "textures/customization/hair/classic.png",
                            ""
                    ).channel(GeoLayerArmorItem.ColorChannel.HAIR)
                     .stackModel(com.hmc.zenkai.feature.race.hair.HairStacks::model)
                     .stackTexture(com.hmc.zenkai.feature.race.hair.HairStacks::texture));

    // Cola de Saiyan (dos variantes cosméticas, ver TailResolver/TailGeoLayer).
    // CHESTPLATE y NO LEGGINGS/HELMET — no es un capricho, es un requisito real de GeckoLib:
    // GeoArmorRenderer.applyBoneVisibilityBySlot() solo muestra los huesos "armorRightLeg"/
    // "armorLeftLeg" durante el pase de LEGGINGS (comprobado decompilando geckolib-neoforge-
    // 4.8.4.jar), NUNCA "armorBody". Como el hueso raíz de la cola ("cola"/"colacintura") cuelga
    // de "torso" → "armorBody" (mismo patrón que el propio arcosiano, que solo se ve completo
    // porque viste las 4 ranuras a la vez), con LEGGINGS la cola cargaba bien pero quedaba oculta
    // en TODOS los casos sin ningún error en el log — el pase de CHESTPLATE sí muestra armorBody
    // (+brazos). HEAD queda descartado aparte porque esa ranura ya la comparten pelo+halo+scouter
    // en secuencia. La ranura vanilla sigue siendo solo el "vehículo" que usa HumanoidArmorLayer
    // para invocar al renderer de GeckoLib — la geometría real la pone el .geo.json propio (Halo
    // ya lo demuestra: HELMET pero flota sobre la cabeza, no "es" un casco).
    // Sin canal de tinte propio (NONE, el default): el color de la cola no depende de la piel
    // ni del pelo elegidos por el jugador, siempre lo pone TailResolver vía DYED_COLOR según la
    // forma activa (ver GeoLayerArmorRenderer.getRenderColor y FormDef.tailRgb).
    public static final Supplier<GeoLayerArmorItem> TAIL_LOOSE =
            ITEMS.register("tail_loose", () ->
                    new GeoLayerArmorItem(ModArmorMaterials.RACE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                            new Item.Properties(),
                            "geo/tail/tail_loose.geo.json",
                            "textures/customization/tail/tail_loose.png",
                            "animations/tail_loose.animation.json"
                    ));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}