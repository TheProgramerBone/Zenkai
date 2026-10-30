package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.CommonPanel;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.buttons.TabIconButton;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

    /** Tamaño real del panel de esta pantalla — puede encoger por debajo de CommonPanel.IDEAL
     *  en ventanas pequeñas (ver CommonPanel). Calculado en init(), no constante. */
    protected int BG_W;
    protected int BG_H;

    private static final int TAB_ICON = 20; // tamaño de celda del atlas
    private static final int TAB_GAP  = 4;
    /** Hueco entre el borde inferior del panel y la fila de pestañas. */
    private static final int TAB_ROW_GAP = 6;
    /** Aire bajo la fila de pestañas antes del borde de la ventana. */
    private static final int TAB_ROW_MARGIN = 2;
    /** Alto que hay que reservar por DEBAJO del panel para la fila de pestañas — igual que
     *  CommonPanel.TITLE_RESERVE pero para lo que vive bajo el panel en vez de encima. */
    static final int TAB_RESERVE = TAB_ROW_GAP + TAB_ICON + TAB_ROW_MARGIN;

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
        var w = CommonPanel.fitAxis(this.width, CommonPanel.MARGIN, CommonPanel.MARGIN);
        var h = CommonPanel.fitAxis(this.height, CommonPanel.TITLE_RESERVE, TAB_RESERVE);
        BG_W = w.size();
        BG_H = h.size();
        panelLeft = w.pos();
        panelTop = h.pos();
        addTabButtons();
        initContent();
    }

    /** Fila de pestañas con ícono (icons.png), centrada BAJO el panel. Nombre = tooltip. */
    private void addTabButtons() {
        ZenkaiTab[] tabs = ZenkaiTab.values();
        int rowW = tabs.length * TAB_ICON + (tabs.length - 1) * TAB_GAP;
        // TAB_RESERVE ya le hizo sitio a init(), así que esto normalmente no dispara — se deja
        // como red de seguridad última por si algún día una pantalla hija pisa la reserva.
        int y = Math.min(this.height - TAB_ICON - TAB_ROW_MARGIN, panelTop + BG_H + TAB_ROW_GAP);
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

    /** Pestañas "hub": tienen subscreens propias (TrainingHubScreen abre las tres pantallas de
     *  minijuego) que también reportan esta MISMA currentTab(), así que el guard de no-op de
     *  abajo tiene que eximirlas o no habría forma de volver al hub pulsando la pestaña de nuevo
     *  estando ya dentro de una.
     *  TECHNIQUES salió de aquí al fusionarse sus tres pantallas en TechniquesScreen: ya no hay
     *  hub del que volver, y su único hijo (TechniqueEditScreen) no dibuja barra de pestañas.
     *  CONFIG entra por CreditsScreen, que se abre desde un botón de ClientConfigScreen y
     *  marca Config como pestaña activa: pulsarla tiene que devolver a la configuración. */
    private static final java.util.Set<ZenkaiTab> ALWAYS_REOPEN =
            java.util.EnumSet.of(ZenkaiTab.TRAINING, ZenkaiTab.CONFIG);

    private void open(ZenkaiTab t) {
        if (t == currentTab() && !ALWAYS_REOPEN.contains(t)) return;
        mc.setScreen(createScreen(t));
    }

    /** Fábrica de screens por pestaña. Las que faltan (pasos 5+) muestran "Coming soon". */
    public static Screen createScreen(ZenkaiTab t) {
        return switch (t) {
            case STATS -> new StatsScreen();
            case SKILLS -> new com.hmc.zenkai.client.gui.screens.skilltree.SkillsTreeScreen();
            case TECHNIQUES -> new TechniquesScreen();
            case TRAINING -> new TrainingHubScreen();
            case TRANSFORMATIONS -> new com.hmc.zenkai.client.gui.screens.skilltree.SkillTreeScreen();
            case PARTY -> new PartyScreen();
            case CONFIG -> new ClientConfigScreen();
            default -> new ComingSoonScreen(t);
        };
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        CommonPanel.blit(g, panelLeft, panelTop, BG_W, BG_H);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}