package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraPreviewRenderer;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.buttons.ArrowIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.client.gui.widgets.ColorBoxButton;
import com.hmc.zenkai.client.gui.widgets.ColorPickerWidget;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.RaceStatTable;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.combat.PowerLevel;
import com.hmc.zenkai.feature.player.PlayerRaceStats;
import com.hmc.zenkai.network.ChooseStylePacket;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.feature.stats.ChooseRacePacket;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.race.UpdatePlayerVisualPacket;
import net.minecraft.client.Minecraft;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StyleSelectionScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int IN_X1 = 10;
    private static final int IN_Y1 = 10;
    private static final int IN_X2 = 245;
    private static final int IN_Y2 = 240;
    private static final int IN_W  = IN_X2 - IN_X1;
    private static final int BTN_BAR_Y = 260;
    private static final int BTN_W     = 60;

    private static final int PAD     = 8;
    private static final int ARROW_W = 12;
    private static final int TITLE_H = 11; // separación título → fila de valor

    private static final int COLOR_BOX_W = 20;
    private static final int COLOR_BOX_H = 12;
    /** Distancia del centro de la fila Ki Color al borde interior de cada flecha. Fija en los
     *  dos estados (Default/Custom) a propósito: si dependiera del ancho del texto/swatch, las
     *  flechas saltarían de sitio al alternar, el mismo "pop" que el resto del mod evita. */
    private static final int KI_ROW_HALF = 44;
    /** Separación entre el panel principal y el popup de Ki Color (antes 8: casi pegados). */
    private static final int PICKER_GAP = 14;

    private static final int S_TITLE_Y = ScreenTitle.CONTENT_TOP;
    private static final int S_VALUE_Y = S_TITLE_Y + TITLE_H;
    private static final int DIV1_Y    = S_VALUE_Y + 14;
    private static final int DESC_Y    = DIV1_Y + 6;
    private static final int DIV2_Y    = DESC_Y + 44; // espacio para ~4 líneas de descripción
    private static final int PREVIEW_W = 70;
    private static final int PREVIEW_SIZE = 45;

    // ── Colores de texto ──────────────────────────────────────────────────────
    /** Etiqueta del bloque. Antes 0x4A3726: el valor correcto pero SIN canal alfa. */
    private static final int COLOR_TITLE  = ZenkaiPalette.LABEL_ON_PANEL; // marrón oscuro → título de campo (Fighting Style)
    /** Valor elegido. Era blanco CON sombra sobre el beige: ilegible. Ahora dorado
     *  quemado y sin sombra, como el resto de valores del mod. */
    private static final int COLOR_VALUE  = ZenkaiPalette.VALUE_ON_PANEL; // blanco+sombra → valor seleccionado (Martial Artist, ...)
    /** Cuerpo de la descripción. Era 0x5A4636 suelto — el mismo valor que BODY_ON_PANEL,
     *  copiado a mano en vez de referenciado (RaceSelectionScreen tenía el mismo duplicado). */
    private static final int COLOR_DESC   = ZenkaiPalette.BODY_ON_PANEL; // marrón medio  → cuerpo de la descripción
    private static final int COLOR_SWATCH = ZenkaiPalette.LABEL_ON_PANEL; // bronce/dorado → etiqueta de swatch (Ki Color)

    // ── Bloque de estadísticas (a la derecha del preview) ─────────────────────
    private static final int STATS_X    = IN_X1 + PAD + PREVIEW_W + 6;   // 94
    private static final int STATS_R    = IN_X2 - PAD;                   // 237
    private static final int COL_BASE_R = STATS_X + 44;
    private static final int COL_COEF_R = STATS_X + 92;
    /** Justo debajo del swatch de Ki Color. */
    private static final int STATS_Y    = DIV2_Y + 8 + 14 + COLOR_BOX_H + 6;
    private static final int STAT_ROW_H = 9;

    /** Cabecera de columna, muy tenue. Era 0x7A6450 suelto — el mismo ROL que
     *  ZenkaiPalette.MUTED_ON_PANEL ya documenta ("Cabecera de columna y texto secundario"),
     *  reinventado con un hex ligeramente distinto en vez de reusar el nombrado. */
    private static final int COLOR_HEADER = ZenkaiPalette.MUTED_ON_PANEL;
    /** Pools (Body/Stamina/Ki), mismo tono que los títulos de campo — antes un segundo literal
     *  0x4A3726 idéntico a COLOR_TITLE en vez de reusarla, pese a que el propio comentario ya
     *  decía "mismo tono que los títulos". */
    private static final int COLOR_POOL   = COLOR_TITLE;

    @Nullable private final AppearanceScreen appearanceScreen;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean confirmed = false;
    private boolean goingBack = false;

    private int leftPos, topPos;
    private final Style[] styles = Style.values();
    private int styleIndex = 0;

    private int kiAreaCX;
    /** Color de aura por defecto. NO es paleta de interfaz: es un dato del personaje que el
     *  jugador va a cambiar con el selector, igual que el color de piel o de pelo. */
    private int kiColor = 0xFF33CCFF;
    // Solo se activa al TOCAR el picker (ver openKiPicker) — si se activara al confirmar sin
    // tocarlo, cualquiera que solo pasara por esta pantalla perdería el tinte por alineamiento
    // (AuraColors) la primera vez, sin haber elegido nunca un color propio.
    private boolean auraColorTouched = false;
    private boolean kiPickerOpen = false;
    @Nullable private ColorPickerWidget picker = null;
    @Nullable private ColorBoxButton colorBox = null;
    private int kiBoxY;

    public StyleSelectionScreen(@Nullable AppearanceScreen appearanceScreen,
                                @Nullable CompoundTag statsSnapshot,
                                @Nullable CompoundTag visualSnapshot) {
        super(Component.translatable("screen.zenkai.choose_style.title"));
        this.appearanceScreen = appearanceScreen;
        this.statsSnapshot    = statsSnapshot;
        this.visualSnapshot   = visualSnapshot;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.clearWidgets();
        this.leftPos = (this.width  - BG_W) / 2;
        this.topPos  = (this.height - BG_H) / 2;

        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());

        kiColor = visual.getAuraColorRgb() | 0xFF000000;
        auraColorTouched = visual.isCustomAuraColor();

        Style cur = stats.getStyle();
        for (int i = 0; i < styles.length; i++) {
            if (styles[i] == cur) { styleIndex = i; break; }
        }

        int lp = leftPos;
        int tp = topPos;

        // Flechas de estilo — a los lados de la fila de valor
        int arrowY = tp + S_VALUE_Y - 2;
        addRenderableWidget(new ArrowIconButton(
                lp + IN_X1 + PAD, arrowY,
                ArrowIconButton.Dir.LEFT,
                () -> styleIndex = (styleIndex - 1 + styles.length) % styles.length));
        addRenderableWidget(new ArrowIconButton(
                lp + IN_X2 - PAD - ARROW_W, arrowY,
                ArrowIconButton.Dir.RIGHT,
                () -> styleIndex = (styleIndex + 1) % styles.length));

        // Fila Ki Color — a la derecha del preview (patrón de AppearanceScreen), MISMO lenguaje
        // que la fila de Fighting Style de arriba: flechas a los lados, valor centrado. Default
        // (el aura sigue el alineamiento, ver AuraColors) es el estado inicial; la flecha
        // alterna a Custom y ahí abre el popup de color. Ver toggleKiColorMode.
        this.kiAreaCX = lp + IN_X1 + PAD + PREVIEW_W + (IN_W - PAD * 2 - PREVIEW_W) / 2;
        int kiBoxX = kiAreaCX - COLOR_BOX_W / 2;
        this.kiBoxY = tp + DIV2_Y + 8 + 14;
        addRenderableWidget(new ArrowIconButton(
                kiAreaCX - KI_ROW_HALF - ARROW_W, kiBoxY,
                ArrowIconButton.Dir.LEFT, this::toggleKiColorMode));
        addRenderableWidget(new ArrowIconButton(
                kiAreaCX + KI_ROW_HALF, kiBoxY,
                ArrowIconButton.Dir.RIGHT, this::toggleKiColorMode));
        colorBox = new ColorBoxButton(kiBoxX, kiBoxY, COLOR_BOX_W, COLOR_BOX_H,
                () -> kiColor & 0xFFFFFF, () -> kiPickerOpen, this::toggleKiPicker);
        colorBox.visible = auraColorTouched;
        addRenderableWidget(colorBox);

        // Botones en la barra inferior (solo texto, sin fondo gris de vanilla)
        addRenderableWidget(new TextOnlyButton(
                lp + IN_X1, tp + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.back"),
                TEX_BTN, null,
                () -> { goingBack = true;
                    mc.setScreen(appearanceScreen);
                }));

        addRenderableWidget(new TextOnlyButton(
                lp + IN_X2 - BTN_W, tp + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.confirm"),
                TEX_BTN, null,          // ← textura normal, y null = sin versión hover
                this::onConfirm));
    }

    private void toggleKiPicker() {
        if (kiPickerOpen && picker != null) { closeKiPicker(); return; }
        openKiPicker();
    }

    private void openKiPicker() {
        closeKiPicker();
        kiPickerOpen = true;
        int pickerX = leftPos + BG_W + PICKER_GAP;
        if (pickerX + ColorPickerWidget.TOTAL_W > this.width - 4)
            pickerX = leftPos - ColorPickerWidget.TOTAL_W - PICKER_GAP;
        picker = new ColorPickerWidget(pickerX, topPos + IN_Y1, kiColor, "Ki Color", argb -> {
            kiColor = argb;
            auraColorTouched = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null)
                mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).setAuraColorRgb(argb & 0xFFFFFF);
        });
        addRenderableWidget(picker);
    }

    private void closeKiPicker() {
        if (picker != null) { removeWidget(picker); picker = null; }
        kiPickerOpen = false;
    }

    /**
     * Flechas de la fila Ki Color: alternan Default (el aura sigue AuraColors/el alineamiento,
     * ver el propio tooltip) y Custom (swatch + popup de color). Solo dos estados, así que las
     * dos flechas hacen lo mismo — no hay "más allá" que recorrer en ningún sentido.
     * Pasar a Custom abre el popup DE INMEDIATO (pedido explícito): no hace falta un segundo
     * click en el swatch. Volver a Default cierra el popup si estaba abierto, pero NO toca
     * `kiColor` — si el jugador vuelve a Custom más tarde, recupera el mismo color que dejó.
     */
    private void toggleKiColorMode() {
        auraColorTouched = !auraColorTouched;
        if (colorBox != null) colorBox.visible = auraColorTouched;
        if (auraColorTouched) {
            openKiPicker();
        } else {
            closeKiPicker();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Style s = styles[styleIndex];
        String styleKey = "screen.zenkai.style." + s.name().toLowerCase();
        int lp = leftPos;
        int tp = topPos;
        int cx = lp + BG_W / 2;

        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, lp, tp, 0, 0, BG_W, BG_H);

        ScreenTitle.drawAbovePanel(g, mc.font, this.title, cx, tp);

        // Bloque estilo — título arriba, valor (entre flechas) debajo
        drawCenteredNoShadow(g, Component.translatable("screen.zenkai.label.style"),
                cx, tp + S_TITLE_Y, COLOR_TITLE);
        PanelText.centeredOnPanel(g, mc.font,
                Component.translatable(styleKey),
                cx, tp + S_VALUE_Y, COLOR_VALUE);

        g.fill(lp + IN_X1 + PAD, tp + DIV1_Y, lp + IN_X2 - PAD, tp + DIV1_Y + 1, ZenkaiPalette.SEPARATOR);

        // Descripción
        String[] lines = wrapText(Component.translatable(styleKey + ".desc").getString(),
                mc.font, IN_W - PAD * 2);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(mc.font, Component.literal(lines[i]),
                    lp + IN_X1 + PAD, tp + DESC_Y + i * 11, COLOR_DESC, false);
        }

        g.fill(lp + IN_X1 + PAD, tp + DIV2_Y, lp + IN_X2 - PAD, tp + DIV2_Y + 1, ZenkaiPalette.SEPARATOR);

        // Preview jugador — izquierda zona inferior (con aura de ki EN VIVO)
        int pvX1 = lp + IN_X1 + PAD;
        int pvY1 = tp + DIV2_Y + 8;
        int pvX2 = lp + IN_X1 + PAD + PREVIEW_W;
        int pvY2 = tp + IN_Y2 - 4;
        g.enableScissor(pvX1, pvY1, pvX2, pvY2);   // recorta el aura al recuadro del preview
        AuraPreviewRenderer.ACTIVE = true;         // el hook dibuja el aura SOLO en esta pasada
        // El color de ki elegido aquí (Default/Custom + swatch) no se guarda en el attachment
        // hasta onConfirm — sin este override el preview seguiría mostrando el tinte YA
        // guardado (p. ej. el de alineamiento) aunque el jugador ya haya pasado a Custom.
        AuraPreviewRenderer.colorOverrideActive = true;
        AuraPreviewRenderer.colorOverrideCustom = auraColorTouched;
        AuraPreviewRenderer.colorOverrideRgb = kiColor & 0xFFFFFF;
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, pvX1, pvY1, pvX2, pvY2,
                    PREVIEW_SIZE, 0.0625f,
                    (float) mouseX, (float) mouseY, mc.player);
        } finally {
            AuraPreviewRenderer.ACTIVE = false;
            AuraPreviewRenderer.colorOverrideActive = false;
            g.disableScissor();
        }

        drawStatBlock(g, mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).getRace(),
                s, lp, tp);

        // Label "Ki Color" encima de la fila de flechas
        drawCenteredNoShadow(g, Component.translatable("screen.zenkai.label.ki_color"),
                kiAreaCX, tp + DIV2_Y + 8, COLOR_SWATCH);

        // Estado Default: el swatch (widget, ver init) está oculto y en su lugar se dibuja el
        // texto "Default" — con tooltip manual porque no es un widget, mismo patrón que el
        // resto de tooltips dibujados a mano en este mod (ver MasterScreen/SkillsScreen).
        if (!auraColorTouched) {
            PanelText.centeredOnPanel(g, mc.font,
                    Component.translatable("screen.zenkai.ki_color.mode.default"),
                    kiAreaCX, kiBoxY + 1, COLOR_VALUE);
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (!auraColorTouched) {
            int tx1 = kiAreaCX - KI_ROW_HALF, tx2 = kiAreaCX + KI_ROW_HALF;
            int ty1 = kiBoxY, ty2 = ty1 + COLOR_BOX_H;
            if (mouseX >= tx1 && mouseX < tx2 && mouseY >= ty1 && mouseY < ty2) {
                g.renderTooltip(mc.font, mc.font.split(
                        Component.translatable("screen.zenkai.ki_color.mode.default.tooltip"), 150),
                        mouseX, mouseY);
            }
        }
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    /** Delega en PanelText: la regla de sombra vive en un solo sitio. */
    private void drawCenteredNoShadow(GuiGraphics g, Component text, int cx, int y, int color) {
        PanelText.centeredOnPanel(g, Minecraft.getInstance().font, text, cx, y, color);
    }

    /**
     * Atributos de salida, cuánto rinde cada punto y el stat efectivo resultante, para la
     * combinación raza+estilo que el jugador está mirando ahora mismo.
     * Los VALORES BASE son de la raza y no cambian al girar la flecha; lo que cambia son los
     * coeficientes y, por tanto, las dos columnas de la derecha. Por eso la base va en el
     * tono apagado y el efectivo en blanco: se ve de un vistazo qué está eligiendo.
     * El PL sale de PowerLevel.compute, no de una suma a mano: si mañana tocas los pesos del
     * medidor, esta pantalla cambia sola en vez de mentir.
     */
    private void drawStatBlock(GuiGraphics g, Race race, Style style, int lp, int tp) {
        var font = Minecraft.getInstance().font;
        int[] base = RaceStatTable.baseAttributes(race);

        int str = base[ZenkaiAttributes.STRENGTH.ordinal()];
        int con = base[ZenkaiAttributes.CONSTITUTION.ordinal()];
        int dex = base[ZenkaiAttributes.DEXTERITY.ordinal()];
        int wil = base[ZenkaiAttributes.WILLPOWER.ordinal()];
        int spi = base[ZenkaiAttributes.SPIRIT.ordinal()];

        double melee  = str * RaceStatTable.melee(race, style);
        double body   = con * RaceStatTable.health(race, style);
        double defe   = dex * RaceStatTable.defense(race, style);
        double kiDmg  = wil * RaceStatTable.kiDamage(race, style);
        double kiPool = spi * RaceStatTable.kiReserves(race, style);

        long pl = PowerLevel.compute(melee, body, defe, kiDmg, kiPool);

        int y = tp + STATS_Y;

        // Titular: el número que el jugador va a comparar entre estilos.
        drawCenteredNoShadow(g, Component.translatable("screen.zenkai.style.power", pl),
                lp + (STATS_X + STATS_R) / 2, y, COLOR_VALUE);
        y += 11;

        drawRight(g, Component.translatable("screen.zenkai.style.col_base"),
                lp + COL_BASE_R, y, COLOR_HEADER);
        drawRight(g, Component.translatable("screen.zenkai.style.col_per"),
                lp + COL_COEF_R, y, COLOR_HEADER);
        drawRight(g, Component.translatable("screen.zenkai.style.col_total"),
                lp + STATS_R, y, COLOR_HEADER);
        y += 10;

        for (ZenkaiAttributes a : ZenkaiAttributes.values()) {
            g.drawString(font, Component.translatable("attr.zenkai." + a.name().toLowerCase() + ".short"),
                    lp + STATS_X, y, COLOR_TITLE, false);

            drawRight(g, Component.literal(String.valueOf(base[a.ordinal()])),
                    lp + COL_BASE_R, y, COLOR_DESC);

            RaceStatTable.Col col = RaceStatTable.colFor(a);
            if (col == null) {
                // MND no da stat de combate: es requisito de habilidades.
                drawRight(g, Component.literal("—"), lp + COL_COEF_R, y, COLOR_DESC);
                drawRight(g, Component.literal("—"), lp + STATS_R, y, COLOR_DESC);
            } else {
                double coef = RaceStatTable.get(race, style, col);
                drawRight(g, Component.literal("×" + trim(coef)), lp + COL_COEF_R, y, COLOR_DESC);
                drawRight(g, boldValue(String.valueOf(Math.round(base[a.ordinal()] * coef))),
                        lp + STATS_R, y, COLOR_VALUE);
            }
            y += STAT_ROW_H;
        }

        y += 3;
        g.fill(lp + STATS_X, y, lp + STATS_R, y + 1, ZenkaiPalette.SEPARATOR);
        y += 4;

        // Los pools no son stats de combate: llevan offset y su propia escala de config, así
        // que salen del mismo sitio que los del jugador real y no de una fórmula copiada.
        var p = PlayerRaceStats.pools(race, style, con, spi);
        drawPool(g, "screen.zenkai.style.body",    p.bodyMax(),    lp, y);
        drawPool(g, "screen.zenkai.style.stamina", p.staminaMax(), lp, y + STAT_ROW_H);
        drawPool(g, "screen.zenkai.style.ki",      p.energyMax(),  lp, y + STAT_ROW_H * 2);
    }

    private void drawPool(GuiGraphics g, String key, int value, int lp, int y) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable(key), lp + STATS_X, y, COLOR_POOL, false);
        drawRight(g, boldValue(String.valueOf(value)), lp + STATS_R, y, COLOR_VALUE);
    }

    /**
     * Texto alineado a la derecha de x. Los números en columna solo se leen así.
     * SIEMPRE sin sombra: sobre el beige del panel la regla del mod es sin sombra para
     * cualquier color _ON_PANEL (ver cabecera de ZenkaiPalette), sea cual sea. Antes esto
     * activaba sombra "cuando color == COLOR_VALUE" — la columna Total y los pools salían
     * con una sombra que ningún otro valor del mod lleva sobre beige. El realce que se
     * buscaba con eso sale ahora de negrita en el propio Component (ver boldValue), no de
     * romper la regla de sombra.
     */
    private void drawRight(GuiGraphics g, Component text, int x, int y, int color) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, text, x - font.width(text), y, color, false);
    }

    /** El total calculado (columna Total, pools): el número que el jugador realmente compara
     *  entre estilos, así que lleva más peso visual que la base/coeficiente de al lado. */
    private static Component boldValue(String s) {
        return Component.literal(s).withStyle(net.minecraft.ChatFormatting.BOLD);
    }

    /** 11.0 -> "11", 9.4 -> "9.4". Un decimal muerto ocupa columna y no dice nada. */
    private static String trim(double d) {
        return d == Math.rint(d)
                ? String.valueOf((long) d)
                : String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker != null) {
            boolean inside = mx >= picker.getX() && mx < picker.getX() + ColorPickerWidget.TOTAL_W
                    && my >= picker.getY() && my < picker.getY() + ColorPickerWidget.TOTAL_H;
            if (!inside) closeKiPicker();
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void removed() {
        if (!confirmed && !goingBack) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
                var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
                if (statsSnapshot  != null) stats.load(statsSnapshot);
                if (visualSnapshot != null) visual.load(visualSnapshot);
            }
        }
        super.removed();
    }

    private void onConfirm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        visual.setAuraColorRgb(kiColor & 0xFFFFFF);
        visual.setCustomAuraColor(auraColorTouched);
        PacketDistributor.sendToServer(new ChooseRacePacket(stats.getRace()));
        PacketDistributor.sendToServer(UpdatePlayerVisualPacket.from(visual));
        PacketDistributor.sendToServer(new ChooseStylePacket(styles[styleIndex]));
        confirmed = true;
        if (appearanceScreen != null) appearanceScreen.markConfirmed();
        mc.setScreen(null);
    }

    private String[] wrapText(String text, net.minecraft.client.gui.Font font, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.isEmpty() ? w : line + " " + w;
            if (font.width(test) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString()); line = new StringBuilder(w);
            } else line = new StringBuilder(test);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.toArray(new String[0]);
    }

    @Override public boolean isPauseScreen() { return false; }
}