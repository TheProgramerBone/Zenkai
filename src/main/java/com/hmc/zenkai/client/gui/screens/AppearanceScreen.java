package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.buttons.AtlasIconButton;
import com.hmc.zenkai.client.gui.buttons.TextOnlyButton;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Personalización de aspecto tras elegir raza en RaceSelectionScreen — HUB de 3 filas
 * verticales ("Head"/"Body & Colors"/"Gender") que llevan a la sección correspondiente:
 *   - Head ({@link HeadAppearanceScreen}): ojos (estilo+color), pelo (estilo+color, solo
 *     Human/Saiyan), boca, nariz.
 *   - Body & Colors ({@link BodyColorsScreen}): tono de piel (Human/Saiyan/Majin) o filas de
 *     capa (Namek/Arcosian).
 *   - Gender: NO es una pantalla — fila inline aquí mismo, mismo lenguaje visual que Head/Body
 *     (icono a la izquierda + etiqueta), pero en vez de navegar a un sitio, un click en la fila
 *     ALTERNA Male/Female directamente y el icono cambia para reflejarlo (ICON_GENDER_MALE_U/V
 *     e ICON_GENDER_FEMALE_U/V, celdas reservadas en icons.png para arte hecho a mano — ver
 *     tools/gen_appearance_icons.py; dos rondas de icono generado, un badge pintado y luego un
 *     glifo ♂/♀ con fill(), no convencieron, así que esta vez no se genera). Solo razas de
 *     tinte simple tienen assets distintos por género hoy (isTintRace) — la fila aparece
 *     oscurecida con tooltip para Namek/Arcosian, mismo lenguaje que
 *     MasterScreen.renderHubOption para un maestro sin ese tipo de fila.
 * Antes esto era una sola clase con un enum Mode (ver git history) — se volvió a 3 Screen
 * separadas porque, en juego, tener un "‹ Back" pequeño (volver al hub) Y un "Back" grande
 * abajo (salir del paso de apariencia) en la MISMA pantalla resultaba confuso, y separarlas da
 * a Head/Body mucho más espacio propio para su contenido (Body & Colors en concreto: Arcosiano
 * tiene 6 filas de color, ver BodyColorsScreen). Por la misma razón, Head/Body NO tienen un
 * "‹ Back" propio arriba — solo el "Back" de la barra inferior, que en esas dos pantallas
 * vuelve aquí (al hub) en vez de a RaceSelectionScreen; un único botón "Back" por pantalla,
 * siempre "sube un nivel" (Hub -> RaceSelectionScreen, Head/Body -> Hub).
 * SINCRONIZACIÓN: la edición sigue siendo 100% local — cada pantalla escribe en su propio
 * PlayerVisualAttachment vía SU applyPreview() (aquí, solo el género); el ÚNICO paquete de red
 * del flujo Race→Appearance→Style sigue siendo el que manda
 * StyleSelectionScreen.onConfirm(). El Hub se REUTILIZA (Head/Body vuelven con
 * mc.setScreen(hubScreen), nunca crean uno nuevo) — mismo patrón que raceScreen ya usa hoy.
 * Head/Body en cambio se crean SIEMPRE de cero al entrar desde aquí (relo Head/Body no guardan
 * nada que no esté ya en el attachment). Cada pantalla marca un flag ANTES de cualquier
 * mc.setScreen(...) propio, y removed() (revierte contra statsSnapshot/visualSnapshot) solo
 * actúa si NINGÚN flag de salida intencional se marcó — igual que
 * RaceSelectionScreen/StyleSelectionScreen ya hacen. Como el Hub SÍ vuelve a mostrarse de
 * verdad tras un viaje a Head/Body, init() resetea sus propios flags cada vez (mismo patrón que
 * RaceSelectionScreen.init() ya resetea goingNext) — si no, un goingToHead=true de una visita
 * anterior protegería de un revert legítimo la próxima vez que el jugador cierre con Escape
 * estando de vuelta en el hub.
 */
public class AppearanceScreen extends Screen {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/common_screen.png");

    private static final ResourceLocation TEX_BTN =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/btn_wide.png");

