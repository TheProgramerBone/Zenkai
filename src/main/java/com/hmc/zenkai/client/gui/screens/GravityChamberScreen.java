package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.client.gui.buttons.TechButton;
import com.hmc.zenkai.client.gui.menu.GravityChamberMenu;
import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.feature.gravity.GravityDroneRound;
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
 *   panel de lectura: estado, g máxima, coste, FE, volumen, fugas y puertos; con el botón de la cabecera
 *   pasa a la vista «Drones» (Fase 4): 8 ranuras, modo, iniciar/cancelar ronda, temporizador y resultado.
 *   La columna de control no cambia entre vistas.
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

    // Panel oscuro y 28..137. Las dos vistas empiezan en y = 32 y la última línea cae en 125 (2026-11-22: antes la
    // vista Gravedad empezaba en 38 y dejaba aire arriba; ahora los grupos se separan con ese aire).
    private static final int INFO_X = 126, INFO_Y = 32, INFO_LH = 11, INFO_RIGHT = 236, INFO_W = INFO_RIGHT - INFO_X;
    private static final int INFO_GAP_1 = 6, INFO_GAP_2 = 10;
    private static final int INV_LABEL_X = 47, INV_LABEL_Y = 157;

    // Vista Drones (Fase 4): botón de cabecera y panel. Los pozos de las ranuras se copian del primer
    // hueco de la bandeja (46,169) en renderBg: solo existen en esta vista, así que no van en la textura.
    private static final int VIEW_X = 164, VIEW_Y = 4, VIEW_W = 80, VIEW_H = 13;
    private static final int WELL_U = 46, WELL_V = 169;
    // Panel de drones (y 28..137): cabecera, ranuras (GravityChamberMenu.DRONE_Y = 43, dos filas hasta 79), fila de
    // modo + ronda, fila de dificultad a lo ancho y dos líneas de lectura. Botones de 13 px para que quepa todo.
    private static final int DR_HEAD_Y = 32, DR_BTN_Y = 82, DR_BTN_W = 54, DR_BTN_H = 13, DR_DIFF_Y = 97, DR_LINE_Y = 114;
    /**
     * Vista Drones. La recuerda la CONSOLA (2026-11-22, pedido del usuario): llega en el buffer de apertura, el botón
     * la cambia aquí al momento y avisa al servidor (BTN_VIEW_*) para que la guarde. Antes era un static de la
     * pantalla: se compartía entre consolas y se perdía al cerrar el juego.
     */
    private boolean droneView;
    /** Solo para las capturas automáticas (ScreenShots modo «drones»): fuerza la vista en el próximo refresco. */
    @org.jetbrains.annotations.Nullable private static Boolean devView;

    public static void devSetDroneView(boolean v) { devView = v; }

    private TechButton hologramButton;
    private TechButton powerButton;
    private TechButton viewButton;
    private TechButton modeButton;
    private TechButton roundButton;
    private TechButton difficultyButton;

    public GravityChamberScreen(GravityChamberMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.droneView = menu.droneView();
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
        viewButton = new TechButton(leftPos + VIEW_X, topPos + VIEW_Y, VIEW_W, VIEW_H,
                Component.empty(), TechButton.Kind.SECONDARY, () -> {
                    droneView = !droneView;
                    press(droneView ? GravityChamberMenu.BTN_VIEW_DRONES : GravityChamberMenu.BTN_VIEW_GRAVITY);
                    refresh();
                });
        addRenderableWidget(viewButton);
        modeButton = new TechButton(leftPos + INFO_X, topPos + DR_BTN_Y, DR_BTN_W, DR_BTN_H,
                Component.empty(), TechButton.Kind.SECONDARY, () -> press(GravityChamberMenu.BTN_DRONE_MODE));
        addRenderableWidget(modeButton);
        roundButton = new TechButton(leftPos + INFO_RIGHT - DR_BTN_W, topPos + DR_BTN_Y, DR_BTN_W, DR_BTN_H,
                Component.empty(), TechButton.Kind.PRIMARY, () -> press(GravityChamberMenu.BTN_ROUND));
        addRenderableWidget(roundButton);
        difficultyButton = new TechButton(leftPos + INFO_X, topPos + DR_DIFF_Y, INFO_RIGHT - INFO_X, DR_BTN_H,
                Component.empty(), TechButton.Kind.SECONDARY, () -> press(GravityChamberMenu.BTN_DIFFICULTY));
        addRenderableWidget(difficultyButton);
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
        if (devView != null) {
            droneView = devView;
            devView = null;
        }
        menu.setDroneView(droneView);
        viewButton.setMessage(Component.translatable(droneView
                ? "screen.zenkai.gravity_chamber.view_gravity" : "screen.zenkai.gravity_chamber.view_drones"));
        modeButton.visible = droneView;
        roundButton.visible = droneView;
        modeButton.active = !menu.roundRunning();
        difficultyButton.visible = droneView;
        difficultyButton.active = !menu.roundRunning();
        difficultyButton.setMessage(Component.translatable("screen.zenkai.gravity_chamber.difficulty",
                Component.translatable(menu.difficulty().langKey())));
        modeButton.setMessage(Component.translatable(menu.aggressiveMode()
                ? "screen.zenkai.gravity_chamber.mode_aggressive" : "screen.zenkai.gravity_chamber.mode_passive"));
        roundButton.setMessage(Component.translatable(menu.roundRunning()
                ? "screen.zenkai.gravity_chamber.round_cancel" : "screen.zenkai.gravity_chamber.round_start"));
        hologramButton.setMessage(Component.translatable(menu.hologram()
                ? "screen.zenkai.gravity_chamber.hologram_on" : "screen.zenkai.gravity_chamber.hologram_off"));
        powerButton.setMessage(Component.translatable(menu.enabled()
                ? "screen.zenkai.gravity_chamber.power_on" : "screen.zenkai.gravity_chamber.power_off"));
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        if (!droneView) return;
        for (int i = GravityChamberMenu.DRONE_FIRST; i < GravityChamberMenu.DRONE_END; i++) {
            var slot = menu.slots.get(i);
            g.blit(BG, leftPos + slot.x - 1, topPos + slot.y - 1, WELL_U, WELL_V, 18, 18, PANEL_W, PANEL_H);
        }
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

        if (droneView) { renderDronePanel(g); return; }

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
        y += INFO_GAP_1;
        y = infoRow(g, y, "screen.zenkai.gravity_chamber.volume",
                Component.literal(ZenkaiNumbers.screen((long) menu.interior())), ZenkaiTechPalette.TEXT_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.gravity_chamber.leaks",
                Component.literal(ZenkaiNumbers.screen((long) menu.leaks())),
                menu.leaks() > 0 ? ZenkaiTechPalette.DENIED_ON_SCREEN : ZenkaiTechPalette.OK_ON_SCREEN);
        y = infoRow(g, y, "screen.zenkai.gravity_chamber.ports",
                Component.literal(ZenkaiNumbers.screen((long) menu.ports())), ZenkaiTechPalette.TEXT_ON_SCREEN);
        y += INFO_GAP_2;
        fittedOnDark(g, statusLine(), INFO_X, y, INFO_W, statusColor());
    }

    /** Vista Drones: cabecera con cuántos caben, ranuras (las pinta vanilla), botones y una línea de estado. */
    private void renderDronePanel(GuiGraphics g) {
        int usable = menu.usableDrones();
        int max = menu.maxDrones();
        Component head = Component.translatable("screen.zenkai.gravity_chamber.drones_head",
                ZenkaiNumbers.screen((long) Math.min(usable, max)), ZenkaiNumbers.screen((long) max));
        fittedOnDark(g, head, INFO_X, DR_HEAD_Y, INFO_W, ZenkaiTechPalette.DIM_ON_SCREEN);

        int y = DR_LINE_Y;
        if (menu.roundRunning()) {
            infoRow(g, y, "screen.zenkai.gravity_chamber.round_time",
                    Component.literal(ZenkaiNumbers.screen((long) menu.roundSecondsLeft()) + " s"),
                    menu.roundSecondsLeft() <= 5 ? ZenkaiTechPalette.DENIED_ON_SCREEN : ZenkaiTechPalette.TEXT_ON_SCREEN);
            y += INFO_LH;
            infoRow(g, y, menu.roundAggressive() ? "screen.zenkai.gravity_chamber.round_destroyed"
                            : "screen.zenkai.gravity_chamber.round_hit",
                    Component.literal(ZenkaiNumbers.screen((long) menu.roundDone()) + " / "
                            + ZenkaiNumbers.screen((long) menu.roundTotal())), ZenkaiTechPalette.OK_ON_SCREEN);
            return;
        }
        // Sin ronda: TP base que pagaría (antes de fatiga, pesas y gravedad) y el resultado de la última.
        int n = Math.min(usable, max);
        int gEff = Math.max(1, menu.maxGravity() <= 1 ? 1 : Math.min(menu.targetGravity(), menu.maxGravity()));
        double raw = GravityDroneRound.rawTp(gEff, n, menu.aggressiveMode(), menu.difficulty());
        infoRow(g, y, "screen.zenkai.gravity_chamber.round_base_tp",
                Component.literal(raw > 0 ? ZenkaiNumbers.screen((long) Math.floor(raw)) : "—"),
                raw > 0 ? ZenkaiTechPalette.TEXT_ON_SCREEN : ZenkaiTechPalette.DIM_ON_SCREEN);
        y += INFO_LH;
        // Última ronda como fila etiqueta + valor: la frase entera («Última ronda: derribado, +12 345 TP») no cabía.
        int result = menu.lastResult();
        if (result == GravityDroneRound.RESULT_NONE) {
            fittedOnDark(g, Component.translatable("screen.zenkai.gravity_chamber.last_none"), INFO_X, y, INFO_W,
                    ZenkaiTechPalette.DIM_ON_SCREEN);
            return;
        }
        String label = result == GravityDroneRound.RESULT_DEFEATED
                ? "screen.zenkai.gravity_chamber.last_label_ko" : "screen.zenkai.gravity_chamber.last_label";
        Component value = switch (result) {
            case GravityDroneRound.RESULT_SUCCESS, GravityDroneRound.RESULT_DEFEATED -> Component.translatable(
                    "screen.zenkai.gravity_chamber.last_tp", ZenkaiNumbers.screen((long) menu.lastTp()));
            case GravityDroneRound.RESULT_TIMEOUT -> Component.translatable("screen.zenkai.gravity_chamber.last_timeout");
            default -> Component.translatable("screen.zenkai.gravity_chamber.last_cancelled");
        };
        int color = switch (result) {
            case GravityDroneRound.RESULT_SUCCESS -> ZenkaiTechPalette.OK_ON_SCREEN;
            case GravityDroneRound.RESULT_DEFEATED -> ZenkaiTechPalette.MAXED_ON_SCREEN;
            default -> ZenkaiTechPalette.DENIED_ON_SCREEN;
        };
        infoRow(g, y, label, value, color);
    }

    /**
     * Texto de una línea que NUNCA se sale de su hueco: si no cabe se encoge (centrado en su línea) en vez de
     * pisar el borde del panel. Red de seguridad para traducciones largas; lo normal es que quepa a tamaño real.
     */
    private void fittedOnDark(GuiGraphics g, Component text, int x, int y, int maxW, int color) {
        int w = font.width(text);
        if (w <= maxW) { PanelText.onDark(g, font, text, x, y, color); return; }
        float sc = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1f - sc) * font.lineHeight / 2f, 0);
        g.pose().scale(sc, sc, 1f);
        PanelText.onDark(g, font, text, 0, 0, color);
        g.pose().popPose();
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
        int valueW = font.width(value);
        // La etiqueta cede ante el valor (la cifra es lo que importa): se encoge si no caben las dos con 4 px de aire.
        fittedOnDark(g, Component.translatable(key), INFO_X, y, Math.max(20, INFO_W - valueW - 4), ZenkaiTechPalette.DIM_ON_SCREEN);
        PanelText.rightOnDark(g, font, value, INFO_RIGHT, y, valueColor);
        return y + INFO_LH;
    }

    /** Tooltips de la vista Drones (ranura vacía, modo y botón de ronda). @return true si dibujó uno */
    private boolean droneTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int idx = menu.slots.indexOf(hoveredSlot);
            if (idx >= GravityChamberMenu.DRONE_FIRST && idx < GravityChamberMenu.DRONE_END) {
                drawTooltip(g, mouseX, mouseY, List.of(
                        TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.drone_slot").withStyle(ChatFormatting.BOLD)),
                        TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.drone_slot.desc",
                                ZenkaiNumbers.screen((long) menu.maxDrones())).withStyle(ChatFormatting.GRAY))));
                return true;
            }
        }
        if (modeButton != null && modeButton.visible && modeButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable(menu.aggressiveMode()
                            ? "screen.zenkai.gravity_chamber.mode_aggressive.tip" : "screen.zenkai.gravity_chamber.mode_passive.tip")
                            .withStyle(ChatFormatting.GRAY))));
            return true;
        }
        if (difficultyButton != null && difficultyButton.visible && difficultyButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, difficultyTip(menu.difficulty(), menu.aggressiveMode()));
            return true;
        }
        if (roundButton != null && roundButton.visible && roundButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.round.tip1").withStyle(ChatFormatting.GRAY)),
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.round.tip2").withStyle(ChatFormatting.AQUA))));
            return true;
        }
        return false;
    }

    /** Lo que cambia la dificultad elegida en el modo elegido (cifras de DroneDifficulty, por ZenkaiNumbers). */
    private static List<TipLine> difficultyTip(com.hmc.zenkai.feature.gravity.DroneDifficulty d, boolean aggressive) {
        List<TipLine> out = new java.util.ArrayList<>();
        out.add(TipLine.of(Component.translatable(d.langKey()).withStyle(ChatFormatting.BOLD)));
        out.add(TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.difficulty.tp",
                ZenkaiNumbers.fmt2(d.tpMult)).withStyle(ChatFormatting.AQUA)));
        if (aggressive) {
            out.add(TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.difficulty.damage",
                    ZenkaiNumbers.fmt2(d.shotBodyPercent)).withStyle(ChatFormatting.GRAY)));
            out.add(TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.difficulty.fire_rate",
                    ZenkaiNumbers.fmt2(d.shotIntervalTicks / 20.0)).withStyle(ChatFormatting.GRAY)));
            out.add(TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.difficulty.hits",
                    ZenkaiNumbers.screen((long) d.hitsToBreak)).withStyle(ChatFormatting.GRAY)));
            out.add(TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.difficulty.time_cap",
                    ZenkaiNumbers.screen((long) (d.aggressiveMaxTicks / 20))).withStyle(ChatFormatting.GRAY)));
        } else {
            out.add(TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.difficulty.time",
                    ZenkaiNumbers.screen((long) (d.passiveBaseTicks / 20)),
                    ZenkaiNumbers.fmt2(d.passivePerDroneTicks / 20.0)).withStyle(ChatFormatting.GRAY)));
        }
        double dodge = d.dodgeChance(aggressive);
        out.add(TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.difficulty.dodge",
                ZenkaiNumbers.screen(Math.round(dodge * 100))).withStyle(dodge > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY)));
        out.add(TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.difficulty.click").withStyle(ChatFormatting.DARK_GRAY)));
        return out;
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
        if (droneView && droneTooltips(g, mouseX, mouseY)) return;
        // Convención de ZenkaiNumbers: toneladas compactadas (filas 2 y 3 de la info) -> exacto en tooltip.
        if (!droneView && lmx >= INFO_X && lmx < INFO_RIGHT && lmy >= INFO_Y + INFO_LH && lmy < INFO_Y + 3 * INFO_LH) {
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
        if (viewButton != null && viewButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.view.tip").withStyle(ChatFormatting.GRAY))));
            return;
        }
        if (powerButton != null && powerButton.isMouseOver(mouseX, mouseY)) {
            drawTooltip(g, mouseX, mouseY, List.of(
                    TipLine.of(Component.translatable("screen.zenkai.gravity_chamber.power.tip").withStyle(ChatFormatting.GRAY))));
        }
    }
}
