package com.hmc.zenkai.client.gui.screens;

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
    /** Ancho reservado al control de la derecha. Igual para los tres tipos, o las columnas
     *  bailarían de fila en fila. */
    private static final int CONTROL_W = 72;
    /** Lado de ArrowIconButton. No lo expone como constante, así que se replica aquí. */
    private static final int ARROW_W = 12;

    /** Pantalla desde la que se abrió, si vino de la lista de mods. Null si es una pestaña. */
    private final Screen returnTo;

    /**
     * Valor en edición de cada opción, sin escribir todavía en el toml.
         * Object y no Boolean porque ahora hay tres tipos de opción. El tipo real se recupera del
     * Entry correspondiente, que es sellado: el switch de abajo cubre los tres casos y el
     * compilador avisará si algún día aparece un cuarto.
     */
    private final List<Object> staged = new ArrayList<>();
    private final List<ToggleButton> toggles = new ArrayList<>();
    private int scroll = 0;
    private PanelButton saveButton;

    public ClientConfigScreen() { this(null); }

    public ClientConfigScreen(Screen returnTo) {
        super(Component.translatable("screen.zenkai.client_config.title"));
        this.returnTo = returnTo;
        for (var e : ClientConfig.entries()) staged.add(currentValueOf(e));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.CONFIG; }

    // ── Geometría ────────────────────────────────────────────────────────────

    private int listTop()    { return panelTop + CONTENT_TOP; }
    private int listHeight() { return BG_H - CONTENT_TOP - MARGIN - SAVE_H * 2 - 16; }
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
            int rowY = listTop() + i * ROW_H;
            addControlFor(entries.get(index), index, rowY);
        }

        // Colocación del HUD: no es una fila de la lista porque no es un valor que se elige,
        // es una pantalla a la que se va. Meterla como opción obligaría a inventar un control
        // para algo que no tiene estados.
        addRenderableWidget(PanelButton.secondary(
                panelLeft + (BG_W - SAVE_W) / 2,
                panelTop + BG_H - MARGIN - SAVE_H * 2 - 6,
                Component.translatable("screen.zenkai.client_config.place_hud"),
                () -> {
                    save();   // el arrastre abre otra pantalla: se conserva lo editado aquí
                    mc.setScreen(new HudPlacementScreen(new ClientConfigScreen(returnTo)));
                }));

        saveButton = PanelButton.primary(
                panelLeft + (BG_W - SAVE_W) / 2,
                panelTop + BG_H - MARGIN - SAVE_H,
                Component.translatable("screen.zenkai.gui.save"), this::save);
        addRenderableWidget(saveButton);
    }

    /**
     * Crea el control de una fila según su tipo.
         * El switch va sobre la interfaz sellada Entry, así que si mañana se añade un cuarto tipo
     * de opción el compilador obliga a pasar por aquí. Ese es justo el motivo de que Entry sea
     * sellada y no una jerarquía abierta: una opción sin control visible existiría en el toml y
     * sería invisible en la GUI, que es el fallo silencioso que este automatismo debe evitar.
     */
    private void addControlFor(ClientConfig.Entry entry, int index, int rowY) {
        int right = panelLeft + BG_W - MARGIN;

        switch (entry) {
            case ClientConfig.BoolEntry ignored -> {
                int y = rowY + (ROW_H - ToggleButton.H) / 2;
                addRenderableWidget(new ToggleButton(right - ToggleButton.W, y,
                        () -> (Boolean) staged.get(index),
                        v -> staged.set(index, v)));
            }
            case ClientConfig.EnumEntry<?> e -> addCycler(e, index, rowY, right);
            case ClientConfig.IntEntry e -> addStepper(e, index, rowY, right);
        }
    }

    /**
     * Ciclador de enumerado: [<] valor [>].
         * Genérico auxiliar para poder nombrar el tipo del enum: EnumEntry<?> no deja llamar a
     * cycle(), que necesita saber que el valor y la lista son del mismo tipo. Es la única forma
     * de mantener el comodín fuera y el tipo dentro.
     */
    private <T extends Enum<T>> void addCycler(ClientConfig.EnumEntry<T> entry, int index,
                                               int rowY, int right) {
        int y = rowY + (ROW_H - 14) / 2;
        int arrowW = ARROW_W;
        int labelW = CONTROL_W - arrowW * 2 - 4;
        int x = right - CONTROL_W;

        addRenderableWidget(new ArrowIconButton(x, y + 1, ArrowIconButton.Dir.LEFT,
                () -> cycleStaged(entry, index, -1)));

        // El propio valor es clicable y avanza: es lo que la mitad de la gente intenta antes
        // de fijarse en las flechas.
        addRenderableWidget(new TextOnlyButton(x + arrowW + 2, y, labelW, 14,
                Component.empty(), () -> cycleStaged(entry, index, 1)) {
            @Override
            public Component getMessage() {
                return Component.translatable(enumNameKey(stagedEnum(entry, index)));
            }
        }.textColors(ZenkaiPalette.LABEL_ON_PANEL, ZenkaiPalette.DENIED_ON_PANEL,
                ZenkaiPalette.MUTED_ON_PANEL));

        addRenderableWidget(new ArrowIconButton(right - arrowW, y + 1, ArrowIconButton.Dir.RIGHT,
                () -> cycleStaged(entry, index, 1)));
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> T stagedEnum(ClientConfig.EnumEntry<T> entry, int index) {
        return (T) staged.get(index);
    }

    private <T extends Enum<T>> void cycleStaged(ClientConfig.EnumEntry<T> entry, int index, int dir) {
        staged.set(index, entry.cycle(stagedEnum(entry, index), dir));
    }

    /** Selector numérico: [<] valor [>], saltando de step en step y sin dar la vuelta. */
    private void addStepper(ClientConfig.IntEntry entry, int index, int rowY, int right) {
        int y = rowY + (ROW_H - 14) / 2;
        int arrowW = ARROW_W;
        int labelW = CONTROL_W - arrowW * 2 - 4;
        int x = right - CONTROL_W;

        addRenderableWidget(new ArrowIconButton(x, y + 1, ArrowIconButton.Dir.LEFT,
                () -> stepStaged(entry, index, -entry.step())));

        addRenderableWidget(new TextOnlyButton(x + arrowW + 2, y, labelW, 14,
                Component.empty(), () -> {}) {
            @Override
            public Component getMessage() {
                return Component.literal(String.valueOf((Integer) staged.get(index)));
            }
        }.textColors(ZenkaiPalette.LABEL_ON_PANEL, ZenkaiPalette.LABEL_ON_PANEL,
                ZenkaiPalette.MUTED_ON_PANEL));

        addRenderableWidget(new ArrowIconButton(right - arrowW, y + 1, ArrowIconButton.Dir.RIGHT,
                () -> stepStaged(entry, index, entry.step())));
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

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        if (saveButton != null) saveButton.active = isDirty();

        super.render(g, mx, my, pt);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);

        var entries = ClientConfig.entries();
        int shown = Math.min(visibleRows(), entries.size() - scroll);
        int textRight = panelLeft + BG_W - MARGIN - CONTROL_W - 8;
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
                    g.drawString(this.font, lines.get(0), panelLeft + MARGIN, rowY + 16,
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