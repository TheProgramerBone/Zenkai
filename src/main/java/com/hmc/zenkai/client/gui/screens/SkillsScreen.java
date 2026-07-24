package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.core.network.feature.ZenkaiAttributes;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.skills.SkillDef;
import com.hmc.zenkai.core.skills.SuperForms;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña Habilidades: SOLO las que el jugador ya tiene (el nivel 1 lo dan maestros o
 * /zenkai skill give). Muestra nivel actual, coste del siguiente y un botón + para subirlo.
 * Lista con scroll de rueda: caben 5 filas y el mod va camino de tener bastantes más
 * habilidades, así que la ventana se desplaza por filas enteras (nada de medias filas
 * cortadas) y una barra a la derecha indica dónde estás.
 * Los textos se recortan con puntos suspensivos al ancho disponible; la descripción
 * completa sale en un tooltip al pasar el ratón por encima de la fila.
 */

public class SkillsScreen extends ZenkaiMenuScreen {

    private static final int ROW_H = 42;   // 33 px de texto + 9 de aire entre habilidades
    private static final int PLUS_SIZE = 12;   // tamaño de PlusIconButton

    private static final int LIST_TOP_OFF = 58;      // bajo cabecera de TP/MND
    private static final int LIST_BOTTOM_MARGIN = 14;
    private static final int TEXT_X_OFF = 16;
    private static final int SCROLLBAR_W = 4;
    private static final int TOOLTIP_W = 200;

    private static final int COL_NAME  = 0xFF7CFC7C;
    private static final int COL_DESC  = 0xFFAAAAAA;
    private static final int COL_COST  = 0xFFFFD966;
    private static final int COL_POOR  = 0xFFCC6666;
    /** Azul: al máximo. Antes era verde y se confundía con el nombre de la habilidad. */
    private static final int COL_MAXED = 0xFF7FD4FF;
    private static final int COL_SEP   = 0x22FFFFFF;

    private final List<String> rowIds = new ArrayList<>();
    private final List<PlusIconButton> plusButtons = new ArrayList<>();

    private int scrollRow = 0;

