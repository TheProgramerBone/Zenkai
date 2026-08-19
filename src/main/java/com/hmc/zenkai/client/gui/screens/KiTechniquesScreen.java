package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.TechniqueIcons;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.BindBar;
import com.hmc.zenkai.client.gui.widgets.SlotCell;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.technique.KiTechnique;
import com.hmc.zenkai.feature.technique.TechniquePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña Técnicas Ki.
 * QUÉ CAMBIA respecto a la versión anterior:
 *  - La barra de las nueve posiciones era un TextOnlyButton vacío por celda con el ícono y el
 *    número dibujados ENCIMA en un bucle aparte. Resultado: una posición libre no se veía (solo
 *    flotaba su número) y una ocupada tenía el ícono de 20x20 sobre una celda de 20x20, tapando
 *    el número. Ahora es un BindBar de SlotCell, compartido con la pestaña física.
 *  - La lista NO tenía scroll. Con techniqueMaxSlots por encima de 7 las últimas técnicas se
 *    dibujaban fuera del panel y sus botones quedaban sobre el mundo. Ahora scrollea por filas
 *    enteras, igual que Skills y Mastery.
 *  - "Nueva técnica" viajaba con la lista y acababa donde acabase la última fila. Ahora es un
 *    botón fijo en el pie, que es donde el jugador lo busca.
 *  - El modo asignación solo se anunciaba con un texto bajo la barra. Ahora la fila armada se
 *    resalta Y las celdas libres pulsan (SlotCell lo hace solo), así que se ve dónde soltar.
 *  - Cada fila enseña coste y tamaño además del tipo. Antes la sub-línea era solo el tipo, que
 *    ya se deduce del ícono: la fila entera no aportaba un dato nuevo.
 */
public class KiTechniquesScreen extends ZenkaiMenuScreen {

    private static final int ROW_H = 28;
    private static final int BAR_Y_OFF  = CONTENT_TOP + 24;
    private static final int LIST_Y_OFF = BAR_Y_OFF + SlotCell.SIZE + 16;
    private static final int LIST_BOTTOM_MARGIN = 34;   // hueco del botón "nueva técnica"
    private static final int TEXT_X_OFF = 16;
    private static final int ICON = 18;
    private static final int SCROLLBAR_W = 4;
    private static final int X_SIZE = 12;

    private static final ResourceLocation TEX_X =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x.png");
    private static final ResourceLocation TEX_X_HL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_x_highlight.png");

    /** Índice de técnica armada para asignar; -1 = ninguna. */
    private int assigning = -1;
    /** Nº de slots con el que se construyeron los widgets (auto-rebuild al llegar syncs). */
    private int builtSlotCount = -1;
    private int scrollRow = 0;

    private BindBar bindBar;
    private final List<TextOnlyButton> rowButtons = new ArrayList<>();

