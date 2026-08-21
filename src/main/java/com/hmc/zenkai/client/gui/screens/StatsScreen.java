package com.hmc.zenkai.client.gui.screens;

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
import com.hmc.zenkai.feature.StatSynergy;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.ki.SetPowerPercentPacket;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.race.RacePassives;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.stats.RefundTpPacket;
import com.hmc.zenkai.feature.stats.SpendTpPacket;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pestaña PRINCIPAL del menú Zenkai.
 * ═══ REPARTO DE INFORMACIÓN ═══
 * El panel guarda lo que el jugador MANIPULA (atributos, TP, Ki Control) y lo que necesita ver
 * de un vistazo mientras lo manipula (poder, pools). Lo demás vive en dos popups laterales
 * que se abren a la vez y no tapan el panel:
 *   IZQUIERDA — STATS:      combate, movilidad, carga, inversión. Lo derivado de los números.
 *   DERECHA   — PROGRESIÓN: forma, maestría, kaioken, pasiva racial, alineamiento.
 * Esa segunda columna estaba antes en el tooltip del render, que desaparecía al mover el ratón:
 * era imposible mirar la maestría y el multiplicador a la vez que se decidía dónde gastar.
 * ═══ REGLA DE SOMBRA ═══
 * Sobre el beige del panel se dibuja SIN sombra y con la escala tierra de ZenkaiPalette; en los
 * popups, CON sombra y con la escala clara. Ver ZenkaiPalette.
 * ═══ SELECTOR DE PASO COMPARTIDO ═══
 * El multiplicador (x1 … x1000) gobierna el + Y el −. Es un solo control para una sola idea
 * ("de cuánto en cuánto me muevo"), y con dos selectores separados el jugador acabaría
 * comprando de cien en cien y devolviendo de uno en uno sin darse cuenta.
 */
public class StatsScreen extends ZenkaiMenuScreen {

    // Orden de atributos (MND al final)
    private static final List<ZenkaiAttributes> ORDER = List.of(
            ZenkaiAttributes.STRENGTH, ZenkaiAttributes.DEXTERITY, ZenkaiAttributes.CONSTITUTION,
            ZenkaiAttributes.WILLPOWER, ZenkaiAttributes.SPIRIT, ZenkaiAttributes.MIND
    );

    // ── Layout (verificado: el contenido acaba en 240 de 256) ────────────────
    /**
     * 15 y no 12. El marco de common_screen ocupa los ~8 px exteriores y su brillo llega un
     * poco más adentro, así que con 12 el contenido quedaba a 4 px del anillo y las barras de
     * pool y la de Ki Control parecían tocarlo. 15 deja 7 px de aire limpio por lado.
     */
    private static final int MARGIN      = 15;
    private static final int HEADER_Y    = CONTENT_TOP;        // 16
    private static final int HEADER_Y2   = HEADER_Y + 10;      // 26
    private static final int PL_Y        = HEADER_Y + 22;      // 38
    private static final int KI_BAR_Y    = PL_Y + 10;          // 48
    private static final int DIV_Y       = PL_Y + 20;          // 58
    private static final int ATTR_LABEL_Y= DIV_Y + 4;          // 62
    private static final int ATTR_Y0     = DIV_Y + 16;         // 74
    private static final int ATTR_STEP   = 17;
    private static final int BTN_W       = 12;

    // El retrato acaba 4 px antes del final del contenido (244), no EN él. En la versión
    // anterior llegaba justo a 244 y además el marco se dibujaba hacia fuera, así que los
    // anillos invadían el borde del panel y el personaje parecía pegado a la pared.
    private static final int PREVIEW_X1  = 143, PREVIEW_X2 = 237;
    private static final int PREVIEW_Y1  = DIV_Y + 6;          // 64
    private static final int PREVIEW_Y2  = PREVIEW_Y1 + 104;   // 168
    private static final int POPUP_BTN_Y = PREVIEW_Y2 + 3;     // 171
    private static final int POPUP_BTN_H = 13;

    private static final int DIV2_Y      = 188;
    private static final int POOLS_Y     = 193;
    private static final int POOL_STEP   = 10;
    private static final int FOOT_DIV_Y  = 226;
    private static final int FOOT_Y      = 231;

    private static final int KI_BAR_W    = 88;
    /** Escalón del arrastre de Ki Control. Coincide con el de la tecla de carga. */
    private static final int KI_STEP     = 5;
    /**
     * Suelo del % de Ki Control. NO es cero: setPowerPercent está definido sobre [0, techo] y
     * la tecla de carga nunca baja de ahí. La versión anterior de esta barra mapeaba el
     * arrastre sobre 0..100, así que dejaba llegar a 0 —un estado que el resto del juego no
     * produce— y comprimía el rango útil hasta hacerlo inservible: con Ki Control a nivel 0 el
     * techo es 50, o sea que mínimo y máximo coinciden y no hay NADA que arrastrar.
     * ⚠ Si SkillEffects expone una constante para esto, usar aquella y borrar esta.
     */
    private static final int KI_MIN = 0;

    // Popups laterales
    private static final int POPUP_W = 136;
    private static final int POPUP_GAP = 8;

    private static final int[] TP_STEPS = {1, 10, 100, 1000};
    private int tpStepIndex = 0;

    private int tpcLabelX, tpcLabelY, tpcLabelW, tpcLabelH;
    private int plLabelX, plLabelY, plLabelW;
    private int kiBarX, kiBarY;

