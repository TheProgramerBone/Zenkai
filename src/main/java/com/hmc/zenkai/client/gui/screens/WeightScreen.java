package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.CommonPanel;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.NineSlice;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.AcceptIconButton;
import com.hmc.zenkai.client.gui.buttons.BackIconButton;
import com.hmc.zenkai.content.item.WeightArmorItem;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.SetWeightPacket;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Ajuste del peso de una pesa de entrenamiento, sobre common_screen.
 * ORDEN DE RENDER (convención del mod, ver TechniqueEditScreen):
 *   renderBackground() -> dim + blit del panel.
 *   render() -> super.render() PRIMERO (fondo + widgets) y el texto DESPUÉS.
 * Al revés, los widgets se pintan encima del texto y el título sale lavado por el dim.
 * Atrás/Aceptar son iconos de esquina DENTRO del panel (CommonPanel.iconLeft/iconRight), como en Training. La carga se ve en una barra con tres zonas
 * (cómodo / exigente / sobrecarga) en vez de un porcentaje suelto, y las cuatro cifras
 * derivadas en una rejilla de dos columnas.
 * Pantalla de cliente pura + un packet al aceptar: no hay inventario que sincronizar, así
 * que un AbstractContainerMenu sería el doble de código para lo mismo.
 * Ninguna cifra se calcula aquí: cada una sale de WeightSystem, el mismo sitio que las aplica
 * en el juego. Así la previsualización no puede mentir.
 */

