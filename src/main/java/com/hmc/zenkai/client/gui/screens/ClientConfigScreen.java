package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña de configuración de cliente.
 *
 * Las filas se construyen recorriendo ClientConfig.entries(), así que añadir una opción no
 * requiere tocar este archivo. La lista la alimenta el helper defineBool() de ClientConfig:
 * si alguna vez se declara una opción con BUILDER.define(...) a pelo, existirá en el toml
 * pero será invisible aquí.
 *
 * Los cambios se guardan en un buffer local y solo se escriben al pulsar Guardar. Cambiar
 * de pestaña o pulsar Escape descarta, que es lo que espera cualquiera al ver un botón de
 * guardar.
 */
public class ClientConfigScreen extends ZenkaiMenuScreen {

    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");

    private static final int MARGIN = 16;
    private static final int ROW_H = 24;
    private static final int VISIBLE_ROWS = 6;
    private static final int TOGGLE_W = 54, TOGGLE_H = 20;
    private static final int SAVE_W = 60, SAVE_H = 25;

    /** Pantalla desde la que se abrió, si vino de la lista de mods. Null si es una pestaña. */
    private final Screen returnTo;

    private final List<Boolean> staged = new ArrayList<>();
    private int scroll = 0;

    public ClientConfigScreen() {
        this(null);
    }

    public ClientConfigScreen(Screen returnTo) {
        super(Component.translatable("screen.zenkai.client_config.title"));
        this.returnTo = returnTo;
        for (var e : ClientConfig.entries()) staged.add(e.value().get());
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.CONFIG; }

    @Override
    protected void initContent() {
        var entries = ClientConfig.entries();
        int shown = Math.min(VISIBLE_ROWS, entries.size() - scroll);

        for (int i = 0; i < shown; i++) {
            final int index = scroll + i;
            int y = panelTop + CONTENT_TOP + i * ROW_H;
            addRenderableWidget(Button.builder(toggleLabel(index), b -> {
                staged.set(index, !staged.get(index));
                b.setMessage(toggleLabel(index));
            }).bounds(panelLeft + BG_W - MARGIN - TOGGLE_W, y, TOGGLE_W, TOGGLE_H).build());
        }

        if (entries.size() > VISIBLE_ROWS) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
                scroll = Math.max(0, scroll - 1);
                rebuild();
            }).bounds(panelLeft + BG_W - MARGIN - 20, panelTop + CONTENT_TOP - 22, 20, 20).build());

            addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
                scroll = Math.min(entries.size() - VISIBLE_ROWS, scroll + 1);
                rebuild();
            }).bounds(panelLeft + BG_W - MARGIN - 20,
                    panelTop + CONTENT_TOP + VISIBLE_ROWS * ROW_H, 20, 20).build());
        }

        // DENTRO del panel: la franja de debajo la ocupa la fila de pestañas de la base.
        addRenderableWidget(new TextOnlyButton(
                panelLeft + (BG_W - SAVE_W) / 2, panelTop + BG_H - MARGIN - SAVE_H,
                SAVE_W, SAVE_H,
                Component.translatable("screen.zenkai.gui.save"), TEX_BTN, null, this::save));
    }

    /** init() es final en la base, así que el refresco pasa por rehacer la pantalla entera. */
    private void rebuild() {
        this.clearWidgets();
        this.rebuildWidgets();
    }

    private Component toggleLabel(int index) {
        return Component.translatable(staged.get(index) ? "options.on" : "options.off");
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
        super.render(g, mx, my, pt);

        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);

        var entries = ClientConfig.entries();
        int shown = Math.min(VISIBLE_ROWS, entries.size() - scroll);
        for (int i = 0; i < shown; i++) {
            var e = entries.get(scroll + i);
            int y = panelTop + CONTENT_TOP + i * ROW_H + 6;
            g.drawString(this.font, Component.translatable(e.titleKey()),
                    panelLeft + MARGIN, y, 0xFFFFFFFF, true);
        }

        // Sin Configured no hay GUI para common/server. Decir dónde están es más honesto que
        // dejar creer que esta pantalla es toda la configuración del mod.
        if (!ModList.get().isLoaded("configured")) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.zenkai.client_config.more_options"),
                    this.width / 2, panelTop + BG_H - 14, 0xA0A0A0);
        }
    }

    @Override
    public void onClose() {
        // Si vino de la lista de mods, vuelve ahí; si es una pestaña, cierra normal.
        if (returnTo != null) {
            assert this.minecraft != null;
            this.minecraft.setScreen(returnTo);
        }
        else super.onClose();
    }
}