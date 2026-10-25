package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.screens.ZenkaiPanelScreen;
import com.hmc.zenkai.feature.wishes.WishLegendaryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Deseo de ser Legendario (solo Saiyan). Confirmación pura, con la misma estructura que ImmortalWishScreen: lo que
 * concede y, aparte y en rojo, lo que cuesta, porque es irreversible.
 */
public class LegendaryWishScreen extends ZenkaiPanelScreen {

    public LegendaryWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.legendary"), parent);
    }

    @Override protected int titleColor() { return ZenkaiPalette.SHENLONG; }

    @Override
    protected void initContent() { /* solo el pie de página estándar */ }

    @Override
    protected void onConfirm() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new WishLegendaryPayload());
        onClose();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int y = panelTop + CONTENT_TOP + 24;
        y = drawWrappedOnPanel(g, Component.translatable("screen.zenkai.wish.legendary.desc"),
                y, ZenkaiPalette.LABEL_ON_PANEL);
        y += 10;
        drawDivider(g, y);
        y += 10;
        drawWrappedOnPanel(g, Component.translatable("screen.zenkai.wish.legendary.warning"),
                y, ZenkaiPalette.DENIED);
    }
}
