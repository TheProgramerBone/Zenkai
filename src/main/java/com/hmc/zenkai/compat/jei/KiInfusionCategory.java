package com.hmc.zenkai.compat.jei;

import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * JEI: infusora de Ki (Fase 5c). Una entrada por tipo de Ki: lo infundible + {@link KiInfusion#MB_PER_ITEM} mB del
 * tipo → lo mismo infundido; a la derecha, la FE y lo que da la infusión (durabilidad y coste de imbuir).
 */
public class KiInfusionCategory extends AbstractRecipeCategory<KiInfusionCategory.Display> {

    public static final RecipeType<Display> TYPE = RecipeType.create(com.hmc.zenkai.Zenkai.MOD_ID, "ki_infusion", Display.class);

    public record Display(List<ItemStack> inputs, KiEnergyType ki) {}

    private static final int W = 170, H = 40, TEXT_X = 92;
    private final IDrawableStatic arrow;

    public KiInfusionCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("jei.zenkai.ki_infusion"), gui.createDrawableItemLike(ModBlocks.KI_INFUSER.get()), W, H);
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder b, @NotNull Display d, @NotNull IFocusGroup focuses) {
        var in = b.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStacks(d.inputs());
        b.addSlot(RecipeIngredientRole.INPUT, 1, 21).setStandardSlotBackground()
                .setFluidRenderer(KiInfusion.MB_PER_ITEM, false, 16, 16)
                .addFluidStack(d.ki().fluid().source().get(), KiInfusion.MB_PER_ITEM);
        var out = b.addSlot(RecipeIngredientRole.OUTPUT, 64, 11).setOutputSlotBackground()
                .addItemStacks(d.inputs().stream().map(s -> KiInfusion.infused(s, d.ki())).toList());
        b.createFocusLink(in, out);
    }

    @Override
    public void draw(@NotNull Display d, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics g, double mouseX, double mouseY) {
        arrow.draw(g, 32, 11);
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable(d.ki().langKey()), TEXT_X, 2, d.ki().argb(), true);
        g.drawString(font, Component.literal(ZenkaiNumbers.screen((long) KiInfusion.FE_PER_ITEM) + " FE"), TEXT_X, 14, 0xFF404040, false);
        g.drawString(font, Component.translatable("jei.zenkai.ki_infusion.gives",
                ZenkaiNumbers.fmt2(KiInfusion.durabilityMult(d.ki())), ZenkaiNumbers.fmt2(KiInfusion.imbueCostMult(d.ki()))),
                TEXT_X, 26, 0xFF404040, false);
    }
}
