package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.menu.CrusherMenu;
import com.hmc.zenkai.content.blockentity.CrusherBlockEntity;
import com.hmc.zenkai.feature.crusher.CrushingRecipe;
import com.hmc.zenkai.registry.ModRecipes;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pantalla del Triturador (Fase 5d de la 0.7.0), sobre {@link KiMachineScreen}: barra de FE, tanque de Ki (turbo) y
 * columna de mejoras; en el centro entrada → barra de progreso → cuatro salidas 2x2; abajo el panel de lectura con el
 * estado, lo que sale (los extras con su probabilidad en el tooltip del panel) y el coste. Fondo de
 * tools/gen_crusher_screen.py (geometría == ese script y CrusherMenu). Cifras por ZenkaiNumbers (regla de CLAUDE.md).
 */
public class CrusherScreen extends KiMachineScreen<CrusherMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/crusher.png");

    // ── Layout (== tools/gen_crusher_screen.py) ──────────────────────────────
    public static final int PROG_X = 106, PROG_Y = 57, PROG_W = 36, PROG_H = 6;
    private static final int INFO_X = 68, INFO_Y = 88, INFO_LH = 10;
    private static final int INFO_W = INFO_RIGHT - INFO_X;
    /** Zona del panel de lectura (para su tooltip con las probabilidades). */
    private static final int PANEL_X1 = 64, PANEL_Y1 = 84, PANEL_X2 = 218, PANEL_Y2 = 137;

    public CrusherScreen(CrusherMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override protected String tankTipKey() { return "screen.zenkai.crusher.tank.tip"; }
    @Override protected String noToleranceKey() { return "screen.zenkai.armor_fabricator.tank.no_tolerance"; }

    @Override
    protected ResourceLocation background() { return BG; }

    /** Receta para lo que hay en la entrada (las recetas se sincronizan al cliente). */
    private Optional<RecipeHolder<CrushingRecipe>> recipe() {
        if (minecraft == null || minecraft.level == null) return Optional.empty();
        ItemStack in = menu.machineStack(CrusherBlockEntity.SLOT_INPUT);
        if (in.isEmpty()) return Optional.empty();
        return minecraft.level.getRecipeManager().getRecipeFor(ModRecipes.CRUSHING.get(),
                new CrushingRecipe.Input(in), minecraft.level);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawHeader(g, menu.isWorking());
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);
        drawMachineBars(g, menu.isWorking());
        drawBar(g, PROG_X, PROG_Y, PROG_W, PROG_H, menu.progressFraction());

        int y = INFO_Y;
        ItemStack in = menu.machineStack(CrusherBlockEntity.SLOT_INPUT);
        Optional<RecipeHolder<CrushingRecipe>> r = recipe();
        Component status;
        int color;
        if (in.isEmpty()) {
            status = Component.translatable("screen.zenkai.crusher.status.empty");
            color = ZenkaiTechPalette.DIM_ON_SCREEN;
        } else if (r.isEmpty()) {
            status = Component.translatable("screen.zenkai.crusher.status.no_recipe");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else if (menu.isWorking()) {
            status = Component.translatable(menu.isTurbo() ? "screen.zenkai.crusher.status.turbo" : "screen.zenkai.crusher.status.working");
            color = ZenkaiTechPalette.OK_ON_SCREEN;
        } else if (in.getCount() < r.get().value().count()) {
            status = Component.translatable("screen.zenkai.crusher.status.not_enough", r.get().value().count());
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else if (menu.energy() < r.get().value().energyPerTick()) {
            status = Component.translatable("screen.zenkai.crusher.status.no_energy");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        } else {
            status = Component.translatable("screen.zenkai.crusher.status.output_full");
            color = ZenkaiTechPalette.DENIED_ON_SCREEN;
        }
        PanelText.onDark(g, font, PanelText.fit(font, status, INFO_W), INFO_X, y, color);
        y += INFO_LH + 2;
        if (r.isEmpty()) return;
        CrushingRecipe rec = r.get().value();
        CrushingRecipe.Output main = rec.results().get(0);
        infoRow(g, INFO_X, y, main.stack().getHoverName(), ZenkaiTechPalette.DIM_ON_SCREEN,
                Component.literal(main.guaranteed() ? "×" + ZenkaiNumbers.screen((long) main.stack().getCount())
                        : ZenkaiNumbers.screen(Math.round(main.chance() * 1000) / 10.0) + " %"), ZenkaiTechPalette.TEXT_ON_SCREEN);
        y += INFO_LH;
        int extras = rec.results().size() - 1;
        if (extras > 0) {
            infoRow(g, INFO_X, y, "screen.zenkai.crusher.extras", Component.literal("+" + ZenkaiNumbers.screen((long) extras)),
                    ZenkaiTechPalette.OK_ON_SCREEN);
            y += INFO_LH;
        }
        infoRow(g, INFO_X, y, "screen.zenkai.armor_fabricator.cost",
                Component.literal(ZenkaiNumbers.screen((long) rec.energy()) + " FE"), ZenkaiTechPalette.TEXT_ON_SCREEN);
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        if (machineTooltip(g, mouseX, mouseY)) return;
        int lmx = localMouseX(mouseX), lmy = localMouseY(mouseY);
        if (!inside(lmx, lmy, PANEL_X1, PANEL_Y1, PANEL_X2 - PANEL_X1, PANEL_Y2 - PANEL_Y1)) return;
        Optional<RecipeHolder<CrushingRecipe>> r = recipe();
        if (r.isEmpty()) return;
        // Cada salida con su probabilidad, como en JEI.
        List<TipLine> lines = new ArrayList<>();
        lines.add(TipLine.of(Component.translatable("screen.zenkai.crusher.results").withStyle(ChatFormatting.BOLD)));
        for (CrushingRecipe.Output o : r.get().value().results()) {
            String chance = o.guaranteed() ? "" : "  (" + ZenkaiNumbers.screen(Math.round(o.chance() * 1000) / 10.0) + " %)";
            lines.add(TipLine.of(Component.literal(ZenkaiNumbers.screen((long) o.stack().getCount()) + "× ")
                    .append(o.stack().getHoverName()).append(Component.literal(chance).withStyle(ChatFormatting.GRAY))));
        }
        if (r.get().value().infusionAware()) {
            lines.add(TipLine.of(Component.translatable("screen.zenkai.crusher.infusion_aware").withStyle(ChatFormatting.GRAY)));
        }
        drawTooltip(g, mouseX, mouseY, lines);
    }


    @Override
    protected @Nullable String slotKey(int idx) {
        if (idx == CrusherBlockEntity.SLOT_INPUT) return "screen.zenkai.crusher.slot.input";
        if (CrusherBlockEntity.isOutput(idx)) return "screen.zenkai.crusher.slot.output";
        return null;
    }
}
