package com.hmc.zenkai.compat.jei;

import com.hmc.zenkai.feature.crusher.CrushingRecipe;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.util.ZenkaiNumbers;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * JEI: Triturador (Fase 5d). Entrada (con su cantidad) → hasta cuatro salidas; las de probabilidad llevan el % en el
 * tooltip del hueco, como Create. Abajo, tiempo y FE.
 */
public class CrushingCategory extends AbstractRecipeCategory<RecipeHolder<CrushingRecipe>> {

    @SuppressWarnings("unchecked")
    public static final RecipeType<RecipeHolder<CrushingRecipe>> TYPE = RecipeType.create(com.hmc.zenkai.Zenkai.MOD_ID,
            "crushing", (Class<RecipeHolder<CrushingRecipe>>) (Class<?>) RecipeHolder.class);

    /**
     * Rejilla 2×2 de salidas con huecos ESTÁNDAR de 18 px. Antes usaban setOutputSlotBackground (26×26) con paso de 18:
     * se pisaban entre sí y el de abajo tapaba la línea de FE (captura del usuario 2026-11-23).
     */
    private static final int W = 170, H = 48, OUT_X = 58, OUT_Y = 0, TEXT_Y = 39;
    private final IDrawableStatic arrow;

    public CrushingCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("jei.zenkai.crushing"), gui.createDrawableItemLike(ModBlocks.CRUSHER.get()), W, H);
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder b, @NotNull RecipeHolder<CrushingRecipe> h, @NotNull IFocusGroup focuses) {
        CrushingRecipe r = h.value();
        b.addSlot(RecipeIngredientRole.INPUT, 1, 10).setStandardSlotBackground()
                .addItemStacks(Arrays.stream(r.ingredient().getItems()).map(s -> s.copyWithCount(r.count())).toList());
        for (int i = 0; i < r.results().size(); i++) {
            CrushingRecipe.Output o = r.results().get(i);
            var slot = b.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + 1 + (i % 2) * 18, OUT_Y + 1 + (i / 2) * 18)
                    .setStandardSlotBackground().addItemStack(o.stack());
            if (!o.guaranteed()) {
                slot.addRichTooltipCallback((view, tip) -> tip.add(Component.translatable("jei.zenkai.crushing.chance",
                        ZenkaiNumbers.screen(Math.round(o.chance() * 1000) / 10.0)).withStyle(ChatFormatting.GOLD)));
            }
        }
    }

    @Override
    public void draw(@NotNull RecipeHolder<CrushingRecipe> h, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics g,
                     double mouseX, double mouseY) {
        CrushingRecipe r = h.value();
        arrow.draw(g, 26, 10);
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.literal(ZenkaiNumbers.screen((long) r.energy()) + " FE · "
                + ZenkaiNumbers.screen(r.time() / 20.0) + " s"), 1, TEXT_Y, 0xFF404040, false);
        if (r.infusionAware()) {
            g.drawString(font, Component.translatable("jei.zenkai.crushing.infusion_aware"), OUT_X + 40, 14, 0xFF404040, false);
        }
    }
}
