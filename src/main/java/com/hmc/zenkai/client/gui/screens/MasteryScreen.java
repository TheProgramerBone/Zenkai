package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.event.tick.KaiokenSystem;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.PotentialUnlock;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SuperForms;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pestaña MAESTRÍAS. Existe porque la maestría se acumulaba desde hace mucho y no se veía en
 * ningún sitio: el jugador entrenaba a ciegas sin saber ni cuánto llevaba ni qué le daba.
 * QUÉ RESPONDE ESTA PANTALLA, y por qué es distinta del tooltip de stats: aquí no interesa
 * "cuánto valgo ahora" (eso ya lo dice el tooltip del render en Stats, con el multiplicador
 * efectivo y el ki/s reales), sino "QUÉ ME QUEDA POR GANAR". Por eso cada fila muestra el
 * valor ACTUAL y el valor AL 100 %: sin el segundo, un 40 % de maestría es un número sin
 * significado.
 * Solo se listan cosas que el jugador YA TIENE: formas compradas, técnicas con JSON activo,
 * kaioken si tiene la habilidad. Enseñar en gris lo que no posee convertiría la pantalla en un
 * catálogo y no en un registro de progreso.
 * FILAS DE ALTURA FIJA, cabeceras incluidas. Desperdicia unos píxeles en las cabeceras, pero
 * permite reutilizar tal cual el scroll por filas enteras de SkillsScreen: con alturas
 * variables habría que llevar índices y offsets aparte, y aparecen medias filas cortadas.
 */
public class MasteryScreen extends ZenkaiMenuScreen {

    private static final int ROW_H = 26;
    private static final int LIST_TOP_OFF = CONTENT_TOP;
    private static final int LIST_BOTTOM_MARGIN = 14;
    private static final int TEXT_X_OFF = 16;
    private static final int SCROLLBAR_W = 4;
    private static final int BAR_H = 3;

    private static final int COL_HEADER = 0xFFFFD966;
    private static final int COL_NAME   = 0xFF7CFC7C;
    private static final int COL_DETAIL = 0xFFAAAAAA;
    private static final int COL_PCT    = 0xFFFFFFFF;
    private static final int COL_BAR_BG = 0x40000000;
    private static final int COL_BAR_FG = 0xFF7FD4FF;
    private static final int COL_BAR_MAX = 0xFF7CFC7C;
    private static final int COL_SEP    = 0x22FFFFFF;

    /** Una fila: cabecera de sección (mastery &lt; 0) o entrada con progreso. */
    private record Row(Component name, Component detail, float mastery) {
        static Row header(Component name) { return new Row(name, Component.empty(), -1f); }
        boolean isHeader() { return mastery < 0f; }
    }

    private final List<Row> rows = new ArrayList<>();
    private int scrollRow = 0;

