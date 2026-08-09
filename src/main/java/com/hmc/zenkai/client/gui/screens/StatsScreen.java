package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.AlignmentPalette;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.StatBar;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.MinusIconButton;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.event.tick.KaiokenSystem;
import com.hmc.zenkai.event.tick.RacePassiveSystem;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.RaceStatTable;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.race.RacePassives;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.stats.RefundTpPacket;
import com.hmc.zenkai.feature.stats.SpendTpPacket;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pestaña PRINCIPAL del menú Zenkai.
 *
 * QUÉ CAMBIA Y POR QUÉ:
 *
 *  - EL POWER LEVEL ESTÁ EN LA CABECERA. Era el hueco más raro del mod: getPowerLevel() existe,
 *    el scouter lo lee, los maestros lo exigen y los logros lo comprueban, pero la pantalla del
 *    propio personaje no lo mostraba. En Dragon Ball ese número ES el personaje.
 *    Se muestran los DOS que importan: el liberable (tu tope real hoy) y, entre paréntesis, el
 *    aparente, que es lo único que ven los demás. Son distintos siempre que el Ki Control esté
 *    por debajo del tope, y confundirlos es confundir "cuánto valgo" con "cuánto enseño".
 *
 *  - KI CONTROL COMO BARRA. Gatea STR, DEX y WIL y decide tu PL aparente, y no aparecía en
 *    ningún sitio salvo el HUD.
 *
 *  - BARRAS DE BODY / STAMINA / KI. Estaban solo como texto "200/200" dentro de un popup que
 *    había que abrir a mano.
 *
 *  - BOTONES − JUNTO A LOS +. El reembolso lo calcula el servidor de forma proporcional a la
 *    curva (ver PlayerRaceStats#refundPoint); aquí solo se apaga el botón cuando ese atributo
 *    no tiene puntos INVERTIDOS, porque los de base racial no se venden.
 *
 *  - LA PASIVA RACIAL SE VE. Cada raza tiene ahora una identidad mecánica y el jugador tiene
 *    que poder leerla sin salir del juego.
 *
 *  - EL POPUP LATERAL sigue fuera del panel (aprovecha el ancho de la pantalla), pero deja de
 *    ser una lista plana: cabecera con marco ornamentado, tres secciones separadas (ofensiva,
 *    movilidad, carga) y valores alineados a la derecha. Y ahora contiene lo que NO cabe en el
 *    panel, en vez de repetir lo que ya está.
 */
public class StatsScreen extends ZenkaiMenuScreen {

    // Orden de atributos (MND al final)
    private static final List<ZenkaiAttributes> ORDER = List.of(
            ZenkaiAttributes.STRENGTH, ZenkaiAttributes.DEXTERITY, ZenkaiAttributes.CONSTITUTION,
            ZenkaiAttributes.WILLPOWER, ZenkaiAttributes.SPIRIT, ZenkaiAttributes.MIND
    );

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int MARGIN     = 12;
    private static final int HEADER_Y   = CONTENT_TOP;
    private static final int PL_Y       = HEADER_Y + 22;
    private static final int DIV_Y      = PL_Y + 22;
    private static final int ATTR_LABEL_Y = DIV_Y + 4;
    private static final int ATTR_Y0    = DIV_Y + 16;
    private static final int ATTR_STEP  = 18;
    private static final int BTN_W      = 12;

    private static final int PREVIEW_X1 = 148, PREVIEW_X2 = 244;
    private static final int PREVIEW_Y1 = DIV_Y + 8, PREVIEW_Y2 = PREVIEW_Y1 + 106;

    private static final int POOLS_Y    = ATTR_Y0 + 6 * ATTR_STEP + 6;
    private static final int POOL_STEP  = 11;
    private static final int BOTTOM_Y   = POOLS_Y + 3 * POOL_STEP + 6;

    private static final int ALIGN_BAR_W = 108, ALIGN_BAR_H = 6;
    private static final int POPUP_W = 132;

    private static final int[] TP_STEPS = {1, 10, 100, 1000, 10000, 100000};
    private int tpStepIndex = 0;

    private int tpcLabelX, tpcLabelY, tpcLabelW, tpcLabelH;
    private int alignBarX, alignBarY;
    private int plLabelX, plLabelY, plLabelW;
    private boolean showEffectiveStats = false;

    private int getCurrentTpStep() { return TP_STEPS[tpStepIndex]; }
    private void cycleTpStep()     { tpStepIndex = (tpStepIndex + 1) % TP_STEPS.length; }

    private record AttrArea(ZenkaiAttributes attr, int x, int y, int w, int h) {
        boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<AttrArea> attrAreas = new ArrayList<>();
    private final List<MinusIconButton> minusButtons = new ArrayList<>();

    public StatsScreen() { super(Component.translatable("screen.zenkai.stats_screen.title")); }

    @Override protected ZenkaiTab currentTab() { return ZenkaiTab.STATS; }

    @Override
    protected void initContent() {
        minusButtons.clear();
        int x = panelLeft + MARGIN;
        int y = panelTop + ATTR_Y0;

        for (ZenkaiAttributes a : ORDER) {
            final String name = a.name();

            MinusIconButton minus = new MinusIconButton(x, y, () -> refund(name));
            minusButtons.add(minus);
            addRenderableWidget(minus);

            addRenderableWidget(new PlusIconButton(x + BTN_W + 2, y,
                    () -> spend(name, getCurrentTpStep())));
            y += ATTR_STEP;
        }

        // TPx + botón de multiplicador (abajo-izquierda)
        Font font = this.font;
        tpcLabelX = panelLeft + MARGIN;
        tpcLabelY = panelTop + BOTTOM_Y;
        tpcLabelW = font.width("TPx: x100000");
        tpcLabelH = font.lineHeight;
        addRenderableWidget(new PlusIconButton(tpcLabelX + tpcLabelW + 6, tpcLabelY - 2, this::cycleTpStep));

        // Botón que abre/cierra el popup de stats efectivas (bajo el render)
        int btnW = 80, btnH = 13;
        int btnX = panelLeft + (PREVIEW_X1 + PREVIEW_X2) / 2 - btnW / 2;
        int btnY = panelTop + PREVIEW_Y2 + 2;
        addRenderableWidget(new TextOnlyButton(btnX, btnY, btnW, btnH,
                Component.translatableWithFallback("screen.zenkai.stats_screen.effective", "Stats"),
                () -> showEffectiveStats = !showEffectiveStats)
                .textColors(ZenkaiPalette.LABEL_ON_PANEL, ZenkaiPalette.TEXT_HOVER, ZenkaiPalette.TEXT_OFF));

        // Barra de alineamiento (abajo-derecha)
        alignBarX = panelLeft + BG_W - MARGIN - ALIGN_BAR_W;
        alignBarY = panelTop + BOTTOM_Y + 10;
    }

    private void spend(String attrName, int points) {
        PacketDistributor.sendToServer(new SpendTpPacket(attrName, points));
    }

    /** El − siempre devuelve UN punto: el precio depende de la posición en la curva, así que
     *  devolver diez de golpe no es diez veces el mismo precio. Ver RefundTpPacket. */
    private void refund(String attrName) {
        PacketDistributor.sendToServer(new RefundTpPacket(attrName));
    }

    private void layoutRefundButtons() {
        if (att == null) return;
        for (int i = 0; i < minusButtons.size() && i < ORDER.size(); i++) {
            minusButtons.get(i).active = att.raceStats().canRefund(ORDER.get(i));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (mc.player == null) { super.render(g, mouseX, mouseY, partialTick); return; }
        att = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        PlayerFormAttachment form = mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        layoutRefundButtons();

        super.render(g, mouseX, mouseY, partialTick);

        Font font = this.font;
        int left = panelLeft + MARGIN;
        int right = panelLeft + BG_W - MARGIN;

        ScreenTitle.drawAbovePanel(g, font, this.title, panelLeft + BG_W / 2, panelTop);

        // ══ Cabecera: Raza | Estilo · Forma | TP ══
        int col2 = panelLeft + 130;
        int hy = panelTop + HEADER_Y;
        drawField(g, left, hy, "screen.zenkai.stats_screen.race", raceName(), ZenkaiPalette.OK);
        drawField(g, col2, hy, "screen.zenkai.stats_screen.style", styleName(), ZenkaiPalette.OK);
        hy += 10;
        drawField(g, left, hy, "screen.zenkai.stats_screen.form",
                formName(form.getFormId()), ZenkaiPalette.MAXED);
        drawField(g, col2, hy, "screen.zenkai.stats_screen.tp",
                Component.literal(String.valueOf(att.getTP())), ZenkaiPalette.VALUE);

        // ══ Power Level + Ki Control ══
        renderPowerBlock(g, font, left, right, panelTop + PL_Y);

        g.fill(left, panelTop + DIV_Y, right, panelTop + DIV_Y + 1, ZenkaiPalette.SEPARATOR);

        // ══ Atributos ══
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.attributes"),
                left, panelTop + ATTR_LABEL_Y, ZenkaiPalette.LABEL_ON_PANEL, false);

        attrAreas.clear();
        int ay = panelTop + ATTR_Y0 + 2;
        int ax = left + BTN_W * 2 + 6;
        for (ZenkaiAttributes a : ORDER) {
            int raw = att.getAttribute(a);
            int eff = att.getEffectiveAttribute(a);
            Component line = (eff != raw)
                    ? getAttributeLabel(a, eff + " (" + raw + ")")
                    : getAttributeLabel(a, String.valueOf(raw));
            g.drawString(font, line, ax, ay,
                    (eff != raw) ? ZenkaiPalette.VALUE : ZenkaiPalette.LABEL_ON_PANEL, false);
            attrAreas.add(new AttrArea(a, ax, ay, font.width(line), font.lineHeight));
            ay += ATTR_STEP;
        }

        // ══ Render del jugador ══
        int px1 = panelLeft + PREVIEW_X1, px2 = panelLeft + PREVIEW_X2;
        int py1 = panelTop + PREVIEW_Y1,  py2 = panelTop + PREVIEW_Y2;
        g.fill(px1 - 1, py1 - 1, px2 + 1, py2 + 1, ZenkaiPalette.SEPARATOR);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, px1, py1, px2, py2, 40, 0.0625f, (float) mouseX, (float) mouseY, mc.player);

        // ══ Pools ══
        renderPools(g, font, left, right, panelTop + POOLS_Y);

        // ══ Pie: TPx / coste + alineamiento ══
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.tpx", getCurrentTpStep()),
                tpcLabelX, tpcLabelY, ZenkaiPalette.LABEL_ON_PANEL, false);
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.cost", computeCurrentTpCost()),
                tpcLabelX, tpcLabelY + font.lineHeight + 3, ZenkaiPalette.BODY_ON_PANEL, false);

        renderAlignmentBar(g, font, att.getAlignment(), mouseX, mouseY);

        if (showEffectiveStats) renderEffectiveStatsPopup(g, font);

        // ══ Tooltips ══
        renderPlayerHoverTooltip(g, form, mouseX, mouseY, px1, py1, px2, py2);
        renderAttributeTooltip(g, mouseX, mouseY);
        renderTpStepTooltip(g, mouseX, mouseY);
        renderPowerTooltip(g, mouseX, mouseY);
    }

    /** Etiqueta en marrón + valor en color. Sin sombra: el panel es beige claro. */
    private void drawField(GuiGraphics g, int x, int y, String key, Component value, int valueColor) {
        Component label = Component.translatable(key);
        g.drawString(this.font, label, x, y, ZenkaiPalette.LABEL_ON_PANEL, false);
        g.drawString(this.font, value, x + this.font.width(label), y, valueColor, false);
    }

    // ── Bloque de poder ──────────────────────────────────────────────────────

    /**
     * Izquierda: PL liberable, grande y dorado. Derecha: barra de Ki Control.
     *
     * Se muestra el LIBERABLE y no el crudo porque es el número que el jugador puede usar hoy;
     * el crudo incluye potencial que su nivel de Ki Control aún no le deja sacar, y enseñarlo
     * como "tu poder" haría que se sintiera más débil de lo que dice su propia ficha cada vez
     * que pelea. El aparente va detrás, atenuado: es lo que leen los scouters ajenos.
     */
    private void renderPowerBlock(GuiGraphics g, Font font, int left, int right, int y) {
        long releasable = att.getReleasablePowerLevel();
        long apparent   = att.getApparentPowerLevel();

        Component label = Component.translatable("screen.zenkai.stats_screen.power_level");
        g.drawString(font, label, left, y, ZenkaiPalette.LABEL_ON_PANEL, false);

        Component value = Component.literal(compact(releasable)).withStyle(ChatFormatting.BOLD);
        int vx = left + font.width(label) + 2;
        g.drawString(font, value, vx, y, ZenkaiPalette.GOLD, false);

        plLabelX = left;
        plLabelY = y;
        plLabelW = font.width(label) + 2 + font.width(value);

        // El aparente solo aparece cuando DIFIERE: si estás al tope, repetirlo es ruido.
        if (apparent != releasable) {
            Component ap = Component.literal(" (" + compact(apparent) + ")");
            g.drawString(font, ap, vx + font.width(value), y, ZenkaiPalette.MUTED_ON_PANEL, false);
            plLabelW += font.width(ap);
        }

        // Ki Control: barra a la derecha, con el tope de la habilidad marcado.
        int barW = 90;
        int bx = right - barW;
        int by = y + font.lineHeight + 1;
        Component kc = Component.translatable("screen.zenkai.stats_screen.ki_control");
        g.drawString(font, kc, bx, y, ZenkaiPalette.LABEL_ON_PANEL, false);
        Component pct = Component.literal(att.getPowerPercent() + "%");
        g.drawString(font, pct, right - font.width(pct), y, ZenkaiPalette.BODY_ON_PANEL, false);

        StatBar.draw(g, bx, by, barW, StatBar.H, att.getPowerPercent(), 100.0,
                ZenkaiPalette.BAR_CONTROL);

        // Marca del techo: hasta dónde puede subir con su nivel de habilidad. Sin esta marca,
        // un jugador con el tope al 70 % no tiene forma de saber por qué no llega al 100.
        int cap = Math.min(100, SkillEffects.maxPowerPercent(att));
        if (cap < 100) {
            int cx = bx + Math.round(barW * (cap / 100f));
            g.fill(cx, by - 2, cx + 1, by + StatBar.H + 2, ZenkaiPalette.DENIED);
        }
    }

    /** Miles y millones abreviados: un PL de siete cifras no cabe en la cabecera. */
    private static String compact(long v) {
        if (v < 10_000) return String.valueOf(v);
        if (v < 1_000_000) return String.format(Locale.ROOT, "%.1fK", v / 1_000.0);
        if (v < 1_000_000_000L) return String.format(Locale.ROOT, "%.2fM", v / 1_000_000.0);
        return String.format(Locale.ROOT, "%.2fB", v / 1_000_000_000.0);
    }

    // ── Pools ────────────────────────────────────────────────────────────────

    /** Tres barras compactas: etiqueta a la izquierda, barra en medio, valor a la derecha. */
    private void renderPools(GuiGraphics g, Font font, int left, int right, int y) {
        int labelW = 42;
        int valueW = 54;
        int barX = left + labelW;
        int barW = (right - valueW - 2) - barX;

        poolRow(g, font, left, barX, barW, y, "screen.zenkai.stats_screen.stat.body_short",
                att.getBody(), att.getBodyMax(), ZenkaiPalette.BAR_BODY, right);
        poolRow(g, font, left, barX, barW, y + POOL_STEP, "screen.zenkai.stats_screen.stat.stamina_short",
                att.getStamina(), att.getStaminaMax(), ZenkaiPalette.BAR_STAMINA, right);
        poolRow(g, font, left, barX, barW, y + POOL_STEP * 2, "screen.zenkai.stats_screen.stat.ki_short",
                att.getEnergy(), att.getEnergyMax(), ZenkaiPalette.BAR_KI, right);
    }

    private void poolRow(GuiGraphics g, Font font, int labelX, int barX, int barW, int y,
                         String key, int cur, int max, int color, int right) {
        g.drawString(font, Component.translatable(key), labelX, y, ZenkaiPalette.LABEL_ON_PANEL, false);
        StatBar.draw(g, barX, y + 1, barW, StatBar.H - 1, cur, max, color);
        Component v = Component.literal(cur + "/" + max);
        g.drawString(font, v, right - font.width(v), y, ZenkaiPalette.BODY_ON_PANEL, false);
    }

    // ── Nombres traducibles ──────────────────────────────────────────────────
    private Component raceName() {
        return Component.translatable("screen.zenkai.race." + att.getRace().name().toLowerCase(Locale.ROOT));
    }
    private Component styleName() {
        return Component.translatable("screen.zenkai.style." + att.getStyle().name().toLowerCase(Locale.ROOT));
    }
    private static Component formName(ResourceLocation formId) {
        String key = "form." + formId.getNamespace() + "." + formId.getPath().replace('/', '.');
        String last = formId.getPath();
        int slash = last.lastIndexOf('/');
        if (slash >= 0) last = last.substring(slash + 1);
        StringBuilder pretty = new StringBuilder();
        for (String w : last.split("_")) {
            if (w.isEmpty()) continue;
            if (!pretty.isEmpty()) pretty.append(' ');
            pretty.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return Component.translatableWithFallback(key, pretty.toString());
    }

    // ── Barra de alineamiento ────────────────────────────────────────────────
    private void renderAlignmentBar(GuiGraphics g, Font font, int alignment, int mouseX, int mouseY) {
        Component label = Component.translatableWithFallback(
                "screen.zenkai.stats_screen.alignment", "Alignment");
        g.drawString(font, label, alignBarX, alignBarY - 10, ZenkaiPalette.LABEL_ON_PANEL, false);

        g.fill(alignBarX - 1, alignBarY - 1, alignBarX + ALIGN_BAR_W + 1, alignBarY + ALIGN_BAR_H + 1,
                ZenkaiPalette.BAR_FRAME);
        for (int i = 0; i < ALIGN_BAR_W; i++) {
            int rgb = AlignmentPalette.gradient(i / (float) (ALIGN_BAR_W - 1));
            g.fill(alignBarX + i, alignBarY, alignBarX + i + 1, alignBarY + ALIGN_BAR_H, 0xFF000000 | rgb);
        }

        int mx = alignBarX + Math.round((alignment + 100) / 200f * (ALIGN_BAR_W - 1));
        g.fill(mx - 1, alignBarY - 2, mx + 2, alignBarY + ALIGN_BAR_H + 2, 0xFFFFFFFF);

        if (mouseX >= alignBarX && mouseX < alignBarX + ALIGN_BAR_W
                && mouseY >= alignBarY - 2 && mouseY < alignBarY + ALIGN_BAR_H + 2) {
            String v = (alignment > 0 ? "+" : "") + alignment;
            g.renderTooltip(font, Component.literal(v), mouseX, mouseY);
        }
    }

    // ── Popup lateral de stats efectivas ─────────────────────────────────────

    /** Fila del popup: etiqueta a la izquierda, valor alineado a la derecha. */
    private record StatRow(Component label, Component value, int color) {
        static StatRow header(Component label) { return new StatRow(label, null, 0); }
        boolean isHeader() { return value == null; }
    }

    /**
     * Popup FUERA del panel, a la derecha (o a la izquierda si no cabe). Ahora lleva marco
     * ornamentado, cabecera propia y secciones separadas, y contiene lo que NO está en el panel:
     * el desglose ofensivo, la movilidad y la carga. Repetir los pools aquí, como hacía antes,
     * gastaba la mitad del espacio en decir dos veces lo mismo.
     */
    private void renderEffectiveStatsPopup(GuiGraphics g, Font font) {
        assert mc.player != null;
        List<StatRow> rows = buildStatRows();

        int h = 8 + 12;
        for (StatRow r : rows) h += r.isHeader() ? 12 : 10;
        h += 6;

        int x = panelLeft + BG_W + 8;
        if (x + POPUP_W > this.width - 2) x = panelLeft - POPUP_W - 8;
        int y = panelTop + CONTENT_TOP;

        // Marco de tres anillos, como el panel: pertenece a la misma familia visual.
        g.fill(x - 2, y - 2, x + POPUP_W + 2, y + h + 2, ZenkaiPalette.BORDER_IN);
        g.fill(x - 1, y - 1, x + POPUP_W + 1, y + h + 1, ZenkaiPalette.BORDER_MID);
        g.fill(x, y, x + POPUP_W, y + h, 0xF01E1410);

        int tx = x + 7, tr = x + POPUP_W - 7, ty = y + 6;

        g.drawString(font, ScreenTitle.styled(
                        Component.translatable("screen.zenkai.stats_screen.stats")),
                tx, ty, ZenkaiPalette.GOLD, true);
        ty += 12;

        for (StatRow r : rows) {
            if (r.isHeader()) {
                g.fill(tx, ty + 1, tr, ty + 2, ZenkaiPalette.SEPARATOR);
                g.drawString(font, r.label(), tx, ty + 4, ZenkaiPalette.MUTED_ON_PANEL, false);
                ty += 12;
            } else {
                g.drawString(font, r.label(), tx, ty, ZenkaiPalette.TEXT_DIM, false);
                g.drawString(font, r.value(), tr - font.width(r.value()), ty, r.color(), false);
                ty += 10;
            }
        }
    }

    private List<StatRow> buildStatRows() {
        assert mc.player != null;
        List<StatRow> out = new ArrayList<>();
        boolean majin = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).isMajinControlled();

        out.add(StatRow.header(Component.translatable("screen.zenkai.stats_screen.section.offense")));
        out.add(row("screen.zenkai.stats_screen.stat.melee", fmt(att.computeMeleeFinal()), ZenkaiPalette.TEXT));
        out.add(row("screen.zenkai.stats_screen.stat.defense", fmt(att.computeDefenseFinal()), ZenkaiPalette.TEXT));
        out.add(row("screen.zenkai.stats_screen.stat.ki_power", fmt(att.computeKiPowerFinal()), ZenkaiPalette.TEXT));
        if (majin) {
            out.add(row("screen.zenkai.stats_screen.majin_boost",
                    "+" + Math.round(CommonConfig.majinStatBonus() * 100) + "%", ZenkaiPalette.ERROR));
        }

        out.add(StatRow.header(Component.translatable("screen.zenkai.stats_screen.section.mobility")));
        out.add(row("screen.zenkai.stats_screen.stat.running",
                Math.round(att.getMoveMultiplier() * 100) + "%", ZenkaiPalette.TEXT));
        out.add(row("screen.zenkai.stats_screen.stat.flying",
                Math.round(att.getFlyMultiplier() * 100) + "%", ZenkaiPalette.TEXT));

        // Carga: solo si lleva pesas. Los números salen de WeightSystem, nunca de una fórmula
        // local, o la pantalla y el juego se separarían al primer ajuste.
        double load = att.getWeightLoad();
        if (load > 0.0) {
            out.add(StatRow.header(Component.translatable("screen.zenkai.stats_screen.section.load")));
            out.add(row("screen.zenkai.stats_screen.stat.load_short",
                    String.format(Locale.ROOT, "%.2f / %.2f t",
                            WeightSystem.equippedTons(mc.player),
                            WeightSystem.capacityTons(att.getPowerLevelRaw())), ZenkaiPalette.TEXT));
            out.add(row("screen.zenkai.stats_screen.stat.load_pct",
                    Math.round(load * 100) + "%", ZenkaiPalette.VALUE));
            out.add(row("screen.zenkai.stats_screen.stat.weight_tp",
                    "x" + String.format(Locale.ROOT, "%.2f", WeightSystem.tpFactor(load)),
                    ZenkaiPalette.OK));
        }

        out.add(StatRow.header(Component.translatable("screen.zenkai.stats_screen.section.investment")));
        out.add(row("screen.zenkai.stats_screen.tp_spent",
                String.valueOf(att.raceStats().getTpSpent()), ZenkaiPalette.VALUE));
        out.add(row("screen.zenkai.stats_screen.points_invested",
                String.valueOf(att.raceStats().totalInvested()), ZenkaiPalette.TEXT));

        return out;
    }

    private StatRow row(String key, String value, int color) {
        return new StatRow(Component.translatable(key), Component.literal(value), color);
    }

    private static String fmt(double d) { return String.format(Locale.ROOT, "%.1f", d); }
    private static String fmt2(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    // ── Tooltips ─────────────────────────────────────────────────────────────

    /** Hover sobre el PL: los tres números y qué significa cada uno. */
    private void renderPowerTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX < plLabelX || mouseX >= plLabelX + plLabelW
                || mouseY < plLabelY || mouseY >= plLabelY + this.font.lineHeight) return;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.zenkai.stats_screen.pl.raw",
                String.valueOf(att.getPowerLevelRaw())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.zenkai.stats_screen.pl.releasable",
                String.valueOf(att.getReleasablePowerLevel())).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("screen.zenkai.stats_screen.pl.apparent",
                String.valueOf(att.getApparentPowerLevel())).withStyle(ChatFormatting.AQUA));
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /** Hover sobre el render: forma, maestría, kaioken, pasiva racial. */
    private void renderPlayerHoverTooltip(GuiGraphics g, PlayerFormAttachment form,
                                          int mouseX, int mouseY, int x1, int y1, int x2, int y2) {
        if (mouseX < x1 || mouseX >= x2 || mouseY < y1 || mouseY >= y2) return;
        float mastery = form.getFormMastery(form.getFormId());
        List<Component> lines = new ArrayList<>();
        lines.add(formName(form.getFormId()));
        lines.add(Component.translatableWithFallback("screen.zenkai.stats_screen.mastery",
                "Mastery: %s%%", fmt(mastery)).withStyle(ChatFormatting.GOLD));
        if (att != null) {
            lines.add(Component.translatableWithFallback("screen.zenkai.stats_screen.multiplier",
                    "Multiplier: x%s", fmt2(att.getStatMultiplier())).withStyle(ChatFormatting.GREEN));
        }
        if (!FormIds.BASE.equals(form.getFormId())) {
            double kiPerSecond = form.formKiDrainPerTick() * 20.0;
            lines.add(kiPerSecond > 0.0
                    ? Component.translatableWithFallback("screen.zenkai.stats_screen.form_drain",
                    "-%s ki/s", fmt(kiPerSecond)).withStyle(ChatFormatting.AQUA)
                    : Component.translatableWithFallback("screen.zenkai.stats_screen.form_no_drain",
                    "No ki cost", "No ki cost").withStyle(ChatFormatting.DARK_AQUA));
        }

        KaiokenTier tier = form.getKaioken();
        if (tier.isOn() && att != null && mc.player != null) {
            double hpPerSecond = KaiokenSystem.drainPerTick(att, form,
                    SkillEffects.kaiokenDrainFactor(mc.player)) * 20.0;
            lines.add(Component.translatableWithFallback("screen.zenkai.stats_screen.kaioken",
                    "Kaioken %s", tier.label()).withStyle(ChatFormatting.RED));
            lines.add(Component.translatableWithFallback("screen.zenkai.stats_screen.kaioken_mastery",
                    "  Mastery: %s%%", fmt(form.getKaiokenMastery())).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatableWithFallback("screen.zenkai.stats_screen.kaioken_stats",
                    "  +%s%% stats", fmt(tier.statPercent() * 100.0)).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatableWithFallback("screen.zenkai.stats_screen.kaioken_drain",
                    "  -%s HP/s", fmt(hpPerSecond)).withStyle(ChatFormatting.DARK_RED));
        }
        if (att != null && mc.player != null && form.isStrained(mc.player.level().getGameTime())) {
            lines.add(Component.translatableWithFallback("screen.zenkai.stats_screen.kaioken_strain",
                            "Strain: %ss (-10%% stats)",
                            fmt(form.strainSecondsLeft(mc.player.level().getGameTime())))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }

        // Pasiva racial: separada del resto por una línea en blanco. Es información permanente
        // del personaje, no un estado momentáneo como el kaioken.
        if (att != null && att.isRaceChosen()) {
            lines.add(Component.empty());
            lines.add(Component.translatable(RacePassives.nameKey(att.getRace()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            for (var l : this.font.getSplitter().splitLines(
                    Component.translatable(RacePassives.descKey(att.getRace())),
                    170, net.minecraft.network.chat.Style.EMPTY)) {
                lines.add(Component.literal(l.getString()).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (mc.player != null && RacePassiveSystem.zenkaiActive(mc.player)) {
                lines.add(Component.translatable("screen.zenkai.stats_screen.zenkai_active",
                                RacePassiveSystem.zenkaiSecondsLeft(mc.player))
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private void renderTpStepTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX >= tpcLabelX && mouseX < tpcLabelX + tpcLabelW &&
                mouseY >= tpcLabelY && mouseY < tpcLabelY + tpcLabelH) {
            g.renderTooltip(this.font, Component.translatable("screen.zenkai.stats_screen.tp_des"), mouseX, mouseY);
        }
    }

    private void renderAttributeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (AttrArea area : attrAreas) {
            if (area.contains(mouseX, mouseY)) {
                g.renderComponentTooltip(this.font, getAttributeDescription(area.attr()), mouseX, mouseY);
                break;
            }
        }
    }

    private int computeCurrentTpCost() {
        if (att == null) return 0;
        // El coste depende del TOTAL invertido, no del atributo: es el mismo para los seis.
        // El bucle anterior buscaba el mínimo entre atributos, lo que solo servía para tapar
        // los que ya estaban al tope — y hacía creer que unos atributos eran más baratos.
        return att.previewTpCost(ZenkaiAttributes.STRENGTH, getCurrentTpStep());
    }

    private Component getAttributeLabel(ZenkaiAttributes attr, String value) {
        return switch (attr) {
            case STRENGTH     -> Component.translatable("attribute.zenkai.str", value);
            case DEXTERITY    -> Component.translatable("attribute.zenkai.dex", value);
            case CONSTITUTION -> Component.translatable("attribute.zenkai.con", value);
            case WILLPOWER    -> Component.translatable("attribute.zenkai.wil", value);
            case MIND         -> Component.translatable("attribute.zenkai.mnd", value);
            case SPIRIT       -> Component.translatable("attribute.zenkai.spi", value);
        };
    }

    /**
     * Descripción del atributo: rendimiento principal + las contribuciones CRUZADAS.
     *
     * Sin la segunda línea las sinergias serían invisibles y por tanto inexistentes para el
     * jugador: nadie va a deducir que su CON le está dando defensa comparando números.
     */
    private List<Component> getAttributeDescription(ZenkaiAttributes attr) {
        Race race = att.getRace();
        Style style = att.getStyle();
        List<Component> out = new ArrayList<>();

        switch (attr) {
            case STRENGTH -> {
                out.add(Component.translatable("tooltip.zenkai.attr.str",
                        fmt(RaceStatTable.melee(race, style))));
            }
            case CONSTITUTION -> {
                out.add(Component.translatable("tooltip.zenkai.attr.con",
                        fmt(RaceStatTable.health(race, style)),
                        fmt(RaceStatTable.stamina(race, style))));
                out.add(cross("tooltip.zenkai.attr.con.cross",
                        fmt(RaceStatTable.defense(race, style)
                                * com.hmc.zenkai.feature.StatSynergy.DEFENSE_FROM_CON)));
            }
            case DEXTERITY -> {
                out.add(Component.translatable("tooltip.zenkai.attr.dex",
                        fmt(RaceStatTable.defense(race, style))));
            }
            case WILLPOWER -> {
                out.add(Component.translatable("tooltip.zenkai.attr.wil",
                        fmt(RaceStatTable.kiDamage(race, style))));
                out.add(cross("tooltip.zenkai.attr.wil.cross",
                        fmt(RaceStatTable.melee(race, style)
                                * com.hmc.zenkai.feature.StatSynergy.MELEE_FROM_WIL)));
            }
            case SPIRIT -> {
                out.add(Component.translatable("tooltip.zenkai.attr.spi",
                        fmt(RaceStatTable.kiReserves(race, style))));
                out.add(cross("tooltip.zenkai.attr.spi.cross",
                        fmt(RaceStatTable.kiDamage(race, style)
                                * com.hmc.zenkai.feature.StatSynergy.KIPOWER_FROM_SPI)));
            }
            case MIND -> {
                out.add(Component.translatable("tooltip.zenkai.attr.mnd"));
                int free = att.mindFree();
                out.add(Component.translatable("tooltip.zenkai.attr.mnd.free", free)
                        .withStyle(free < 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
            }
        }

        int inv = att.raceStats().investedIn(attr);
        out.add(Component.translatable("tooltip.zenkai.attr.invested", inv)
                .withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    private Component cross(String key, String value) {
        return Component.translatable(key, value).withStyle(ChatFormatting.DARK_AQUA);
    }
}