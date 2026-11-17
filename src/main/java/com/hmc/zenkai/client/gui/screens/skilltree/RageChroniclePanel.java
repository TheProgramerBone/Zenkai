package com.hmc.zenkai.client.gui.screens.skilltree;

import com.hmc.zenkai.client.gui.NineSlice;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.rage.RageChronicle;
import com.hmc.zenkai.feature.rage.RageChronicleClientState;
import com.hmc.zenkai.feature.rage.RageTrigger;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista de la Crónica de Rage Forms dentro de {@link SkillTreeScreen} (la pestaña Transformaciones). Solo LECTURA: una lista
 * con la entrada más reciente arriba, y al pasar el ratón por una fila, un tooltip con todos sus campos. No concede, modifica
 * ni repite nada: la crónica es historia, no una fuente de poder. Los datos llegan por {@link RageChronicleClientState} (el
 * servidor los manda bajo demanda al abrir la vista).
 *
 * Diseño aprobado en .claude/workbench/rage-cronica/maqueta_cronica.png (estados B, C y D). Geometría en px de un panel de 256
 * (visor: x 12..244, y 13..243): título a vpTop+30, lista de vpTop+43 a vpBottom-35 (deja libre el botón de volver, que ocupa
 * la esquina de abajo a la derecha), filas de 30 px. Familia de la placa del skill tree: texto CON sombra, colores
 * {@code CHRONICLE_*}/semánticos de ZenkaiPalette.
 */
final class RageChroniclePanel {
    static final int ROW_H = 30;
    private static final int SIDE = 6;               // margen del visor a la placa de la lista
    private static final int ROW_INSET = 4;          // de la placa a cada fila
    private static final int ICON = 16;              // icono de la forma: 32 texeles a 16 px, 1:1 con el árbol
    private static final int SCROLLBAR_W = 3;
    private static final int ROW_BG = 0xFF201C2E, ROW_BG_HOVER = 0xFF2A263C;

    private int scroll;                              // px desplazados desde arriba
    private int listX1, listY1, listX2, listY2;      // rectángulo de CONTENIDO de la placa

    // ── Geometría ────────────────────────────────────────────────────────────

    /** Se llama en cada frame con el visor actual (el panel puede moverse). */
    void layout(int vpLeft, int vpTop, int vpRight, int vpBottom) {
        listX1 = vpLeft + SIDE;
        listX2 = vpRight - SIDE;
        listY1 = vpTop + 43;
        listY2 = vpBottom - 35;
    }

    boolean inList(double mx, double my) { return mx >= listX1 && mx < listX2 && my >= listY1 && my < listY2; }

    /** Las entradas, la más reciente primero. */
    private static List<RageChronicle.Entry> newestFirst() {
        List<RageChronicle.Entry> src = RageChronicleClientState.entries();
        List<RageChronicle.Entry> out = new ArrayList<>(src.size());
        for (int i = src.size() - 1; i >= 0; i--) out.add(src.get(i));
        return out;
    }

    private int maxScroll(int rows) { return Math.max(0, rows * ROW_H - (listY2 - listY1 - 2 * ROW_INSET) + ROW_INSET); }

    void scrollBy(double notches) {
        int rows = RageChronicleClientState.entries().size();
        scroll = Math.max(0, Math.min(maxScroll(rows), scroll - (int) Math.round(notches * ROW_H / 2.0)));
    }

    /** Solo SkillTreeShots: centro en pantalla de la fila {@code i} (0 = la más reciente) para simular el ratón encima. */
    int[] devRowCenter(int i) {
        return new int[]{(listX1 + listX2) / 2 - 20, listY1 + ROW_INSET + i * ROW_H - scroll + 12};
    }

