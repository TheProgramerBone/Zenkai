package com.hmc.zenkai.compat.jei;

import com.hmc.zenkai.feature.armor.TemplateDuplicationRecipe;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.util.ZenkaiNumbers;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
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
 * JEI: duplicar una plantilla de atributos en la máquina de fabricación (Fase 5c). La plantilla (se gasta) + N de
 * material → las plantillas de salida; FE y tiempo a la derecha, como la categoría de fabricación.
 */
public class TemplateDuplicationCategory extends AbstractRecipeCategory<RecipeHolder<TemplateDuplicationRecipe>> {

    public static final RecipeType<RecipeHolder<TemplateDuplicationRecipe>> TYPE =
            RecipeType.createRecipeHolderType(JeiIds.id("template_duplication"));

    private static final int W = 150, H = 40, TEXT_X = 86;
    private final IDrawableAnimated arrow;

    public TemplateDuplicationCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("jei.zenkai.template_duplication"),
                gui.createDrawableItemLike(ModBlocks.ARMOR_FABRICATOR.get()), W, H);
        this.arrow = gui.createAnimatedRecipeArrow(100);
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder b, @NotNull RecipeHolder<TemplateDuplicationRecipe> holder,
                          @NotNull IFocusGroup focuses) {
        TemplateDuplicationRecipe r = holder.value();
        b.addSlot(RecipeIngredientRole.INPUT, 1, 2).setStandardSlotBackground().addIngredients(r.template());
        List<ItemStack> material = Arrays.stream(r.material().getItems()).map(s -> s.copyWithCount(r.count())).toList();
        b.addSlot(RecipeIngredientRole.INPUT, 1, 22).setStandardSlotBackground().addItemStacks(material);
        b.addSlot(RecipeIngredientRole.OUTPUT, 58, 12).setOutputSlotBackground().addItemStack(r.result());
    }

    @Override
    public void draw(@NotNull RecipeHolder<TemplateDuplicationRecipe> holder, @NotNull IRecipeSlotsView view,
                     @NotNull GuiGraphics g, double mouseX, double mouseY) {
        TemplateDuplicationRecipe r = holder.value();
        arrow.draw(g, 26, 12);
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable("jei.zenkai.armor_fabrication.energy", ZenkaiNumbers.screen((long) r.energy())),
                TEXT_X, 8, 0xFF404040, false);
        g.drawString(font, Component.translatable("jei.zenkai.armor_fabrication.time", ZenkaiNumbers.screen(r.time() / 20.0)),
                TEXT_X, 22, 0xFF404040, false);
    }
}