    // Iconos de fila: v=80 de icons.png — (0,80)/(20,80) ya las usa MasterScreen (Técnicas/
    // Servicios). 40/60 = Head/Body (pintados por gen_appearance_icons.py). 80/100 = Gender
    // Male/Female — RESERVADAS, sin generar: el usuario las dibuja a mano (ver el propio
    // script, dos rondas de icono generado para esta fila no convencieron).
    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICONS_ATLAS = 256;
    private static final int ICON_CELL = 20;
    private static final int ICON_HEAD_U = 40, ICON_HEAD_V = 80;
    private static final int ICON_BODY_U = 60, ICON_BODY_V = 80;
    private static final int ICON_GENDER_MALE_U   = 80,  ICON_GENDER_MALE_V   = 80;
    private static final int ICON_GENDER_FEMALE_U = 100, ICON_GENDER_FEMALE_V = 80;

    private static final int BG_W = 256;
    private static final int BG_H = 256;
    private static final int IN_X1 = 10;
    private static final int IN_Y1 = 10;
    private static final int IN_X2 = 245;
    private static final int IN_Y2 = 240;
    private static final int BTN_BAR_Y = 260;
    private static final int BTN_W     = 60;

    private static final int CONTENT_Y0 = IN_Y1 + 6;
    private static final int PAD = 8;

    // 3 filas fijas: Head / Body & Colors / Gender, mismo alto — ninguna raza cambia cuántas
    // hay (Gender simplemente se ve oscurecida para Namek/Arcosian, el layout no salta de
    // tamaño, mismo principio que MasterScreen documenta en su propio comentario).
    private static final int HUB_ROW_H = 32;
    private static final int HUB_GAP   = 8;
    private static final int ROW1_Y = CONTENT_Y0;                      // Head
    private static final int ROW2_Y = ROW1_Y + HUB_ROW_H + HUB_GAP;    // Body & Colors
    private static final int ROW3_Y = ROW2_Y + HUB_ROW_H + HUB_GAP;    // Gender
    private static final int DIV_Y  = ROW3_Y + HUB_ROW_H + 6;

    /** Preview CENTRADO bajo las 3 filas — a diferencia de las 3 pantallas anteriores, aquí no
     *  hay nada más compartiendo esa franja, así que puede ocupar el centro entero. */
    private static final int PREVIEW_W_HUB    = 90;
    private static final int PREVIEW_SIZE_HUB = 55;

    // ── Zoom con rueda sobre el preview + botón de reset (lupa) — mismo criterio que
    // HeadAppearanceScreen/BodyColorsScreen (ver su javadoc): el recuadro no cambia con el
    // zoom, solo el "size"/escala del jugador dentro. A diferencia de esas dos, este Hub se
    // REUTILIZA (no se crea de cero en cada visita) — zoomMult persiste entre viajes a
    // Head/Body y vuelta, en vez de resetearse a 1.0 cada vez, igual que "race"/"genderFemale"
    // ya sobreviven a esos viajes.
    private static final float ZOOM_MIN = 0.6f, ZOOM_MAX = 1.8f, ZOOM_STEP = 0.1f;
    private static final int ZOOMED_SIZE_MIN = 20, ZOOMED_SIZE_MAX = 110;
    // Misma celda reservada que Head/Body (ver su javadoc): pintada a mano por el usuario.
    private static final int ICON_RESET_VIEW_U = 0, ICON_RESET_VIEW_V = 100;

    @Nullable private final RaceSelectionScreen raceScreen;
    private final CompoundTag statsSnapshot;
    private final CompoundTag visualSnapshot;

    private boolean confirmed = false, goingBack = false, goingNext = false;
    private boolean goingToHead = false, goingToBody = false;
    private int panelLeft, panelTop;
    private float zoomMult = 1.0f;

    private Race race = Race.HUMAN;
    private boolean genderFemale = false;