    public MasteryScreen() {
        super(Component.translatable(ZenkaiTab.MASTERY.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.MASTERY; }

    @Override
    protected void initContent() {
        scrollRow = 0;
    }

    // ── Construcción de filas ────────────────────────────────────────────────

    /**
     * Se reconstruye en cada frame a propósito. La maestría sube de forma continua (cada 5 s
     * llega un sync), así que cachear la lista obligaría a invalidarla desde el sistema de
     * maestría; y son 20 filas de aritmética, no un coste medible.
     */
    private void rebuildRows(PlayerStatsAttachment st, PlayerFormAttachment form) {
        rows.clear();
        if (st == null || form == null) return;

        addForms(st, form);
        addKaioken(form);
        addKiTechniques(st);
        addPhysical(st);

        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    private void addForms(PlayerStatsAttachment st, PlayerFormAttachment form) {
        Race race = st.getRace();
        List<Row> out = new ArrayList<>();

        for (ResourceLocation id : SuperForms.chain(race)) {
            if (!SuperForms.unlocked(mc.player, id)) continue;
            FormDef d = FormDef.get(id);
            if (d == null) continue;
            float m = form.getFormMastery(id);
            out.add(new Row(
                    Component.translatable("form.zenkai." + id.getPath()),
                    Component.literal(statsRange(d.statPercent(m), d.statPercentMastered())
                            + "  " + drainRange(d.kiDrainPerTick(m), d.kiDrainMastered())),
                    m));
        }

        // Potential Unlock va aparte: no está en la cadena (es divine) y su techo no sale del
        // JSON sino del acoplamiento, así que el "al 100 %" hay que calcularlo aquí.
        if (SkillEffects.level(mc.player, SkillEffects.POTENTIAL_UNLOCK) > 0) {
            float m = form.getFormMastery(FormIds.POTENTIAL_UNLOCK);
            double ceiling = form.getPuCeiling();
            out.add(new Row(
                    Component.translatable("form.zenkai.potential_unlock"),
                    Component.literal(statsRange(
                            PotentialUnlock.statPercent(ceiling, m),
                            ceiling * PotentialUnlock.MASTERY_CEIL_FACTOR)
                            + "  " + noDrainLabel()),
                    m));
        }

        if (out.isEmpty()) return;
        rows.add(Row.header(Component.translatable("screen.zenkai.mastery.forms")));
        rows.addAll(out);
    }

    private void addKaioken(PlayerFormAttachment form) {
        if (SkillEffects.level(mc.player, "kaioken") <= 0) return;
        float m = form.getKaiokenMastery();
        // Una sola fila: la maestría es general. Lo que cambia entre escalones es el ritmo de
        // ganancia, no el progreso, así que cinco filas mostrarían el mismo número cinco veces.
        double factor = KaiokenSystem.masteryFactor(form);
        rows.add(Row.header(Component.translatable("screen.zenkai.mastery.kaioken")));
        rows.add(new Row(
                Component.translatable("screen.zenkai.mastery.kaioken_row"),
                Component.translatable("screen.zenkai.mastery.kaioken_detail",
                        pct((1.0 - factor) * 100.0)),
                m));
    }

    private void addKiTechniques(PlayerStatsAttachment st) {
        List<Row> out = new ArrayList<>();
        for (KiTechniqueType t : KiTechniqueType.values()) {
            if (!t.enabled()) continue;
            float m = st.getTechniqueMastery(t.name());
            out.add(new Row(Component.translatable(t.nameKey()), techDetail(m, true), m));
        }
        if (out.isEmpty()) return;
        rows.add(Row.header(Component.translatable("screen.zenkai.mastery.ki")));
        rows.addAll(out);
    }

    private void addPhysical(PlayerStatsAttachment st) {
        List<Row> out = new ArrayList<>();
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (!t.enabled()) continue;
            float m = st.getTechniqueMastery(t.name());
            out.add(new Row(Component.translatable(t.nameKey()), techDetail(m, false), m));
        }
        if (out.isEmpty()) return;
        rows.add(Row.header(Component.translatable("screen.zenkai.mastery.physical")));
        rows.addAll(out);
    }

    // ── Formato ──────────────────────────────────────────────────────────────

    /** "x1.40 -> x2.50" : multiplicador de stats actual y con la forma dominada. */
    private static String statsRange(double now, double mastered) {
        return "x" + f2(1.0 + now) + " \u2192 x" + f2(1.0 + mastered);
    }

    /** "0.40 -> 0.10 ki/s": el drenaje BAJA al dominar, de ahí que el orden parezca al revés. */
    private static String drainRange(double now, double mastered) {
        return f2(now * 20.0) + " \u2192 " + f2(mastered * 20.0) + " ki/s";
    }

    private static String noDrainLabel() { return "0 ki/s"; }

    /**
     * Detalle de una técnica. Los tres factores salen de MasteryEffects, y el "al 100 %" es
     * directamente el valor de config: así la pantalla no reimplementa las curvas, solo las
     * evalúa en los dos extremos que importan.
     */
    private Component techDetail(float mastery, boolean ki) {
        double m = mastery / 100.0;
        double dmg  = CommonConfig.masteryTechDamageBonus()   * m;
        double cost = CommonConfig.masteryTechCostReduction()  * m;
        StringBuilder sb = new StringBuilder();
        sb.append("+").append(pct(dmg * 100.0)).append(" dmg");
        sb.append("  -").append(pct(cost * 100.0)).append(" cost");
        if (ki) {
            double cast = CommonConfig.masteryTechCastReduction() * m;
            sb.append("  -").append(pct(cast * 100.0)).append(" charge");
        }
        return Component.literal(sb.toString());
    }

    private static String f2(double d) { return String.format(Locale.ROOT, "%.2f", d); }
    private static String pct(double d) { return String.format(Locale.ROOT, "%.0f%%", d); }
    private static String f1(double d) { return String.format(Locale.ROOT, "%.1f", d); }

    // ── Geometría ────────────────────────────────────────────────────────────

    private int listTop()    { return panelTop + LIST_TOP_OFF; }
    private int listHeight() { return BG_H - LIST_TOP_OFF - LIST_BOTTOM_MARGIN; }
    private int visibleRows(){ return Math.max(1, listHeight() / ROW_H); }
    private int viewHeight() { return visibleRows() * ROW_H; }
    private int maxScroll()  { return Math.max(0, rows.size() - visibleRows()); }
    private int rowTop(int i){ return listTop() + (i - scrollRow) * ROW_H; }
    private int barW()       { return BG_W - TEXT_X_OFF * 2 - 8; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        PlayerStatsAttachment st = mc.player == null ? att
                : mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        PlayerFormAttachment form = mc.player == null ? null
                : mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        rebuildRows(st, form);

        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);

        if (rows.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("screen.zenkai.mastery.empty"),
                    panelLeft + BG_W / 2, panelTop + BG_H / 2 - 4, 0xFFAAAAAA);
            return;
        }

        int textX = panelLeft + TEXT_X_OFF;
        g.enableScissor(panelLeft, listTop(), panelLeft + BG_W, listTop() + viewHeight());

        int last = Math.min(rows.size(), scrollRow + visibleRows());
        for (int i = scrollRow; i < last; i++) {
            Row r = rows.get(i);
            int y = rowTop(i);

            if (r.isHeader()) {
                g.drawString(this.font, r.name(), textX, y + 8, COL_HEADER, true);
                g.fill(textX, y + 20, panelLeft + BG_W - TEXT_X_OFF, y + 21, COL_SEP);
                continue;
            }

            g.drawString(this.font, r.name(), textX + 4, y + 1, COL_NAME);

            String pctText = f1(r.mastery()) + "%";
            int pctX = panelLeft + BG_W - TEXT_X_OFF - this.font.width(pctText);
            g.drawString(this.font, pctText, pctX, y + 1, COL_PCT);

            g.drawString(this.font, r.detail(), textX + 4, y + 11, COL_DETAIL);

            int bx = textX + 4, by = y + 21, bw = barW();
            g.fill(bx, by, bx + bw, by + BAR_H, COL_BAR_BG);
            int filled = (int) (bw * Mth.clamp(r.mastery() / 100f, 0f, 1f));
            if (filled > 0) {
                g.fill(bx, by, bx + filled, by + BAR_H,
                        r.mastery() >= 100f ? COL_BAR_MAX : COL_BAR_FG);
            }
        }

        g.disableScissor();
        drawScrollbar(g);
    }

    private void drawScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) return;
        int x = panelLeft + BG_W - 10;
        int top = listTop(), h = viewHeight();
        g.fill(x, top, x + SCROLLBAR_W, top + h, COL_BAR_BG);
        int thumbH = Math.max(12, h * visibleRows() / rows.size());
        int thumbY = top + (h - thumbH) * scrollRow / max;
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, COL_HEADER);
    }
}