    public KiTechniquesScreen() {
        super(Component.translatable(ZenkaiTab.KI_TECHNIQUES.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.KI_TECHNIQUES; }

    // ── Geometría ────────────────────────────────────────────────────────────

    private int listTop()    { return panelTop + LIST_Y_OFF; }
    private int listHeight() { return BG_H - LIST_Y_OFF - LIST_BOTTOM_MARGIN; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    private int viewHeight() { return visibleRows() * ROW_H; }
    private int rowTop(int i){ return listTop() + (i - scrollRow) * ROW_H; }
    private int rowCount()   { return att == null ? 0 : att.techniques().slotCount(); }
    private int maxScroll()  { return Math.max(0, rowCount() - visibleRows()); }
    private boolean onScreen(int i) { int r = i - scrollRow; return r >= 0 && r < visibleRows(); }

    private int rightEdge()   { return panelLeft + BG_W - 14; }
    private int xDelete()     { return rightEdge() - X_SIZE; }
    private int xEdit()       { return xDelete() - 6 - 40; }   // 40 = "[ Edit ]" en negrita
    private int xAssign()     { return xEdit() - 4 - 52; }   // 52 = "[ Assign ]" en negrita
    private int textMaxWidth(){ return xAssign() - 6 - (panelLeft + TEXT_X_OFF + ICON + 4); }

    @Override
    protected void initContent() {
        rowButtons.clear();
        if (att == null) return;
        builtSlotCount = att.techniques().slotCount();
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());

        // ── Barra de las nueve posiciones ──
        bindBar = BindBar.create(
                panelLeft + (BG_W - BindBar.WIDTH) / 2, panelTop + BAR_Y_OFF,
                att,
                () -> assigning >= 0,
                this::onPositionClicked,
                this::addRenderableWidget);

        // ── Filas ──
        var slots = att.techniques().slots();
        for (int i = 0; i < slots.size(); i++) {
            final int idx = i;
            int y = rowTop(i) + 5;

            // asAction() los envuelve en corchetes y negrita: sin eso son texto suelto a la
            // derecha de una fila que ya lleva nombre y sub-línea, y nada dice que se pulsen.
            rowButtons.add(addRenderableWidget(new TextOnlyButton(xAssign(), y, 52, 16,
                    Component.translatable("screen.zenkai.technique.assign"),
                    () -> assigning = (assigning == idx) ? -1 : idx)
                    .onPanel().asAction()));

            rowButtons.add(addRenderableWidget(new TextOnlyButton(xEdit(), y, 40, 16,
                    Component.translatable("screen.zenkai.technique.edit"),
                    () -> mc.setScreen(new TechniqueEditScreen(idx)))
                    .onPanel().asAction()));

            // La ✖ del mod, no el glifo Unicode: el "✖" dependía de la fuente instalada y a
            // GUI Scale bajo se veía como un aspa borrosa de otro tamaño que el resto de iconos.
            rowButtons.add(addRenderableWidget(new TextOnlyButton(
                    xDelete(), y + (16 - X_SIZE) / 2, X_SIZE, X_SIZE,
                    Component.empty(), TEX_X, TEX_X_HL,
                    () -> {
                        att.techniques().removeSlot(idx);                       // optimista
                        PacketDistributor.sendToServer(TechniquePacket.delete(idx));
                        assigning = -1;
                        this.rebuildWidgets();
                    })));
        }

        // ── Pie: crear técnica ──
        if (slots.size() < CommonConfig.techniqueMaxSlots()) {
            addRenderableWidget(PanelButton.primary(
                    panelLeft + (BG_W - PanelButton.W) / 2,
                    panelTop + BG_H - 12 - PanelButton.H,
                    Component.translatable("screen.zenkai.technique.create"),
                    () -> mc.setScreen(new TechniqueEditScreen(-1))));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (att != null && att.techniques().slotCount() != builtSlotCount) {
            this.rebuildWidgets(); // el sync trajo más/menos técnicas que las construidas
        }
    }

    // ── Ratón: el BindBar mira primero ───────────────────────────────────────
    // Tiene que interceptar ANTES que super porque distinguir un clic de un arrastre exige
    // esperar al mouseReleased, y AbstractWidget dispara su acción ya en el mouseClicked.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bindBar != null && bindBar.mouseClicked(att, mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (bindBar != null && bindBar.mouseDragged(att, mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // true = era un arrastre y ya está resuelto. false = era un clic simple, y entonces se
        // deja pasar a super para que la celda ejecute su acción normal.
        if (bindBar != null && bindBar.mouseReleased(att, mouseX, mouseY)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Teclas 1-9 mientras hay una técnica armada.
         * Es el mismo gesto que se usará en combate para lanzarla, así que asignar con la tecla que
     * la va a disparar deja la asociación hecha en la cabeza del jugador. Solo funciona en modo
     * asignación: fuera de él, los números no deben hacer nada aquí.
     */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (assigning >= 0) {
            // 49..57 = teclas 1..9 de la fila superior; 321..329 = las del teclado numérico.
            int pos = (key >= 49 && key <= 57) ? key - 49
                    : (key >= 321 && key <= 329) ? key - 321
                    : -1;
            if (pos >= 0) {
                onPositionClicked(pos);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            // Un arrastre en curso se cancela: las filas se mueven bajo el cursor y el destino
            // dejaría de ser el que el jugador estaba mirando.
            if (bindBar != null) bindBar.cancelDrag();
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Click en una posición: asigna lo armado, o desasigna al ocupante si no hay nada armado. */
    private void onPositionClicked(int pos) {
        if (att == null) return;
        if (assigning >= 0) {
            att.techniques().bind(pos, assigning);                              // optimista
            PacketDistributor.sendToServer(TechniquePacket.bind(assigning, pos));
            assigning = -1;
        } else {
            int occupant = att.techniques().binding(pos);
            if (occupant >= 0) {
                att.techniques().bind(-1, occupant);                            // optimista
                PacketDistributor.sendToServer(TechniquePacket.bind(occupant, -1));
            }
        }
    }

    /** Coloca los botones ANTES de super.render(): es super quien los dibuja, y ajustarlos
     *  después los dejaría un frame por detrás del scroll. */
    private void layoutRows() {
        for (int i = 0; i * 3 < rowButtons.size(); i++) {
            int y = rowTop(i) + 5;
            boolean vis = onScreen(i);
            for (int b = 0; b < 3; b++) {
                TextOnlyButton btn = rowButtons.get(i * 3 + b);
                btn.setY(y);
                btn.visible = vis;
                btn.active = vis;
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (att != null) layoutRows();

        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);
        if (att == null) return;

        // ── Cabecera: TP + ocupación de slots ──
        PanelText.onPanel(g, this.font,
                Component.translatable("screen.zenkai.technique.tp", att.getTP()),
                panelLeft + TEXT_X_OFF, panelTop + CONTENT_TOP, ZenkaiPalette.TP_ON_PANEL);

        Component slotsLabel = Component.translatable("screen.zenkai.technique.slots_used",
                att.techniques().slotCount(), CommonConfig.techniqueMaxSlots());
        PanelText.rightOnPanel(g, this.font, slotsLabel, rightEdge(), panelTop + CONTENT_TOP,
                ZenkaiPalette.MUTED_ON_PANEL);

        // ── Barra de posiciones ──
        if (bindBar != null) {
            bindBar.refreshIcons(att);
            bindBar.renderFrame(g, this.font, null);
        }
        if (assigning >= 0) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.technique.assign_hint"),
                    panelLeft + BG_W / 2, panelTop + BAR_Y_OFF + SlotCell.SIZE + 7,
                    ZenkaiPalette.TP_ON_PANEL);
        }

        // ── Lista ──
        var slots = att.techniques().slots();
        if (slots.isEmpty()) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.technique.empty"),
                    panelLeft + BG_W / 2, listTop() + viewHeight() / 2 - 4,
                    ZenkaiPalette.MUTED_ON_PANEL);
            return;
        }

        int textX = panelLeft + TEXT_X_OFF;
        Component hovered = null;

        g.enableScissor(panelLeft + 4, listTop(), panelLeft + BG_W - 4, listTop() + viewHeight());

        int last = Math.min(slots.size(), scrollRow + visibleRows());
        for (int i = scrollRow; i < last; i++) {
            KiTechnique t = slots.get(i);
            int y = rowTop(i);

            if (i == assigning) {
                g.fill(panelLeft + 10, y, panelLeft + BG_W - 10, y + ROW_H - 2,
                        ZenkaiPalette.SELECT_VEIL);
            }

            TechniqueIcons.draw(g, textX, y + 4, ICON, t);

            int nameX = textX + ICON + 4;
            Component name = fit(t.displayName(), textMaxWidth());
            PanelText.onPanel(g, this.font, name, nameX, y + 4, ZenkaiPalette.LABEL_ON_PANEL);

            // Sub-línea: tipo · tamaño · posición asignada. El tipo solo ya se deduce del ícono;
            // lo que el jugador no puede ver de otro modo es el tamaño y a qué tecla va.
            int pos = att.techniques().positionOf(i);
            Component sub = Component.translatable("screen.zenkai.technique.size", t.size())
                    .append(Component.literal(" · "));
            PanelText.onPanel(g, this.font, fit(sub, textMaxWidth()), nameX, y + 15,
                    ZenkaiPalette.MUTED_ON_PANEL);

            if (pos >= 0) {
                Component key = Component.literal("[" + (pos + 1) + "]");
                PanelText.onPanel(g, this.font, key,
                        nameX + textMaxWidth() - this.font.width(key)-30, y + 15,
                        ZenkaiPalette.OK_ON_PANEL);
            }

            if (i < last - 1) {
                g.fill(textX, y + ROW_H - 3, rightEdge(), y + ROW_H - 2, ZenkaiPalette.SEPARATOR);
            }

            if (mouseY >= y && mouseY < y + ROW_H && mouseX >= textX && mouseX < xAssign()) {
                hovered = t.displayName();
            }
        }
        g.disableScissor();

        drawScrollbar(g);

        // Los tooltips van FUERA del scissor o se recortarían con la lista.
        Component barTip = (bindBar != null) ? bindBar.tooltipAt(att, mouseX, mouseY) : null;
        if (barTip != null) g.renderTooltip(this.font, barTip, mouseX, mouseY);
        else if (hovered != null) g.renderTooltip(this.font, hovered, mouseX, mouseY);

        // El icono que viaja con el cursor va el ÚLTIMO: por encima de las celdas, de
        // la lista y de los tooltips.
        if (bindBar != null) bindBar.renderDragGhost(g, mouseX, mouseY);
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;
        int x = panelLeft + BG_W - 10;
        int top = listTop(), h = viewHeight();
        g.fill(x, top, x + SCROLLBAR_W, top + h, ZenkaiPalette.BAR_BG);
        int thumbH = Math.max(12, h * visibleRows() / rowCount());
        int thumbY = top + (h - thumbH) * scrollRow / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.VALUE_ON_PANEL);
    }

    /** Delega en PanelText: el criterio de recorte estaba copiado en tres pantallas. */
    private Component fit(Component c, int maxW) {
        return PanelText.fit(this.font, c, maxW);
    }
}