    public AppearanceScreen(@Nullable RaceSelectionScreen raceScreen,
                            @Nullable CompoundTag statsSnapshot,
                            @Nullable CompoundTag visualSnapshot) {
        super(Component.translatable("screen.zenkai.appearance.title"));
        this.raceScreen    = raceScreen;
        this.statsSnapshot  = statsSnapshot;
        this.visualSnapshot = visualSnapshot;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        this.clearWidgets();
        this.panelLeft = (this.width  - BG_W) / 2;
        this.panelTop  = (this.height - BG_H) / 2;
        // El hub SÍ vuelve a mostrarse de verdad (Head/Body regresan a esta misma instancia) —
        // sin este reset, un flag de una visita anterior protegería de un revert legítimo.
        goingBack = goingNext = goingToHead = goingToBody = false;

        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        this.race = stats.getRace();
        this.genderFemale = visual.getGender() == PlayerVisualAttachment.Gender.FEMALE;

        addRenderableWidget(new TextOnlyButton(
                panelLeft + IN_X1, panelTop + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.back"),
                TEX_BTN, null,
                () -> { goingBack = true;
                    mc.setScreen(raceScreen);
                }));

        addRenderableWidget(new TextOnlyButton(
                panelLeft + IN_X2 - BTN_W, panelTop + BTN_BAR_Y, BTN_W, 20,
                Component.translatable("screen.zenkai.next"),
                TEX_BTN, null,
                this::goToStyle));

        int[] rect = previewRect();
        var resetViewBtn = new AtlasIconButton(rect[2] - 18, rect[1] + 2,
                ICON_RESET_VIEW_U, ICON_RESET_VIEW_V, () -> zoomMult = 1.0f);
        resetViewBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.appearance.reset_view")));
        addRenderableWidget(resetViewBtn);
    }

    private boolean genderSupported() { return isTintRace(race); }

    private boolean isTintRace(Race r) {
        return r == Race.HUMAN || r == Race.SAIYAN || r == Race.MAJIN;
    }

    /** Recuadro (x1,y1,x2,y2) del preview — el mismo que dibuja render(), extraído para que
     *  init() (botón de reset) y mouseScrolled() (hover) no dupliquen la fórmula. Independiente
     *  de zoomMult: el recuadro no cambia con el zoom, solo el "size" del jugador dentro. */
    private int[] previewRect() {
        int previewCX = panelLeft + BG_W / 2;
        int x1 = previewCX - PREVIEW_W_HUB / 2, x2 = previewCX + PREVIEW_W_HUB / 2;
        int y1 = panelTop + DIV_Y + 8, y2 = panelTop + IN_Y2 - 4;
        return new int[] { x1, y1, x2, y2 };
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int pl = panelLeft, pt = panelTop;

        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(BG, pl, pt, 0, 0, BG_W, BG_H);

        ScreenTitle.drawAbovePanel(g, mc.font, this.title, pl + BG_W / 2, pt);

        renderHub(g, mouseX, mouseY);

        g.fill(pl + IN_X1 + PAD, pt + DIV_Y, pl + IN_X2 - PAD, pt + DIV_Y + 1, ZenkaiPalette.SEPARATOR);

        int[] rect = previewRect();
        int zoomedSize = Mth.clamp(Math.round(PREVIEW_SIZE_HUB * zoomMult), ZOOMED_SIZE_MIN, ZOOMED_SIZE_MAX);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, rect[0], rect[1], rect[2], rect[3], zoomedSize, 0.0625f,
                (float) mouseX, (float) mouseY, mc.player);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {}

    private void renderHub(GuiGraphics g, int mouseX, int mouseY) {
        int x = panelLeft + IN_X1 + PAD;
        int w = (panelLeft + IN_X2 - PAD) - x;

        renderHubOption(g, x, panelTop + ROW1_Y, w, HUB_ROW_H, ICON_HEAD_U, ICON_HEAD_V,
                Component.translatable("screen.zenkai.appearance.hub.head"), true, mouseX, mouseY);
        renderHubOption(g, x, panelTop + ROW2_Y, w, HUB_ROW_H, ICON_BODY_U, ICON_BODY_V,
                Component.translatable("screen.zenkai.appearance.hub.body"), true, mouseX, mouseY);

        boolean genderEnabled = genderSupported();
        renderHubOption(g, x, panelTop + ROW3_Y, w, HUB_ROW_H,
                genderFemale ? ICON_GENDER_FEMALE_U : ICON_GENDER_MALE_U,
                genderFemale ? ICON_GENDER_FEMALE_V : ICON_GENDER_MALE_V,
                Component.translatable("screen.zenkai.appearance.hub.gender"), genderEnabled, mouseX, mouseY);
    }

