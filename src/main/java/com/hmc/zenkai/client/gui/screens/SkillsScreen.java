package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.client.gui.buttons.XIconButton;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.ForgetSkillPacket;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.FormDrivenSkills;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

    private static final int LIST_TOP_OFF = CONTENT_TOP + 18;   // bajo cabecera de TP/MND
    private static final int LIST_BOTTOM_MARGIN = 14;
    private static final int TEXT_X_OFF = 16;
    private static final int SCROLLBAR_W = 4;
    private static final int TOOLTIP_W = 200;

    // Los colores salen de ZenkaiPalette, no de literales locales. Antes esta pantalla usaba
    // 0xFF7CFC7C para el nombre y 0xFFFFD966 para el coste: dos tonos pensados para leerse
    // sobre negro que, sobre el beige del panel y con sombra debajo, quedaban lavados.
    // MasteryScreen tenía SUS PROPIAS copias de los mismos valores con nombres distintos.

    private final List<String> rowIds = new ArrayList<>();
    private final List<PlusIconButton> plusButtons = new ArrayList<>();
    private final List<XIconButton> forgetButtons = new ArrayList<>();

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

        forgetButtons.clear();
        rowIds.addAll(st.skills().all());
        for (int i = 0; i < rowIds.size(); i++) {
            final String id = rowIds.get(i);
            PlusIconButton b = new PlusIconButton(plusX(), rowTop(i) + 2, () -> buy(id));
            plusButtons.add(b);
            addRenderableWidget(b);

            // Tooltip real (fijo/"locked") lo pone layoutRows() cada frame, según pueda o no
            // bajar de nivel: aquí basta un valor inicial, se sobrescribe antes del primer render.
            XIconButton x = new XIconButton(forgetX(), rowTop(i) + 2, () -> forget(id));
            x.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("screen.zenkai.skills.forget")));
            forgetButtons.add(x);
            addRenderableWidget(x);
        }
        // Si la lista encogió (respec, revoke), el scroll viejo puede quedar fuera de rango.
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    /** Comprar el siguiente nivel. Olvidar tiene su propio botón: forget(). */
    private void buy(String id) {
        PlayerStatsAttachment st = stats();
        SkillDef def = SkillDef.get(id);
        if (st == null || def == null || !def.purchasable()) return;

        int lvl = st.skills().level(id);
        if (lvl >= maxLevelOf(def) || !canAfford(st, def, lvl)) return;
        PacketDistributor.sendToServer(new SkillBuyPacket(id));
    }

    /** Bajar un nivel; desde el 1 la borra. Quién PUEDE bajar lo valida el servidor
     *  (ForgetSkillPacket) y el suelo de maestro PlayerSkills.lower(): aquí no se duplica esa
     *  regla, solo se comprueba que haya algo que quitar. */
    private void forget(String id) {
        PlayerStatsAttachment st = stats();
        SkillDef def = SkillDef.get(id);
        if (st == null || def == null || !def.purchasable()) return;
        if (st.skills().level(id) <= 0) return;
        PacketDistributor.sendToServer(new ForgetSkillPacket(id));
    }

    /** Attachment fresco: tras comprar, el server sincroniza y esto refleja el cambio solo. */
    private PlayerStatsAttachment stats() {
        return mc.player == null ? att : mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
    }

    // ── Geometría de la lista ────────────────────────────────────────────────

    private int plusX()      { return panelLeft + BG_W - 16 - PLUS_SIZE; }
    /** La X va a la izquierda del +, con 3 px de aire. */
    private int forgetX()    { return plusX() - PLUS_SIZE - 3; }
    private int listTop()    { return panelTop + LIST_TOP_OFF; }
    private int listHeight() { return BG_H - LIST_TOP_OFF - LIST_BOTTOM_MARGIN; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    private int viewHeight() { return visibleRows() * ROW_H; }

    private int maxScroll() { return Math.max(0, rowIds.size() - visibleRows()); }

    /** Y de la fila i teniendo en cuenta el scroll (puede quedar fuera de la vista). */
    private int rowTop(int index) { return listTop() + (index - scrollRow) * ROW_H; }

    /** Ancho útil de texto: desde el margen izquierdo hasta justo antes del botón +. */
    private int textMaxWidth() { return forgetX() - 6 - (panelLeft + TEXT_X_OFF); }

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
        if (def == null || mc.player == null) return def == null ? 0 : def.maxLevel();
        return FormDrivenSkills.maxLevel(def, mc.player);
    }

    /** Coste en TP de subir al nivel indicado (derivado de la forma que desbloquea, si aplica). */
    private int costOf(SkillDef def, int nextLevel) {
        if (def == null) return Integer.MAX_VALUE;
        if (mc.player == null) return def.tpCost();
        return FormDrivenSkills.tpCostForLevel(def, mc.player, nextLevel);
    }

    /** ¿Se puede pagar el siguiente nivel de esta habilidad? */
    private boolean canAfford(PlayerStatsAttachment st, SkillDef def, int currentLevel) {
        return st.getTP() >= costOf(def, currentLevel + 1)
                && st.mindFree() >= def.mindReqFor(currentLevel + 1) - def.mindReqFor(currentLevel);
    }

    /** Delega en PanelText: el criterio de recorte estaba copiado en tres pantallas. */
    private Component fit(Component c, int maxW) {
        return PanelText.fit(this.font, c, maxW);
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

            // La X NO comparte condición con el +: al nivel máximo el + se apaga y la X debe
            // seguir ahí, que es cuando más falta hace. Solo se oculta si no hay nivel que bajar.
            //
            // ACTIVO exige además boughtLevels() > 0. lower() se niega en seco si el nivel
            // actual no supera el suelo otorgado (maestro/admin) — PlayerSkills.lower(). Antes
            // el botón se quedaba SIEMPRE activo con solo lvl > 0, así que en una skill 100%
            // otorgada (p. ej. dada con /zenkai skill give o giveall) parecía un botón normal,
            // el clic mandaba el paquete, el servidor lo rechazaba en silencio y el jugador no
            // tenía forma de saber si había pulsado mal o si el botón estaba roto.
            String id = rowIds.get(i);
            boolean canForget = def != null && def.purchasable() && lvl > 0
                    && st.skills().boughtLevels(id) > 0;

            XIconButton x = forgetButtons.get(i);
            x.setPosition(forgetX(), rowTop(i) + 2);
            x.visible = def != null && def.purchasable() && lvl > 0 && onScreen(i);
            x.active = x.visible && canForget;
            x.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                    canForget ? "screen.zenkai.skills.forget" : "screen.zenkai.skills.forget.locked")));
        }
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;

        int x = panelLeft + BG_W - 10;
        int top = listTop(), h = viewHeight();
        g.fill(x, top, x + SCROLLBAR_W, top + h, ZenkaiPalette.BAR_BG);

        int thumbH = Math.max(12, h * visibleRows() / rowIds.size());
        int thumbY = top + (h - thumbH) * scrollRow / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.VALUE_ON_PANEL);
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

        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);
        if (st == null) return;

        // TP y MIND en DOS textos, cada uno con su color de rol. Juntos en una sola clave
        // compartían el amarillo y no se distinguían de un vistazo, que es justo lo que se
        // necesita al decidir si una habilidad está a tu alcance.
        Component tp = Component.translatable("screen.zenkai.skills.tp", st.getTP());
        PanelText.onPanel(g, this.font, tp, panelLeft + 16, panelTop + CONTENT_TOP,
                ZenkaiPalette.TP_ON_PANEL);

        int free = st.mindFree();
        PanelText.onPanel(g, this.font,
                Component.translatable("screen.zenkai.skills.mind",
                        free, st.getAttribute(ZenkaiAttributes.MIND)),
                panelLeft + 16 + this.font.width(tp) + 10, panelTop + CONTENT_TOP,
                // El déficit se señala en rojo: un MIND negativo es un problema real, no un
                // valor bajo.
                free < 0 ? ZenkaiPalette.DENIED_ON_PANEL : ZenkaiPalette.MIND_ON_PANEL);

        if (rowIds.isEmpty()) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.skills.empty"),
                    panelLeft + BG_W / 2, panelTop + BG_H / 2 - 4, ZenkaiPalette.MUTED_ON_PANEL);
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
            PanelText.onPanel(g, this.font, name, textX, y, ZenkaiPalette.OWNED_ON_PANEL);

            // Separador tenue: sin él las filas se leen como un solo bloque de texto.
            if (i < last - 1) {
                g.fill(textX, y + ROW_H - 6, plusX() + PLUS_SIZE, y + ROW_H - 5, ZenkaiPalette.SEPARATOR);
            }

            // < forgetX(), NO < plusX(): el botón de olvidar (X) vive justo antes del +, dentro
            // de ese rango. Con < plusX() el hit-test de la descripción lo incluía, así que pasar
            // el ratón por la X disparaba A LA VEZ el tooltip manual de esta fila (renderTooltip,
            // inmediato) Y el tooltip propio del XIconButton (setTooltip → cola de GuiGraphics,
            // se pinta al final del frame) — los dos en el mismo (mouseX, mouseY), superpuestos
            // en un mismo bloque de texto ilegible.
            if (mouseY >= y && mouseY < y + ROW_H && mouseX >= textX && mouseX < forgetX()) {
                hoveredDesc = def != null ? Component.translatable(def.descKey()) : null;
            }

            if (def == null) continue;

            PanelText.onPanel(g, this.font, fit(Component.translatable(def.descKey()), maxW),
                    textX, y + 11, ZenkaiPalette.BODY_ON_PANEL);

            boolean canLevel = def.purchasable() && lvl < maxLevelOf(def);
            if (!canLevel) {
                if (lvl >= maxLevelOf(def)) {
                    PanelText.onPanel(g, this.font,
                            Component.translatable("screen.zenkai.skills.maxed"),
                            textX, y + 22, ZenkaiPalette.MAXED_ON_PANEL);
                }
                continue;
            }
            PanelText.onPanel(g, this.font,
                    Component.translatable("screen.zenkai.skills.cost",
                            costOf(def, lvl + 1), def.mindReqFor(lvl + 1) - def.mindReqFor(lvl)),
                    textX, y + 22,
                    canAfford(st, def, lvl) ? ZenkaiPalette.TP_ON_PANEL
                            : ZenkaiPalette.DENIED_ON_PANEL);
        }
        g.disableScissor();
        drawScrollbar(g);
        // El tooltip va fuera del scissor o se recortaría con la lista.
        if (hoveredDesc != null) {
            g.renderTooltip(this.font, this.font.split(hoveredDesc, TOOLTIP_W), mouseX, mouseY);
        }
    }
}