    private boolean showStats = false;
    private boolean showProgress = false;
    /**
     * Valor en curso del arrastre de Ki Control, o -1 si no se está arrastrando.
         * Es la FUENTE DE VERDAD mientras dura el gesto, en vez de escribir en el attachment y
     * dejar de releerlo. Con lo anterior, cualquier sync del servidor durante el arrastre
     * revertía el valor a mitad de gesto y la barra saltaba bajo el cursor; y peor, el estado
     * de la pantalla quedaba acoplado a que el render se saltara una lectura.
     */
    private int kiDrag = -1;

    private int getCurrentTpStep() { return TP_STEPS[tpStepIndex]; }
    private void cycleTpStep()     { tpStepIndex = (tpStepIndex + 1) % TP_STEPS.length; }

    private record AttrArea(ZenkaiAttributes attr, int x, int y, int w, int h) {
        boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<AttrArea> attrAreas = new ArrayList<>();
    private final List<MinusIconButton> minusButtons = new ArrayList<>();

    public StatsScreen() { super(Component.translatable("screen.zenkai.stats_screen.title")); }

    @Override protected ZenkaiTab currentTab() { return ZenkaiTab.STATS; }

    // ── Construcción ─────────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        minusButtons.clear();
        int x = panelLeft + MARGIN;
        int y = panelTop + ATTR_Y0;

        for (ZenkaiAttributes a : ORDER) {
            final String name = a.name();

            MinusIconButton minus = new MinusIconButton(x, y, () -> refund(name, getCurrentTpStep()));
            minusButtons.add(minus);
            addRenderableWidget(minus);

            addRenderableWidget(new PlusIconButton(x + BTN_W + 3, y,
                    () -> spend(name, getCurrentTpStep())));
            y += ATTR_STEP;
        }

        // Pie: selector de paso.
        Font font = this.font;
        tpcLabelX = panelLeft + MARGIN;
        tpcLabelY = panelTop + FOOT_Y;
        tpcLabelW = font.width(Component.translatable("screen.zenkai.stats_screen.tpx", 1000));
        tpcLabelH = font.lineHeight;
        addRenderableWidget(new PlusIconButton(tpcLabelX + tpcLabelW + 6, tpcLabelY - 2, this::cycleTpStep));

        // Dos botones bajo el render, uno por popup.
        int bw = (PREVIEW_X2 - PREVIEW_X1 - 4) / 2;
        int bx = panelLeft + PREVIEW_X1;
        int by = panelTop + POPUP_BTN_Y;
        addRenderableWidget(new TextOnlyButton(bx, by, bw, POPUP_BTN_H,
                Component.translatable("screen.zenkai.stats_screen.popup.stats"),
                () -> showStats = !showStats)
                .onPanel());
        addRenderableWidget(new TextOnlyButton(bx + bw + 4, by, bw, POPUP_BTN_H,
                Component.translatable("screen.zenkai.stats_screen.popup.progress"),
                () -> showProgress = !showProgress)
                .onPanel());

        kiBarX = panelLeft + BG_W - MARGIN - KI_BAR_W;
        kiBarY = panelTop + KI_BAR_Y;
    }

    private void spend(String attrName, int points) {
        PacketDistributor.sendToServer(new SpendTpPacket(attrName, points));
    }

    /** La cantidad va en el paquete y el bucle lo hace el servidor: con el paso a x1000, un
     *  paquete por punto serían mil mensajes por clic. Ver RefundTpPacket. */
    private void refund(String attrName, int points) {
        PacketDistributor.sendToServer(new RefundTpPacket(attrName, points));
    }

    private void layoutRefundButtons() {
        if (att == null) return;
        for (int i = 0; i < minusButtons.size() && i < ORDER.size(); i++) {
            minusButtons.get(i).active = att.raceStats().canRefund(ORDER.get(i));
        }
    }

    // ── Ki Control arrastrable ───────────────────────────────────────────────

    private boolean overKiBar(double mx, double my) {
        return mx >= kiBarX - 2 && mx <= kiBarX + KI_BAR_W + 2
                && my >= kiBarY - 3 && my <= kiBarY + StatBar.H_WIDE + 3;
    }

    private int kiCap() {
        return att == null ? KI_MIN : Math.max(KI_MIN, Math.min(100, SkillEffects.maxPowerPercent(att)));
    }

    /** % que se está mostrando: el del arrastre si lo hay, el del personaje si no. */
    private int kiShown() {
        if (kiDrag >= 0) return kiDrag;
        return att == null ? KI_MIN : att.getPowerPercent();
    }

    /** Fracción 0..1 de un porcentaje DENTRO del rango útil [KI_MIN, cap]. */
    private float kiFraction(int pct) {
        int cap = kiCap();
        if (cap <= KI_MIN) return 1f;
        return Mth.clamp((pct - KI_MIN) / (float) (cap - KI_MIN), 0f, 1f);
    }

