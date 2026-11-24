package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.armor.ArmorFabricationRecipe;
import com.hmc.zenkai.feature.armor.ArmorImbuingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Tipos y serializadores de receta propios. Fabricación de armaduras e imbuido por tipo de Ki (Fase 5b de la 0.7.0). */
public final class ModRecipes {
    private ModRecipes() {}

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Zenkai.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Zenkai.MOD_ID);

    public static final Supplier<RecipeType<ArmorFabricationRecipe>> ARMOR_FABRICATION =
            TYPES.register("armor_fabrication", () -> RecipeType.simple(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "armor_fabrication")));
    public static final Supplier<RecipeSerializer<ArmorFabricationRecipe>> ARMOR_FABRICATION_SERIALIZER =
            SERIALIZERS.register("armor_fabrication", ArmorFabricationRecipe.Serializer::new);

    /** Tipo de Ki que pide imbuir cada pieza (sin receta = Base). */
    public static final Supplier<RecipeType<ArmorImbuingRecipe>> ARMOR_IMBUING =
            TYPES.register("armor_imbuing", () -> RecipeType.simple(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "armor_imbuing")));
    public static final Supplier<RecipeSerializer<ArmorImbuingRecipe>> ARMOR_IMBUING_SERIALIZER =
            SERIALIZERS.register("armor_imbuing", ArmorImbuingRecipe.Serializer::new);

    // zenkai:infused_crafting (Fase 5c) se quitó el 2026-11-23: el katchin infundido son ítems propios y sus recetas son
    // de mesa normales.

    /** Duplicado de plantillas de atributos en la máquina de fabricación (Fase 5c). */
    public static final Supplier<RecipeType<com.hmc.zenkai.feature.armor.TemplateDuplicationRecipe>> TEMPLATE_DUPLICATION =
            TYPES.register("template_duplication", () -> RecipeType.simple(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "template_duplication")));
    public static final Supplier<RecipeSerializer<com.hmc.zenkai.feature.armor.TemplateDuplicationRecipe>> TEMPLATE_DUPLICATION_SERIALIZER =
            SERIALIZERS.register("template_duplication", com.hmc.zenkai.feature.armor.TemplateDuplicationRecipe.Serializer::new);

    /** Triturador (Fase 5d de la 0.7.0): recetas con probabilidad al estilo de Create. */
    public static final Supplier<RecipeType<com.hmc.zenkai.feature.crusher.CrushingRecipe>> CRUSHING =
            TYPES.register("crushing", () -> RecipeType.simple(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "crushing")));
    public static final Supplier<RecipeSerializer<com.hmc.zenkai.feature.crusher.CrushingRecipe>> CRUSHING_SERIALIZER =
            SERIALIZERS.register("crushing", com.hmc.zenkai.feature.crusher.CrushingRecipe.Serializer::new);

    /** Infusora de Ki (Fase 5d de la 0.7.0): ítem + fluido de Ki → ítem, al estilo del metallurgic_infusing de Mekanism. */
    public static final Supplier<RecipeType<com.hmc.zenkai.feature.armor.KiInfusionRecipe>> KI_INFUSION =
            TYPES.register("ki_infusion", () -> RecipeType.simple(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_infusion")));
    public static final Supplier<RecipeSerializer<com.hmc.zenkai.feature.armor.KiInfusionRecipe>> KI_INFUSION_SERIALIZER =
            SERIALIZERS.register("ki_infusion", com.hmc.zenkai.feature.armor.KiInfusionRecipe.Serializer::new);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }
}