public class WeightScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/screens/common_screen.png");

    private static final int BG_W = CommonPanel.IDEAL;
    private static final int BG_H = CommonPanel.IDEAL;
    private static final int MARGIN = 16;
    private static final int Y_LABEL  = ScreenTitle.CONTENT_TOP;
    private static final int Y_FIELD  = Y_LABEL + 14;
    private static final int Y_BAR    = Y_FIELD + 34;
    private static final int BAR_H    = 10;
    private static final int Y_LOAD   = Y_BAR + BAR_H + 5;
    private static final int Y_INFO   = Y_LOAD + 24;
    private static final int LINE_H   = 13;
    private static final int Y_PL     = Y_INFO + LINE_H * 2 + 12;
    private static final int Y_WARN   = Y_PL + LINE_H + 4;
    /** Fracción del umbral de sobrecarga hasta la que la carga se considera cómoda (zona verde de la barra). */
    private static final double COMFORT = 0.6;

    /** Placa del valor: interior de PLATE_W x PLATE_H (el marco queda 2 px por fuera). */
    private static final int PLATE_W  = 130;
    private static final int PLATE_H  = 24;
    private static final int ARROW    = 12;


    private final Minecraft mc = Minecraft.getInstance();
    private final InteractionHand hand;
    private final WeightArmorItem item;
    private double tons;

    /** Mientras se escribe, el valor es esta cadena (sin reformatear a cada tecla); si no, se enseña fmt(tons). */
    private boolean editing;
    private String buffer = "";
    private int leftPos, topPos;

    private WeightScreen(InteractionHand hand, WeightArmorItem item, double tons) {
        super(Component.translatable("screen.zenkai.weight.title"));
        this.hand = hand;
        this.item = item;
        this.tons = tons;
    }

    /** Punto de entrada desde WeightArmorItem. Use(). Solo se llama en cliente. */
    public static void open(InteractionHand hand, WeightArmorItem item, double tons) {
        Minecraft.getInstance().setScreen(new WeightScreen(hand, item, tons));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - BG_W) / 2;
        this.topPos  = (this.height - BG_H) / 2;

        int cx = leftPos + BG_W / 2;

        // El valor ya no es la caja de texto de vanilla: una placa con el icono de la prenda y la cifra en grande.
        // Se escribe pulsando sobre ella (charTyped/keyPressed), o con las flechas y la rueda.
        addRenderableWidget(new ArrowIconButton(plateX() - ARROW - 6, plateY() + (PLATE_H - ARROW) / 2,
                ArrowIconButton.Dir.LEFT, () -> nudge(-WeightArmorItem.STEP_TONS)));
        addRenderableWidget(new ArrowIconButton(plateX() + PLATE_W + 6, plateY() + (PLATE_H - ARROW) / 2,
                ArrowIconButton.Dir.RIGHT, () -> nudge(WeightArmorItem.STEP_TONS)));

        // Iconos de esquina (mismo sitio que en Training): Atrás abajo-izquierda, Aceptar abajo-derecha.
        BackIconButton back = new BackIconButton(CommonPanel.iconLeft(leftPos),
                CommonPanel.iconBottom(topPos, BG_H), CommonPanel.ICON, this::onClose);
        back.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.gui.back")));
        addRenderableWidget(back);
        AcceptIconButton accept = new AcceptIconButton(CommonPanel.iconRight(leftPos),
                CommonPanel.iconBottom(topPos, BG_H), CommonPanel.ICON, this::confirm);
        accept.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.gui.confirm")));
        addRenderableWidget(accept);
    }

    private int plateX() { return leftPos + BG_W / 2 - PLATE_W / 2; }
    private int plateY() { return topPos + Y_FIELD + 2; }

    private boolean overPlate(double mx, double my) {
        return mx >= plateX() && mx < plateX() + PLATE_W && my >= plateY() && my < plateY() + PLATE_H;
    }

    /** Re-lee el texto escrito; una cadena a medio escribir ("", ".") conserva el último valor válido. */
    private void onTyped() {
        try {
            tons = item.clampTons(Double.parseDouble(buffer.replace(',', '.')));
        } catch (NumberFormatException ignored) {
            // Campo a medio escribir: se conserva el último valor válido.
        }
    }

    private void nudge(double delta) {
        editing = false;
        tons = item.clampTons(tons + delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overPlate(mx, my)) {
            editing = true;
            buffer = "";
            return true;
        }
        editing = false;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (overPlate(mx, my) && dy != 0) {
            nudge(dy > 0 ? WeightArmorItem.STEP_TONS : -WeightArmorItem.STEP_TONS);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (editing && (Character.isDigit(c) || c == '.' || c == ',') && buffer.length() < 8) {
            buffer += c;
            onTyped();
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (editing) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                if (!buffer.isEmpty()) buffer = buffer.substring(0, buffer.length() - 1);
                onTyped();
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                editing = false; // Esc deja de escribir; otro Esc cierra la pantalla
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    private void confirm() {
        var conn = mc.getConnection();
        if (conn != null) conn.send(new SetWeightPacket(hand == InteractionHand.MAIN_HAND, tons));
        this.onClose();
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, leftPos, topPos, 0, 0, BG_W, BG_H);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int cx = leftPos + BG_W / 2;
        ScreenTitle.drawAbovePanel(g, this.font, this.title, cx, topPos);

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.weight.field"),
                cx, topPos + Y_LABEL, ZenkaiPalette.LABEL_ON_PANEL);

        if (mc.player == null) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);

        // Con la gravedad de donde estás: la pesa pesa tons × g y la gravedad ambiental se suma (MISMA
        // regla que WeightSystem.computeLoad, así la previsualización no miente). En gravedad normal = tons.
        double shownTons = tons * Math.max(1.0, WeightSystem.gravityMultiplier(mc.player))
                + WeightSystem.ambientTons(mc.player);
        double cap = WeightSystem.playerCapacity(att);
        double r = cap <= 0 ? 0 : shownTons / cap;
        boolean over = WeightSystem.isOverloaded(r);

        drawValuePlate(g, mouseX, mouseY, over);
        drawLoadBar(g, r);
        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.weight.load", fmt(shownTons), fmt(cap), (int) Math.round(r * 100)),
                cx, topPos + Y_LOAD, over ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.LABEL_ON_PANEL);

        // Rejilla 2x2 de lo que cambia al llevar esta carga.
        int xl = leftPos + CommonPanel.IN_X1 + 14, xr = cx + 6, y = topPos + Y_INFO;
        line(g, xl, y, "screen.zenkai.weight.speed", ZenkaiPalette.BODY_ON_PANEL, pct(WeightSystem.moveFactor(r)));
        line(g, xr, y, "screen.zenkai.weight.jump", ZenkaiPalette.BODY_ON_PANEL, pct(WeightSystem.jumpFactor(r)));
        y += LINE_H;
        line(g, xl, y, "screen.zenkai.weight.stats", ZenkaiPalette.BODY_ON_PANEL, pct(WeightSystem.statFactor(r)));
        // El bono de TP es la recompensa de llevar lastre: verde cuando cuenta, rojo cuando te
        // has pasado y deja de contar.
        line(g, xr, y, "screen.zenkai.weight.tp",
                over ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.OK_ON_PANEL,
                fmt(WeightSystem.tpFactor(r) * WeightSystem.gravityTpFactor(mc.player, r)));

        PanelText.centeredOnPanel(g, this.font, Component.translatable("screen.zenkai.weight.pl_needed",
                ZenkaiNumbers.format(WeightSystem.plForTons(shownTons))), cx, topPos + Y_PL, ZenkaiPalette.MUTED_ON_PANEL);

        if (over) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.weight.overloaded"),
                    cx, topPos + Y_WARN, ZenkaiPalette.DENIED_ON_PANEL);
        }
    }

    /** Placa oscura (la del skill tree) con el icono de la prenda a la izquierda y la cifra en grande. */
    private void drawValuePlate(GuiGraphics g, int mouseX, int mouseY, boolean over) {
        int x = plateX(), y = plateY();
        NineSlice.plateFrame(g, x, y, PLATE_W, PLATE_H);
        boolean hover = overPlate(mouseX, mouseY);
        if (hover || editing) g.fill(x, y, x + PLATE_W, y + PLATE_H, editing ? 0x33FFD966 : 0x22FFD966);
        // Las texturas de las pesas son casi negras: sobre la placa oscura no se verían, así que llevan un
        // recuadro beige detrás (el mismo tono del panel).
        int sx = x + 3, sy = y + (PLATE_H - 20) / 2;
        g.fill(sx, sy, sx + 20, sy + 20, ZenkaiPalette.BEIGE_DEEP);
        g.fill(sx + 1, sy + 1, sx + 19, sy + 19, ZenkaiPalette.BEIGE);
        g.renderItem(new ItemStack(item), sx + 2, sy + 2);
        String text = editing ? buffer + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : fmt(tons);
        int color = over ? ZenkaiPalette.DENIED : ZenkaiPalette.TP_ON_DARK;
        // Cifra a doble tamaño, centrada en el hueco entre el icono y la unidad.
        int areaL = x + 28, areaR = x + PLATE_W - 14;
        var pose = g.pose();
        pose.pushPose();
        pose.translate((areaL + areaR) / 2.0f - this.font.width(text), y + (PLATE_H - 16) / 2.0f + 1, 0);
        pose.scale(2f, 2f, 1f);
        g.drawString(this.font, text, 0, 0, color, true);
        pose.popPose();
        g.drawString(this.font, "t", x + PLATE_W - 10, y + PLATE_H - 12, ZenkaiPalette.TEXT_DIM, true);
        if (hover && !editing) {
            com.hmc.zenkai.client.gui.TooltipText.render(g, this.font, Component.translatable("screen.zenkai.weight.field.hint"), mouseX, mouseY);
        }
    }

    /**
     * Barra de carga con tres zonas: cómodo (verde), exigente (ámbar) y sobrecarga (rojo). El dominio llega a un 25 %
     * más allá del umbral de sobrecarga, o a la carga actual si lo pasa. Las zonas se pintan apagadas y lo relleno
     * hasta la carga actual, con un marcador, en el color pleno. Los colores son los de texto sobre beige.
     */
    private void drawLoadBar(GuiGraphics g, double r) {
        double thr = com.hmc.zenkai.config.ServerConfig.weightOverloadThreshold();
        double domain = Math.max(thr * 1.25, r);
        int x0 = leftPos + CommonPanel.IN_X1 + 14, x1 = leftPos + CommonPanel.IN_X2 - 14;
        int y0 = topPos + Y_BAR, y1 = y0 + BAR_H, w = x1 - x0;
        int comfortX = x0 + (int) Math.round(w * (thr * COMFORT) / domain);
        int overX = x0 + (int) Math.round(w * thr / domain);
        int[] edges = {x0, comfortX, overX, x1};
        int[] full = {ZenkaiPalette.OK_ON_PANEL, ZenkaiPalette.VALUE_ON_PANEL, ZenkaiPalette.DENIED_ON_PANEL};
        int fillX = x0 + (int) Math.round(w * Math.min(r, domain) / domain);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, ZenkaiPalette.BAR_FRAME);
        g.fill(x0, y0, x1, y1, ZenkaiPalette.BAR_BG); // fondo beige: las zonas apagadas llevan alfa y sobre el marco se teñían de rojo
        for (int z = 0; z < 3; z++) {
            g.fill(edges[z], y0, edges[z + 1], y1, ZenkaiPalette.withAlpha(full[z], 0x55));
            // Solo si la carga llega a esta zona: GuiGraphics.fill INVIERTE el rectángulo si el ancho es negativo,
            // y las zonas por delante de la carga se pintaban hacia atrás (visto en capturas, 2026-10-29).
            if (fillX > edges[z]) g.fill(edges[z], y0, Math.min(edges[z + 1], fillX), y1, full[z]);
        }
        int mx = Math.min(fillX, x1 - 1);
        g.fill(mx, y0 - 3, mx + 1, y1 + 3, ZenkaiPalette.LABEL_ON_PANEL);
    }

    /** Fila de la ficha. Va SIN sombra: esto se dibuja sobre el beige del panel. */
    private void line(GuiGraphics g, int x, int y, String key, int color, Object... args) {
        PanelText.onPanel(g, this.font, Component.translatable(key, args), x, y, color);
    }

    @Override
    public void onClose() {
        mc.setScreen(null);
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String pct(double factor) { return (int) Math.round(factor * 100) + "%"; }

    @Override public boolean isPauseScreen() { return false; }
}