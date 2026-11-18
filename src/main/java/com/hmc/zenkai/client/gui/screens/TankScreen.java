package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.menu.TankMenu;
import com.hmc.zenkai.util.ZenkaiNumbers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Pantalla de los tanques de energía y de fluidos (0.7.0). Familia tecnológica, hermana de la cámara
 * de contención: a la izquierda el medidor del GRUPO (un tanque de 2x2x3 se lee como uno), en el centro
 * las ranuras (energía: tres para cargar y tres para descargar ítems; fluido: llenar y vaciar
 * recipientes, cubos de Ki incluidos); a la derecha la lectura. Sin botón de caras (quitado el
 * 2026-11-17): los puertos de un tanque se cambian con la llave, clic derecho en la cara.
 *
 * Fondo == tools/gen_tank_screen.py (tank_energy.png / tank_fluid.png). Coordenadas LOCALES en
 * renderLabels y ABSOLUTAS en renderFloating, como el resto de la familia (TechPanelScreen).
 */
public class TankScreen extends TechPanelScreen<TankMenu> {

    private static final ResourceLocation BG_ENERGY =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/tank_energy.png");
    private static final ResourceLocation BG_FLUID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/tank_fluid.png");

    private static final int TITLE_X = 12, TITLE_Y = 7;
    /** == pozo de medidor heredado de ki_chamber.png. */
    private static final int BAR_X = 18, BAR_Y = 32, BAR_W = 16, BAR_H = 104;
    private static final int LABEL_DY = -10;
    private static final int INFO_X = 126, INFO_Y = 38, INFO_LH = 11, INFO_RIGHT = 236;
    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 157;

    public TankScreen(TankMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation background() { return menu.isEnergy() ? BG_ENERGY : BG_FLUID; }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawTitle(g, TITLE_X, TITLE_Y);
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);

        boolean energy = menu.isEnergy();
        if (energy) {
            drawVBar(g, BAR_X, BAR_Y, BAR_W, BAR_H, menu.fraction(), ZenkaiTechPalette.LED_GREEN_MID);
        } else {
            drawFluid(g, menu.fluid(), menu.fraction());
        }

        // Etiquetas de las filas de ranuras (carcasa: oscuro, sin sombra).
        String row1 = energy ? "screen.zenkai.tank.charge" : "screen.zenkai.tank.fill";
        String row2 = energy ? "screen.zenkai.tank.discharge" : "screen.zenkai.tank.drain";
        int labelX = (energy ? TankMenu.ENERGY_SLOT_X : TankMenu.SLOT_IN_X) - 1;
        PanelText.onPanel(g, font, Component.translatable(row1), labelX, TankMenu.SLOT_ROW1_Y + LABEL_DY,
                ZenkaiTechPalette.LABEL_ON_CHASSIS);
        PanelText.onPanel(g, font, Component.translatable(row2), labelX, TankMenu.SLOT_ROW2_Y + LABEL_DY,
                ZenkaiTechPalette.LABEL_ON_CHASSIS);

