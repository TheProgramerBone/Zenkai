package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.ToggleButton;
import com.hmc.zenkai.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña de configuración de cliente.
 * Las filas siguen saliendo de ClientConfig.entries(), así que añadir una opción sigue sin
 * tocar este archivo. Lo que cambia es lo demás:
 *   - los ON/OFF eran Button.builder() grises de vanilla, el único punto del menú entero con
 *     widgets sin estilo. Ahora son ToggleButton;
 *   - el scroll era un par de botones "▲"/"▼" con la flecha como TEXTO, colocados uno encima
 *     del panel y otro colgando por debajo. Se sustituyen por rueda del ratón + barra de
 *     posición, que es el mismo gesto que ya usan Skills y Mastery;
 *   - cada fila lleva descripción bajo el título. Un interruptor llamado "Ki Sense camera
 *     shake" no dice si mueve la cámara al sentir a otros o al usar la habilidad;
 *   - hay indicador de cambios sin guardar. Antes se podía cambiar cinco opciones, salir por
 *     Escape y perderlo sin un solo aviso.
 */
public class ClientConfigScreen extends ZenkaiMenuScreen {

    /** 20 y no 16: deja hueco para la barra de scroll sin que pise los interruptores. */
    private static final int MARGIN = 20;
    private static final int ROW_H = 30;
    private static final int SAVE_W = 84, SAVE_H = 20;
    private static final int SCROLLBAR_W = 4;

    /** Pantalla desde la que se abrió, si vino de la lista de mods. Null si es una pestaña. */
    private final Screen returnTo;

    private final List<Boolean> staged = new ArrayList<>();
    private final List<ToggleButton> toggles = new ArrayList<>();
    private int scroll = 0;
    private PanelButton saveButton;

    public ClientConfigScreen() { this(null); }

    public ClientConfigScreen(Screen returnTo) {
        super(Component.translatable("screen.zenkai.client_config.title"));
        this.returnTo = returnTo;
        for (var e : ClientConfig.entries()) staged.add(e.value().get());
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.CONFIG; }

    // ── Geometría ────────────────────────────────────────────────────────────

    private int listTop()    { return panelTop + CONTENT_TOP; }
    private int listHeight() { return BG_H - CONTENT_TOP - MARGIN - SAVE_H - 10; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    private int maxScroll()  { return Math.max(0, ClientConfig.entries().size() - visibleRows()); }

    @Override
    protected void initContent() {
        toggles.clear();
        var entries = ClientConfig.entries();
        scroll = Mth.clamp(scroll, 0, maxScroll());

        int shown = Math.min(visibleRows(), entries.size() - scroll);
        for (int i = 0; i < shown; i++) {
            final int index = scroll + i;
            int y = listTop() + i * ROW_H + (ROW_H - ToggleButton.H) / 2;
            ToggleButton t = new ToggleButton(
                    panelLeft + BG_W - MARGIN - ToggleButton.W, y,
                    () -> staged.get(index),
                    v -> staged.set(index, v));
            toggles.add(t);
            addRenderableWidget(t);
        }

        saveButton = PanelButton.primary(
                panelLeft + (BG_W - SAVE_W) / 2,
                panelTop + BG_H - MARGIN - SAVE_H,
                Component.translatable("screen.zenkai.gui.save"), this::save);
        addRenderableWidget(saveButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** init() es final en la base, así que refrescar pasa por rehacer los widgets. */
    private void rebuild() {
        this.clearWidgets();
        this.rebuildWidgets();
    }

    private boolean isDirty() {
        var entries = ClientConfig.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).value().get().equals(staged.get(i))) return true;
        }
        return false;
    }

    private void save() {
        var entries = ClientConfig.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).value().get().equals(staged.get(i))) {
                entries.get(i).value().set(staged.get(i));
            }
        }
        ClientConfig.SPEC.save();   // ⚠ API
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        if (saveButton != null) saveButton.active = isDirty();

        super.render(g, mx, my, pt);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);

        var entries = ClientConfig.entries();
        int shown = Math.min(visibleRows(), entries.size() - scroll);
        int textRight = panelLeft + BG_W - MARGIN - ToggleButton.W - 8;
        int textW = textRight - (panelLeft + MARGIN);

        for (int i = 0; i < shown; i++) {
            var e = entries.get(scroll + i);
            int rowY = listTop() + i * ROW_H;

            // Banda alterna: con filas de 30 px y sin fondo, el ojo pierde qué interruptor
            // pertenece a qué texto en cuanto hay más de tres opciones.
            if (((scroll + i) & 1) == 0) {
                g.fill(panelLeft + MARGIN - 4, rowY, panelLeft + BG_W - MARGIN + 4,
                        rowY + ROW_H - 2, 0x18AC421B);
            }

            g.drawString(this.font, Component.translatable(e.titleKey()),
                    panelLeft + MARGIN, rowY + 5, ZenkaiPalette.LABEL_ON_PANEL, false);

            // Descripción opcional: si no existe la clave, la línea no se dibuja en absoluto
            // (nada de mostrar la clave cruda, que es lo que hace translatable por defecto).
            String descKey = e.titleKey() + ".desc";
            if (hasTranslation(descKey)) {
                var lines = this.font.split(Component.translatable(descKey), textW);
                if (!lines.isEmpty()) {
                    g.drawString(this.font, lines.getFirst(), panelLeft + MARGIN, rowY + 16,
                            ZenkaiPalette.MUTED_ON_PANEL, false);
                }
            }
        }

        drawScrollbar(g);

        if (isDirty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.zenkai.client_config.unsaved"),
                    panelLeft + BG_W / 2, panelTop + BG_H - MARGIN - SAVE_H - 11,
                    ZenkaiPalette.VALUE);
        }

        // Sin Configured no hay GUI para common/server. Decir dónde están es más honesto que
        // dejar creer que esta pantalla es toda la configuración del mod.
        if (!ModList.get().isLoaded("configured")) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.zenkai.client_config.more_options"),
                    this.width / 2, panelTop + BG_H + 26, ZenkaiPalette.TEXT_OFF);
        }
    }

    private boolean hasTranslation(String key) {
        return net.minecraft.client.resources.language.I18n.exists(key);   // ⚠ API
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;
        int x = panelLeft + BG_W - 10;
        int top = listTop(), h = visibleRows() * ROW_H;
        g.fill(x, top, x + SCROLLBAR_W, top + h, ZenkaiPalette.BAR_BG);
        int thumbH = Math.max(12, h * visibleRows() / ClientConfig.entries().size());
        int thumbY = top + (h - thumbH) * scroll / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.VALUE);
    }

    @Override
    public void onClose() {
        if (returnTo != null && this.minecraft != null) this.minecraft.setScreen(returnTo);
        else super.onClose();
    }
}