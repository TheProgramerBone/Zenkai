package com.hmc.zenkai.client.gui.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Placeholder de las pestañas aún sin contenido (Técnicas Físicas, Historia, Party, Config).
 * Los pasos 5+ del release las reemplazan en ZenkaiMenuScreen.createScreen.
 */
public class ComingSoonScreen extends ZenkaiMenuScreen {

    private final ZenkaiTab tab;

    public ComingSoonScreen(ZenkaiTab tab) {
        super(Component.translatable(tab.titleKey()));
        this.tab = tab;
    }

    @Override
    protected ZenkaiTab currentTab() { return tab; }

    @Override
    protected void initContent() {
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(this.font, this.title, panelLeft + 16, panelTop + 24, 0xFFFFFFFF, true);
        g.drawCenteredString(this.font, Component.translatable("screen.zenkai.coming_soon"),
                panelLeft + BG_W / 2, panelTop + BG_H / 2 - 4, 0xFFAAAAAA);
    }
}