    /**
     * El arrastre recorre el rango ÚTIL [KI_MIN, cap], no 0..100.
         * Mapear sobre 0..100 desperdiciaba media barra en valores que el juego no acepta y hacía
     * que los escalones de 5 cayeran en menos de un píxel cada uno. Sobre el rango real, un
     * jugador con el techo al 80 % tiene siete escalones repartidos en 88 px: doce píxeles por
     * escalón, que sí se pueden apuntar.
     */
    private void applyKiDrag(double mouseX) {
        int cap = kiCap();
        if (cap <= KI_MIN) { kiDrag = KI_MIN; return; }

        float frac = Mth.clamp((float) (mouseX - kiBarX) / KI_BAR_W, 0f, 1f);
        int raw = Math.round(KI_MIN + frac * (cap - KI_MIN));
        kiDrag = Mth.clamp(Math.round(raw / (float) KI_STEP) * KI_STEP, KI_MIN, cap);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Con el techo al mínimo no hay nada que elegir: la barra no se agarra siquiera, para
        // que el jugador note que el límite es suyo y no un fallo del control.
        if (button == 0 && overKiBar(mouseX, mouseY) && kiCap() > KI_MIN) {
            applyKiDrag(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (kiDrag >= 0) { applyKiDrag(mouseX); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (kiDrag >= 0) {
            // UN solo paquete, al soltar. Durante el gesto el valor vive en kiDrag: mandar uno
            // por movimiento del ratón serían cien mensajes por arrastre.
            PacketDistributor.sendToServer(new SetPowerPercentPacket(kiDrag));
            if (att != null) att.setPowerPercent(kiDrag, kiCap());   // optimista
            kiDrag = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ── Render ───────────────────────────────────────────────────────────────

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

        // ══ Cabecera ══
        int col2 = panelLeft + 128;
        drawField(g, left, panelTop + HEADER_Y, "screen.zenkai.stats_screen.race",
                raceName(), ZenkaiPalette.OK_ON_PANEL);
        drawField(g, col2, panelTop + HEADER_Y, "screen.zenkai.stats_screen.style",
                styleName(), ZenkaiPalette.OK_ON_PANEL);
        drawField(g, left, panelTop + HEADER_Y2, "screen.zenkai.stats_screen.form",
                formName(form.getFormId()), ZenkaiPalette.SPECIAL_ON_PANEL);
        drawField(g, col2, panelTop + HEADER_Y2, "screen.zenkai.stats_screen.tp",
                Component.literal(String.valueOf(att.getTP())), ZenkaiPalette.VALUE_ON_PANEL);

        renderPowerBlock(g, font, left, right);

        g.fill(left, panelTop + DIV_Y, right, panelTop + DIV_Y + 1, ZenkaiPalette.SEPARATOR);

        // ══ Atributos ══
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.attributes"),
                left, panelTop + ATTR_LABEL_Y, ZenkaiPalette.LABEL_ON_PANEL, false);

        attrAreas.clear();
        int ay = panelTop + ATTR_Y0 + 2;
        int ax = left + BTN_W * 2 + 8;
        for (ZenkaiAttributes a : ORDER) {
            int raw = att.getAttribute(a);
            int eff = att.getEffectiveAttribute(a);
            // Compacto en cuanto los números crecen: con una transformación activa, el efectivo
            // y el crudo juntos ("921047 (200000)") se salían de la columna y quedaban debajo
            // del retrato. El valor exacto pasa al tooltip, que es donde se consulta y no donde
            // se ojea.
            Component line = (eff != raw)
                    ? getAttributeLabel(a, attrText(eff) + " (" + attrText(raw) + ")")
                    : getAttributeLabel(a, attrText(raw));

            // MND en déficit se ve en la LISTA, no solo en el tooltip: es un problema, y un
            // problema que hay que descubrir pasando el ratón por encima no está señalado.
            int color = (a == ZenkaiAttributes.MIND && att.mindFree() < 0)
                    ? ZenkaiPalette.DENIED_ON_PANEL
                    : (eff != raw) ? ZenkaiPalette.VALUE_ON_PANEL : ZenkaiPalette.LABEL_ON_PANEL;

            g.drawString(font, line, ax, ay, color, false);
            attrAreas.add(new AttrArea(a, ax, ay, font.width(line), font.lineHeight));
            ay += ATTR_STEP;
        }

        // ══ Render del jugador, con marco de tres anillos como el editor de técnicas ══
        int px1 = panelLeft + PREVIEW_X1, px2 = panelLeft + PREVIEW_X2;
        int py1 = panelTop + PREVIEW_Y1,  py2 = panelTop + PREVIEW_Y2;
        drawPortraitFrame(g, px1, py1, px2, py2);
        int ix1 = px1 + 3, iy1 = py1 + 3, ix2 = px2 - 3, iy2 = py2 - 3;
        g.enableScissor(ix1, iy1, ix2, iy2);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, ix1, iy1, ix2, iy2, 40, 0.0625f, (float) mouseX, (float) mouseY, mc.player);
        g.disableScissor();

        g.fill(left, panelTop + DIV2_Y, right, panelTop + DIV2_Y + 1, ZenkaiPalette.SEPARATOR);

        // ══ Pools ══
        renderPools(g, font, left, right, panelTop + POOLS_Y);

        g.fill(left, panelTop + FOOT_DIV_Y, right, panelTop + FOOT_DIV_Y + 1, ZenkaiPalette.SEPARATOR);

        // ══ Pie: paso + coste, en UNA línea ══
        g.drawString(font, Component.translatable("screen.zenkai.stats_screen.tpx", getCurrentTpStep()),
                tpcLabelX, tpcLabelY, ZenkaiPalette.LABEL_ON_PANEL, false);
        Component cost = Component.translatable("screen.zenkai.stats_screen.cost", computeCurrentTpCost());
        g.drawString(font, cost, right - font.width(cost), tpcLabelY,
                canAffordStep() ? ZenkaiPalette.VALUE_ON_PANEL : ZenkaiPalette.DENIED_ON_PANEL, false);

        // ══ Popups ══
        pendingTip = null;
        if (showStats)    renderPopup(g, font, true,  buildStatRows(),
                Component.translatable("screen.zenkai.stats_screen.popup.stats"), mouseX, mouseY);
        if (showProgress) renderPopup(g, font, false, buildProgressRows(form),
                Component.translatable("screen.zenkai.stats_screen.popup.progress"), mouseX, mouseY);

        // ══ Tooltips ══
        renderAttributeTooltip(g, mouseX, mouseY);
        renderTpStepTooltip(g, mouseX, mouseY);
        renderPowerTooltip(g, mouseX, mouseY);
        renderKiBarTooltip(g, mouseX, mouseY);
        if (pendingTip != null) {
            g.renderTooltip(this.font, this.font.split(pendingTip, 180), mouseX, mouseY); // ⚠ firma
        }
    }

    /**
     * Marco de tres anillos: el mismo lenguaje del panel y del editor de técnicas.
         * Los anillos se dibujan HACIA DENTRO y no hacia fuera, para que el rect que recibe esta
     * función sea el espacio total ocupado. Dibujándolos hacia fuera, el marco se comía los
     * píxeles de separación con el borde del panel y el retrato acababa pegado a él.
     */
    private void drawPortraitFrame(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, ZenkaiPalette.BORDER_IN);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, ZenkaiPalette.BORDER_MID);
        g.fill(x1 + 2, y1 + 2, x2 - 2, y2 - 2, ZenkaiPalette.BORDER_OUT);
        g.fill(x1 + 3, y1 + 3, x2 - 3, y2 - 3, ZenkaiPalette.BEIGE_DEEP);
        // Esquinas recortadas, como common_screen: sin esto el marco parece de otro mod.
        for (int[] c : new int[][]{{x1, y1}, {x2 - 3, y1}, {x1, y2 - 3}, {x2 - 3, y2 - 3}}) {
            g.fill(c[0], c[1], c[0] + 3, c[1] + 3, ZenkaiPalette.BEIGE);
        }
    }

