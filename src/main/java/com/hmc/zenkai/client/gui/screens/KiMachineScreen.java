package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.menu.KiMachineMenu;
import com.hmc.zenkai.content.blockentity.KiMachineBlockEntity;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base de las pantallas de las máquinas sobre {@link KiMachineBlockEntity}/{@link KiMachineMenu} (2026-11-23: la
 * Fabricadora, la Estación de imbuido y la Infusora repetían lo mismo). Da hecho, con la geometría común de sus fondos
 * (tools/gen_armor_fabricator_screen.py y gen_ki_infuser_screen.py: barra de FE en x=18 y tanque en x=42, 16x104 desde
 * y=32; mejoras en la columna derecha):
 * - {@link #drawMachineBars}: barra de FE (verde mientras trabaja) y tanque de Ki con su fluido.
 * - {@link #machineTooltip}: tooltips de la barra y del tanque (cifras exactas, tipo, falta de tolerancia) y el de la
 *   ranura vacía bajo el ratón (las cuatro mejoras ya resueltas; el resto lo da {@link #slotKey}).
 * - {@link #infoRow}: fila «etiqueta … cifra» del panel oscuro de lectura (borde derecho {@link #INFO_RIGHT}).
 * - {@link #inside}, {@link #sprite}.
 * Cada pantalla pone sus claves con {@link #tankTipKey} y {@link #noToleranceKey}, y su lectura propia.
 */
public abstract class KiMachineScreen<M extends KiMachineMenu> extends TechPanelScreen<M> {

    protected static final int BAR_X = 18, BAR_Y = 32, BAR_W = 16, BAR_H = 104, TANK_X = 42;
    protected static final int INV_LABEL_X = 47, INV_LABEL_Y = 157;
    /** Borde derecho del texto en el panel oscuro de lectura (el mismo en los fondos de las tres máquinas). */
    protected static final int INFO_RIGHT = 214;

    private static final String[] UPGRADE_KEYS = {
            "screen.zenkai.ki_chamber.slot.tolerance", "screen.zenkai.ki_chamber.slot.speed",
            "screen.zenkai.ki_chamber.slot.storage", "screen.zenkai.armor_fabricator.slot.efficiency",
            "screen.zenkai.armor_fabricator.slot.battery" };

    /** Siluetas (estilo de los iconos de ranura de Curios) de las cinco mejoras, en el orden de las ranuras (la batería la última). */
    private static final ResourceLocation[] UPGRADE_GHOSTS = {
            ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_upgrade_tolerance.png"), ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_upgrade_speed.png"),
            ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_upgrade_storage.png"), ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_upgrade_efficiency.png"),
            ResourceLocation.fromNamespaceAndPath(com.hmc.zenkai.Zenkai.MOD_ID, "textures/slot/empty_upgrade_battery.png") };

    @Override
    protected @Nullable ResourceLocation slotGhost(int handlerIndex) {
        if (handlerIndex < 0) return null;
        return handlerIndex < UPGRADE_GHOSTS.length ? UPGRADE_GHOSTS[handlerIndex] : workGhost(handlerIndex);
    }

    /** Silueta de una ranura de trabajo vacía de esta máquina (índice del handler), o null. Por defecto, ninguna. */
    protected @Nullable ResourceLocation workGhost(int handlerIndex) { return null; }

    protected KiMachineScreen(M menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    /** Clave de la línea gris del tooltip del tanque (para qué usa el fluido esta máquina). */
    protected abstract String tankTipKey();

    /** Clave del aviso rojo del tanque cuando su tipo no está tolerado. */
    protected abstract String noToleranceKey();

    /** Clave de lang (nombre; la descripción es {@code <clave>.desc}) de una ranura propia de la máquina, o null. */
    @Nullable
    protected abstract String slotKey(int slotIndex);

    /** Barra de FE y tanque de Ki. */
    protected void drawMachineBars(GuiGraphics g, boolean working) {
        drawVBar(g, BAR_X, BAR_Y, BAR_W, BAR_H, menu.energyFraction(),
                working ? ZenkaiTechPalette.LED_GREEN_MID : ZenkaiTechPalette.BAR_FILL);
        KiEnergyType inTank = menu.tankType();
        drawKiTank(g, TANK_X, BAR_Y, BAR_W, BAR_H, menu.tankFraction(), inTank == null ? null : sprite(inTank));
    }

    /**
     * Tooltips comunes: barra de FE, tanque y ranura vacía. Devuelve true si pintó uno; la pantalla llama a esto al
     * principio o al final de su renderFloating según qué deba tener prioridad.
     */
    protected boolean machineTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int lmx = localMouseX(mouseX), lmy = localMouseY(mouseY);
        if (inside(lmx, lmy, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.ki_chamber.fe").withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(ZenkaiNumbers.exact((long) menu.energy()) + " / "
                            + ZenkaiNumbers.exact((long) menu.energyCapacity()) + " FE"))));
            return true;
        }
        if (inside(lmx, lmy, TANK_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            KiEnergyType t = menu.tankType();
            Component name = t == null ? Component.translatable("screen.zenkai.ki_chamber.tank_empty")
                    : Component.translatable(t.langKey()).withStyle(s -> s.withColor(t.argb() & 0xFFFFFF));
            List<TipLine> lines = new ArrayList<>(List.of(
                    TipLine.of(name.copy().withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(ZenkaiNumbers.exact((long) menu.tankAmount()) + " / "
                            + ZenkaiNumbers.exact((long) menu.tankCapacity()) + " mB")),
                    TipLine.of(Component.translatable(tankTipKey()).withStyle(ChatFormatting.GRAY))));
            if (t != null && !menu.tolerates(t)) {
                lines.add(TipLine.of(Component.translatable(noToleranceKey()).withStyle(ChatFormatting.RED)));
            }
            drawTooltip(g, mouseX, mouseY, lines);
            return true;
        }
        return emptySlotTooltip(g, mouseX, mouseY);
    }

    /** Nombre y descripción de la ranura vacía bajo el ratón (mejoras incluidas). */
    protected boolean emptySlotTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredSlot == null || hoveredSlot.hasItem() || !menu.getCarried().isEmpty()) return false;
        int idx = menu.slots.indexOf(hoveredSlot);
        String k = idx < 0 ? null : idx < KiMachineBlockEntity.UPGRADE_SLOTS ? UPGRADE_KEYS[idx] : slotKey(idx);
        if (k == null) return false;
        drawTooltip(g, mouseX, mouseY, List.of(
                TipLine.of(Component.translatable(k).withStyle(ChatFormatting.BOLD)),
                TipLine.of(Component.translatable(k + ".desc").withStyle(ChatFormatting.GRAY))));
        return true;
    }

    /** Etiqueta (clave de lang, gris) a la izquierda y cifra a la derecha; la etiqueta se recorta para no pisar la cifra. */
    protected void infoRow(GuiGraphics g, int x, int y, String key, Component value, int valueColor) {
        infoRow(g, x, y, Component.translatable(key), ZenkaiTechPalette.DIM_ON_SCREEN, value, valueColor);
    }

    protected void infoRow(GuiGraphics g, int x, int y, String key, int labelColor, Component value, int valueColor) {
        infoRow(g, x, y, Component.translatable(key), labelColor, value, valueColor);
    }

    protected void infoRow(GuiGraphics g, int x, int y, Component label, int labelColor, Component value, int valueColor) {
        int room = INFO_RIGHT - x - font.width(value) - 4;
        PanelText.onDark(g, font, PanelText.fit(font, label, room), x, y, labelColor);
        PanelText.rightOnDark(g, font, value, INFO_RIGHT, y, valueColor);
    }

    protected static ResourceLocation sprite(KiEnergyType type) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "block/" + type.fluidName() + "_still");
    }

    protected static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
