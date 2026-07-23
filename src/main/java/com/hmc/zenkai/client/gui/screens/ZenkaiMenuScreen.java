package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.buttons.TabIconButton;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Base del menú Zenkai: UNA screen por pestaña (nada de monolitos).
 * Aporta: fondo common_screen centrado, barra de pestañas con íconos (cambiar de pestaña =
 * setScreen de la screen correspondiente), attachment refrescado y posiciones del panel.
 *
 * Las hijas implementan initContent() (widgets) y dibujan su contenido en render()
 * después de super.render(). Campos protected con los MISMOS nombres que usaba StatsScreen
 * (mc, att, panelLeft, panelTop, BG_W, BG_H) para que su código migre sin tocarse.
 */
public abstract class ZenkaiMenuScreen extends Screen {

    protected static final ResourceLocation BG_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    protected static final int BG_W = 256;
    protected static final int BG_H = 256;

    protected final Minecraft mc = Minecraft.getInstance();
    protected PlayerStatsAttachment att;
    protected int panelLeft;
    protected int panelTop;

    protected ZenkaiMenuScreen(Component title) {
        super(title);
    }

    /** Pestaña que representa esta screen (marca el ícono activo). */
    protected abstract ZenkaiTab currentTab();

    /** Widgets propios de la pantalla (se llama desde init(), con panel y att ya listos). */
    protected abstract void initContent();

    @Override
    protected final void init() {
        if (mc.player != null) {
            att = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        }
        panelLeft = (this.width - BG_W) / 2;
        panelTop = (this.height - BG_H) / 2;
        addTabButtons();
        initContent();
    }

    /** Fila de pestañas con ícono (icons.png), centrada sobre el panel. Nombre = tooltip. */
    private void addTabButtons() {
        ZenkaiTab[] tabs = ZenkaiTab.values();
        int icon = 20, gap = 4; // icon = tamaño de celda del atlas
        int rowW = tabs.length * icon + (tabs.length - 1) * gap;
        int y = Math.max(2, panelTop - icon - 6);
        int x = panelLeft + (BG_W - rowW) / 2;
        for (ZenkaiTab t : tabs) {
            TabIconButton b = new TabIconButton(x, y, icon, t.u, t.v,
                    Component.translatable(t.titleKey()),
                    () -> currentTab() == t,
                    () -> open(t));
            b.setTooltip(Tooltip.create(Component.translatable(t.titleKey())));
            this.addRenderableWidget(b);
            x += icon + gap;
        }
    }

    private void open(ZenkaiTab t) {
        if (t == currentTab()) return;
        mc.setScreen(createScreen(t));
    }

    /** Fábrica de screens por pestaña. Las que faltan (pasos 5+) muestran "Coming soon". */
    public static Screen createScreen(ZenkaiTab t) {
        return switch (t) {
            case STATS -> new StatsScreen();
            case SKILLS -> new SkillsScreen();
            case KI_TECHNIQUES -> new KiTechniquesScreen();
            case PHYSICAL_TECHNIQUES -> new PhysicalScreen();
            default -> new ComingSoonScreen(t);
        };
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG_TEX, panelLeft, panelTop, 0, 0, BG_W, BG_H);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}