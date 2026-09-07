package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.StatBar;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.training.TrainingFatigueRequestPacket;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ModDimensions;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Hub de la pestaña Training: 3 filas de minijuego ("Train with your shadow" / "Meditation" /
 * "Ki Target Practice", ver sus respectivas *TrainingScreen/MeditationScreen/
 * TargetPracticeScreen) + el panel "TP Modifiers", que es la sección "Carga" que ANTES vivía en
 * el popup de StatsScreen (buildStatRows) — se migró aquí entera porque el popup de Stats ya iba
 * apretado de espacio y porque este es el sitio temático correcto para mostrar TODO lo que
 * afecta la ganancia de TP, pesas Y HTC juntos (HTC no se mostraba en ningún lado antes).
 *
 * Mismo idioma visual "hub de filas grandes" que {@link AppearanceScreen} — ícono a la
 * izquierda, etiqueta a la derecha, y cada fila abre una Screen de verdad en vez de cambiar un
 * enum-Mode interno (ver el javadoc de AppearanceScreen para por qué se prefirió eso).
 *
 * La carga de pesas ya viaja en PlayerStatsAttachment (sincronizado por SyncPlayerStatsPacket) y
 * la dimensión HTC es un dato puramente local del cliente (siempre sabe en qué dimensión está su
 * propio jugador) — ver el comentario de ServerConfig.trainingHtcMultiplier() para el
 * multiplicador, ya sincronizado automáticamente por ser Type.SERVER. La fatiga
 * (TrainingData.fatigue) SÍ necesita un packet propio (no vive en ningún attachment ya
 * sincronizado) — ver TrainingFatigueRequestPacket/onFatigueReceived, pedido explícito del
 * usuario tras quedar fuera a propósito en la primera versión de este panel.
 */
public class TrainingHubScreen extends ZenkaiMenuScreen {

    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICONS_ATLAS = 256;
    private static final int ICON_CELL = 20;
    // Íconos propios (tools/gen_training_hub_icons.py, fila v=120) — antes estas 3 filas
    // reusaban celdas de otro concepto visual (ki-charge/turbo/kaioken del HUD de estado) como
    // placeholder, y Shadow/Meditation acababan compartiendo literalmente el mismo glifo.
    private static final int ICON_SHADOW_U = 140, ICON_SHADOW_V = 80;
    private static final int ICON_MEDITATION_U = 160, ICON_MEDITATION_V = 80;
    private static final int ICON_TARGET_PRACTICE_U = 180, ICON_TARGET_PRACTICE_V = 80;

    private static final int IN_X1 = 10;
    private static final int IN_X2 = 245;
    private static final int HUB_ROW_H = 32;
    private static final int HUB_GAP = 6;
    private static final int ROW1_Y = CONTENT_TOP + 8;
    private static final int ROW2_Y = ROW1_Y + HUB_ROW_H + HUB_GAP;
    private static final int ROW3_Y = ROW2_Y + HUB_ROW_H + HUB_GAP;

    private static final int PANEL_Y = ROW3_Y + HUB_ROW_H + 10;
    private static final int PANEL_ROW_H = 12;

    /** null hasta que responde TrainingFatigueRequestPacket — ver onFatigueReceived(). 1.0 =
     *  sin penalización (fatiga en 0). */
    private Double fatigueEfficiency;

