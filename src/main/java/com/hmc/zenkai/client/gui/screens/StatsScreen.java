package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.mastery.MasteryEffects;
import com.hmc.zenkai.core.network.feature.ZenkaiAttributes;
import com.hmc.zenkai.core.network.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.*;
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
 * Pestaña PRINCIPAL del menú Zenkai (renovada).
 * Layout: cabecera (Race/Style/Form/TP) · izquierda atributos · derecha render del
 * jugador (hover = maestría de la forma actual) · botón que abre popup lateral con
 * stats efectivas · abajo TPC/coste + barra de alineamiento.
 */
public class StatsScreen extends ZenkaiMenuScreen {

    private static final int PAD = 8;

    // Orden de atributos (MND al final)
    private static final List<ZenkaiAttributes> ORDER = List.of(
            ZenkaiAttributes.STRENGTH, ZenkaiAttributes.DEXTERITY, ZenkaiAttributes.CONSTITUTION,
            ZenkaiAttributes.WILLPOWER, ZenkaiAttributes.SPIRIT, ZenkaiAttributes.MIND
    );

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int HEADER_Y   = 30;  // 1ª línea de cabecera (rel. panelTop)
    private static final int DIV_Y      = 54;  // divisor bajo cabecera
    private static final int ATTR_Y0    = 66;  // 1ª fila de atributos
    private static final int ATTR_STEP  = 20;  // paso entre filas
    private static final int PREVIEW_X1 = 150, PREVIEW_X2 = 244; // zona del render (rel. panelLeft)
    private static final int PREVIEW_Y1 = 62,  PREVIEW_Y2 = 186; // (rel. panelTop)
    private static final int ALIGN_BAR_W = 130, ALIGN_BAR_H = 7;

    // Popup de stats efectivas
    private static final int POPUP_W = 118, POPUP_H = 104;

    private static final int[] TP_STEPS = {1, 10, 100, 1000, 10000, 100000};
    private int tpStepIndex = 0;

    private int tpcLabelX, tpcLabelY, tpcLabelW, tpcLabelH;
    private int alignBarX, alignBarY;
    private boolean showEffectiveStats = false;

    private int getCurrentTpStep() { return TP_STEPS[tpStepIndex]; }
    private void cycleTpStep()     { tpStepIndex = (tpStepIndex + 1) % TP_STEPS.length; }

    private record AttrArea(ZenkaiAttributes attr, int x, int y, int w, int h) {
        boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<AttrArea> attrAreas = new ArrayList<>();

    public StatsScreen() { super(Component.translatable("screen.zenkai.stats_screen.title")); }

    @Override protected ZenkaiTab currentTab() { return ZenkaiTab.MAIN; }

    @Override
    protected void initContent() {
        int x = panelLeft + 16;
        int y = panelTop + ATTR_Y0;

        for (ZenkaiAttributes a : ORDER) {
            final String name = a.name();
            this.addRenderableWidget(new PlusIconButton(x + 60, y + 1,
                    () -> spend(name, getCurrentTpStep())));
            y += ATTR_STEP;
        }

        // TPC + botón de multiplicador (abajo-izquierda)
        Font font = this.font;
        tpcLabelX = panelLeft + 12;
        tpcLabelY = panelTop + BG_H - 30;
        tpcLabelW = font.width("TPC: x100000");
        tpcLabelH = font.lineHeight;
        this.addRenderableWidget(new PlusIconButton(tpcLabelX + tpcLabelW + 10, tpcLabelY - 2, this::cycleTpStep));

        // Botón que abre/cierra el popup de stats efectivas (bajo el render)
        int btnW = 78, btnH = 14;
        int btnX = panelLeft + (PREVIEW_X1 + PREVIEW_X2) / 2 - btnW / 2;
        int btnY = panelTop + PREVIEW_Y2 + 4;
        this.addRenderableWidget(new TextOnlyButton(btnX, btnY, btnW, btnH,
                Component.translatableWithFallback("screen.zenkai.stats_screen.effective", "Stats"),
                () -> showEffectiveStats = !showEffectiveStats)
                .textColors(0xFFFFFF, 0xFFF149, 0xA0A0A0));

        // Barra de alineamiento (abajo-derecha)
        alignBarX = panelLeft + BG_W - 12 - ALIGN_BAR_W;
        alignBarY = panelTop + BG_H - 20;
    }

