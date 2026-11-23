package com.hmc.zenkai.compat.jei;

import com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity;
import com.hmc.zenkai.feature.armor.ArmorImbuingRecipe;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
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
 * JEI: imbuir en la máquina de fabricación. Una entrada por receta {@code zenkai:armor_imbuing} y, para las piezas sin
 * receta, una por tipo automático (armadura + dureza, ArmorImbuingRecipe.autoTier). Pieza + cualquier sangre + fluido de Ki → la misma pieza con +calidad en el
 * atributo de la sangre. El hueco del fluido rota por los tipos que valen con lo que gasta cada uno POR PUNTO bajo el
 * umbral de la curva (la mitad por cada tipo de más, {@link ArmorImbuingRecipe#scaledMb}); la FE no se enseña porque
 * depende de lo que la pieza ya lleve imbuido.
 */
public class ArmorImbuingCategory extends AbstractRecipeCategory<ArmorImbuingCategory.Display> {

    public static final RecipeType<Display> TYPE = RecipeType.create(com.hmc.zenkai.Zenkai.MOD_ID, "armor_imbuing", Display.class);

    /** Lo que enseña una entrada: piezas que piden {@code ki}; {@code fallback} = tipo automático, sin receta. */
    public record Display(List<ItemStack> pieces, KiEnergyType ki, boolean fallback) {}

    private static final int W = 170, H = 52;
    private final IDrawableStatic arrow;

    public ArmorImbuingCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("jei.zenkai.armor_imbuing"),
                gui.createDrawableItemLike(ModBlocks.ARMOR_FABRICATOR.get()), W, H);
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder b, @NotNull Display d, @NotNull IFocusGroup focuses) {
        IRecipeSlotBuilder in = b.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStacks(d.pieces());
        b.addSlot(RecipeIngredientRole.INPUT, 1, 21).setStandardSlotBackground().addItemStacks(JeiBloods.all());
        IRecipeSlotBuilder fluid = b.addSlot(RecipeIngredientRole.INPUT, 21, 11).setStandardSlotBackground()
                .setFluidRenderer((long) ArmorFabricatorBlockEntity.IMBUE_MB_PER_POINT, false, 16, 16);
        long base = (long) ArmorFabricatorBlockEntity.IMBUE_MB_PER_POINT;
        for (KiEnergyType t : KiEnergyType.values()) {
            long mb = ArmorImbuingRecipe.scaledMb(base, d.ki(), t);
            if (mb > 0) fluid.addFluidStack(t.fluid().source().get(), mb);
        }
        fluid.addRichTooltipCallback((view, tip) -> tip.add(
                Component.translatable("jei.zenkai.armor_imbuing.per_point").withStyle(net.minecraft.ChatFormatting.GRAY)));
        IRecipeSlotBuilder out = b.addSlot(RecipeIngredientRole.OUTPUT, 70, 11).setOutputSlotBackground()
                .addItemStacks(d.pieces());
        b.createFocusLink(in, out);   // la pieza de entrada y la de salida rotan juntas
    }

    @Override
    public void draw(@NotNull Display d, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics g, double mouseX, double mouseY) {
        arrow.draw(g, 43, 11);
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable(d.ki().langKey()), 94, 9, d.ki().argb(), true);
        g.drawString(font, Component.translatable("jei.zenkai.armor_imbuing.ki"), 94, 20, 0xFF404040, false);
        if (d.fallback()) {
            g.drawString(font, Component.translatable("jei.zenkai.armor_imbuing.auto"), 1, 43, 0xFF404040, false);
        }
    }
}