    /** Etiqueta en marrón + valor en color. Sin sombra: el panel es beige claro. */
    private void drawField(GuiGraphics g, int x, int y, String key, Component value, int valueColor) {
        Component label = Component.translatable(key);
        g.drawString(this.font, label, x, y, ZenkaiPalette.LABEL_ON_PANEL, false);
        g.drawString(this.font, value, x + this.font.width(label), y, valueColor, false);
    }

    // ── Bloque de poder ──────────────────────────────────────────────────────

    private void renderPowerBlock(GuiGraphics g, Font font, int left, int right) {
        long releasable = att.getReleasablePowerLevel();
        long apparent   = att.getApparentPowerLevel();
        int y = panelTop + PL_Y;

        Component label = Component.translatable("screen.zenkai.stats_screen.power_level");
        g.drawString(font, label, left, y, ZenkaiPalette.LABEL_ON_PANEL, false);

        Component value = Component.literal(ZenkaiNumbers.format(releasable));
        int vx = left + font.width(label);
        g.drawString(font, value, vx, y, ZenkaiPalette.VALUE_ON_PANEL, false);

        plLabelX = left;
        plLabelY = y;
        plLabelW = font.width(label) + font.width(value);

        // El aparente solo aparece cuando DIFIERE: si estás al tope, repetirlo es ruido.
        if (apparent != releasable) {
            Component ap = Component.literal(" (" + ZenkaiNumbers.format(apparent) + ")");
            g.drawString(font, ap, vx + font.width(value), y, ZenkaiPalette.MUTED_ON_PANEL, false);
            plLabelW += font.width(ap);
        }

        // Ki Control: etiqueta + porcentaje arriba, barra arrastrable debajo.
        int shown = kiShown();
        int cap = kiCap();
        boolean adjustable = cap > KI_MIN;

        Component kc = Component.translatable("screen.zenkai.stats_screen.ki_control");
        g.drawString(font, kc, kiBarX, y, ZenkaiPalette.LABEL_ON_PANEL, false);
        Component pct = Component.literal(shown + "%");
        g.drawString(font, pct, right - font.width(pct), y,
                adjustable ? ZenkaiPalette.VALUE_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL, false);

        // La barra se llena según la posición DENTRO del rango útil [KI_MIN, cap]: llena = tope
        // de este personaje, vacía = mínimo. Dibujarla como fracción de 100 hacía que un
        // jugador al tope de su habilidad viera la barra a medias y creyera que le faltaba algo.
        StatBar.draw(g, kiBarX, kiBarY, KI_BAR_W, StatBar.H_WIDE,
                kiFraction(shown), 1.0, ZenkaiPalette.BAR_CONTROL);

        if (adjustable) {
            // Muescas en cada escalón de 5. Aquí sí caben: el rango útil son pocos escalones
            // repartidos en 88 px, no veinte como pasaría sobre 0..100.
            for (int v = KI_MIN + KI_STEP; v < cap; v += KI_STEP) {
                int qx = kiBarX + Math.round(KI_BAR_W * kiFraction(v));
                g.fill(qx, kiBarY, qx + 1, kiBarY + 1,
                        ZenkaiPalette.withAlpha(ZenkaiPalette.BORDER_IN, 90));
                g.fill(qx, kiBarY + StatBar.H_WIDE - 1, qx + 1, kiBarY + StatBar.H_WIDE,
                        ZenkaiPalette.withAlpha(ZenkaiPalette.BORDER_IN, 90));
            }

            // Agarradera: hace evidente que la barra se arrastra.
            int hx = kiBarX + Math.round(KI_BAR_W * kiFraction(shown));
            hx = Mth.clamp(hx, kiBarX, kiBarX + KI_BAR_W - 1);
            g.fill(hx - 1, kiBarY - 2, hx + 2, kiBarY + StatBar.H_WIDE + 2, ZenkaiPalette.BORDER_IN);
            g.fill(hx, kiBarY - 1, hx + 1, kiBarY + StatBar.H_WIDE + 1, ZenkaiPalette.BORDER_OUT);
        } else {
            // Sin margen de ajuste la barra se raya entera: el jugador ve que el control existe
            // y que el límite es su nivel de habilidad, no un fallo del arrastre.
            for (int i = kiBarX; i < kiBarX + KI_BAR_W; i += 3) {
                g.fill(i, kiBarY, i + 1, kiBarY + StatBar.H_WIDE,
                        ZenkaiPalette.withAlpha(ZenkaiPalette.BORDER_IN, 70));
            }
        }
    }

