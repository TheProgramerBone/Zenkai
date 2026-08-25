package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.content.item.WeightArmorItem;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.SetWeightPacket;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Ajuste del peso de una pesa de entrenamiento, sobre common_screen.
 * ORDEN DE RENDER (convención del mod, ver TechniqueEditScreen):
 *   renderBackground() -> dim + blit del panel.
 *   render() -> super.render() PRIMERO (fondo + widgets) y el texto DESPUÉS.
 * Al revés, los widgets se pintan encima del texto y el título sale lavado por el dim.
 * Los botones de acción van FUERA del panel (Y_BUTTONS = BG_H + 4), como el resto de
 * pantallas: dentro chocarían con el bloque de previsualización.
 * Pantalla de cliente pura + un packet al aceptar: no hay inventario que sincronizar, así
 * que un AbstractContainerMenu sería el doble de código para lo mismo.
 * Ninguna cifra se calcula aquí: cada una sale de WeightSystem, el mismo sitio que las aplica
 * en el juego. Así la previsualización no puede mentir.
 */

public class WeightScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int MARGIN = 16;
    private static final int Y_LABEL  = ScreenTitle.CONTENT_TOP;
    private static final int Y_FIELD  = Y_LABEL + 14;
    private static final int Y_INFO   = Y_FIELD + 36;
    private static final int LINE_H   = 13;
    private static final int Y_WARN   = Y_INFO + LINE_H * 6 + 6;

    private static final int FIELD_W  = 90;
    private static final int FIELD_H  = 16;
    private static final int ARROW    = 12;

    private static final int BTN_W = 60;
    private static final int BTN_H = 25;
    private static final int Y_BUTTONS = BG_H + 4; // FUERA del panel

    private final Minecraft mc = Minecraft.getInstance();
    private final InteractionHand hand;
    private final WeightArmorItem item;
    private double tons;

    private EditBox field;
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

        this.field = new EditBox(this.font, cx - FIELD_W / 2, topPos + Y_FIELD,
                FIELD_W, FIELD_H, Component.translatable("screen.zenkai.weight.field"));
        this.field.setMaxLength(12);
        this.field.setValue(fmt(tons));
        this.field.setResponder(this::onTyped);
        addRenderableWidget(this.field);

        addRenderableWidget(new ArrowIconButton(cx - FIELD_W / 2 - ARROW - 6,
                topPos + Y_FIELD + 2, ArrowIconButton.Dir.LEFT,
                () -> nudge(-WeightArmorItem.STEP_TONS)));
        addRenderableWidget(new ArrowIconButton(cx + FIELD_W / 2 + 6,
                topPos + Y_FIELD + 2, ArrowIconButton.Dir.RIGHT,
                () -> nudge(WeightArmorItem.STEP_TONS)));

        addRenderableWidget(new TextOnlyButton(
                leftPos + MARGIN, topPos + Y_BUTTONS, BTN_W, BTN_H,
                Component.translatable("screen.zenkai.gui.back"),
                TEX_BTN, null, this::onClose));

        addRenderableWidget(new TextOnlyButton(
                leftPos + BG_W - MARGIN - BTN_W, topPos + Y_BUTTONS, BTN_W, BTN_H,
                Component.translatable("screen.zenkai.gui.confirm"),
                TEX_BTN, null, this::confirm));
    }

    private void onTyped(String raw) {
        try {
            tons = item.clampTons(Double.parseDouble(raw.replace(',', '.')));
        } catch (NumberFormatException ignored) {
            // Campo a medio escribir: se conserva el último valor válido.
        }
    }

    private void nudge(double delta) {
        tons = item.clampTons(tons + delta);
        this.field.setValue(fmt(tons)); // dispara el responder, que revalida
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

        double cap = WeightSystem.capacityTons(att.getPowerLevelRaw());
        double r = cap <= 0 ? 0 : tons / cap;
        boolean over = WeightSystem.isOverloaded(r);

        int x = leftPos + MARGIN + 8;
        int y = topPos + Y_INFO;

        line(g, x, y, "screen.zenkai.weight.load",
                over ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.LABEL_ON_PANEL,
                fmt(tons), fmt(cap), (int) Math.round(r * 100));
        y += LINE_H;
        line(g, x, y, "screen.zenkai.weight.speed", ZenkaiPalette.BODY_ON_PANEL,
                pct(WeightSystem.moveFactor(r)));
        y += LINE_H;
        line(g, x, y, "screen.zenkai.weight.jump", ZenkaiPalette.BODY_ON_PANEL,
                pct(WeightSystem.jumpFactor(r)));
        y += LINE_H;
        line(g, x, y, "screen.zenkai.weight.stats", ZenkaiPalette.BODY_ON_PANEL,
                pct(WeightSystem.statFactor(r)));
        y += LINE_H;
        // El bono de TP es la recompensa de llevar lastre: verde cuando cuenta, rojo cuando te
        // has pasado y deja de contar.
        line(g, x, y, "screen.zenkai.weight.tp",
                over ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.OK_ON_PANEL,
                fmt(WeightSystem.tpFactor(r)));
        y += LINE_H;
        line(g, x, y, "screen.zenkai.weight.pl_needed", ZenkaiPalette.MUTED_ON_PANEL,
                ZenkaiNumbers.format(WeightSystem.plForTons(tons)));

        if (over) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.weight.overloaded"),
                    cx, topPos + Y_WARN, ZenkaiPalette.DENIED_ON_PANEL);
        }
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