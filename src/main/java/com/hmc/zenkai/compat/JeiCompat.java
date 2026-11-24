package com.hmc.zenkai.compat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.ArmorFabricatorScreen;
import com.hmc.zenkai.compat.jei.ArmorFabricationCategory;
import com.hmc.zenkai.compat.jei.ArmorImbuingCategory;
import com.hmc.zenkai.compat.jei.BloodDropsCategory;
import com.hmc.zenkai.content.item.BloodItem;
import com.hmc.zenkai.feature.armor.ArmorImbuingRecipe;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModDataMaps;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Compat OPCIONAL con JEI (solo la API en compileOnly; sin JEI esta clase nunca se carga).
 *
 * Subtipos: la gelatina del Janemba se tiñe con el componente BLOCK_STATE (ver JanembaBubbleBlock) y JEI, sin intérprete
 * de subtipos, trataba los 16 colores como el mismo ítem: avisaba de "15 duplicate items" y ocultaba los teñidos. Lo
 * mismo con el patrón de armadura (un ítem, el set en {@code zenkai:armor_pattern}) y las sangres (la calidad en
 * {@code zenkai:blood_quality}).
 *
 * Recetas (0.7.0, 2026-11-22, pedido del usuario): fabricación de armaduras, imbuir (tipo de Ki por pieza) y qué mob
 * suelta qué sangre; Fase 5c: infusora de Ki y duplicado de plantillas. Catalizador de las dos primeras: la máquina de fabricación; zona clicable sobre la barra de
 * progreso, solo en la pestaña Fabricar (en Atributos no hay hueco libre que no sea un botón).
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "jei");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(@NotNull ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModBlocks.HFIL_JANEMBA_BUBBLE.get().asItem(),
                new ComponentSubtype<>(DataComponents.BLOCK_STATE, true));
        registration.registerSubtypeInterpreter(ModItems.ARMOR_PATTERN.get(),
                new ComponentSubtype<>(ModDataComponents.ARMOR_PATTERN.get(), false));
        for (var item : BuiltInRegistries.ITEM) {
            if (item instanceof BloodItem) {
                registration.registerSubtypeInterpreter(item, new ComponentSubtype<>(ModDataComponents.BLOOD_QUALITY.get(), false));
            }
        }
    }

    /**
     * Subtipo = valor de un componente; sin él es el subtipo base. {@code emptyIsBase}: además, un valor «vacío» (estado
     * de bloque sin propiedades) cuenta como base.
     */
    private record ComponentSubtype<V>(DataComponentType<V> type, boolean emptyIsBase) implements ISubtypeInterpreter<ItemStack> {
        @Nullable
        @Override
        public Object getSubtypeData(ItemStack stack, @NotNull UidContext context) {
            V v = stack.get(type);
            if (v == null) return null;
            if (emptyIsBase && v instanceof net.minecraft.world.item.component.BlockItemStateProperties p && p.isEmpty()) return null;
            return v;
        }

        /**
         * JEI aún obliga a implementarlo (deprecated desde 19.9.0), pero solo sirve para leer configs ANTIGUAS que
         * guardaban ids de subtipo como texto. Este plugin no tiene ninguna: la documentación de JEI dice que se
         * devuelve "" en ese caso. Se evita a propósito IIngredientSubtypeInterpreter.NONE, marcada "forRemoval".
         */
        @SuppressWarnings("deprecation")
        @Override
        public @NotNull String getLegacyStringSubtypeInfo(@NotNull ItemStack stack, @NotNull UidContext context) {
            return "";
        }
    }

    @Override
    public void registerCategories(@NotNull IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ArmorFabricationCategory(gui), new ArmorImbuingCategory(gui),
                new BloodDropsCategory(gui), new com.hmc.zenkai.compat.jei.KiInfusionCategory(gui),
                new com.hmc.zenkai.compat.jei.TemplateDuplicationCategory(gui),
                new com.hmc.zenkai.compat.jei.CrushingCategory(gui));
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        var recipes = level.getRecipeManager();
        registration.addRecipes(ArmorFabricationCategory.TYPE,
                new ArrayList<>(recipes.getAllRecipesFor(ModRecipes.ARMOR_FABRICATION.get())));

        // Imbuir: una entrada por receta (de más exigente a menos) y, para las piezas sin receta, una por tipo automático
        // (armadura + dureza, ArmorImbuingRecipe.autoTier), con cualquier armadura de cualquier mod.
        List<ArmorImbuingCategory.Display> imbue = new ArrayList<>();
        for (var h : recipes.getAllRecipesFor(ModRecipes.ARMOR_IMBUING.get())) {
            ArmorImbuingRecipe r = h.value();
            imbue.add(new ArmorImbuingCategory.Display(Arrays.asList(r.piece().getItems()), r.ki(), false));
        }
        imbue.sort((a, b) -> b.ki().ordinal() - a.ki().ordinal());
        java.util.Map<KiEnergyType, List<ItemStack>> auto = new java.util.EnumMap<>(KiEnergyType.class);
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack s = new ItemStack(item);
            if (!com.hmc.zenkai.content.blockentity.ImbuingStationBlockEntity.isArmorPiece(s) || ArmorImbuingRecipe.explicitFor(level, s) != null) continue;
            auto.computeIfAbsent(ArmorImbuingRecipe.autoTier(s), k -> new ArrayList<>()).add(s);
        }
        for (KiEnergyType t : KiEnergyType.values()) {
            List<ItemStack> list = auto.get(t);
            if (list != null) imbue.add(new ArmorImbuingCategory.Display(list, t, true));
        }
        registration.addRecipes(ArmorImbuingCategory.TYPE, imbue);

        List<BloodDropsCategory.Drop> drops = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.getDataMap(ModDataMaps.BLOOD_SOURCES).forEach((key, list) ->
                BuiltInRegistries.ENTITY_TYPE.getOptional(key).ifPresent(t -> drops.add(new BloodDropsCategory.Drop(t, list))));
        // Primero los de más calidad; dentro, por nombre del mob.
        drops.sort((a, b) -> b.maxQuality() != a.maxQuality() ? b.maxQuality() - a.maxQuality()
                : BuiltInRegistries.ENTITY_TYPE.getKey(a.entity()).compareTo(BuiltInRegistries.ENTITY_TYPE.getKey(b.entity())));
        registration.addRecipes(BloodDropsCategory.TYPE, drops);

        // Infusora (Fase 5c): recetas zenkai:ki_infusion por datapack desde el 2026-11-23.
        registration.addRecipes(com.hmc.zenkai.compat.jei.KiInfusionCategory.TYPE,
                new ArrayList<>(recipes.getAllRecipesFor(ModRecipes.KI_INFUSION.get())));
        registration.addRecipes(com.hmc.zenkai.compat.jei.TemplateDuplicationCategory.TYPE,
                new ArrayList<>(recipes.getAllRecipesFor(ModRecipes.TEMPLATE_DUPLICATION.get())));
        // Triturador (Fase 5d).
        registration.addRecipes(com.hmc.zenkai.compat.jei.CrushingCategory.TYPE,
                new ArrayList<>(recipes.getAllRecipesFor(ModRecipes.CRUSHING.get())));
    }

    @Override
    public void onRuntimeAvailable(@NotNull mezz.jei.api.runtime.IJeiRuntime runtime) {
        com.hmc.zenkai.compat.jei.JeiDev.runtime = runtime;
    }

    @Override
    public void onRuntimeUnavailable() {
        com.hmc.zenkai.compat.jei.JeiDev.runtime = null;
    }

    @Override
    public void registerRecipeCatalysts(@NotNull IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.ARMOR_FABRICATOR.get(), ArmorFabricationCategory.TYPE,
                com.hmc.zenkai.compat.jei.TemplateDuplicationCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.IMBUING_STATION.get(), ArmorImbuingCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.KI_INFUSER.get(), com.hmc.zenkai.compat.jei.KiInfusionCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.CRUSHER.get(), com.hmc.zenkai.compat.jei.CrushingCategory.TYPE);
    }

    /** Zona clicable sobre una barra de progreso (coordenadas de la pantalla): abre las recetas de su categoría. */
    private static <S extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>> void progressArea(
            IGuiHandlerRegistration registration, Class<S> screen, int x, int y, int w, int h,
            mezz.jei.api.recipe.RecipeType<?> type, java.util.function.Predicate<S> active) {
        registration.addGuiContainerHandler(screen, new IGuiContainerHandler<S>() {
            @Override
            public @NotNull Collection<IGuiClickableArea> getGuiClickableAreas(@NotNull S s, double mouseX, double mouseY) {
                return active.test(s) ? List.of(IGuiClickableArea.createBasic(x, y - 3, w, h + 6, type)) : List.of();
            }
        });
    }

    @Override
    public void registerGuiHandlers(@NotNull IGuiHandlerRegistration registration) {
        // Triturador, Infusora y Estación de imbuido (barra de carga, solo en la pestaña Atributos): como la Fabricadora.
        progressArea(registration, com.hmc.zenkai.client.gui.screens.CrusherScreen.class,
                com.hmc.zenkai.client.gui.screens.CrusherScreen.PROG_X, 57, 36, 6,
                com.hmc.zenkai.compat.jei.CrushingCategory.TYPE, s -> true);
        progressArea(registration, com.hmc.zenkai.client.gui.screens.KiInfuserScreen.class,
                com.hmc.zenkai.client.gui.screens.KiInfuserScreen.PROG_X, 57, 32, 6,
                com.hmc.zenkai.compat.jei.KiInfusionCategory.TYPE, s -> true);
        progressArea(registration, com.hmc.zenkai.client.gui.screens.ImbuingStationScreen.class,
                com.hmc.zenkai.client.gui.screens.ImbuingStationScreen.CHARGE_X, 56, 16, 4,
                ArmorImbuingCategory.TYPE,
                s -> s.getMenu().tab() == com.hmc.zenkai.client.gui.menu.ImbuingStationMenu.TAB_ATTR);
        registration.addGuiContainerHandler(ArmorFabricatorScreen.class, new IGuiContainerHandler<>() {
            @Override
            public @NotNull Collection<IGuiClickableArea> getGuiClickableAreas(@NotNull ArmorFabricatorScreen screen,
                                                                               double mouseX, double mouseY) {
                return List.of(IGuiClickableArea.createBasic(ArmorFabricatorScreen.PROG_X, ArmorFabricatorScreen.PROG_Y - 3,
                        ArmorFabricatorScreen.PROG_W, ArmorFabricatorScreen.PROG_H + 6, ArmorFabricationCategory.TYPE));
            }
        });
    }
}
