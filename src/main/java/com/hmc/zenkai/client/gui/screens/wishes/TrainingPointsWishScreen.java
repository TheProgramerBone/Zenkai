package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.screens.ZenkaiPanelScreen;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.wishes.WishTrainingPointsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Deseo de puntos de entrenamiento.
 * Era la pantalla más pobre del lote: fondo vanilla, un título arriba y dos botones
 * grises, sin UNA sola línea de texto. El jugador pedía "ser más poderoso" y no sabía qué iba a
 * recibir ni cuánto tenía ya.
 * Ahora muestra el TP actual. La CANTIDAD que concede el deseo vive en ServerConfig y no está
 * sincronizada al cliente, así que aquí no se inventa un número: se describe el efecto y el
 * servidor anuncia la cifra concedida por chat (messages.zenkai.wish_desc.training_points ya lo
 * hace). Poner un valor de config en pantalla sin sincronizarlo mentiría en cualquier servidor
 * que lo hubiera cambiado.
 */
public class TrainingPointsWishScreen extends ZenkaiPanelScreen {

    public TrainingPointsWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.training_points"), parent);
    }

    @Override protected int titleColor() { return ZenkaiPalette.SHENLONG; }

    @Override
    protected void initContent() { /* solo el pie de página estándar */ }

    @Override
    protected void onConfirm() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new WishTrainingPointsPayload());
        onClose();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y = panelTop + CONTENT_TOP + 24;

        y = drawWrappedOnPanel(g, Component.translatable("screen.zenkai.wish.training_points.desc"),
                y, ZenkaiPalette.LABEL_ON_PANEL);

        y += 12;
        drawDivider(g, y);
        y += 10;

        if (mc.player != null) {
            PlayerStatsAttachment st = PlayerStatsAttachment.get(mc.player);
            drawCenteredOnPanel(g,
                    Component.translatable("screen.zenkai.wish.training_points.current", st.getTP()),
                    y, ZenkaiPalette.BODY_ON_PANEL);
        }
    }
}