    /**
     * Umbral a partir del cual un atributo se muestra compacto.
         * 20.000 y no antes: por debajo, "18500" cabe de sobra y el número exacto es más útil que
     * "18.5K" cuando el jugador está decidiendo dónde meter puntos. Por encima, el efectivo de
     * una transformación mete seis o siete cifras y la columna se desborda sobre el retrato —
     * que es justo lo que pasaba con 921047 (200000).
     */
    private static final int COMPACT_FROM = 20_000;

    /** Compacto por encima del umbral, exacto por debajo. */
    private static String attrText(int v) {
        return Math.abs(v) >= COMPACT_FROM ? ZenkaiNumbers.format(v) : String.valueOf(v);
    }

    // ── Pools ────────────────────────────────────────────────────────────────

    private void renderPools(GuiGraphics g, Font font, int left, int right, int y) {
        int barX = left + 34;

        StatBar.row(g, font, left, barX, right, y,
                Component.translatable("screen.zenkai.stats_screen.stat.body_short"),
                att.getBody(), att.getBodyMax(), ZenkaiPalette.BAR_BODY);
        StatBar.row(g, font, left, barX, right, y + POOL_STEP,
                Component.translatable("screen.zenkai.stats_screen.stat.stamina_short"),
                att.getStamina(), att.getStaminaMax(), ZenkaiPalette.BAR_STAMINA);
        StatBar.row(g, font, left, barX, right, y + POOL_STEP * 2,
                Component.translatable("screen.zenkai.stats_screen.stat.ki_short"),
                att.getEnergy(), att.getEnergyMax(), ZenkaiPalette.BAR_KI);
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

    // ── Popups laterales ─────────────────────────────────────────────────────

    /** Fila de popup. bar >= 0 dibuja además una barra de progreso bajo el texto.
     *  tip != null cuelga una explicación del ratón: el popup no tiene ancho para meter dos
     *  líneas de descripción dentro de la fila, y el nombre a secas no dice qué hace. */
    private record Row(Component label, Component value, int color, float bar, int barColor,
                       Component tip) {
        /** Cabecera de sección. El color lo pone quien la crea, desde ZenkaiPalette.SECTION_*. */
        static Row header(String key, int color) {
            return new Row(Component.translatable(key), null, color, -1f, 0, null);
        }
        static Row of(Component l, Component v, int c) { return new Row(l, v, c, -1f, 0, null); }
        static Row bar(Component l, Component v, int c, float pct, int bc) {
            return new Row(l, v, c, pct, bc, null);
        }
        static Row tip(Component l, Component v, int c, Component tip) {
            return new Row(l, v, c, -1f, 0, tip);
        }
        boolean isHeader() { return value == null; }
        boolean hasBar() { return bar >= 0f; }
    }

    /** Tooltip que una fila de popup ha reclamado este frame. Se resuelve DESPUÉS de pintar
     *  los dos popups: si se dibujara dentro de renderPopup, el popup derecho lo taparía. */
    private Component pendingTip;

    /**
     * Popup lateral. El izquierdo y el derecho pueden estar abiertos a la vez y ninguno tapa el
     * panel: si un lado no cabe en la ventana, ese popup se pega al borde en vez de invadir el
     * centro. Con GUI Scale muy alto y ventana estrecha se solaparán con el panel, y ahí ya es
     * preferible eso a esconder la información.
     */
    private void renderPopup(GuiGraphics g, Font font, boolean leftSide,
                             List<Row> rows, Component title, int mouseX, int mouseY) {
        // Alturas. La cabecera necesita AIRE ARRIBA, no solo debajo: pegada a la última fila de
        // la sección anterior, "Combat" y "Melee" se leían como una sola cosa de dos líneas
        // —igual que "Mobility/Running" e "Investment/TP spent"— y el popup perdía la
        // estructura que las cabeceras existen para dar. Lo que separa bloques es el espacio
        // por ENCIMA del título, así que va ahí y no repartido.
        final int rowH = 11, headerH = 22, barExtra = 8, firstHeaderH = 14;

        int h = 7 + 13;
        boolean first = true;
        for (Row r : rows) {
            if (r.isHeader()) {
                h += first ? firstHeaderH : headerH;
                first = false;
            } else {
                h += rowH + (r.hasBar() ? barExtra : 0);
            }
        }
        h += 8;

        int x = leftSide
                ? panelLeft - POPUP_W - POPUP_GAP
                : panelLeft + BG_W + POPUP_GAP;
        x = Mth.clamp(x, 2, this.width - POPUP_W - 2);
        int y = panelTop + CONTENT_TOP;

        // Marco de tres anillos: pertenece a la misma familia visual que el panel.
        g.fill(x - 2, y - 2, x + POPUP_W + 2, y + h + 2, ZenkaiPalette.BORDER_IN);
        g.fill(x - 1, y - 1, x + POPUP_W + 1, y + h + 1, ZenkaiPalette.BORDER_MID);
        g.fill(x, y, x + POPUP_W, y + h, ZenkaiPalette.POPUP_BG);

        int tx = x + 8, tr = x + POPUP_W - 8, ty = y + 6;

        // Dentro del popup el fondo es oscuro: aquí SÍ va sombra.
        g.drawString(font, ScreenTitle.styled(title), tx, ty, ZenkaiPalette.GOLD, true);
        ty += 13;

        first = true;
        for (Row r : rows) {
            if (r.isHeader()) {
                int gap = first ? 2 : 10;   // aire antes de la cabecera
                // Filete del color de la sección a la izquierda del título: marca el bloque en
                // vertical aunque el jugador esté mirando la fila de abajo.
                g.fill(tx, ty + gap, tx + 2, ty + gap + font.lineHeight, r.color());
                g.drawString(font, ScreenTitle.styled(r.label()), tx + 6, ty + gap + 1,
                        r.color(), true);
                // Línea tenue bajo el título, del mismo color y muy atenuada.
                g.fill(tx, ty + gap + font.lineHeight + 3, tr, ty + gap + font.lineHeight + 4,
                        ZenkaiPalette.withAlpha(r.color(), 60));
                ty += first ? firstHeaderH : headerH;
                first = false;
                continue;
            }
            // La etiqueta en NEGRITA hereda el color del valor: es la señal de que esa fila ES
            // el dato, no la descripción de otro (el alineamiento, el nombre de la forma). El
            // resto van en gris para que el ojo caiga en la columna de la derecha.
            boolean emphasized = r.label().getStyle().isBold();
            g.drawString(font, r.label(), tx + 6, ty + 4,
                    emphasized ? r.color() : ZenkaiPalette.TEXT_DIM, true);
            g.drawString(font, r.value(), tr - font.width(r.value()), ty + 4, r.color(), true);
            if (r.tip() != null && mouseX >= tx && mouseX <= tr
                    && mouseY >= ty + 2 && mouseY < ty + 2 + rowH) {
                pendingTip = r.tip();
            }
            ty += rowH;
            if (r.hasBar()) {
                StatBar.drawOnDark(g, tx + 6, ty + 5, tr - tx - 6, StatBar.H_THIN,
                        r.bar(), 100.0, r.barColor());
                ty += barExtra;
            }
        }
    }

    private List<Row> buildStatRows() {
        assert mc.player != null;
        List<Row> out = new ArrayList<>();
        boolean majin = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).isMajinControlled();

        out.add(Row.header("screen.zenkai.stats_screen.section.offense", ZenkaiPalette.SECTION_COMBAT));
        out.add(val("screen.zenkai.stats_screen.stat.melee", fmt(att.computeMeleeFinal()), ZenkaiPalette.TEXT));
        out.add(val("screen.zenkai.stats_screen.stat.defense", fmt(att.computeDefenseFinal()), ZenkaiPalette.TEXT));
        out.add(val("screen.zenkai.stats_screen.stat.ki_power", fmt(att.computeKiPowerFinal()), ZenkaiPalette.TEXT));
        if (majin) {
            out.add(val("screen.zenkai.stats_screen.majin_boost",
                    "+" + Math.round(CommonConfig.majinStatBonus() * 100) + "%", ZenkaiPalette.ERROR));
        }

        out.add(Row.header("screen.zenkai.stats_screen.section.mobility", ZenkaiPalette.SECTION_MOBILITY));
        out.add(val("screen.zenkai.stats_screen.stat.running",
                Math.round(att.getMoveMultiplier() * 100) + "%", ZenkaiPalette.TEXT));
        out.add(val("screen.zenkai.stats_screen.stat.flying",
                Math.round(att.getFlyMultiplier() * 100) + "%", ZenkaiPalette.TEXT));

        // Carga: solo si lleva pesas. Los números salen de WeightSystem, nunca de una fórmula
        // local, o la pantalla y el juego se separarían al primer ajuste.
        double load = att.getWeightLoad();
        if (load > 0.0) {
            out.add(Row.header("screen.zenkai.stats_screen.section.load", ZenkaiPalette.SECTION_LOAD));
            out.add(val("screen.zenkai.stats_screen.stat.load_short",
                    String.format(Locale.ROOT, "%.2f / %.2f t",
                            WeightSystem.equippedTons(mc.player),
                            WeightSystem.capacityTons(att.getPowerLevelRaw())), ZenkaiPalette.TEXT));
            out.add(Row.bar(Component.translatable("screen.zenkai.stats_screen.stat.load_pct.label"),
                    Component.literal(Math.round(load * 100) + "%"), ZenkaiPalette.VALUE,
                    (float) Math.min(100.0, load * 100), ZenkaiPalette.BAR_CONTROL));
            out.add(val("screen.zenkai.stats_screen.stat.weight_tp",
                    "x" + fmt2(WeightSystem.tpFactor(load)), ZenkaiPalette.OK));
        }

        out.add(Row.header("screen.zenkai.stats_screen.section.investment", ZenkaiPalette.SECTION_INVESTMENT));
        out.add(val("screen.zenkai.stats_screen.tp_spent",
                String.valueOf(att.raceStats().getTpSpent()), ZenkaiPalette.VALUE));
        out.add(val("screen.zenkai.stats_screen.points_invested",
                String.valueOf(att.raceStats().totalInvested()), ZenkaiPalette.TEXT));
        return out;
    }

