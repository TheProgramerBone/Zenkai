package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

public class ModArmorMaterials {

    /** DeferredRegister y no Registry.registerForHolder en un static: ModItems lee estos holders al cargarse (durante
     *  la construcción del mod), y para entonces el registro vanilla ya está congelado. */
    public static final net.neoforged.neoforge.registries.DeferredRegister<ArmorMaterial> MATERIALS =
            net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.ARMOR_MATERIAL, Zenkai.MOD_ID);

    public static void register(net.neoforged.bus.api.IEventBus bus) { MATERIALS.register(bus); }

    public static final Holder<ArmorMaterial> RACE_ARMOR_MATERIAL = register("race",
            Util.make(new EnumMap<>(ArmorItem.Type.class), attribute -> {
                attribute.put(ArmorItem.Type.BOOTS, 0);
                attribute.put(ArmorItem.Type.LEGGINGS, 0);
                attribute.put(ArmorItem.Type.CHESTPLATE, 0);
                attribute.put(ArmorItem.Type.HELMET, 0);
                attribute.put(ArmorItem.Type.BODY, 0);
            }), 0, 0, 0, ModItems.SENZU_BEAN);

    // ── Armaduras de Dragon Ball (Fase 5b de la 0.7.0) ───────────────────────
    // Solo salen de la máquina de fabricación con su patrón. Primera tanda (P14): armadura Saiyan (metálica,
    // de lingote, a la altura del diamante), ropa Namek y gi de entrenamiento (de tela, entre el hierro y el
    // cuero). Cifras de partida; la infusión de la 5c sube la durabilidad sin cambiar el aspecto.
    // Textura del modelo: textures/models/armor/<nombre>_layer_1/_layer_2.png (tools/gen_dragonball_armor.py).
    public static final Holder<ArmorMaterial> SAIYAN_ARMOR = register("saiyan_armor",
            protection(3, 8, 6, 3), 15, 2.0f, 0.0f, ModItems.KATCHIN_INGOT, SoundEvents.ARMOR_EQUIP_NETHERITE);
    public static final Holder<ArmorMaterial> NAMEK_CLOTHES = register("namek_clothes",
            protection(2, 6, 5, 2), 18, 1.0f, 0.0f, ModItems.KATCHIN_CLOTH, SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final Holder<ArmorMaterial> TRAINING_GI = register("training_gi",
            protection(2, 5, 4, 1), 20, 0.5f, 0.0f, ModItems.KATCHIN_CLOTH, SoundEvents.ARMOR_EQUIP_LEATHER);

    /** Durabilidad (multiplicador de ArmorItem.Type.getDurability) de cada set: diamante 33, hierro 15. */
    public static final int SAIYAN_ARMOR_DURABILITY = 33, NAMEK_CLOTHES_DURABILITY = 20, TRAINING_GI_DURABILITY = 15;

    private static EnumMap<ArmorItem.Type, Integer> protection(int helmet, int chest, int legs, int boots) {
        EnumMap<ArmorItem.Type, Integer> m = new EnumMap<>(ArmorItem.Type.class);
        m.put(ArmorItem.Type.HELMET, helmet);
        m.put(ArmorItem.Type.CHESTPLATE, chest);
        m.put(ArmorItem.Type.LEGGINGS, legs);
        m.put(ArmorItem.Type.BOOTS, boots);
        m.put(ArmorItem.Type.BODY, chest);
        return m;
    }

    private static Holder<ArmorMaterial> register(String name, EnumMap<ArmorItem.Type, Integer> typeProtection,
                                                  int enchantability, float toughness, float knockbackResistance,
                                                  Supplier<Item> ingredientItem, Holder<SoundEvent> equipSound) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name);
        Supplier<Ingredient> ingredient = () -> Ingredient.of(ingredientItem.get());
        List<ArmorMaterial.Layer> layers = List.of(new ArmorMaterial.Layer(location));
        return MATERIALS.register(name, () ->
                new ArmorMaterial(typeProtection, enchantability, equipSound, ingredient, layers, toughness, knockbackResistance));
    }




    private static Holder<ArmorMaterial> register(String name, EnumMap<ArmorItem.Type, Integer> typeProtection,
                                                  int enchantability, float toughness, float knockbackResistance,
                                                  Supplier<Item> ingredientItem) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name);
        Holder<SoundEvent> equipSound = SoundEvents.ARMOR_EQUIP_DIAMOND;
        Supplier<Ingredient> ingredient = () -> Ingredient.of(ingredientItem.get());
        List<ArmorMaterial.Layer> layers = List.of(new ArmorMaterial.Layer(location));

        EnumMap<ArmorItem.Type, Integer> typeMap = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            typeMap.put(type, typeProtection.get(type));
        }

        return MATERIALS.register(name, () ->
                new ArmorMaterial(typeProtection, enchantability, equipSound, ingredient, layers, toughness, knockbackResistance));
    }
}