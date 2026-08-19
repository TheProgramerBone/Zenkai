package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.buttons.ToggleButton;
import com.hmc.zenkai.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña de configuración de cliente.
 * Las filas salen de ClientConfig.entries(), así que añadir una opción no toca este archivo.
 * Entry es una interfaz sellada de tres casos y el switch los cubre todos: si algún día aparece
 * un cuarto tipo, el compilador obliga a pasar por aquí. Ese es el punto de que sea sellada —
 * una opción sin control visible existiría en el toml y sería invisible en la GUI.
 * ═══ TEXTO SIN SOMBRA SOBRE EL BEIGE ═══
 * El dibujo pasa por PanelText. La versión anterior usaba GuiGraphics#drawCenteredString
 * para el aviso de cambios, y esa firma dibuja SIEMPRE con sombra: una sombra negra bajo un
 * dorado quemado sobre beige claro es un borrón. El helper no deja equivocarse porque el
 * proceso obvio ya hace lo correcto.
 * ═══ LA DESCRIPCIÓN COMPLETA ESTÁ EN EL TOOLTIP ═══
 * En la fila solo cabe una línea, y recortarla dejaba frases a medias ("Show model and
 * texture…"). El texto entero aparece al pasar el ratón por la fila, reutilizando la MISMA
 * clave: una segunda clave para la versión larga se desincronizaría de la corta al primer
 * retoque, y además obligaría a traducir dos veces lo mismo.
 */
public class ClientConfigScreen extends ZenkaiMenuScreen {

    /** 20 y no 16: deja hueco para la barra de scroll sin que pise los controles. */
    private static final int MARGIN = 20;
    private static final int ROW_H = 30;
    private static final int SAVE_W = 84, SAVE_H = 20;
    private static final int SCROLLBAR_W = 4;
    /**
     * Ancho reservado al control de la derecha, igual para los tres tipos o las columnas
     * bailarían de fila en fila. 96 y no 72: con 72 el texto útil del ciclador eran 44 px y
     * "Bottom right" ocupa 60, así que el valor se metía debajo de las flechas.
     */
    private static final int CONTROL_W = 96;
    /** Lado de ArrowIconButton. No lo expone como constante, así que se replica aquí. */
    private static final int ARROW_W = 12;
    /** Separación entre la flecha y el valor. */
    private static final int ARROW_GAP = 3;

    /** Pantalla desde la que se abrió, si vino de la lista de mods. Null si es una pestaña. */
    @Nullable private final Screen returnTo;

    /**
     * Valor en edición de cada opción, sin escribir todavía en el toml.
     * Object y no Boolean porque hay tres tipos de opción. El tipo real se recupera del Entry
     * correspondiente.
     */
    private final List<Object> staged = new ArrayList<>();

    /** Rect de cada fila visible, para el tooltip. Se rehace en cada render. */
    private record RowArea(int index, int y) {}
    private final List<RowArea> rowAreas = new ArrayList<>();

    private int scroll = 0;
    private PanelButton saveButton;

    public ClientConfigScreen() { this(null); }

    public ClientConfigScreen(@Nullable Screen returnTo) {
        super(Component.translatable("screen.zenkai.client_config.title"));
        this.returnTo = returnTo;
        for (var e : ClientConfig.entries()) staged.add(currentValueOf(e));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.CONFIG; }

    // ── Geometría ────────────────────────────────────────────────────────────

    private int listTop()    { return panelTop + CONTENT_TOP; }
    private int listHeight() { return BG_H - CONTENT_TOP - MARGIN - SAVE_H - 24; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    /** Filas de la pantalla = opciones + UNA fila de acción al final (colocar el HUD).
     *  La fila de acción NO está en ClientConfig.entries(): ese modelo es de valores con buffer
     *  y guardado, y una acción no tiene ninguna de las dos cosas. La lista visual es cosa de
     *  la pantalla; el modelo de configuración se queda limpio. */
    private int rowCount()   { return ClientConfig.entries().size() + 1; }
    private int actionRow()  { return ClientConfig.entries().size(); }
    private int maxScroll()  { return Math.max(0, rowCount() - visibleRows()); }
    private int rowRight()   { return panelLeft + BG_W - MARGIN; }
    private int textRight()  { return rowRight() - CONTROL_W - 8; }
    private int textWidth()  { return textRight() - (panelLeft + MARGIN); }

    // ── Construcción ─────────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        var entries = ClientConfig.entries();
        scroll = Mth.clamp(scroll, 0, maxScroll());

        int shown = Math.min(visibleRows(), rowCount() - scroll);
        for (int i = 0; i < shown; i++) {
            final int index = scroll + i;
            int rowY = listTop() + i * ROW_H;
            if (index == actionRow()) addActionRow(rowY);
            else addControlFor(entries.get(index), index, rowY);
        }

        int footY = panelTop + BG_H - MARGIN - SAVE_H;
        saveButton = PanelButton.primary(panelLeft + (BG_W - SAVE_W) / 2, footY,
                Component.translatable("screen.zenkai.gui.save"), this::save);
        addRenderableWidget(saveButton);
    }

    /**
     * Fila de acción: rótulo a la izquierda como cualquier opción, botón en la columna de
     * control. No tiene valor en staged porque no hay nada que guardar aquí — la pantalla de
     * colocación escribe sola por setHudPlacement().
     * Se pasa THIS como padre: al volver, el buffer staged sigue intacto y no hace falta
     * guardar a la fuerza antes de salir.
     */
    private void addActionRow(int rowY) {
        int y = rowY + (ROW_H - PanelButton.H) / 2;
        addRenderableWidget(PanelButton.secondary(rowRight() - PanelButton.W, y,
                Component.translatable("screen.zenkai.client_config.hud_layout.open"),
                () -> mc.setScreen(new HudPlacementScreen(this))));
    }

    /** Crea el control de una fila según su tipo. */
    private void addControlFor(ClientConfig.Entry entry, int index, int rowY) {
        switch (entry) {
            case ClientConfig.BoolEntry ignored -> {
                int y = rowY + (ROW_H - ToggleButton.H) / 2;
                // El interruptor se alinea a la DERECHA del hueco de control, no lo llena: es
                // más estrecho que un ciclador y estirarlo lo haría parecer otra cosa.
                addRenderableWidget(new ToggleButton(rowRight() - ToggleButton.W, y,
                        () -> (Boolean) staged.get(index),
                        v -> staged.set(index, v)));
            }
            case ClientConfig.EnumEntry<?> e -> addCycler(e, index, rowY);
            case ClientConfig.IntEntry e -> addStepper(e, index, rowY);
        }
    }

    /**
     * Ciclador de enumerado: [<] valor [>].
     * Genérico auxiliar para poder nombrar el tipo del enum: EnumEntry&lt;?&gt; no deja llamar
     * a cycle(), que necesita saber que el valor y la lista son del mismo tipo.
     */
    private <T extends Enum<T>> void addCycler(ClientConfig.EnumEntry<T> entry, int index, int rowY) {
        int y = rowY + (ROW_H - 14) / 2;
        int x = rowRight() - CONTROL_W;
        int labelW = CONTROL_W - (ARROW_W + ARROW_GAP) * 2;

        addRenderableWidget(new ArrowIconButton(x, y + 1, ArrowIconButton.Dir.LEFT,
                () -> cycleStaged(entry, index, -1)));

        // El propio valor es clicable y avanza: es lo que la mitad de la gente intenta antes de
        // fijarse en las flechas.
        addRenderableWidget(new TextOnlyButton(x + ARROW_W + ARROW_GAP, y, labelW, 14,
                Component.empty(), () -> cycleStaged(entry, index, 1)) {
            @Override
            public @NotNull Component getMessage() {
                return PanelText.fit(ClientConfigScreen.this.font,
                        Component.translatable(enumNameKey(stagedEnum(entry, index))), labelW - 2);
            }
        }.onPanel());

        addRenderableWidget(new ArrowIconButton(rowRight() - ARROW_W, y + 1,
                ArrowIconButton.Dir.RIGHT, () -> cycleStaged(entry, index, 1)));
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> T stagedEnum(ClientConfig.EnumEntry<T> entry, int index) {
        return (T) staged.get(index);
    }

    private <T extends Enum<T>> void cycleStaged(ClientConfig.EnumEntry<T> entry, int index, int dir) {
        staged.set(index, entry.cycle(stagedEnum(entry, index), dir));
    }

    /** Selector numérico: [<] valor [>], saltando de step en step y sin dar la vuelta. */
    private void addStepper(ClientConfig.IntEntry entry, int index, int rowY) {
        int y = rowY + (ROW_H - 14) / 2;
        int x = rowRight() - CONTROL_W;
        int labelW = CONTROL_W - (ARROW_W + ARROW_GAP) * 2;

        addRenderableWidget(new ArrowIconButton(x, y + 1, ArrowIconButton.Dir.LEFT,
                () -> stepStaged(entry, index, -entry.step())));

        addRenderableWidget(new TextOnlyButton(x + ARROW_W + ARROW_GAP, y, labelW, 14,
                Component.empty(), () -> {}) {
            @Override
            public @NotNull Component getMessage() {
                return Component.literal(String.valueOf((Integer) staged.get(index)));
            }
        }.textColors(ZenkaiPalette.LABEL_ON_PANEL, ZenkaiPalette.LABEL_ON_PANEL,
                ZenkaiPalette.MUTED_ON_PANEL).noShadow());

        addRenderableWidget(new ArrowIconButton(rowRight() - ARROW_W, y + 1,
                ArrowIconButton.Dir.RIGHT, () -> stepStaged(entry, index, entry.step())));
    }

    /** Los números NO dan la vuelta: pasar de 100 a 0 por un clic de más sería una trampa. */
    private void stepStaged(ClientConfig.IntEntry entry, int index, int delta) {
        int v = (Integer) staged.get(index) + delta;
        staged.set(index, Mth.clamp(v, entry.min(), entry.max()));
    }

    /** Clave de traducción del valor de un enum. Los enums del HUD la exponen ellos mismos. */
    private static String enumNameKey(Enum<?> value) {
        if (value instanceof com.hmc.zenkai.client.overlay.HudAnchor a) return a.nameKey();
        if (value instanceof com.hmc.zenkai.client.overlay.HudOrientation o) return o.nameKey();
        return "config.zenkai.value." + value.name().toLowerCase();
    }

    private static Object currentValueOf(ClientConfig.Entry e) {
        return switch (e) {
            case ClientConfig.BoolEntry b -> b.value().get();
            case ClientConfig.EnumEntry<?> en -> en.value().get();
            case ClientConfig.IntEntry i -> i.value().get();
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void writeValue(ClientConfig.Entry e, Object staged) {
        switch (e) {
            case ClientConfig.BoolEntry b -> b.value().set((Boolean) staged);
            case ClientConfig.EnumEntry en -> en.value().set((Enum) staged);
            case ClientConfig.IntEntry i -> i.value().set((Integer) staged);
        }
    }

    // ── Interacción ──────────────────────────────────────────────────────────

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
            if (!currentValueOf(entries.get(i)).equals(staged.get(i))) return true;
        }
        return false;
    }

    private void save() {
        var entries = ClientConfig.entries();
        for (int i = 0; i < entries.size(); i++) {
            if (!currentValueOf(entries.get(i)).equals(staged.get(i))) {
                writeValue(entries.get(i), staged.get(i));
            }
        }
        ClientConfig.SPEC.save();   // ⚠ API
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        if (saveButton != null) saveButton.active = isDirty();

        super.render(g, mx, my, pt);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);

        var entries = ClientConfig.entries();
        int shown = Math.min(visibleRows(), rowCount() - scroll);
        rowAreas.clear();

        for (int i = 0; i < shown; i++) {
            int index = scroll + i;
            int rowY = listTop() + i * ROW_H;
            rowAreas.add(new RowArea(index, rowY));

            // Banda alterna: con filas de 30 px y sin fondo, el ojo pierde qué control
            // pertenece a qué texto en cuanto hay más de tres opciones.
            if ((index & 1) == 0) {
                g.fill(panelLeft + MARGIN - 4, rowY, panelLeft + BG_W - MARGIN + 4,
                        rowY + ROW_H - 2, ZenkaiPalette.ROW_BAND);
            }

            String titleKey;
            String descKey;
            if (index == actionRow()) {
                titleKey = "screen.zenkai.client_config.hud_layout";
                descKey = "screen.zenkai.client_config.hud_layout.desc";
            } else {
                var e = entries.get(index);
                titleKey = e.titleKey();
                descKey = e.titleKey() + ".desc";
            }

            PanelText.onPanel(g, this.font, Component.translatable(titleKey),
                    panelLeft + MARGIN, rowY + 5, ZenkaiPalette.LABEL_ON_PANEL);

            // Descripción: una línea recortada. La completa va en el tooltip de la fila.
            if (hasTranslation(descKey)) {
                Component desc = Component.translatable(descKey);
                PanelText.onPanel(g, this.font, PanelText.fit(this.font, desc, textWidth()),
                        panelLeft + MARGIN, rowY + 16, ZenkaiPalette.MUTED_ON_PANEL);
            }
        }

        drawScrollbar(g);

        if (isDirty()) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.client_config.unsaved"),
                    panelLeft + BG_W / 2, panelTop + BG_H - MARGIN - SAVE_H - 12,
                    ZenkaiPalette.DENIED_ON_PANEL);
        }

        // Fuera del panel: aquí el fondo es el mundo, así que este SÍ lleva sombra.
        if (!ModList.get().isLoaded("configured")) {
            PanelText.centeredOnDark(g, this.font,
                    Component.translatable("screen.zenkai.client_config.more_options"),
                    this.width / 2, panelTop + BG_H + 26, ZenkaiPalette.TEXT_OFF);
        }

        renderRowTooltip(g, mx, my);
    }

    /**
     * Descripción completa al pasar el ratón por la fila.
     * Se envuelve a 220 px y no al ancho de la fila: el tooltip flota libre y puede permitirse
     * ser más ancho que la columna donde no cabía el texto — que es justo el motivo de que
     * exista.
     */
    private void renderRowTooltip(GuiGraphics g, int mx, int my) {
        if (mx < panelLeft + MARGIN || mx > textRight()) return;

        var entries = ClientConfig.entries();
        for (RowArea area : rowAreas) {
            if (my < area.y() || my >= area.y() + ROW_H - 2) continue;

            String titleKey;
            String descKey;
            if (area.index() == actionRow()) {
                titleKey = "screen.zenkai.client_config.hud_layout";
                descKey = "screen.zenkai.client_config.hud_layout.desc";
            } else {
                var e = entries.get(area.index());
                titleKey = e.titleKey();
                descKey = e.titleKey() + ".desc";
            }

            if (!hasTranslation(descKey)) return;

            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(titleKey));
            for (var l : this.font.getSplitter().splitLines(
                    Component.translatable(descKey), 220, net.minecraft.network.chat.Style.EMPTY)) {
                lines.add(Component.literal(l.getString())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(this.font, lines, mx, my);
            return;
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
        int thumbH = Math.max(12, h * visibleRows() / rowCount());
        int thumbY = top + (h - thumbH) * scroll / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.VALUE_ON_PANEL);
    }

    @Override
    public void onClose() {
        if (returnTo != null && this.minecraft != null) this.minecraft.setScreen(returnTo);
        else super.onClose();
    }
}