    /** Botón grande horizontal (icono a la izquierda, etiqueta a la derecha) sobre el panel
     *  beige — mismo fondo hundido/hover que ShenlongWishScreen usa para sus filas (INSET_BG/
     *  ROW_HOVER). Deshabilitado = icono atenuado + etiqueta apagada + tooltip al pasar el
     *  ratón. */
    private void renderHubOption(GuiGraphics g, int x, int y, int w, int h, int iconU, int iconV,
                                  Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hoveredRect = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        boolean hovered = enabled && hoveredRect;

        g.fill(x, y, x + w, y + h, hovered ? ZenkaiPalette.ROW_HOVER : ZenkaiPalette.INSET_BG);
        g.fill(x, y, x + w, y + 1, ZenkaiPalette.BORDER_IN);
        g.fill(x, y + h - 1, x + w, y + h, ZenkaiPalette.BORDER_IN);
        g.fill(x, y, x + 1, y + h, ZenkaiPalette.BORDER_IN);
        g.fill(x + w - 1, y, x + w, y + h, ZenkaiPalette.BORDER_IN);

        int iconX = x + 10;
        int iconY = y + (h - ICON_CELL) / 2;
        if (!enabled) g.setColor(0.6F, 0.6F, 0.6F, 1.0F);
        g.blit(ICONS_TEX, iconX, iconY, iconU, iconV, ICON_CELL, ICON_CELL, ICONS_ATLAS, ICONS_ATLAS);
        if (!enabled) g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        PanelText.onPanel(g, this.font, label, iconX + ICON_CELL + 8, y + (h - 8) / 2,
                enabled ? ZenkaiPalette.LABEL_ON_PANEL : ZenkaiPalette.MUTED_ON_PANEL);

        if (!enabled && hoveredRect) {
            Component tip = Component.translatable("screen.zenkai.appearance.hub.locked");
            g.renderTooltip(this.font, this.font.split(tip, 150), mouseX, mouseY);
        }
    }

    // ── Entrada ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && clickHub(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int[] rect = previewRect();
        if (mouseX >= rect[0] && mouseX < rect[2] && mouseY >= rect[1] && mouseY < rect[3]) {
            zoomMult = Mth.clamp(zoomMult + (dy > 0 ? ZOOM_STEP : -ZOOM_STEP), ZOOM_MIN, ZOOM_MAX);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    private boolean clickHub(double mouseX, double mouseY) {
        int x = panelLeft + IN_X1 + PAD;
        int w = (panelLeft + IN_X2 - PAD) - x;
        if (mouseX < x || mouseX >= x + w) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mouseY >= panelTop + ROW1_Y && mouseY < panelTop + ROW1_Y + HUB_ROW_H) {
            goingToHead = true;
            mc.setScreen(new HeadAppearanceScreen(this, statsSnapshot, visualSnapshot));
            return true;
        }
        if (mouseY >= panelTop + ROW2_Y && mouseY < panelTop + ROW2_Y + HUB_ROW_H) {
            goingToBody = true;
            mc.setScreen(new BodyColorsScreen(this, statsSnapshot, visualSnapshot));
            return true;
        }
        if (mouseY >= panelTop + ROW3_Y && mouseY < panelTop + ROW3_Y + HUB_ROW_H) {
            // Click en la fila entera = alterna Male/Female (si la raza lo soporta); no hay
            // nada más que cazar dentro de la fila, a diferencia de Head/Body que navegan.
            if (genderSupported()) { genderFemale = !genderFemale; applyPreview(); }
            return true;
        }
        return false;
    }

    private void applyPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        visual.setGender(genderFemale ? PlayerVisualAttachment.Gender.FEMALE
                : PlayerVisualAttachment.Gender.MALE);
    }

    private void goToStyle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        goingNext = true;
        mc.setScreen(new StyleSelectionScreen(this, statsSnapshot, visualSnapshot));
    }

    @Override
    public void removed() {
        if (!confirmed && !goingNext && !goingBack && !goingToHead && !goingToBody) {
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

    public void markConfirmed() {
        this.confirmed = true;
        if (raceScreen != null) raceScreen.markConfirmed();
    }

    @Override public boolean isPauseScreen() { return false; }
}
