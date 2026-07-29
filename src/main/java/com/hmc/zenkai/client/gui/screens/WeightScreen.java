package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.content.item.WeightArmorItem;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.weights.SetWeightPacket;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Ajuste del peso de una pesa. Pantalla de cliente pura + un packet al aceptar: no hay
 * inventario que sincronizar, así que un AbstractContainerMenu sería el doble de código
 * para el mismo resultado.
 *
 * Las cifras que muestra NO se calculan aquí: salen de WeightSystem, el mismo sitio que
 * las aplica en el juego. Así la pantalla no puede mentir.
 */
public class WeightScreen extends Screen {

    private static final int PANEL_W = 190;
    private static final int PANEL_H = 150;

    private final InteractionHand hand;
    private final WeightArmorItem item;
    private double tons;

    private EditBox field;

    private WeightScreen(InteractionHand hand, WeightArmorItem item, double tons) {
        super(Component.translatable("screen.zenkai.weight.title"));
        this.hand = hand;
        this.item = item;
        this.tons = tons;
    }

    /** Punto de entrada desde WeightArmorItem.use(). Solo se llama en cliente. */
    public static void open(InteractionHand hand, WeightArmorItem item, double tons) {
        Minecraft.getInstance().setScreen(new WeightScreen(hand, item, tons));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = (this.height - PANEL_H) / 2;

        this.field = new EditBox(this.font, cx - 40, top + 28, 80, 18,
                Component.translatable("screen.zenkai.weight.field"));
        this.field.setMaxLength(12);
        this.field.setValue(fmt(tons));
        this.field.setResponder(this::onTyped);
        this.addRenderableWidget(this.field);

        this.addRenderableWidget(Button.builder(Component.literal("-"),
                b -> nudge(-WeightArmorItem.STEP_TONS)).bounds(cx - 68, top + 28, 22, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"),
                b -> nudge(WeightArmorItem.STEP_TONS)).bounds(cx + 46, top + 28, 22, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("screen.zenkai.gui.confirm"),
                b -> confirm()).bounds(cx - 60, top + PANEL_H - 46, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.zenkai.gui.back"),
                b -> this.onClose()).bounds(cx - 60, top + PANEL_H - 24, 120, 20).build());
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
        this.field.setValue(fmt(tons));
    }

    private void confirm() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new SetWeightPacket(hand == InteractionHand.MAIN_HAND, tons));
        this.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);

        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;

        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE81E1410);
        g.fill(left, top, left + PANEL_W, top + 1, 0xFFFFAA33);
        g.fill(left, top + PANEL_H - 1, left + PANEL_W, top + PANEL_H, 0xFFFFAA33);
        g.fill(left, top, left + 1, top + PANEL_H, 0xFFFFAA33);
        g.fill(left + PANEL_W - 1, top, left + PANEL_W, top + PANEL_H, 0xFFFFAA33);

        g.drawCenteredString(this.font, this.title, cx, top + 8, 0xFFFFAA33);

        super.render(g, mx, my, pt);

        // Previsualización: capacidad con TU PL limpio actual y los factores reales.
        int y = top + 54;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);

        double cap = WeightSystem.capacityTons(att.getPowerLevelRaw());
        double r = cap <= 0 ? 0 : tons / cap;
        boolean over = WeightSystem.isOverloaded(r);

        int color = over ? 0xFFFF5566 : 0xFFFFFFFF;
        g.drawString(this.font, Component.translatable("screen.zenkai.weight.load",
                fmt(tons), fmt(cap), (int) Math.round(r * 100)), left + 10, y, color);
        y += 11;
        g.drawString(this.font, Component.translatable("screen.zenkai.weight.speed",
                pct(WeightSystem.moveFactor(r))), left + 10, y, 0xFFCCCCCC);
        y += 11;
        g.drawString(this.font, Component.translatable("screen.zenkai.weight.jump",
                pct(WeightSystem.jumpFactor(r))), left + 10, y, 0xFFCCCCCC);
        y += 11;
        g.drawString(this.font, Component.translatable("screen.zenkai.weight.stats",
                pct(WeightSystem.statFactor(r))), left + 10, y, 0xFFCCCCCC);
        y += 11;
        g.drawString(this.font, Component.translatable("screen.zenkai.weight.tp",
                        String.format(Locale.ROOT, "%.2f", WeightSystem.tpFactor(r))), left + 10, y,
                over ? 0xFFFF5566 : 0xFF66FF88);
        y += 11;
        g.drawString(this.font, Component.translatable("screen.zenkai.weight.pl_needed",
                ZenkaiNumbers.format(WeightSystem.plForTons(tons))), left + 10, y, 0xFF999999);

        if (over) {
            g.drawString(this.font, Component.translatable("screen.zenkai.weight.overloaded")
                    .withStyle(ChatFormatting.RED), left + 10, y + 11, 0xFFFF5566);
        }
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String pct(double factor) { return (int) Math.round(factor * 100) + "%"; }

    @Override public boolean isPauseScreen() { return false; }
}