    public SkillsScreen() {
        super(Component.translatable(ZenkaiTab.SKILLS.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.SKILLS; }

    @Override
    protected void initContent() {
        rowIds.clear();
        plusButtons.clear();
        PlayerStatsAttachment st = stats();
        if (st == null) return;

        rowIds.addAll(st.skills().all());
        for (int i = 0; i < rowIds.size(); i++) {
            final String id = rowIds.get(i);
            PlusIconButton b = new PlusIconButton(plusX(), rowTop(i) + 2, () -> buy(id));
            plusButtons.add(b);
            addRenderableWidget(b);
        }
        // Si la lista encogió (respec, revoke), el scroll viejo puede quedar fuera de rango.
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    private void buy(String id) {
        PlayerStatsAttachment st = stats();
        SkillDef def = SkillDef.get(id);
        if (st == null || def == null || !def.purchasable()) return;
        int lvl = st.skills().level(id);
        if (lvl >= maxLevelOf(def) || !canAfford(st, def, lvl)) return;
        PacketDistributor.sendToServer(new SkillBuyPacket(id));
    }

    /** Attachment fresco: tras comprar, el server sincroniza y esto refleja el cambio solo. */
    private PlayerStatsAttachment stats() {
        return mc.player == null ? att : mc.player.getData(DataAttachments.PLAYER_STATS.get());
    }

    // ── Geometría de la lista ────────────────────────────────────────────────

    private int plusX()      { return panelLeft + BG_W - 16 - PLUS_SIZE; }
    private int listTop()    { return panelTop + LIST_TOP_OFF; }
    private int listHeight() { return BG_H - LIST_TOP_OFF - LIST_BOTTOM_MARGIN; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    private int viewHeight() { return visibleRows() * ROW_H; }

    private int maxScroll() { return Math.max(0, rowIds.size() - visibleRows()); }

    /** Y de la fila i teniendo en cuenta el scroll (puede quedar fuera de la vista). */
    private int rowTop(int index) { return listTop() + (index - scrollRow) * ROW_H; }

    /** Ancho útil de texto: desde el margen izquierdo hasta justo antes del botón +. */
    private int textMaxWidth() { return plusX() - 6 - (panelLeft + TEXT_X_OFF); }

    private boolean onScreen(int index) {
        int rel = index - scrollRow;
        return rel >= 0 && rel < visibleRows();
    }

    /**
     * Techo REAL de la habilidad para este jugador. Con levels_from_forms el máximo sale de la
     * cadena de formas de su raza (un saiyan tiene 4 transformaciones y un arcosiano 5), así que
     * un max_level fijo mostraría niveles que ese jugador no puede desbloquear nunca.
     */
    private int maxLevelOf(SkillDef def) {
        if (def == null) return 0;
        return def.levelsFromForms() && mc.player != null
                ? Math.min(def.maxLevel(), SuperForms.maxLevel(mc.player))
                : def.maxLevel();
    }

    /** Coste en TP de subir al nivel indicado (derivado de la forma que desbloquea, si aplica). */
    private int costOf(SkillDef def, int nextLevel) {
        if (def == null) return Integer.MAX_VALUE;
        return def.levelsFromForms() && mc.player != null
                ? SuperForms.tpCostForLevel(mc.player, nextLevel)
                : def.tpCost();
    }

    /** ¿Se puede pagar el siguiente nivel de esta habilidad? */
    private boolean canAfford(PlayerStatsAttachment st, SkillDef def, int currentLevel) {
        return st.getTP() >= costOf(def, currentLevel + 1)
                && st.getAttribute(ZenkaiAttributes.MIND) >= def.mindReqFor(currentLevel + 1);
    }

    /** Recorta a una línea con puntos suspensivos: sin esto las descripciones largas
     *  se salían del panel por la derecha. */
    private Component fit(Component c, int maxW) {
        if (maxW <= 0) return Component.empty();
        if (this.font.width(c) <= maxW) return c;
        int dots = this.font.width("...");
        String cut = this.font.plainSubstrByWidth(c.getString(), Math.max(0, maxW - dots));
        return Component.literal(cut + "...");
    }

    // ── Scroll ───────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Coloca y habilita los botones + ANTES de súper.render(): es súper quien los dibuja,
     * así que ajustarlos después los dejaría un frame por detrás del scroll.
     */
    private void layoutRows(PlayerStatsAttachment st) {
        for (int i = 0; i < plusButtons.size() && i < rowIds.size(); i++) {
            PlusIconButton b = plusButtons.get(i);
            b.setPosition(plusX(), rowTop(i) + 2);

            SkillDef def = SkillDef.get(rowIds.get(i));
            int lvl = st.skills().level(rowIds.get(i));
            boolean canLevel = def != null && def.purchasable() && lvl < maxLevelOf(def);

            b.visible = canLevel && onScreen(i);
            b.active = b.visible && canAfford(st, def, lvl);
        }
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;

        int x = panelLeft + BG_W - 10;
        int top = listTop(), h = viewHeight();
        g.fill(x, top, x + SCROLLBAR_W, top + h, 0x40000000);

        int thumbH = Math.max(12, h * visibleRows() / rowIds.size());
        int thumbY = top + (h - thumbH) * scrollRow / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, 0xFFFFD966);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        PlayerStatsAttachment st = stats();

        // Llegó una habilidad nueva por sync: hay que crear su botón.
        // OJO: all() devuelve un Set y rowIds es una List. List.equals(Set) es SIEMPRE
        // false, así que hay que envolverlo en una List antes de comparar o esto
        // reconstruye widgets y sale sin dibujar en cada frame.
        if (st != null) {
            List<String> ids = new ArrayList<>(st.skills().all());
            if (!rowIds.equals(ids)) {
                rebuildWidgets();
                return;
            }
            layoutRows(st);
        }

        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(this.font, this.title, panelLeft + 16, panelTop + 24, 0xFFFFFFFF, true);
        if (st == null) return;

        g.drawString(this.font,
                Component.translatable("screen.zenkai.skills.resources",
                        st.getTP(), st.getAttribute(ZenkaiAttributes.MIND)),
                panelLeft + 16, panelTop + 40, 0xFFFFD966, true);

        if (rowIds.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("screen.zenkai.skills.empty"),
                    panelLeft + BG_W / 2, panelTop + BG_H / 2 - 4, 0xFFAAAAAA);
            return;
        }

        int textX = panelLeft + TEXT_X_OFF;
        int maxW = textMaxWidth();
        Component hoveredDesc = null;

        // Recorte a la ventana visible: una fila a medio salir se corta en vez de
        // pintarse encima del borde del panel.
        g.enableScissor(panelLeft, listTop(), panelLeft + BG_W, listTop() + viewHeight());

        int first = scrollRow;
        int last = Math.min(rowIds.size(), scrollRow + visibleRows());
        for (int i = first; i < last; i++) {
            String id = rowIds.get(i);
            SkillDef def = SkillDef.get(id);
            int y = rowTop(i);
            int lvl = st.skills().level(id);

            // Nombre + nivel: el sufijo x/max nunca se recorta, solo el nombre.
            Component base = def != null
                    ? Component.translatable(def.nameKey())
                    : Component.literal(id); // huérfana: el registro cambió, se muestra el id
            Component name;
            if (def != null && maxLevelOf(def) > 1) {
                Component suffix = Component.literal("  " + lvl + "/" + maxLevelOf(def));
                name = Component.empty()
                        .append(fit(base, maxW - this.font.width(suffix)))
                        .append(suffix);
            } else {
                name = fit(base, maxW);
            }
            g.drawString(this.font, name, textX, y, COL_NAME, true);

            // Separador tenue: sin él las filas se leen como un solo bloque de texto.
            if (i < last - 1) {
                g.fill(textX, y + ROW_H - 6, plusX() + PLUS_SIZE, y + ROW_H - 5, COL_SEP);
            }

            if (mouseY >= y && mouseY < y + ROW_H && mouseX >= textX && mouseX < plusX()) {
                hoveredDesc = def != null ? Component.translatable(def.descKey()) : null;
            }

            if (def == null) continue;

            g.drawString(this.font, fit(Component.translatable(def.descKey()), maxW),
                    textX, y + 11, COL_DESC, true);

            boolean canLevel = def.purchasable() && lvl < maxLevelOf(def);
            if (!canLevel) {
                if (lvl >= maxLevelOf(def)) {
                    g.drawString(this.font, Component.translatable("screen.zenkai.skills.maxed"),
                            textX, y + 22, COL_MAXED, true);
                }
                continue;
            }
            g.drawString(this.font,
                    Component.translatable("screen.zenkai.skills.cost",
                            costOf(def, lvl + 1), def.mindReqFor(lvl + 1)),
                    textX, y + 22, canAfford(st, def, lvl) ? COL_COST : COL_POOR, true);
        }
        g.disableScissor();
        drawScrollbar(g);
        // El tooltip va fuera del scissor o se recortaría con la lista.
        if (hoveredDesc != null) {
            g.renderTooltip(this.font, this.font.split(hoveredDesc, TOOLTIP_W), mouseX, mouseY);
        }
    }
}