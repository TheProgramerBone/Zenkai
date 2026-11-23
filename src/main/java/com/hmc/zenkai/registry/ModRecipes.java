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

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }
}