    /** Vuelve arriba (al abrir la vista). */
    void resetScroll() { scroll = 0; }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    void render(GuiGraphics g, Font font, int mx, int my, int screenW, int screenH, int vpCx, int vpTop) {
        // Sobre una placa propia: el oro del título se perdía contra el cielo dorado del fondo.
        Component title = titleText();
        int tw = font.width(title);
        NineSlice.plateFrame(g, vpCx - tw / 2 - 6, vpTop + 27, tw + 12, 12);
        g.drawCenteredString(font, title, vpCx, vpTop + 30, ZenkaiPalette.GOLD);

        NineSlice.plateFrame(g, listX1, listY1, listX2 - listX1, listY2 - listY1);
        if (!RageChronicleClientState.loaded()) {
            g.drawCenteredString(font, Component.translatable("screen.zenkai.rage.loading"), (listX1 + listX2) / 2,
                    (listY1 + listY2) / 2 - 4, ZenkaiPalette.TEXT_DIM);
            return;
        }
        List<RageChronicle.Entry> list = newestFirst();
        if (list.isEmpty()) {
            drawEmpty(g, font);
            return;
        }
        scroll = Math.min(scroll, maxScroll(list.size()));

        RageChronicle.Entry hovered = null;
        g.enableScissor(listX1, listY1, listX2, listY2);
        int y = listY1 + ROW_INSET - scroll;
        for (int i = 0; i < list.size(); i++, y += ROW_H) {
            if (y + ROW_H < listY1 || y > listY2) continue;
            boolean over = inList(mx, my) && my >= y && my < y + ROW_H - 2;
            if (over) { hovered = list.get(i); }
            drawRow(g, font, list.get(i), y, over);
        }
        drawScrollbar(g, list.size());
        g.disableScissor();
        if (hovered != null) drawTooltip(g, font, hovered, mx, my, screenW, screenH);
    }

    private Component titleText() {
        int n = RageChronicleClientState.entries().size();
        Component base = Component.translatable("screen.zenkai.rage.title");
        return n > 0 && RageChronicleClientState.loaded() ? Component.empty().append(base).append(" (" + n + ")") : base;
    }

