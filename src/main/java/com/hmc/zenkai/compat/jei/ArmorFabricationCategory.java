package com.hmc.zenkai.compat.jei;

import com.hmc.zenkai.feature.armor.ArmorFabricationRecipe;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.util.ZenkaiNumbers;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * JEI: receta {@code zenkai:armor_fabrication}. Patrón (no se gasta) + N de material → la pieza; FE y tiempo a la derecha.
 * El material se enseña con la cantidad TOTAL de la receta en la pila (puede pasar de 64: la máquina lo reparte en 9
 * ranuras). Cifras por ZenkaiNumbers.
 */
public class ArmorFabricationCategory extends AbstractRecipeCategory<RecipeHolder<ArmorFabricationRecipe>> {

    public static final RecipeType<RecipeHolder<ArmorFabricationRecipe>> TYPE =
            RecipeType.createRecipeHolderType(JeiIds.id("armor_fabrication"));

    private static final int W = 150, H = 40, TEXT_X = 86;
    private final IDrawableAnimated arrow;

    public ArmorFabricationCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("jei.zenkai.armor_fabrication"),
                gui.createDrawableItemLike(ModBlocks.ARMOR_FABRICATOR.get()), W, H);
        this.arrow = gui.createAnimatedRecipeArrow(100);
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder b, @NotNull RecipeHolder<ArmorFabricationRecipe> holder,
                          @NotNull IFocusGroup focuses) {
        ArmorFabricationRecipe r = holder.value();
        b.addSlot(RecipeIngredientRole.CATALYST, 1, 2)
                .setStandardSlotBackground()
                .addItemStack(ArmorSets.pattern(r.pattern()))
                .addRichTooltipCallback((view, tip) -> tip.add(Component.translatable("jei.zenkai.armor_fabrication.pattern")));
        List<ItemStack> material = Arrays.stream(r.material().getItems()).map(s -> s.copyWithCount(r.count())).toList();
        b.addSlot(RecipeIngredientRole.INPUT, 1, 22).setStandardSlotBackground().addItemStacks(material);
        b.addSlot(RecipeIngredientRole.OUTPUT, 58, 12).setOutputSlotBackground().addItemStack(r.result());
    }

    @Override
    public void draw(@NotNull RecipeHolder<ArmorFabricationRecipe> holder, @NotNull IRecipeSlotsView view,
                     @NotNull GuiGraphics g, double mouseX, double mouseY) {
        ArmorFabricationRecipe r = holder.value();
        arrow.draw(g, 26, 12);
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable("jei.zenkai.armor_fabrication.energy", ZenkaiNumbers.screen((long) r.energy())),
                TEXT_X, 8, 0xFF404040, false);
        g.drawString(font, Component.translatable("jei.zenkai.armor_fabrication.time", ZenkaiNumbers.screen(r.time() / 20.0)),
                TEXT_X, 22, 0xFF404040, false);
    }

    @Override
    public void getTooltip(@NotNull ITooltipBuilder tip, @NotNull RecipeHolder<ArmorFabricationRecipe> holder,
                           @NotNull IRecipeSlotsView view, double mouseX, double mouseY) {
        // Exacto en el tooltip si la cifra salió compactada (convención de ZenkaiNumbers).
        long fe = holder.value().energy();
        if (ZenkaiNumbers.isCompacted(fe) && mouseX >= TEXT_X && mouseY >= 6 && mouseY < 17) {
            tip.add(Component.literal(ZenkaiNumbers.exact(fe) + " FE"));
        }
    }
}
