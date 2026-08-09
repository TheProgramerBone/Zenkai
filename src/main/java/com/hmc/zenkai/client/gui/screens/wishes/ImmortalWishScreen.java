package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.screens.ZenkaiPanelScreen;
import com.hmc.zenkai.feature.wishes.WishImmortalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Deseo de inmortalidad. Confirmación pura: no hay nada que elegir.
 *
 * Además del panel, esta pantalla gana lo que le faltaba de verdad: decir QUÉ hace el deseo.
 * La versión anterior mostraba una sola línea que además decía lo contrario de lo que quería
 * decir ("podrás morir si recibes más daño del que puedes aguantar"). Ahora son dos bloques
 * separados — lo que concede y lo que NO — porque un deseo irreversible que el jugador
 * malinterpreta es peor que uno que no entiende.
 */
public class ImmortalWishScreen extends ZenkaiPanelScreen {

    public ImmortalWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.immortal"), parent);
    }

    @Override protected int titleColor() { return ZenkaiPalette.SHENLONG; }

    @Override
    protected void initContent() { /* solo el pie de página estándar */ }

    @Override
    protected void onConfirm() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new WishImmortalPayload());
        onClose();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y = panelTop + CONTENT_TOP + 24;

        y = drawWrappedOnPanel(g, Component.translatable("screen.zenkai.wish.immortal.desc"),
                y, ZenkaiPalette.LABEL_ON_PANEL);

        y += 10;
        drawDivider(g, y);
        y += 10;

        // El límite del deseo, aparte y en rojo. Es la parte que el jugador recordará mal si se
        // le cuenta en la misma frase que el beneficio.
        drawWrappedOnPanel(g, Component.translatable("screen.zenkai.wish.immortal.warning"),
                y, ZenkaiPalette.DENIED);
    }
}