    private void drawEmpty(GuiGraphics g, Font font) {
        int w = listX2 - listX1 - 24;
        List<FormattedCharSequence> lines = font.split(Component.translatable("screen.zenkai.rage.empty"), w);
        int y = (listY1 + listY2) / 2 - lines.size() * 11 / 2;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, (listX1 + listX2) / 2 - font.width(line) / 2, y, ZenkaiPalette.TEXT_DIM, true);
            y += 11;
        }
    }

    private void drawRow(GuiGraphics g, Font font, RageChronicle.Entry e, int y, boolean over) {
        int x = listX1 + ROW_INSET, w = listX2 - listX1 - 2 * ROW_INSET - SCROLLBAR_W - 2;
        g.fill(x, y, x + w, y + ROW_H - 2, over ? ROW_BG_HOVER : ROW_BG);
        if (over) {
            g.fill(x, y, x + w, y + 1, ZenkaiPalette.GOLD);
            g.fill(x, y + ROW_H - 3, x + w, y + ROW_H - 2, ZenkaiPalette.GOLD);
        }
        drawFormBadge(g, e, x + 4, y + 7);

        int color2 = lineTwoColor(e);
        g.drawString(font, lineOne(e), x + 26, y + 5, ZenkaiPalette.TEXT, true);
        g.drawString(font, lineTwo(e), x + 26, y + 16, color2, true);

        Component date = dateText(e.worldTime());
        g.drawString(font, date, x + w - 4 - font.width(date), y + 5, ZenkaiPalette.TEXT_DIM, true);
        Component tag = tagText(e);
        g.drawString(font, tag, x + w - 4 - font.width(tag), y + 16, tagColor(e), true);
    }

    /** Icono de la forma (del mismo atlas del árbol); sin forma, un cuadro neutro. */
    private static void drawFormBadge(GuiGraphics g, RageChronicle.Entry e, int x, int y) {
        ResourceLocation form = formOf(e);
        if (form != null && FormDef.get(form) != null) {
            FormIcons.draw(g, form, x, y, ICON, -1);
            return;
        }
        g.fill(x, y, x + ICON, y + ICON, 0xFF161224);
        g.fill(x + 2, y + 2, x + ICON - 2, y + ICON - 2, 0xFF6A6A72);
    }

    private void drawScrollbar(GuiGraphics g, int rows) {
        int max = maxScroll(rows);
        if (max <= 0) return;
        int trackTop = listY1 + 6, trackH = listY2 - listY1 - 12;
        int x = listX2 - 7;
        g.fill(x, trackTop, x + SCROLLBAR_W, trackTop + trackH, 0xFF14121F);
        int thumbH = Math.max(12, trackH * (listY2 - listY1) / (rows * ROW_H));
        int thumbY = trackTop + (trackH - thumbH) * scroll / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.MUTED_ON_PANEL);
    }

    private void drawTooltip(GuiGraphics g, Font font, RageChronicle.Entry e, int mx, int my, int screenW, int screenH) {
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(lineOne(e)); colors.add(ZenkaiPalette.GOLD);
        lines.add(descriptionOf(e)); colors.add(ZenkaiPalette.TEXT);
        if (!e.related().isEmpty()) { lines.add(Component.translatable("screen.zenkai.rage.tip.about", e.related())); colors.add(ZenkaiPalette.TEXT_DIM); }
        ResourceLocation form = formOf(e);
        if (form != null) { lines.add(Component.translatable("screen.zenkai.rage.tip.form", formName(form))); colors.add(ZenkaiPalette.TEXT); }
        lines.add(Component.translatable("screen.zenkai.rage.tip.result", resultName(e))); colors.add(lineTwoColor(e));
        if (e.masteryBefore() >= 0f && e.masteryAfter() >= 0f) {
            lines.add(Component.translatable("screen.zenkai.rage.tip.mastery", pct(e.masteryBefore()), pct(e.masteryAfter())));
            colors.add(ZenkaiPalette.CHRONICLE_PROGRESS);
        }
        if (e.ignoredByCooldown()) { lines.add(Component.translatable("screen.zenkai.rage.tip.cooldown")); colors.add(ZenkaiPalette.CHRONICLE_RECORDED); }
        lines.add(Component.translatable("screen.zenkai.rage.tip.via", viaName(e))); colors.add(ZenkaiPalette.TEXT_DIM);
        if (e.cinematicPlayed()) { lines.add(Component.translatable("screen.zenkai.rage.tip.cinematic")); colors.add(ZenkaiPalette.TEXT_DIM); }
        if (e.interrupted()) { lines.add(Component.translatable("screen.zenkai.rage.tip.interrupted")); colors.add(ZenkaiPalette.TEXT_DIM); }
        lines.add(dateText(e.worldTime())); colors.add(ZenkaiPalette.TEXT_DIM);

        int w = 12;
        for (Component c : lines) w = Math.max(w, font.width(c) + 12);
        int h = 10 + lines.size() * 10;
        int x = Math.min(mx + 12, screenW - w - 6), y = Math.min(my + 6, screenH - h - 6);
        x = Math.max(6, x); y = Math.max(6, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);   // por encima de las filas y del botón de volver
        NineSlice.plateFrame(g, x, y, w, h);
        int ty = y + 5;
        for (int i = 0; i < lines.size(); i++, ty += 10) g.drawString(font, lines.get(i), x + 6, ty, colors.get(i), true);
        g.pose().popPose();
    }

    // ── Textos ───────────────────────────────────────────────────────────────

    private static @Nullable ResourceLocation formOf(RageChronicle.Entry e) {
        return e.targetForm().isEmpty() ? null : ResourceLocation.tryParse(e.targetForm());
    }

    private static Component formName(ResourceLocation form) {
        return Component.translatable(FormDef.nameKey(form));
    }

    private static @Nullable RageTrigger triggerOf(RageChronicle.Entry e) { return RageTrigger.parse(e.trigger()); }

    private static boolean isFirst(RageChronicle.Entry e) { return RageChronicle.FORM_FIRST.equals(e.trigger()); }

    private static Component someone(RageChronicle.Entry e) {
        return e.related().isEmpty() ? Component.translatable("screen.zenkai.rage.someone") : Component.literal(e.related());
    }

    /** «Muere Krillin», «Cae Yamcha cerca», «Primera vez»… */
    static Component lineOne(RageChronicle.Entry e) {
        if (isFirst(e)) return Component.translatable("screen.zenkai.rage.first");
        return Component.translatable("screen.zenkai.rage.line." + e.trigger().toLowerCase(java.util.Locale.ROOT), someone(e));
    }

    private static Component descriptionOf(RageChronicle.Entry e) {
        if (isFirst(e)) return Component.translatable("screen.zenkai.rage.desc.form_first");
        return Component.translatable("screen.zenkai.rage.desc." + e.trigger().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component resultName(RageChronicle.Entry e) {
        return Component.translatable("screen.zenkai.rage.result." + e.result().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component viaName(RageChronicle.Entry e) {
        return Component.translatable(e.acquisition().equals("rage") ? "screen.zenkai.rage.via.rage" : "screen.zenkai.rage.via.other");
    }

    private static String pct(float v) { return Math.round(v) + "%"; }

    /** Segunda línea: la forma y el resultado (o «Ignorado: enfriamiento» / «Anotado»). */
    private static Component lineTwo(RageChronicle.Entry e) {
        ResourceLocation form = formOf(e);
        Component name = form == null ? Component.empty() : formName(form);
        return switch (e.result()) {
            case "AWAKENING" -> Component.translatable("screen.zenkai.rage.row.awakening", name);
            case "PROGRESS" -> Component.translatable("screen.zenkai.rage.row.progress", name, pct(e.masteryBefore()), pct(e.masteryAfter()));
            default -> isFirst(e) && form != null ? name
                    : Component.translatable(e.ignoredByCooldown() ? "screen.zenkai.rage.row.cooldown" : "screen.zenkai.rage.row.noted");
        };
    }

    private static int lineTwoColor(RageChronicle.Entry e) {
        if (isFirst(e)) return ZenkaiPalette.CHRONICLE_FORM;
        return switch (e.result()) {
            case "AWAKENING" -> ZenkaiPalette.CHRONICLE_AWAKENING;
            case "PROGRESS" -> ZenkaiPalette.CHRONICLE_PROGRESS;
            default -> ZenkaiPalette.CHRONICLE_RECORDED;
        };
    }

    /** Etiqueta de la derecha: de dónde vino el hecho. */
    private static Component tagText(RageChronicle.Entry e) {
        if (isFirst(e)) return Component.translatable("screen.zenkai.rage.tag.other");
        if (RageTrigger.PET_DEATH == triggerOf(e)) return Component.translatable("screen.zenkai.rage.tag.pet");
        if (e.result().equals("RECORDED")) return Component.translatable("screen.zenkai.rage.tag.noted");
        return Component.translatable("screen.zenkai.rage.tag.rage");
    }

    private static int tagColor(RageChronicle.Entry e) {
        return e.result().equals("RECORDED") || isFirst(e) ? ZenkaiPalette.CHRONICLE_RECORDED : lineTwoColor(e);
    }

    /** «Día 14 02:35»: día y hora del mundo (el tick 0 es las 6:00). */
    static Component dateText(long worldTime) {
        long day = worldTime / 24000L;
        long tod = worldTime % 24000L;
        long hours = (tod / 1000L + 6L) % 24L;
        long minutes = (tod % 1000L) * 60L / 1000L;
        return Component.translatable("screen.zenkai.rage.date", day, String.format("%02d:%02d", hours, minutes));
    }
}
