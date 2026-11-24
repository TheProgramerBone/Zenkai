package com.hmc.zenkai.compat.jei;

import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.armor.KiInfusionRecipe;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * JEI: infusora de Ki (Fase 5c; recetas por datapack {@code zenkai:ki_infusion} desde el 2026-11-23). Ítem + fluido de Ki
 * de la receta → salida; a la derecha, el tipo, la FE y el tiempo y, si sale katchin infundido, lo que da a la armadura
 * (durabilidad y coste de imbuir).
 */
public class KiInfusionCategory extends AbstractRecipeCategory<RecipeHolder<KiInfusionRecipe>> {

    @SuppressWarnings("unchecked")
    public static final RecipeType<RecipeHolder<KiInfusionRecipe>> TYPE = RecipeType.create(com.hmc.zenkai.Zenkai.MOD_ID,
            "ki_infusion", (Class<RecipeHolder<KiInfusionRecipe>>) (Class<?>) RecipeHolder.class);

    private static final int W = 170, H = 40, TEXT_X = 92;
    private final IDrawableStatic arrow;

    public KiInfusionCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("jei.zenkai.ki_infusion"), gui.createDrawableItemLike(ModBlocks.KI_INFUSER.get()), W, H);
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder b, @NotNull RecipeHolder<KiInfusionRecipe> h, @NotNull IFocusGroup focuses) {
        KiInfusionRecipe r = h.value();
        b.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground()
                .addItemStacks(Arrays.asList(r.input().getItems()));
        b.addSlot(RecipeIngredientRole.INPUT, 1, 21).setStandardSlotBackground()
                .setFluidRenderer(r.fluidAmount(), false, 16, 16)
                .addFluidStack(r.ki().fluid().source().get(), r.fluidAmount());
        b.addSlot(RecipeIngredientRole.OUTPUT, 64, 11).setOutputSlotBackground().addItemStack(r.output());
    }

    @Override
    public void draw(@NotNull RecipeHolder<KiInfusionRecipe> h, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics g,
                     double mouseX, double mouseY) {
        KiInfusionRecipe r = h.value();
        arrow.draw(g, 32, 11);
        line(g, Component.translatable(r.ki().langKey()), 0, r.ki().argb(), true);
        line(g, Component.literal(ZenkaiNumbers.screen((long) r.energy()) + " FE · "
                + ZenkaiNumbers.screen(r.time() / 20.0) + " s"), 10, 0xFF404040, false);
        // Lo que da a la armadura, en DOS líneas: en una sola («Lasts ×2.50 · imbue ×0.50») se salía del recuadro de JEI
        // (captura del usuario 2026-11-23).
        KiEnergyType out = KiInfusion.materialLevel(r.output());
        if (out != null) {
            line(g, Component.translatable("jei.zenkai.ki_infusion.durability", ZenkaiNumbers.fmt2(KiInfusion.durabilityMult(out))),
                    20, 0xFF404040, false);
            line(g, Component.translatable("jei.zenkai.ki_infusion.imbue", ZenkaiNumbers.fmt2(KiInfusion.imbueCostMult(out))),
                    30, 0xFF404040, false);
        }
    }

    /** Una línea de la columna derecha; si una traducción no cabe en el ancho que queda, se encoge en vez de salirse. */
    private static void line(GuiGraphics g, Component text, int y, int color, boolean shadow) {
        var font = Minecraft.getInstance().font;
        int room = W - TEXT_X, w = font.width(text);
        if (w <= room) { g.drawString(font, text, TEXT_X, y, color, shadow); return; }
        float k = room / (float) w;
        g.pose().pushPose();
        g.pose().translate(TEXT_X, y + (1 - k) * font.lineHeight / 2f, 0);
        g.pose().scale(k, k, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }
}