    /** Popup derecho: lo que ANTES vivía en el tooltip del render y se perdía al moverse. */
    private List<Row> buildProgressRows(PlayerFormAttachment form) {
        assert mc.player != null;
        List<Row> out = new ArrayList<>();

        out.add(Row.header("screen.zenkai.stats_screen.section.form", ZenkaiPalette.SECTION_FORM));
        out.add(Row.of(formName(form.getFormId()),
                Component.literal("x" + fmt2(att.getStatMultiplier())), ZenkaiPalette.OK));
        out.add(Row.bar(Component.translatable("screen.zenkai.stats_screen.mastery_short.label"),
                Component.literal(fmt(form.getFormMastery(form.getFormId())) + "%"),
                ZenkaiPalette.GOLD, form.getFormMastery(form.getFormId()), ZenkaiPalette.BAR_MASTERY));

        if (!FormIds.BASE.equals(form.getFormId())) {
            double kiPerSecond = form.formKiDrainPerTick(att.getAttribute(ZenkaiAttributes.SPIRIT)) * 20.0;
            out.add(val("screen.zenkai.stats_screen.drain_short",
                    kiPerSecond > 0.0 ? "-" + fmt(kiPerSecond) + " ki/s" : "—",
                    kiPerSecond > 0.0 ? ZenkaiPalette.MAXED : ZenkaiPalette.TEXT_DIM));
        }

        KaiokenTier tier = form.getKaioken();
        if (tier.isOn()) {
            out.add(Row.header("screen.zenkai.stats_screen.section.kaioken", ZenkaiPalette.SECTION_KAIOKEN));
            double hpPerSecond = KaiokenSystem.drainPerTick(att, form,
                    SkillEffects.kaiokenDrainFactor(mc.player)) * 20.0;
            out.add(Row.of(Component.literal(tier.label()),
                    Component.literal("+" + fmt(tier.statPercent() * 100.0) + "%"), ZenkaiPalette.DENIED));
            out.add(Row.bar(Component.translatable("screen.zenkai.stats_screen.mastery_short.label"),
                    Component.literal(fmt(form.getKaiokenMastery()) + "%"),
                    ZenkaiPalette.GOLD, form.getKaiokenMastery(), ZenkaiPalette.BAR_MASTERY));
            out.add(val("screen.zenkai.stats_screen.drain_short",
                    "-" + fmt(hpPerSecond) + " HP/s", ZenkaiPalette.ERROR));
        }
        if (form.isStrained(mc.player.level().getGameTime())) {
            out.add(val("screen.zenkai.stats_screen.strain_short",
                    fmt(form.strainSecondsLeft(mc.player.level().getGameTime())) + "s",
                    ZenkaiPalette.ERROR));
        }

        // Pasiva racial: permanente, así que va en su propia sección. El nombre en la fila y
        // la descripción en el hover — meterla en el popup costaría tres filas y empujaría el
        // alineamiento fuera de la pantalla en resoluciones bajas.
        if (att.isRaceChosen()) {
            out.add(Row.header("screen.zenkai.stats_screen.section.race_passive",
                    ZenkaiPalette.SECTION_RACE));
            out.add(Row.tip(
                    Component.translatable(RacePassives.nameKey(att.getRace()))
                            .withStyle(net.minecraft.ChatFormatting.BOLD),
                    Component.empty(), ZenkaiPalette.MAXED,
                    Component.translatable(RacePassives.descKey(att.getRace()))));
            if (RacePassiveSystem.zenkaiActive(mc.player)) {
                out.add(val("screen.zenkai.stats_screen.zenkai_short",
                        RacePassiveSystem.zenkaiSecondsLeft(mc.player) + "s", ZenkaiPalette.VALUE));
            }
        }

        // Alineamiento: se mueve aquí desde el pie del panel, que estaba apretado.
        out.add(Row.header("screen.zenkai.stats_screen.alignment", ZenkaiPalette.SECTION_ALIGNMENT));
        // El NOMBRE lleva el color del estado, no solo el número. Antes "Neutral" salía en el
        // gris genérico de etiqueta y el color solo teñía el 0 de la derecha, así que el dato
        // más legible de la fila era el que menos dice.
        //
        // Tres colores discretos y no el degradado: en la barra la posición es continua y el
        // degradado tiene sentido, pero un nombre en un azul ligeramente distinto según tengas
        // 40 o 60 no comunica nada y hace el texto menos reconocible.
        int al = att.getAlignment();
        int alColor = ZenkaiPalette.alignmentColor(al);
        out.add(new Row(
                Component.translatable(al > 20 ? "screen.zenkai.alignment.good"
                        : al < -20 ? "screen.zenkai.alignment.evil"
                        : "screen.zenkai.alignment.neutral").withStyle(net.minecraft.ChatFormatting.BOLD),
                Component.literal((al > 0 ? "+" : "") + al),
                alColor, -1f, 0, null));
        return out;
    }