        // Lectura.
        int y = INFO_Y;
        if (energy) {
            y = row(g, y, "screen.zenkai.tank.energy",
                    Component.literal(ZenkaiNumbers.screen((long) menu.amount()) + " FE"), ZenkaiTechPalette.TEXT_ON_SCREEN);
            y = row(g, y, "screen.zenkai.tank.capacity",
                    Component.literal(ZenkaiNumbers.screen((long) menu.capacity()) + " FE"), ZenkaiTechPalette.DIM_ON_SCREEN);
        } else {
            Fluid f = menu.fluid();
            Component name = f == Fluids.EMPTY ? Component.translatable("screen.zenkai.tank.empty")
                    : new FluidStack(f, 1).getHoverName();
            y = row(g, y, "screen.zenkai.tank.fluid", PanelText.fit(font, name, 60),
                    f == Fluids.EMPTY ? ZenkaiTechPalette.DIM_ON_SCREEN : ZenkaiTechPalette.MAXED_ON_SCREEN);
            y = row(g, y, "screen.zenkai.tank.amount",
                    Component.literal(ZenkaiNumbers.screen((long) menu.amount()) + " mB"), ZenkaiTechPalette.TEXT_ON_SCREEN);
            y = row(g, y, "screen.zenkai.tank.capacity",
                    Component.literal(ZenkaiNumbers.screen((long) menu.capacity()) + " mB"), ZenkaiTechPalette.DIM_ON_SCREEN);
        }
        y += 4;
        y = row(g, y, "screen.zenkai.tank.fill_level",
                Component.literal(Math.round(menu.fraction() * 100f) + " %"), ZenkaiTechPalette.OK_ON_SCREEN);
        row(g, y, "screen.zenkai.tank.blocks",
                Component.literal(String.valueOf(menu.groupSize())), ZenkaiTechPalette.TEXT_ON_SCREEN);
    }

    private int row(GuiGraphics g, int y, String key, Component value, int color) {
        PanelText.onDark(g, font, Component.translatable(key), INFO_X, y, ZenkaiTechPalette.DIM_ON_SCREEN);
        PanelText.rightOnDark(g, font, value, INFO_RIGHT, y, color);
        return y + INFO_LH;
    }

    /**
     * Medidor de fluido para CUALQUIER fluido (agua, lava, ki...): como drawKiTank de la base pero con
     * la textura y el TINTE de su tipo de fluido (el agua viene gris en el atlas y se tiñe), y con el
     * mismo cristal encima.
     */
    private void drawFluid(GuiGraphics g, Fluid fluid, float fraction) {
        g.fill(BAR_X - 1, BAR_Y - 1, BAR_X + BAR_W + 1, BAR_Y + BAR_H + 1, ZenkaiTechPalette.BAR_FRAME);
        g.fill(BAR_X, BAR_Y, BAR_X + BAR_W, BAR_Y + BAR_H, ZenkaiTechPalette.BAR_BG);
        int filled = Math.round(BAR_H * Math.max(0f, Math.min(1f, fraction)));
        if (filled > 0 && fluid != Fluids.EMPTY && minecraft != null) {
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            FluidStack stack = new FluidStack(fluid, 1000);
            TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(stack));
            int tint = ext.getTintColor(stack);
            g.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, 1f);
            g.enableScissor(leftPos + BAR_X, topPos + BAR_Y + BAR_H - filled, leftPos + BAR_X + BAR_W, topPos + BAR_Y + BAR_H);
            for (int cy = BAR_Y + BAR_H - 16; cy > BAR_Y + BAR_H - filled - 16; cy -= 16) {
                g.blit(BAR_X, cy, 0, 16, 16, sprite);
            }
            g.disableScissor();
            g.setColor(1f, 1f, 1f, 1f);
        }
        RenderSystem.enableBlend();
        for (int gy = BAR_Y + BAR_H - TANK_GLASS_H; gy >= BAR_Y; gy -= TANK_GLASS_H) {
            int skip = gy == BAR_Y ? 1 : 0;
            g.blit(TANK_GLASS, BAR_X, gy + skip, 0, skip, TANK_GLASS_W, TANK_GLASS_H - skip, TANK_GLASS_W, TANK_GLASS_H);
        }
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        int lx = localMouseX(mouseX), ly = localMouseY(mouseY);
        if (lx >= BAR_X - 1 && lx < BAR_X + BAR_W + 1 && ly >= BAR_Y - 1 && ly < BAR_Y + BAR_H + 1) {
            Component head = menu.isEnergy() ? Component.translatable("screen.zenkai.tank.energy")
                    : menu.fluid() == Fluids.EMPTY ? Component.translatable("screen.zenkai.tank.empty")
                    : new FluidStack(menu.fluid(), 1).getHoverName();
            String unit = menu.isEnergy() ? " FE" : " mB";
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(head.copy().withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(menu.amount() + " / " + menu.capacity() + unit))));
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int idx = menu.slots.indexOf(hoveredSlot);
            String key = slotKey(idx);
            if (key != null) {
                drawTooltip(g, mouseX, mouseY, List.of(
                        TipLine.of(Component.translatable(key).withStyle(ChatFormatting.BOLD)),
                        TipLine.of(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY))));
            }
        }
    }

    /** Clave de lang de la ranura de máquina bajo el cursor, en el orden en que las añade TankMenu. */
    private String slotKey(int idx) {
        if (menu.isEnergy()) {
            if (idx < 0 || idx >= 6) return null;
            return idx < 3 ? "screen.zenkai.tank.slot.charge" : "screen.zenkai.tank.slot.discharge";
        }
        return switch (idx) {
            case 0 -> "screen.zenkai.tank.slot.fill_in";
            case 1 -> "screen.zenkai.tank.slot.fill_out";
            case 2 -> "screen.zenkai.tank.slot.drain_in";
            case 3 -> "screen.zenkai.tank.slot.drain_out";
            default -> null;
        };
    }
}