    public TrainingHubScreen() {
        super(Component.translatable(ZenkaiTab.TRAINING.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.TRAINING; }

    @Override
    protected void initContent() {
        // Sin widgets propios: filas y panel se dibujan/hit-testean a mano.
        fatigueEfficiency = null;
        PacketDistributor.sendToServer(new TrainingFatigueRequestPacket());
    }

    /** Respuesta a TrainingFatigueRequestPacket (ver ClientPayloadHandlers.onTrainingFatigue) —
     *  empujada aquí igual que TrainingMinigameScreen.onRewardReceived/onTrainingInfoReceived,
     *  pero este hub no implementa esa interfaz (no es un minijuego), así que es un método
     *  público normal en vez de una sobrescritura. */
    public void onFatigueReceived(double efficiency) {
        fatigueEfficiency = efficiency;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);
        if (att == null || mc.player == null) return;

        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;

        renderHubOption(g, x, panelTop + ROW1_Y, w, HUB_ROW_H, ICON_SHADOW_U, ICON_SHADOW_V,
                Component.translatable("screen.zenkai.training_hub.row.shadow"), mouseX, mouseY,
                Component.translatable("screen.zenkai.training_hub.row.shadow.tooltip"));
        renderHubOption(g, x, panelTop + ROW2_Y, w, HUB_ROW_H, ICON_MEDITATION_U, ICON_MEDITATION_V,
                Component.translatable("screen.zenkai.training_hub.row.meditation"), mouseX, mouseY, null);
        renderHubOption(g, x, panelTop + ROW3_Y, w, HUB_ROW_H,
                ICON_TARGET_PRACTICE_U, ICON_TARGET_PRACTICE_V,
                Component.translatable("screen.zenkai.training_hub.row.target_practice"), mouseX, mouseY, null);

        renderModifiersPanel(g, x, panelTop + PANEL_Y, w);
    }

    /** Botón grande horizontal (ícono izq + etiqueta der), mismo idioma que
     *  AppearanceScreen. `tooltip` opcional: el pedido explícito de "Train
     *  with your shadow" es enseñar un resumen ANTES de entrar (qué es, para qué sirve). */
    private void renderHubOption(GuiGraphics g, int x, int y, int w, int h, int iconU, int iconV,
                                  Component label, int mouseX, int mouseY, Component tooltip) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        g.fill(x, y, x + w, y + h, hovered ? ZenkaiPalette.ROW_HOVER : ZenkaiPalette.INSET_BG);
        g.fill(x, y, x + w, y + 1, ZenkaiPalette.BORDER_IN);
        g.fill(x, y + h - 1, x + w, y + h, ZenkaiPalette.BORDER_IN);
        g.fill(x, y, x + 1, y + h, ZenkaiPalette.BORDER_IN);
        g.fill(x + w - 1, y, x + w, y + h, ZenkaiPalette.BORDER_IN);

        int iconX = x + 10;
        int iconY = y + (h - ICON_CELL) / 2;
        g.blit(ICONS_TEX, iconX, iconY, iconU, iconV, ICON_CELL, ICON_CELL, ICONS_ATLAS, ICONS_ATLAS);

        PanelText.onPanel(g, this.font, label, iconX + ICON_CELL + 8, y + (h - 8) / 2,
                ZenkaiPalette.LABEL_ON_PANEL);

        if (tooltip != null && hovered) {
            g.renderTooltip(this.font, this.font.split(tooltip, 200), mouseX, mouseY);
        }
    }

    /** Margen derecho fijo para todo texto alineado a la derecha de este panel — pegado a `x+w`
     *  a secas quedaba a ras del borde/las esquinas del panel (feedback de imagen: "ajusta el
     *  margen de la letra"). */
    private static final int PANEL_RIGHT_MARGIN = 3;
    /** Alto de una línea de texto normal — usado para separar la barra de carga de su propio
     *  porcentaje SIN que se pisen (antes ambos se dibujaban en la misma Y, ver el comentario de
     *  imagen "evita solapamientos de texto"). */
    private static final int TEXT_LINE_H = 9;