    /**
     * Fila etiqueta/valor del popup.
         * Usa las claves .label, NO las antiguas. Las de siempre traen el valor incrustado
     * ("Melee: %s", "Running: %s%%") porque se escribían en una sola línea; aquí la etiqueta y
     * el valor van en columnas separadas, así que al pasar la clave suelta el marcador quedaba
     * visible en pantalla como "Melee: %s   45.2". El sufijo .label da la versión sin marcador
     * y deja intactas las originales para quien las siga usando en línea.
     */
    private Row val(String key, String value, int color) {
        return Row.of(Component.translatable(key + ".label"), Component.literal(value), color);
    }

    private static String fmt(double d) { return String.format(Locale.ROOT, "%.1f", d); }
    private static String fmt2(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    // ── Tooltips ─────────────────────────────────────────────────────────────

    private void renderPowerTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX < plLabelX || mouseX >= plLabelX + plLabelW
                || mouseY < plLabelY || mouseY >= plLabelY + this.font.lineHeight) return;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.zenkai.stats_screen.pl.raw",
                String.valueOf(att.getPowerLevelRaw())));
        lines.add(Component.translatable("screen.zenkai.stats_screen.pl.releasable",
                String.valueOf(att.getReleasablePowerLevel())));
        lines.add(Component.translatable("screen.zenkai.stats_screen.pl.apparent",
                String.valueOf(att.getApparentPowerLevel())));
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private void renderKiBarTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (!overKiBar(mouseX, mouseY)) return;
        int cap = Math.min(100, SkillEffects.maxPowerPercent(att));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.zenkai.stats_screen.ki_control"));
        lines.add(Component.translatable("screen.zenkai.stats_screen.ki_control.cap", cap));
        lines.add(Component.translatable("screen.zenkai.stats_screen.ki_control.hint"));
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private void renderTpStepTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX >= tpcLabelX && mouseX < tpcLabelX + tpcLabelW &&
                mouseY >= tpcLabelY && mouseY < tpcLabelY + tpcLabelH) {
            g.renderTooltip(this.font, Component.translatable("screen.zenkai.stats_screen.tp_des"),
                    mouseX, mouseY);
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

    private boolean canAffordStep() {
        return att != null && att.getTP() >= computeCurrentTpCost();
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
         * Sin la segunda línea las sinergias serían invisibles y por tanto inexistentes para el
     * jugador: nadie va a deducir que su CON le está dando defensa comparando números.
     */
    private List<Component> getAttributeDescription(ZenkaiAttributes attr) {
        Race race = att.getRace();
        Style style = att.getStyle();
        List<Component> out = new ArrayList<>();

        // Valor EXACTO, y solo cuando la lista lo ha abreviado: repetir "STR: 5" en el tooltip
        // de una fila que ya dice "STR: 5" es ruido. Con separadores de miles, que es como se
        // lee un número de siete cifras sin contarlas.
        int raw = att.getAttribute(attr), eff = att.getEffectiveAttribute(attr);
        if (Math.abs(raw) >= COMPACT_FROM || Math.abs(eff) >= COMPACT_FROM) {
            out.add(Component.literal(ZenkaiNumbers.exact(raw))
                    .withStyle(net.minecraft.ChatFormatting.WHITE));
            if (eff != raw) {
                out.add(Component.translatable("tooltip.zenkai.attr.effective",
                        ZenkaiNumbers.exact(eff)).withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            out.add(Component.empty());
        }

        switch (attr) {
            case STRENGTH -> out.add(Component.translatable("tooltip.zenkai.attr.str",
                    fmt(RaceStatTable.melee(race, style))));
            case CONSTITUTION -> {
                out.add(Component.translatable("tooltip.zenkai.attr.con",
                        fmt(RaceStatTable.health(race, style)),
                        fmt(RaceStatTable.stamina(race, style))));
                out.add(cross("tooltip.zenkai.attr.con.cross",
                        fmt(RaceStatTable.defense(race, style) * StatSynergy.DEFENSE_FROM_CON)));
            }
            case DEXTERITY -> out.add(Component.translatable("tooltip.zenkai.attr.dex",
                    fmt(RaceStatTable.defense(race, style))));
            case WILLPOWER -> {
                out.add(Component.translatable("tooltip.zenkai.attr.wil",
                        fmt(RaceStatTable.kiDamage(race, style))));
                out.add(cross("tooltip.zenkai.attr.wil.cross",
                        fmt(RaceStatTable.melee(race, style) * StatSynergy.MELEE_FROM_WIL)));
            }
            case SPIRIT -> {
                out.add(Component.translatable("tooltip.zenkai.attr.spi",
                        fmt(RaceStatTable.kiReserves(race, style))));
                out.add(cross("tooltip.zenkai.attr.spi.cross",
                        fmt(RaceStatTable.kiDamage(race, style) * StatSynergy.KIPOWER_FROM_SPI)));
            }
            case MIND -> {
                out.add(Component.translatable("tooltip.zenkai.attr.mnd"));
                int free = att.mindFree();
                out.add(Component.translatable("tooltip.zenkai.attr.mnd.free", free)
                        .withStyle(free < 0 ? net.minecraft.ChatFormatting.RED
                                : net.minecraft.ChatFormatting.GRAY));
            }
        }

        out.add(Component.translatable("tooltip.zenkai.attr.invested",
                att.raceStats().investedIn(attr)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return out;
    }

    private Component cross(String key, String value) {
        return Component.translatable(key, value).withStyle(net.minecraft.ChatFormatting.DARK_AQUA);
    }
}