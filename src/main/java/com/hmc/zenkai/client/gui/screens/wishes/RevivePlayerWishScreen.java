package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.screens.ZenkaiPanelScreen;
import com.hmc.zenkai.feature.wishes.WishRevivePlayerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Deseo de revivir a un jugador. Pide un nombre exacto.
 *
 * Tres arreglos sobre la versión anterior:
 *   1. Confirmar está APAGADO mientras la caja esté vacía. Antes se podía confirmar en blanco:
 *      el deseo se consumía en el servidor y no revivía a nadie.
 *   2. Sugerencias de los jugadores conectados bajo la caja. "Nombre exacto" en un juego donde
 *      los nicks llevan guiones bajos y mayúsculas es una trampa; con la lista delante deja de
 *      serlo. Se filtra por prefijo y se rellena al pulsar.
 *   3. Enter confirma, que es lo que hace cualquiera tras escribir un nombre.
 */
public class RevivePlayerWishScreen extends ZenkaiPanelScreen {

    private static final int BOX_W = 180, BOX_H = 20;
    private static final int MAX_SUGGESTIONS = 5;

    private EditBox nameBox;

    public RevivePlayerWishScreen(Screen parent) {
        super(Component.translatable("screen.zenkai.wish.revive_player"), parent);
    }

    @Override protected int titleColor() { return ZenkaiPalette.SHENLONG; }

    @Override
    protected void initContent() {
        int y = panelTop + CONTENT_TOP + 40;
        nameBox = new EditBox(this.font, centerX() - BOX_W / 2, y, BOX_W, BOX_H,
                Component.translatable("screen.zenkai.wish.revive_player.hint"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.translatable("screen.zenkai.wish.revive_player.hint"));
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
    }

    @Override
    protected boolean confirmEnabled() {
        return nameBox != null && !nameBox.getValue().trim().isEmpty();
    }

    @Override
    protected void onConfirm() {
        String target = nameBox.getValue().trim();
        if (target.isEmpty()) return;
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.send(new WishRevivePlayerPayload(target));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // 257 = Enter, 335 = Enter del teclado numérico.
        if ((key == 257 || key == 335) && confirmEnabled()) {
            onConfirm();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawWrappedOnPanel(g, Component.translatable("screen.zenkai.wish.revive_player.desc"),
                panelTop + CONTENT_TOP + 12, ZenkaiPalette.LABEL_ON_PANEL);

        List<String> matches = suggestions();
        if (matches.isEmpty()) return;

        int y = nameBox.getY() + BOX_H + 8;
        drawCenteredOnPanel(g, Component.translatable("screen.zenkai.wish.revive_player.online"),
                y, ZenkaiPalette.MUTED_ON_PANEL);
        y += this.font.lineHeight + 3;

        for (String name : matches) {
            int w = this.font.width(name);
            int x = centerX() - w / 2;
            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + this.font.lineHeight;
            g.drawString(this.font, name, x, y,
                    hovered ? ZenkaiPalette.TEXT_HOVER : ZenkaiPalette.BODY_ON_PANEL, false);
            y += this.font.lineHeight + 2;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<String> matches = suggestions();
        int y = (nameBox != null ? nameBox.getY() + BOX_H + 8 : 0) + this.font.lineHeight + 3;
        for (String name : matches) {
            int w = this.font.width(name);
            int x = centerX() - w / 2;
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + this.font.lineHeight) {
                nameBox.setValue(name);
                return true;
            }
            y += this.font.lineHeight + 2;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Jugadores conectados cuyo nombre empieza por lo escrito. La tab list vale: es cliente. */
    private List<String> suggestions() {
        if (nameBox == null || mc.getConnection() == null) return List.of();
        String typed = nameBox.getValue().trim().toLowerCase(Locale.ROOT);
        return mc.getConnection().getOnlinePlayers().stream()
                .map(p -> p.getProfile().getName())
                .filter(n -> n != null && !n.isEmpty())
                .filter(n -> typed.isEmpty() || n.toLowerCase(Locale.ROOT).startsWith(typed))
                .filter(n -> !n.equalsIgnoreCase(typed))
                .sorted()
                .limit(MAX_SUGGESTIONS)
                .toList();
    }
}