    /**
     * Panel "TP Modifiers" — antigua sección "Carga" de StatsScreen, ampliada con HTC y fatiga.
     * Filas fijas: toneladas/capacidad + barra + % (cada una en su propia línea, ver
     * TEXT_LINE_H), multiplicador de pesas, fatiga de entrenamiento SI hay alguna penalización
     * real, multiplicador de HTC si aplica, y el efectivo combinado de los tres.
     */
    private void renderModifiersPanel(GuiGraphics g, int x, int y, int w) {
        var player = mc.player;
        assert player != null;

        double load = att.getWeightLoad();
        boolean inHtc = player.level().dimension() == ModDimensions.HTC_LEVEL;
        double weightMult = WeightSystem.tpFactor(load);
        double htcMult = ServerConfig.trainingHtcMultiplier();
        // fatigueEfficiency null (aún no respondió el servidor) se trata como "sin penalización"
        // para el cálculo, pero la FILA de fatiga solo se dibuja cuando el dato ya llegó Y hay
        // penalización real — ver más abajo.
        double fatigueMult = fatigueEfficiency != null ? fatigueEfficiency : 1.0;
        boolean showFatigueRow = fatigueEfficiency != null && fatigueEfficiency < 0.999;
        double effectiveMult = weightMult * (inHtc ? htcMult : 1.0) * fatigueMult;

        int ty = y;
        PanelText.onPanel(g, this.font,
                Component.translatable("screen.zenkai.training_hub.panel.title")
                        .copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                x, ty, ZenkaiPalette.LABEL_ON_PANEL);
        ty += PANEL_ROW_H + 2;

        if (load > 0.0) {
            panelRow(g, x, ty, w,
                    Component.translatable("screen.zenkai.stats_screen.stat.load_short.label"),
                    Component.literal(String.format(Locale.ROOT, "%.2f / %.2f t",
                            WeightSystem.equippedTons(player),
                            WeightSystem.capacityTons(att.getPowerLevelRaw()))));
            ty += PANEL_ROW_H;

            // Porcentaje en SU PROPIA línea, ANTES de la barra — ya no comparte Y con ella (esa
            // era la superposición del feedback de imagen).
            PanelText.rightOnPanel(g, this.font,
                    Component.literal(Math.round(load * 100) + "%"),
                    x + w - PANEL_RIGHT_MARGIN, ty, ZenkaiPalette.VALUE_ON_PANEL);
            ty += TEXT_LINE_H;

            // Barra SOBRE EL BEIGE del panel: StatBar.draw(), no drawOnDark() — esa segunda
            // usa un canal negro translúcido pensado para un popup oscuro, que sobre el beige
            // pintaba un parche oscuro inesperado (ver feedback de imagen).
            StatBar.draw(g, x, ty, w, StatBar.H_THIN,
                    (float) Math.min(100.0, load * 100), 100.0, ZenkaiPalette.BAR_CONTROL);
            ty += StatBar.H_THIN + 5;

            panelRow(g, x, ty, w,
                    Component.translatable("screen.zenkai.stats_screen.stat.weight_tp.label"),
                    Component.literal("x" + ZenkaiNumbers.fmt2(weightMult)));
            ty += PANEL_ROW_H;
        } else {
            PanelText.onPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.panel.no_weights"),
                    x, ty, ZenkaiPalette.MUTED_ON_PANEL);
            ty += PANEL_ROW_H;
        }

        if (showFatigueRow) {
            panelRow(g, x, ty, w,
                    Component.translatable("screen.zenkai.training_hub.panel.fatigue_mult"),
                    Component.literal("x" + ZenkaiNumbers.fmt2(fatigueMult)));
            ty += PANEL_ROW_H;
        }

        if (inHtc) {
            panelRow(g, x, ty, w,
                    Component.translatable("screen.zenkai.training_hub.panel.htc_mult"),
                    Component.literal("x" + ZenkaiNumbers.fmt2(htcMult)));
            ty += PANEL_ROW_H;
        }

        if (load > 0.0 || inHtc || showFatigueRow) {
            panelRow(g, x, ty, w,
                    Component.translatable("screen.zenkai.training_hub.panel.effective_mult"),
                    Component.literal("x" + ZenkaiNumbers.fmt2(effectiveMult)));
        }
    }

    private void panelRow(GuiGraphics g, int x, int y, int w, Component label, Component value) {
        PanelText.onPanel(g, this.font, label, x, y, ZenkaiPalette.MUTED_ON_PANEL);
        PanelText.rightOnPanel(g, this.font, value, x + w - PANEL_RIGHT_MARGIN, y, ZenkaiPalette.OK_ON_PANEL);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clickHub(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickHub(double mouseX, double mouseY) {
        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;
        if (mouseX < x || mouseX >= x + w) return false;

        if (mouseY >= panelTop + ROW1_Y && mouseY < panelTop + ROW1_Y + HUB_ROW_H) {
            mc.setScreen(new ShadowTrainingScreen());
            return true;
        }
        if (mouseY >= panelTop + ROW2_Y && mouseY < panelTop + ROW2_Y + HUB_ROW_H) {
            mc.setScreen(new MeditationScreen());
            return true;
        }
        if (mouseY >= panelTop + ROW3_Y && mouseY < panelTop + ROW3_Y + HUB_ROW_H) {
            mc.setScreen(new TargetPracticeScreen());
            return true;
        }
        return false;
    }
}
