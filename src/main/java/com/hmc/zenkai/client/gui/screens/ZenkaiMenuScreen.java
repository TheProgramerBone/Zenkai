package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.buttons.TabIconButton;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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
 *
 * La fila de íconos va DEBAJO del panel; el título de cada hija va ENCIMA (ScreenTitle),
 * así que la franja superior queda para el título y la inferior para las pestañas.
 */
public abstract class ZenkaiMenuScreen extends Screen {

    protected static final ResourceLocation BG_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");
    protected static final int BG_W = 256;
    protected static final int BG_H = 256;

    private static final int TAB_ICON = 20; // tamaño de celda del atlas
    private static final int TAB_GAP  = 4;

    protected final Minecraft mc = Minecraft.getInstance();
    protected PlayerStatsAttachment att;
    protected int panelLeft;
    protected int panelTop;

    /** Ver ScreenTitle.CONTENT_TOP. Reexpuesto aquí para que las hijas no importen la clase. */
    protected static final int CONTENT_TOP = ScreenTitle.CONTENT_TOP;

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
            att = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        }
        panelLeft = (this.width - BG_W) / 2;
        panelTop = (this.height - BG_H) / 2;
        addTabButtons();
        initContent();
    }

    /** Fila de pestañas con ícono (icons.png), centrada BAJO el panel. Nombre = tooltip. */
    private void addTabButtons() {
        ZenkaiTab[] tabs = ZenkaiTab.values();
        int rowW = tabs.length * TAB_ICON + (tabs.length - 1) * TAB_GAP;
        // Bajo el panel, pero sin salirse de la ventana en resoluciones pequeñas.
        int y = Math.min(this.height - TAB_ICON - 2, panelTop + BG_H + 6);
        int x = panelLeft + (BG_W - rowW) / 2;
        for (ZenkaiTab t : tabs) {
            TabIconButton b = new TabIconButton(x, y, TAB_ICON, t.u, t.v,
                    Component.translatable(t.titleKey()),
                    () -> currentTab() == t,
                    () -> open(t));
            b.setTooltip(Tooltip.create(Component.translatable(t.titleKey())));
            this.addRenderableWidget(b);
            x += TAB_ICON + TAB_GAP;
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
            case MASTERY -> new MasteryScreen();
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