    private void spend(String attrName, int points) {
        PacketDistributor.sendToServer(new SpendTpPacket(attrName, points));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (mc.player == null) { super.render(g, mouseX, mouseY, partialTick); return; }
        att = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        PlayerFormAttachment form = mc.player.getData(DataAttachments.PLAYER_FORM.get());

        super.render(g, mouseX, mouseY, partialTick);

        Font font = this.font;
        int left = panelLeft + 12;

        g.drawString(font, this.title, left, panelTop + 16, 0xFFFFFF);

        // ======= Cabecera: Race | Style / Form | TP (2 líneas x 2 columnas) =======
        int col2 = panelLeft + 128;
        int hy = panelTop + HEADER_Y;
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.race")
                .append(raceName().copy().withStyle(ChatFormatting.AQUA)), left, hy, 0xFFFFFF);
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.style")
                .append(styleName().copy().withStyle(ChatFormatting.AQUA)), col2, hy, 0xFFFFFF);
        hy += 11;
        g.drawString(font, Component.translatableWithFallback("screen.zenkai.stats_screen.form", "Form: ")
                .append(formName(form.getFormId()).copy().withStyle(ChatFormatting.LIGHT_PURPLE)), left, hy, 0xFFFFFF);
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.tp")
                .append(Component.literal(String.valueOf(att.getTP())).withStyle(ChatFormatting.GOLD)), col2, hy, 0xFFFFFF);

        // Divisor
        g.fill(left, panelTop + DIV_Y, panelLeft + BG_W - 12, panelTop + DIV_Y + 1, 0x44FFFFFF);

        // ======= Columna izquierda: atributos =======
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.attributes"),
                left + 4, panelTop + DIV_Y + 4, 0xFFD0D0);

        attrAreas.clear();
        int ay = panelTop + ATTR_Y0 + 2;
        int ax = panelLeft + 16;
        for (ZenkaiAttributes a : ORDER) {
            Component line = getAttributeLabel(a, att.getAttribute(a));
            g.drawString(font, line, ax, ay, 0xFFFFFF);
            attrAreas.add(new AttrArea(a, ax, ay, font.width(line), font.lineHeight));
            ay += ATTR_STEP;
        }

        // ======= Columna derecha: render del jugador =======
        int px1 = panelLeft + PREVIEW_X1, px2 = panelLeft + PREVIEW_X2;
        int py1 = panelTop + PREVIEW_Y1,  py2 = panelTop + PREVIEW_Y2;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, px1, py1, px2, py2, 42, 0.0625f, (float) mouseX, (float) mouseY, mc.player);

        // ======= TPC + coste =======
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.tpx", getCurrentTpStep()),
                tpcLabelX, tpcLabelY, 0xFFFFFF);
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.cost", computeCurrentTpCost()),
                tpcLabelX, tpcLabelY + font.lineHeight + 4, 0xFFFFFF);

        // ======= Barra de alineamiento =======
        renderAlignmentBar(g, font, att.getAlignment(), mouseX, mouseY);

        // ======= Popup lateral de stats efectivas =======
        if (showEffectiveStats) renderEffectiveStatsPopup(g, font);

        // ======= Tooltips =======
        renderPlayerHoverTooltip(g, form, mouseX, mouseY, px1, py1, px2, py2);
        renderAttributeTooltip(g, mouseX, mouseY);
        renderTpStepTooltip(g, mouseX, mouseY);
    }

    // ── Nombres traducibles (sin guiones bajos crudos) ───────────────────────
    private Component raceName() {
        return Component.translatable("screen.zenkai.race." + att.getRace().name().toLowerCase(Locale.ROOT));
    }
    private Component styleName() {
        return Component.translatable("screen.zenkai.style." + att.getStyle().name().toLowerCase(Locale.ROOT));
    }
    /** form.zenkai.<path con / -> .>; fallback = último segmento embellecido ("super_saiyan" -> "Super Saiyan"). */
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
        Component label = Component.translatableWithFallback("screen.zenkai.stats_screen.alignment", "Alignment");
        g.drawString(font, label, alignBarX, alignBarY - 10, 0xFFD0D0);

        // marco + gradiente horizontal rojo -> gris -> azul (por columnas)
        g.fill(alignBarX - 1, alignBarY - 1, alignBarX + ALIGN_BAR_W + 1, alignBarY + ALIGN_BAR_H + 1, 0xFF3A2A18);
        for (int i = 0; i < ALIGN_BAR_W; i++) {
            float t = i / (float) (ALIGN_BAR_W - 1);
            int rgb = (t < 0.5f)
                    ? lerpRgb(0xD62828, 0x9A9A9A, t * 2f)
                    : lerpRgb(0x9A9A9A, 0x2D6CDF, (t - 0.5f) * 2f);
            g.fill(alignBarX + i, alignBarY, alignBarX + i + 1, alignBarY + ALIGN_BAR_H, 0xFF000000 | rgb);
        }

        // marcador: -100..+100 -> 0..W
        int mx = alignBarX + Math.round((alignment + 100) / 200f * (ALIGN_BAR_W - 1));
        g.fill(mx - 1, alignBarY - 2, mx + 2, alignBarY + ALIGN_BAR_H + 2, 0xFFFFFFFF);

        if (mouseX >= alignBarX && mouseX < alignBarX + ALIGN_BAR_W
                && mouseY >= alignBarY - 2 && mouseY < alignBarY + ALIGN_BAR_H + 2) {
            String v = (alignment > 0 ? "+" : "") + alignment;
            g.renderTooltip(font, Component.literal(v), mouseX, mouseY);
        }
    }

    private static int lerpRgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), gg = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    // ── Popup de stats efectivas ─────────────────────────────────────────────
    private void renderEffectiveStatsPopup(GuiGraphics g, Font font) {
        boolean majin = mc.player.getData(DataAttachments.PLAYER_VISUAL.get()).isMajinControlled();
        List<Component> lines = buildEffectiveStats();

        // Altura dinámica: título (12) + línea majin opcional (10) + stats (10 c/u) + márgenes.
        int h = 5 + 12 + (majin ? 10 : 0) + lines.size() * 10 + 6;

        int x = panelLeft + BG_W + 6;
        if (x + POPUP_W > this.width - 2) x = panelLeft - POPUP_W - 6;
        int y = panelTop + 40;

        g.fill(x, y, x + POPUP_W, y + h, 0xE81E1410);
        g.fill(x, y, x + POPUP_W, y + 1, 0xFFFFAA33);
        g.fill(x, y + h - 1, x + POPUP_W, y + h, 0xFFFFAA33);
        g.fill(x, y, x + 1, y + h, 0xFFFFAA33);
        g.fill(x + POPUP_W - 1, y, x + POPUP_W, y + h, 0xFFFFAA33);

        int tx = x + 6, ty = y + 5;
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.stats"), tx, ty, 0xFFD0D0);
        ty += 12;
        if (majin) {
            g.drawString(font, Component.translatableWithFallback(
                    "screen.zenkai.stats_screen.majin_boost", "Majin +%s%%",
                    String.valueOf((int) Math.round(StatsConfig.majinStatBonus() * 100))), tx, ty, 0xFFFF5566);
            ty += 10;
        }
        for (Component c : lines) {
            g.drawString(font, c, tx, ty, 0xFFFFFF);
            ty += 10;
        }
    }

    private List<Component> buildEffectiveStats() {
        assert mc.player != null;
        double f = MasteryEffects.formStatFactor(mc.player);
        double melee   = att.computeMeleeFinal()   * f;
        double defense = att.computeDefenseFinal() * f;
        double speed   = att.computeSpeedFinal();
        double fly     = att.computeFlyFinal();
        double kiPower = att.computeKiPowerFinal() * f;

        double moveMult = Math.min(1.0 + (speed / 100.0) * StatsConfig.movementScaling(), StatsConfig.speedMultiplierCap());
        double flyMult  = Math.min(1.0 + (fly   / 100.0) * StatsConfig.flyScaling(),      StatsConfig.flyMultiplierCap());

        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("screen.zenkai.stats_screen.stat.melee",   fmt(melee)));
        out.add(Component.translatable("screen.zenkai.stats_screen.stat.defense", fmt(defense)));
        out.add(Component.translatable("screen.zenkai.stats_screen.stat.body",    att.getBody() + "/" + att.getBodyMax()));
        out.add(Component.translatable("screen.zenkai.stats_screen.stat.stamina", att.getStamina() + "/" + att.getStaminaMax()));
        out.add(Component.translatable("screen.zenkai.stats_screen.stat.ki",      att.getEnergy() + "/" + att.getEnergyMax()));
        out.add(Component.translatable("screen.zenkai.stats_screen.stat.ki_power", fmt(kiPower)));
        out.add(Component.translatable("screen.zenkai.stats_screen.stat.running", (int) Math.round(moveMult * 100)));
        out.add(Component.translatable("screen.zenkai.stats_screen.stat.flying",  (int) Math.round(flyMult  * 100)));
        return out;
    }

    private static String fmt(double d) { return String.format(Locale.ROOT, "%.1f", d); }

    // ── Tooltips ─────────────────────────────────────────────────────────────
    /** Hover sobre el render del jugador: forma actual + su maestría. */
    private void renderPlayerHoverTooltip(GuiGraphics g, PlayerFormAttachment form,
                                          int mouseX, int mouseY, int x1, int y1, int x2, int y2) {
        if (mouseX < x1 || mouseX >= x2 || mouseY < y1 || mouseY >= y2) return;
        float mastery = form.getFormMastery(form.getFormId());
        List<Component> lines = List.of(
                formName(form.getFormId()),
                Component.translatableWithFallback("screen.zenkai.stats_screen.mastery", "Mastery: %s%%",
                        fmt(mastery)).withStyle(ChatFormatting.GOLD));
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
                g.renderTooltip(this.font, getAttributeDescription(area.attr(), att), mouseX, mouseY);
                break;
            }
        }
    }

    private int computeCurrentTpCost() {
        if (att == null) return 0;
        int step = getCurrentTpStep();
        int best = Integer.MAX_VALUE;
        for (ZenkaiAttributes a : ORDER) {
            int c = att.previewTpCost(a, step);
            if (c > 0 && c < best) best = c;
        }
        return (best == Integer.MAX_VALUE) ? 0 : best;
    }

    private Component getAttributeLabel(ZenkaiAttributes attr, int value) {
        return switch (attr) {
            case STRENGTH     -> Component.translatable("attribute.zenkai.str", value);
            case DEXTERITY    -> Component.translatable("attribute.zenkai.dex", value);
            case CONSTITUTION -> Component.translatable("attribute.zenkai.con", value);
            case WILLPOWER    -> Component.translatable("attribute.zenkai.wil", value);
            case MIND         -> Component.translatable("attribute.zenkai.mnd", value);
            case SPIRIT       -> Component.translatable("attribute.zenkai.spi", value);
        };
    }

    private Component getAttributeDescription(ZenkaiAttributes attr, PlayerStatsAttachment att) {
        double[] r = StatsConfig.raceMultipliers(att.getRace());
        double[] s = StatsConfig.styleMultipliers(att.getStyle());

        double mSTR = (r.length > 0) ? r[0] : 1.0, mCON = (r.length > 1) ? r[1] : 1.0,
                mDEX = (r.length > 2) ? r[2] : 1.0, mWIL = (r.length > 3) ? r[3] : 1.0,
                mSPI = (r.length > 4) ? r[4] : 1.0, mMND = (r.length > 5) ? r[5] : 1.0;
        double sSTR = (s.length > 0) ? s[0] : 1.0, sCON = (s.length > 1) ? s[1] : 1.0,
                sDEX = (s.length > 2) ? s[2] : 1.0, sWIL = (s.length > 3) ? s[3] : 1.0,
                sSPI = (s.length > 4) ? s[4] : 1.0, sMND = (s.length > 5) ? s[5] : 1.0;

        return switch (attr) {
            case STRENGTH     -> Component.translatable("tooltip.zenkai.attr.str", fmt(mSTR * sSTR));
            case CONSTITUTION -> Component.translatable("tooltip.zenkai.attr.con", fmt(mCON * sCON * 2.0));
            case DEXTERITY    -> Component.translatable("tooltip.zenkai.attr.dex", fmt(mDEX * sDEX));
            case WILLPOWER    -> Component.translatable("tooltip.zenkai.attr.wil", fmt(mWIL * sWIL));
            case SPIRIT       -> Component.translatable("tooltip.zenkai.attr.spi", fmt(mSPI * sSPI));
            case MIND         -> Component.translatable("tooltip.zenkai.attr.mnd", fmt(mMND * sMND));
        };
    }
}