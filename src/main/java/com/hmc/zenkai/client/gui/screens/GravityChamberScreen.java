package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.menu.GravityChamberMenu;
import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Pantalla de la consola de la cámara de gravedad (Fase 3 de la 0.7.0). De izquierda a derecha:
 *   barra de FE (heredada de la carcasa del generador);
 *   columna de control: g elegida en grande, botones −10/−1/+1/+10, radio de la esfera −/+, el
 *   holograma y la ranura de la mejora;
 *   panel de lectura: estado, g máxima, coste, FE, volumen, fugas, puertos y drones (Fase 4).
 *
 * Cada cifra llega por el ContainerData del menú: la pantalla no calcula nada de la máquina (las
 * toneladas salen de WeightSystem, el mismo embudo que usa el servidor).
 * Geometría == tools/gen_gravity_chamber_screen.py (ranura) y la carcasa de energy_generator.png.
 */
public class GravityChamberScreen extends TechPanelScreen<GravityChamberMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/gravity_chamber.png");

    private static final int TITLE_X = 12, TITLE_Y = 7;
    private static final int LED_X = 144, LED_Y = 2;
    private static final int BAR_X = 18, BAR_Y = 32, BAR_W = 16, BAR_H = 104;

    // Columna de control (x 42..117). Rejilla 2x2 de botones de g de 36 px: con 18 px «-10»/«+10» no
    // cabían y el texto se salía del botón (captura del usuario, 2026-11-20). Los botones de radio son
    // estrechos para que «Radio 10» quepa centrado entre ellos sin pisarlos.
    private static final int COL_X = 42, COL_W = 75, COL_CX = COL_X + COL_W / 2;
    private static final int G_VALUE_Y = 37, G_ROW1_Y = 48, G_ROW2_Y = 64, R_ROW_Y = 81, HOLO_Y = 98, POWER_Y = 117;
    private static final int HALF_W = 36, BTN_H = 14, R_BTN_W = 12, POWER_W = 52;

    private static final int INFO_X = 126, INFO_Y = 38, INFO_LH = 11, INFO_RIGHT = 236;
    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 157;

    private TechButton hologramButton;
    private TechButton powerButton;

    public GravityChamberScreen(GravityChamberMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation background() { return BG; }

    @Override
    protected void init() {
        super.init();
        int right = COL_X + COL_W - HALF_W;
        gButton(COL_X, G_ROW1_Y, "-10", GravityChamberMenu.BTN_G_MINUS10);
        gButton(right, G_ROW1_Y, "+10", GravityChamberMenu.BTN_G_PLUS10);
        gButton(COL_X, G_ROW2_Y, "-1", GravityChamberMenu.BTN_G_MINUS1);
        gButton(right, G_ROW2_Y, "+1", GravityChamberMenu.BTN_G_PLUS1);
        addRenderableWidget(new TechButton(leftPos + COL_X, topPos + R_ROW_Y, R_BTN_W, BTN_H,
                Component.literal("-"), TechButton.Kind.SECONDARY, () -> press(GravityChamberMenu.BTN_RADIUS_MINUS)));
        addRenderableWidget(new TechButton(leftPos + COL_X + COL_W - R_BTN_W, topPos + R_ROW_Y, R_BTN_W, BTN_H,
                Component.literal("+"), TechButton.Kind.SECONDARY, () -> press(GravityChamberMenu.BTN_RADIUS_PLUS)));
        hologramButton = new TechButton(leftPos + COL_X, topPos + HOLO_Y, COL_W, BTN_H,
                Component.empty(), TechButton.Kind.SECONDARY, () -> press(GravityChamberMenu.BTN_HOLOGRAM));
        addRenderableWidget(hologramButton);
        powerButton = new TechButton(leftPos + COL_X, topPos + POWER_Y, POWER_W, 16,
                Component.empty(), TechButton.Kind.PRIMARY, () -> press(GravityChamberMenu.BTN_POWER));
        addRenderableWidget(powerButton);
        refresh();
    }

    private void gButton(int x, int y, String label, int id) {
        addRenderableWidget(new TechButton(leftPos + x, topPos + y, HALF_W, BTN_H,
                Component.literal(label), TechButton.Kind.SECONDARY, () -> press(id)));
    }

    private void press(int id) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private void refresh() {
        hologramButton.setMessage(Component.translatable(menu.hologram()
                ? "screen.zenkai.gravity_chamber.hologram_on" : "screen.zenkai.gravity_chamber.hologram_off"));
        powerButton.setMessage(Component.translatable(menu.enabled()
                ? "screen.zenkai.gravity_chamber.power_on" : "screen.zenkai.gravity_chamber.power_off"));
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        drawTitle(g, TITLE_X, TITLE_Y);
        drawLed(g, LED_X, LED_Y, menu.status() == GravityChamberBlockEntity.STATUS_ACTIVE);
        drawInventoryLabel(g, INV_LABEL_X, INV_LABEL_Y);
        drawVBar(g, BAR_X, BAR_Y, BAR_W, BAR_H, menu.energyFraction(),
                menu.status() == GravityChamberBlockEntity.STATUS_ACTIVE ? ZenkaiTechPalette.LED_GREEN_MID : ZenkaiTechPalette.BAR_FILL);

        // g elegida, en grande y centrada sobre la carcasa.
        Component gText = Component.literal("×" + menu.targetGravity()).withStyle(ChatFormatting.BOLD);
        g.pose().pushPose();
        g.pose().translate(COL_CX, G_VALUE_Y, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        PanelText.onPanel(g, font, gText, -font.width(gText) / 2, -2, ZenkaiTechPalette.MUTED_ON_CHASSIS);
        g.pose().popPose();

        Component rText = Component.translatable("screen.zenkai.gravity_chamber.radius_short", menu.radius());
        PanelText.onPanel(g, font, rText, COL_CX - font.width(rText) / 2, R_ROW_Y + 3, ZenkaiTechPalette.MUTED_ON_CHASSIS);

        int y = INFO_Y;
        int max = menu.maxGravity();
        y = infoRow(g, y, "screen.zenkai.gravity_chamber.max",
                Component.literal(max <= 1 ? "—" : "×" + max),
                max <= 1 ? ZenkaiTechPalette.DENIED_ON_SCREEN : ZenkaiTechPalette.TEXT_ON_SCREEN);
        int eff = Math.max(1, max <= 1 ? 1 : Math.min(menu.targetGravity(), max));
        double tons = WeightSystem.tonsForGravity(eff);
        y = infoRow(g, y, "screen.zenkai.gravity_chamber.tons",
                Component.literal(ZenkaiNumbers.tons(tons) + " t"), ZenkaiTechPalette.TEXT_ON_SCREEN);
        // Lo que aguanta QUIEN MIRA, con su forma actual: así se ve que transformarse cambia el límite.
        if (minecraft != null && minecraft.player != null) {
            double cap = WeightSystem.playerCapacity(PlayerStatsAttachment.get(minecraft.player));
            double r = cap <= 0 ? 0 : tons / cap;
            int color = r > com.hmc.zenkai.config.ServerConfig.gravityChamberDrainLoad() ? ZenkaiTechPalette.DENIED_ON_SCREEN
                    : WeightSystem.isOverloaded(r) ? ZenkaiTechPalette.MAXED_ON_SCREEN : ZenkaiTechPalette.OK_ON_SCREEN;
            y = infoRow(g, y, "screen.zenkai.gravity_chamber.capacity",
                    Component.literal(ZenkaiNumbers.tons(cap) + " t"), color);
        }
        y = infoRow(g, y, "screen.zenkai.gravity_chamber.cost",
                Component.literal(menu.cost() > 0 ? ZenkaiNumbers.screen((long) menu.cost()) + " FE/t" : "—"),
                menu.status() == GravityChamberBlockEntity.STATUS_ACTIVE ? ZenkaiTechPalette.OK_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN);
        y += 4;
        y = infoRow(g, y, "screen.zenkai.gravity_chamber.volume",
                Component.literal(ZenkaiNumbers.screen((long) menu.interior())), ZenkaiTechPalette.TEXT_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.gravity_chamber.leaks",
                Component.literal(ZenkaiNumbers.screen((long) menu.leaks())),
                menu.leaks() > 0 ? ZenkaiTechPalette.DENIED_ON_SCREEN : ZenkaiTechPalette.OK_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.gravity_chamber.ports",
                Component.literal(ZenkaiNumbers.screen((long) menu.ports())), ZenkaiTechPalette.TEXT_ON_SCREEN);
        y += 6;
        PanelText.onDark(g, font, statusLine(), INFO_X, y, statusColor());
    }

    private Component statusLine() {
        return switch (menu.status()) {
            case GravityChamberBlockEntity.STATUS_ACTIVE -> Component.translatable("screen.zenkai.gravity_chamber.status.active");
            case GravityChamberBlockEntity.STATUS_IDLE -> Component.translatable("screen.zenkai.gravity_chamber.status.idle");
            case GravityChamberBlockEntity.STATUS_OFF -> Component.translatable("screen.zenkai.gravity_chamber.status.off");
            case GravityChamberBlockEntity.STATUS_TOO_SMALL -> Component.translatable("screen.zenkai.gravity_chamber.status.too_small");
            case GravityChamberBlockEntity.STATUS_NO_UPGRADE -> Component.translatable("screen.zenkai.gravity_chamber.status.no_upgrade");
            case GravityChamberBlockEntity.STATUS_NO_ENERGY -> Component.translatable("screen.zenkai.gravity_chamber.status.no_energy");
            default -> Component.translatable("screen.zenkai.gravity_chamber.status.open");
        };
    }

    private int statusColor() {
        return switch (menu.status()) {
            case GravityChamberBlockEntity.STATUS_ACTIVE -> ZenkaiTechPalette.OK_ON_SCREEN;
            case GravityChamberBlockEntity.STATUS_IDLE, GravityChamberBlockEntity.STATUS_OFF -> ZenkaiTechPalette.DIM_ON_SCREEN;
            default -> ZenkaiTechPalette.DENIED_ON_SCREEN;
        };
    }

    private int infoRow(GuiGraphics g, int y, String key, Component value, int valueColor) {
        PanelText.onDark(g, font, Component.translatable(key), INFO_X, y, ZenkaiTechPalette.DIM_ON_SCREEN);
        PanelText.rightOnDark(g, font, value, INFO_RIGHT, y, valueColor);
        return y + INFO_LH;
    }

    @Override
    protected void renderFloating(GuiGraphics g, int mouseX, int mouseY) {
        int lmx = localMouseX(mouseX), lmy = localMouseY(mouseY);
        if (lmx >= BAR_X - 1 && lmx < BAR_X + BAR_W + 1 && lmy >= BAR_Y - 1 && lmy < BAR_Y + BAR_H + 1) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.fe").withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.literal(ZenkaiNumbers.exact((long) menu.energy()) + " / "
                            + ZenkaiNumbers.exact((long) menu.energyCapacity()) + " FE"))));
            return;
        }
        // Convención de ZenkaiNumbers: toneladas compactadas (filas 2 y 3 de la info) -> exacto en tooltip.
        if (lmx >= INFO_X && lmx < INFO_RIGHT && lmy >= INFO_Y + INFO_LH && lmy < INFO_Y + 3 * INFO_LH) {
            boolean tonsRow = lmy < INFO_Y + 2 * INFO_LH;
            int max = menu.maxGravity();
            double v = tonsRow
                    ? WeightSystem.tonsForGravity(Math.max(1, max <= 1 ? 1 : Math.min(menu.targetGravity(), max)))
                    : (minecraft != null && minecraft.player != null
                        ? WeightSystem.playerCapacity(PlayerStatsAttachment.get(minecraft.player)) : 0);
            if (ZenkaiNumbers.isCompacted(v)) {
                drawTooltip(g, mouseX, mouseY, List.of(TipLine.of(Component.literal(ZenkaiNumbers.exact(v) + " t"))));
                return;
            }
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty() && menu.slots.indexOf(hoveredSlot) == 0) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.slot").withStyle(ChatFormatting.BOLD)),
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.slot.desc").withStyle(ChatFormatting.GRAY))));
            return;
        }
        if (hologramButton != null && hologramButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.hologram.tip1").withStyle(ChatFormatting.GRAY)),
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.hologram.tip2").withStyle(ChatFormatting.AQUA))));
            return;
        }
        // Radio: qué cambia con él (coste y, en la Fase 4, drones a la vez).
        if (lmy >= R_ROW_Y && lmy < R_ROW_Y + BTN_H && lmx >= COL_X + R_BTN_W && lmx < COL_X + COL_W - R_BTN_W) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.radius.tip1").withStyle(ChatFormatting.GRAY)),
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.radius.tip2",
                            Math.max(0, menu.radius() - 2)).withStyle(ChatFormatting.AQUA))));
            return;
        }
        if (powerButton != null && powerButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.power.tip").withStyle(ChatFormatting.GRAY))));
        }
    }
}
