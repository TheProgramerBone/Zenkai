package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.menu.KiInfuserMenu;
import com.hmc.zenkai.content.blockentity.KiInfuserBlockEntity;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de la infusora de Ki (Fase 5c de la 0.7.0). Hermana de la máquina de fabricación: barra de FE, tanque de Ki y
 * columna de seis mejoras; en el centro entrada → barra de progreso → salida, y abajo el panel de lectura con el estado,
 * el tipo con que saldrá el lingote y el coste. Fondo de tools/gen_ki_infuser_screen.py (geometría == ese script y
 * KiInfuserMenu). El estado se deduce aquí con las MISMAS reglas que KiInfuserBlockEntity.state, a partir de lo que el
 * menú sincroniza. Cifras por ZenkaiNumbers (regla de CLAUDE.md).
 */
public class KiInfuserScreen extends TechPanelScreen<KiInfuserMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/ki_infuser.png");

    // ── Layout (== tools/gen_ki_infuser_screen.py) ───────────────────────────
    private static final int TITLE_X = 12, TITLE_Y = 7, LED_X = 126, LED_Y = 2;
    private static final int BAR_X = 18, BAR_Y = 32, BAR_W = 16, BAR_H = 104, TANK_X = 42;
    private static final int PROG_X = 116, PROG_Y = 57, PROG_W = 32, PROG_H = 6;
    private static final int INFO_X = 68, INFO_Y = 88, INFO_RIGHT = 214, INFO_LH = 10;
    private static final int INFO_W = INFO_RIGHT - INFO_X;
    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 157;

    private static final String[] UPGRADE_KEYS = {
            "screen.zenkai.ki_chamber.slot.tolerance_super", "screen.zenkai.ki_chamber.slot.tolerance_divine",
            "screen.zenkai.ki_chamber.slot.tolerance_legendary", "screen.zenkai.ki_chamber.slot.speed",
            "screen.zenkai.ki_chamber.slot.storage", "screen.zenkai.armor_fabricator.slot.battery" };

    public KiInfuserScreen(KiInfuserMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation background() { return BG; }

    /** Mismo orden de comprobaciones que KiInfuserBlockEntity.state. */
    private KiInfuserBlockEntity.State state() {
        ItemStack in = menu.machineStack(KiInfuserBlockEntity.SLOT_INPUT);
        if (!KiInfuserBlockEntity.canInfuse(in)) return KiInfuserBlockEntity.State.NO_INPUT;
        KiEnergyType t = menu.tankType();
        if (t == null || menu.tankAmount() < KiInfusion.MB_PER_ITEM) return KiInfuserBlockEntity.State.NO_FLUID;
        if (!menu.tolerates(t)) return KiInfuserBlockEntity.State.NO_TOLERANCE;
        ItemStack out = menu.machineStack(KiInfuserBlockEntity.SLOT_OUTPUT);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, KiInfusion.infused(in.copyWithCount(1), t))
                || out.getCount() >= out.getMaxStackSize())) return KiInfuserBlockEntity.State.OUTPUT_FULL;
        if (menu.energy() < KiInfuserBlockEntity.fePerTick()) return KiInfuserBlockEntity.State.NO_ENERGY;
        return KiInfuserBlockEntity.State.READY;
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawTitle(g, TITLE_X, TITLE_Y);
        drawLed(g, LED_X, LED_Y, menu.isWorking());
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);
        drawVBar(g, BAR_X, BAR_Y, BAR_W, BAR_H, menu.energyFraction(),
                menu.isWorking() ? ZenkaiTechPalette.LED_GREEN_MID : ZenkaiTechPalette.BAR_FILL);
        KiEnergyType inTank = menu.tankType();
        drawKiTank(g, TANK_X, BAR_Y, BAR_W, BAR_H, menu.tankFraction(), inTank == null ? null : sprite(inTank));
        drawBar(g, PROG_X, PROG_Y, PROG_W, PROG_H, menu.progressFraction());

        KiInfuserBlockEntity.State s = menu.isWorking() ? KiInfuserBlockEntity.State.READY : state();
        Component status = Component.translatable(menu.isWorking() ? "screen.zenkai.ki_infuser.status.working"
                : "screen.zenkai.ki_infuser.status." + s.name().toLowerCase(java.util.Locale.ROOT));
        int color = menu.isWorking() ? ZenkaiTechPalette.OK_ON_SCREEN
                : s == KiInfuserBlockEntity.State.NO_INPUT ? ZenkaiTechPalette.DIM_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN;
        int y = INFO_Y;
        PanelText.onDark(g, font, PanelText.fit(font, status, INFO_W), INFO_X, y, color);
        y += INFO_LH + 2;

        // Tipo con que sale: el del tanque, en su color en la ETIQUETA (el rojo Divino en la cifra parecía «falta»).
        Component amount = Component.literal(ZenkaiNumbers.screen((long) KiInfusion.MB_PER_ITEM) + " mB");
        boolean fluidOk = inTank != null && menu.tankAmount() >= KiInfusion.MB_PER_ITEM && menu.tolerates(inTank);
        if (inTank == null) {
            infoRow(g, INFO_X, y, Component.translatable("screen.zenkai.ki_infuser.ki_any"), ZenkaiTechPalette.DIM_ON_SCREEN,
                    amount, ZenkaiTechPalette.DENIED_ON_SCREEN);
        } else {
            infoRow(g, INFO_X, y, Component.translatable(inTank.langKey()), inTank.argb(), amount,
                    fluidOk ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        }
        y += INFO_LH;
        infoRow(g, INFO_X, y, Component.translatable("screen.zenkai.armor_fabricator.cost"), ZenkaiTechPalette.DIM_ON_SCREEN,
                Component.literal(ZenkaiNumbers.screen((long) KiInfusion.FE_PER_ITEM) + " FE"),
                menu.energy() >= KiInfuserBlockEntity.fePerTick() ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DENIED_ON_SCREEN);
        y += INFO_LH;
        if (inTank != null) {
            Component gives = Component.translatable("screen.zenkai.ki_infuser.gives",
                    ZenkaiNumbers.fmt2(KiInfusion.durabilityMult(inTank)), ZenkaiNumbers.fmt2(KiInfusion.imbueCostMult(inTank)));
            PanelText.onDark(g, font, PanelText.fit(font, gives, INFO_W), INFO_X, y, ZenkaiTechPalette.DIM_ON_SCREEN);
        }
    }

    /** Etiqueta a la izquierda y cifra a la derecha; la etiqueta se recorta para no pisar la cifra. */
    private void infoRow(GuiGraphics g, int x, int y, Component label, int labelColor, Component value, int valueColor) {
        int room = INFO_RIGHT - x - font.width(value) - 4;
        PanelText.onDark(g, font, PanelText.fit(font, label, room), x, y, labelColor);
        PanelText.rightOnDark(g, font, value, INFO_RIGHT, y, valueColor);
    }

    private static ResourceLocation sprite(KiEnergyType type) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "block/" + type.fluidName() + "_still");
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        int lmx = localMouseX(mouseX), lmy = localMouseY(mouseY);
        if (inside(lmx, lmy, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.ki_chamber.fe").withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(ZenkaiNumbers.exact((long) menu.energy()) + " / "
                            + ZenkaiNumbers.exact((long) menu.energyCapacity()) + " FE"))));
            return;
        }
        if (inside(lmx, lmy, TANK_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            KiEnergyType t = menu.tankType();
            Component name = t == null ? Component.translatable("screen.zenkai.ki_chamber.tank_empty")
                    : Component.translatable(t.langKey()).withStyle(s -> s.withColor(t.argb() & 0xFFFFFF));
            List<TipLine> lines = new ArrayList<>(List.of(
                    TipLine.of(name.copy().withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(ZenkaiNumbers.exact((long) menu.tankAmount()) + " / "
                            + ZenkaiNumbers.exact((long) menu.tankCapacity()) + " mB")),
                    TipLine.of(Component.translatable("screen.zenkai.ki_infuser.tank.tip").withStyle(ChatFormatting.GRAY))));
            if (t != null && !menu.tolerates(t)) {
                lines.add(TipLine.of(Component.translatable("screen.zenkai.ki_infuser.status.no_tolerance")
                        .withStyle(ChatFormatting.RED)));
            }
            drawTooltip(g, mouseX, mouseY, lines);
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            String k = slotKey(menu.slots.indexOf(hoveredSlot));
            if (k != null) {
                drawTooltip(g, mouseX, mouseY, List.of(
                        TipLine.of(Component.translatable(k).withStyle(ChatFormatting.BOLD)),
                        TipLine.of(Component.translatable(k + ".desc").withStyle(ChatFormatting.GRAY))));
            }
        }
    }

    @Nullable
    private static String slotKey(int idx) {
        if (idx < 0) return null;
        if (idx < KiInfuserBlockEntity.UPGRADE_SLOTS) return UPGRADE_KEYS[idx];
        if (idx == KiInfuserBlockEntity.SLOT_INPUT) return "screen.zenkai.ki_infuser.slot.input";
        if (idx == KiInfuserBlockEntity.SLOT_OUTPUT) return "screen.zenkai.ki_infuser.slot.output";
        return null;
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
