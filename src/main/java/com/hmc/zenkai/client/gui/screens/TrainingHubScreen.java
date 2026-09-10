package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.StatBar;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.training.TrainingFatigueRequestPacket;
import com.hmc.zenkai.feature.weights.WeightSystem;
import com.hmc.zenkai.registry.ModDimensions;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hub de la pestaña Training: 3 filas de minijuego ("Train with your shadow" / "Meditation" /
 * "Ki Target Practice", ver sus respectivas *TrainingScreen/MeditationScreen/
 * TargetPracticeScreen) + el panel "TP Modifiers", que es la sección "Carga" que ANTES vivía en
 * el popup de StatsScreen (buildStatRows) — se migró aquí entera porque el popup de Stats ya iba
 * apretado de espacio y porque este es el sitio temático correcto para mostrar TODO lo que
 * afecta la ganancia de TP, pesas Y HTC juntos (HTC no se mostraba en ningún lado antes).
 *
 * Mismo idioma visual "hub de filas grandes" que {@link AppearanceScreen} — ícono a la
 * izquierda, etiqueta a la derecha, y cada fila abre una Screen de verdad en vez de cambiar un
 * enum-Mode interno (ver el javadoc de AppearanceScreen para por qué se prefirió eso).
 *
 * La carga de pesas ya viaja en PlayerStatsAttachment (sincronizado por SyncPlayerStatsPacket) y
 * la dimensión HTC es un dato puramente local del cliente (siempre sabe en qué dimensión está su
 * propio jugador) — ver el comentario de ServerConfig.trainingHtcMultiplier() para el
 * multiplicador, ya sincronizado automáticamente por ser Type.SERVER. La fatiga
 * (TrainingData.fatigue) SÍ necesita un packet propio (no vive en ningún attachment ya
 * sincronizado) — ver TrainingFatigueRequestPacket/onFatigueReceived, pedido explícito del
 * usuario tras quedar fuera a propósito en la primera versión de este panel.
 */
public class TrainingHubScreen extends ZenkaiMenuScreen {

    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICONS_ATLAS = 256;
    private static final int ICON_CELL = 20;
    // Íconos propios (tools/gen_training_hub_icons.py, fila v=120) — antes estas 3 filas
    // reusaban celdas de otro concepto visual (ki-charge/turbo/kaioken del HUD de estado) como
    // placeholder, y Shadow/Meditation acababan compartiendo literalmente el mismo glifo.
    private static final int ICON_SHADOW_U = 140, ICON_SHADOW_V = 80;
    private static final int ICON_MEDITATION_U = 160, ICON_MEDITATION_V = 80;
    private static final int ICON_TARGET_PRACTICE_U = 180, ICON_TARGET_PRACTICE_V = 80;

    // El beige real de common_screen.png va de x=12 a x=244 (muestreado píxel a píxel) — 10/245
    // se metían 2px dentro del marco naranja por cada lado, la causa real del "solapamiento con
    // los bordes de la GUI" reportado por el usuario. 243, no 244, deja 1px de aire de sobra.
    private static final int IN_X1 = 12;
    private static final int IN_X2 = 243;
    private static final int HUB_ROW_H = 32;
    private static final int HUB_GAP = 6;
    private static final int ROW1_Y = CONTENT_TOP + 8;
    private static final int ROW2_Y = ROW1_Y + HUB_ROW_H + HUB_GAP;
    private static final int ROW3_Y = ROW2_Y + HUB_ROW_H + HUB_GAP;

    private static final int PANEL_Y = ROW3_Y + HUB_ROW_H + 10;
    private static final int PANEL_ROW_H = 12;
    /** Y de la fila "Gravity" dentro del panel — 2ª línea, justo debajo del título. Constante
     *  propia (no recalculada dentro de renderModifiersPanel) porque clickHub necesita el MISMO
     *  número para el hit-test del botón sin duplicar el cálculo en dos sitios que puedan
     *  desincronizarse. +3 y no +2 (como el resto de filas) porque el botón dibuja un borde 1px
     *  por ENCIMA de su texto (y-1): con el mismo hueco que una fila normal, ese borde quedaba a
     *  un pixel de la línea de abajo de "TP Modifiers" — feedback de imagen 2026-09-10. */
    private static final int GRAVITY_ROW_Y = PANEL_Y + PANEL_ROW_H + 3;
    /** Alto del botón "Gravity" — más que PANEL_ROW_H (12) porque lleva marco propio (1px
     *  arriba/abajo) y sin aire extra ese marco tocaba la fila siguiente ("TP bonus"),
     *  feedback de imagen 2026-09-10: "solapamiento de textos" era en realidad el borde
     *  pegado al texto de abajo, no dos textos dibujados en el mismo sitio. */
    private static final int GRAVITY_ROW_H = PANEL_ROW_H + 3;
    /** Ancho del popup de detalle de gravedad — mismo espíritu que POPUP_W de StatsScreen, pero
     *  más ancho: 150 se quedaba corto para "Total load" + "140.00 / 191.65 t" en la misma
     *  línea y el texto se solapaba (feedback de imagen, 2026-09-10). */
    private static final int GRAVITY_POPUP_W = 170;
    private static final int GRAVITY_POPUP_GAP = 8;

    /** null hasta que responde TrainingFatigueRequestPacket — ver onFatigueReceived(). 1.0 =
     *  sin penalización (fatiga en 0). Las TRES son independientes desde 2026-09-09 (pedido
     *  explícito del usuario: las fatigas ya no se comparten entre categorías — ver
     *  TrainingCategory) — antes era un único `Double`. */
    private Double combatFatigueEfficiency;
    private Double meditationFatigueEfficiency;
    private Double targetPracticeFatigueEfficiency;

    /** Popup de detalle de la fila "Gravity" — pedido explícito del usuario (2026-09-10): un
     *  solo texto de gravedad en el panel, con el desglose (equipo/ambiental/fuente/total)
     *  detrás de un botón en vez de varias filas siempre visibles. */
    private boolean showGravityPopup;

    public TrainingHubScreen() {
        super(Component.translatable(ZenkaiTab.TRAINING.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.TRAINING; }

    @Override
    protected void initContent() {
        // Sin widgets propios: filas y panel se dibujan/hit-testean a mano.
        combatFatigueEfficiency = null;
        meditationFatigueEfficiency = null;
        targetPracticeFatigueEfficiency = null;
        showGravityPopup = false;
        PacketDistributor.sendToServer(new TrainingFatigueRequestPacket());
    }

    /** Respuesta a TrainingFatigueRequestPacket (ver ClientPayloadHandlers.onTrainingFatigue) —
     *  empujada aquí igual que TrainingMinigameScreen.onRewardReceived/onTrainingInfoReceived,
     *  pero este hub no implementa esa interfaz (no es un minijuego), así que es un método
     *  público normal en vez de una sobrescritura. */
    public void onFatigueReceived(double combatEfficiency, double meditationEfficiency,
                                   double targetPracticeEfficiency) {
        combatFatigueEfficiency = combatEfficiency;
        meditationFatigueEfficiency = meditationEfficiency;
        targetPracticeFatigueEfficiency = targetPracticeEfficiency;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);
        if (att == null || mc.player == null) return;

        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;

        // Sin tooltip propio (pedido explícito del usuario): ese mismo párrafo ya se enseña
        // dentro de ShadowTrainingScreen al entrar, así que un tooltip aquí en el hub solo
        // duplicaba el texto un paso antes de que hiciera falta.
        renderHubOption(g, x, panelTop + ROW1_Y, w, HUB_ROW_H, ICON_SHADOW_U, ICON_SHADOW_V,
                Component.translatable("screen.zenkai.training_hub.row.shadow"), mouseX, mouseY, null);
        renderHubOption(g, x, panelTop + ROW2_Y, w, HUB_ROW_H, ICON_MEDITATION_U, ICON_MEDITATION_V,
                Component.translatable("screen.zenkai.training_hub.row.meditation"), mouseX, mouseY, null);
        renderHubOption(g, x, panelTop + ROW3_Y, w, HUB_ROW_H,
                ICON_TARGET_PRACTICE_U, ICON_TARGET_PRACTICE_V,
                Component.translatable("screen.zenkai.training_hub.row.target_practice"), mouseX, mouseY, null);

        renderModifiersPanel(g, x, panelTop + PANEL_Y, w, mouseX, mouseY);

        if (showGravityPopup) renderGravityPopup(g, mouseX, mouseY);
    }

    /** Botón grande horizontal (ícono izq + etiqueta der), mismo idioma que
     *  AppearanceScreen. `tooltip` opcional: el pedido explícito de "Train
     *  with your shadow" es enseñar un resumen ANTES de entrar (qué es, para qué sirve). */
    private void renderHubOption(GuiGraphics g, int x, int y, int w, int h, int iconU, int iconV,
                                  Component label, int mouseX, int mouseY, Component tooltip) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        g.fill(x, y, x + w, y + h, hovered ? ZenkaiPalette.ROW_HOVER : ZenkaiPalette.INSET_BG);
        g.fill(x, y, x + w, y + 1, ZenkaiPalette.BORDER_IN);
        g.fill(x, y + h - 1, x + w, y + h, ZenkaiPalette.BORDER_IN);
        g.fill(x, y, x + 1, y + h, ZenkaiPalette.BORDER_IN);
        g.fill(x + w - 1, y, x + w, y + h, ZenkaiPalette.BORDER_IN);

        int iconX = x + 10;
        int iconY = y + (h - ICON_CELL) / 2;
        g.blit(ICONS_TEX, iconX, iconY, iconU, iconV, ICON_CELL, ICON_CELL, ICONS_ATLAS, ICONS_ATLAS);

        PanelText.onPanel(g, this.font, label, iconX + ICON_CELL + 8, y + (h - 8) / 2,
                ZenkaiPalette.LABEL_ON_PANEL);

        if (tooltip != null && hovered) {
            g.renderTooltip(this.font, this.font.split(tooltip, 200), mouseX, mouseY);
        }
    }

    /** Margen derecho fijo para todo texto alineado a la derecha de este panel — pegado a `x+w`
     *  a secas quedaba a ras del borde/las esquinas del panel (feedback de imagen: "ajusta el
     *  margen de la letra"). */
    private static final int PANEL_RIGHT_MARGIN = 3;

    /**
     * Panel "TP Modifiers" — antigua sección "Carga" de StatsScreen, ampliada con HTC y fatiga.
     * Rediseño 2026-09-10 (pedido explícito del usuario, "solo 1 texto de gravedad"): el
     * desglose de equipo/gravedad ambiental/% de carga que antes vivía en 3-4 filas siempre
     * visibles se movió DETRÁS de un botón — la fila "Gravity" (ver renderGravityRow) abre un
     * popup con el mismo formato de colores que los popups de StatsScreen (renderGravityPopup).
     * Aquí solo quedan: Gravity (botón), TP bonus, HTC si aplica, y tres filas de eficiencia
     * efectiva (pesas+gravedad × HTC × fatiga de ESA categoría) — una por Combat/Meditation/
     * Target Practice, SIEMPRE visibles: las fatigas de entrenamiento ya no se comparten entre
     * categorías (2026-09-09, ver TrainingCategory), así que ya no existe un "efectivo" único.
     */
    private void renderModifiersPanel(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        var player = mc.player;
        assert player != null;

        double load = att.getWeightLoad();
        boolean inHtc = player.level().dimension() == ModDimensions.HTC_LEVEL;
        double weightMult = WeightSystem.tpFactor(load);
        double htcMult = ServerConfig.trainingHtcMultiplier();
        double baseMult = weightMult * (inHtc ? htcMult : 1.0);

        int ty = y;
        PanelText.onPanel(g, this.font,
                Component.translatable("screen.zenkai.training_hub.panel.title")
                        .copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                x, ty, ZenkaiPalette.LABEL_ON_PANEL);
        ty += PANEL_ROW_H + 3;

        renderGravityRow(g, x, ty, w, player, mouseX, mouseY);
        ty += GRAVITY_ROW_H + 2;

        // Siempre visible, incluso en x1.00: es la MISMA cifra que ya multiplican las filas de
        // eficiencia de abajo (baseMult), así que ocultarla cuando vale 1 rompería la lectura de
        // "por qué Combat efficiency es x4.04 y no x4.19" al comparar con HTC.
        panelRow(g, x, ty, w,
                Component.translatable("screen.zenkai.stats_screen.stat.weight_tp.label"),
                Component.literal("x" + ZenkaiNumbers.fmt2(weightMult)));
        ty += PANEL_ROW_H;

        if (inHtc) {
            panelRow(g, x, ty, w,
                    Component.translatable("screen.zenkai.training_hub.panel.htc_mult"),
                    Component.literal("x" + ZenkaiNumbers.fmt2(htcMult)));
            ty += PANEL_ROW_H;
        }

        // Una fila EFECTIVA por categoría (2026-09-09, pedido explícito del usuario: las
        // fatigas de entrenamiento ya no se comparten entre categorías, ver TrainingCategory) —
        // antes era una sola fila "fatiga" + una sola fila "efectivo" combinadas, pero con tres
        // fatigas independientes un único "efectivo" ya no significa nada. Cada fila de aquí YA
        // incluye pesas+HTC (baseMult) multiplicados por la fatiga de ESA categoría — es
        // literalmente "cuánto TP real estás sacando ahora mismo de entrenar así". null (el
        // packet aún no respondió) se trata como sin penalización, igual que antes. Tooltip con
        // cuánto/en cuánto tiempo decae — pedido explícito del usuario.
        ty = fatigueRow(g, x, ty, w, mouseX, mouseY, "screen.zenkai.training_hub.panel.effective_combat",
                baseMult, combatFatigueEfficiency);
        ty = fatigueRow(g, x, ty, w, mouseX, mouseY, "screen.zenkai.training_hub.panel.effective_meditation",
                baseMult, meditationFatigueEfficiency);
        fatigueRow(g, x, ty, w, mouseX, mouseY, "screen.zenkai.training_hub.panel.effective_target_practice",
                baseMult, targetPracticeFatigueEfficiency);
    }

    /** Una fila "eficiencia de X" = baseMult (pesas × HTC) × fatiga de esa categoría. Devuelve
     *  la Y de la siguiente fila. */
    private int fatigueRow(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY, String labelKey,
                           double baseMult, Double categoryEfficiency) {
        double eff = baseMult * (categoryEfficiency != null ? categoryEfficiency : 1.0);
        panelRow(g, x, y, w, Component.translatable(labelKey),
                Component.literal("x" + ZenkaiNumbers.fmt2(eff)));
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + PANEL_ROW_H) {
            g.renderTooltip(this.font, this.font.split(fatigueTooltip(categoryEfficiency), 200), mouseX, mouseY);
        }
        return y + PANEL_ROW_H;
    }

    /**
     * Tooltip de "¿en cuánto baja/se reinicia la fatiga?" — pedido explícito del usuario. La
     * fatiga REAL de esta categoría no viaja por ningún packet (solo la eficiencia YA resultante,
     * ver TrainingFatiguePacket), pero se puede recuperar invirtiendo la MISMA fórmula que
     * TrainingHooks.currentEfficiency() usa para calcularla: m = h/(h+fatiga), así que
     * fatiga = h·(1/m - 1). Con esa fatiga y la tasa de decaimiento (ServerConfig, Type.SERVER =
     * ya sincronizado, no hace falta pedir nada nuevo al servidor) se deriva un "vuelve a estar
     * fresco en ~X min" REAL para ESTE jugador ahora mismo, no un texto genérico de mecánica —
     * mismo espíritu que el resto de Training: nunca mostrar un número que el cliente adivina.
     */
    private Component fatigueTooltip(Double categoryEfficiency) {
        double h = ServerConfig.trainingFatigueHalfLife();
        double minEff = ServerConfig.trainingMinEfficiency();
        double decayPerMin = ServerConfig.trainingFatigueDecayPerMinute();
        double eff = categoryEfficiency != null ? categoryEfficiency : 1.0;

        if (eff >= 0.999) {
            return Component.translatable("screen.zenkai.training_hub.panel.fatigue_tooltip.fresh",
                    ZenkaiNumbers.fmt2(decayPerMin));
        }
        // En el suelo de eficiencia (o decayPerMin=0, config a mano): m ya no distingue "justo en
        // el suelo" de "muy por encima", así que invertir la fórmula subestimaría la fatiga real
        // — mejor no prometer una cuenta atrás que se quedaría corta.
        if (eff <= minEff + 1.0e-6 || decayPerMin <= 0) {
            return Component.translatable("screen.zenkai.training_hub.panel.fatigue_tooltip.floor",
                    ZenkaiNumbers.fmt2(decayPerMin));
        }
        double fatigue = h * (1.0 / eff - 1.0);
        double minutesToZero = fatigue / decayPerMin;
        return Component.translatable("screen.zenkai.training_hub.panel.fatigue_tooltip.eta",
                ZenkaiNumbers.fmt2(decayPerMin), Math.max(1, Math.round(minutesToZero)));
    }

    private void panelRow(GuiGraphics g, int x, int y, int w, Component label, Component value) {
        PanelText.onPanel(g, this.font, label, x, y, ZenkaiPalette.MUTED_ON_PANEL);
        PanelText.rightOnPanel(g, this.font, value, x + w - PANEL_RIGHT_MARGIN, y, ZenkaiPalette.OK_ON_PANEL);
    }

    // ── Gravedad: fila-botón + popup de detalle ─────────────────────────────

    /** Fila "Gravity: xN" — GENÉRICA (sirve para cualquier fuente: Kaiosama, HTC, la futura
     *  cámara de gravedad). Botón DE VERDAD, no solo un highlight al pasar el ratón (pedido
     *  explícito del usuario, "que se vea que es un botón"): marco de 1px + fondo propio
     *  SIEMPRE visibles, mismo lenguaje que renderHubOption, con un fondo más claro al pasar el
     *  ratón encima. Sin tooltip propio (pedido explícito del usuario, "elimina el tooltip, ya
     *  se entiende con el popup") — toda la información vive en renderGravityPopup. El clic lo
     *  procesa clickHub (mismo hit-test de Y que aquí, factorizado en GRAVITY_ROW_Y para que no
     *  puedan desincronizarse). */
    private void renderGravityRow(GuiGraphics g, int x, int y, int w, Player player, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + GRAVITY_ROW_H;

        // Caja del botón: GRAVITY_ROW_H (15px), no PANEL_ROW_H (12px) — con solo 12 el marco de
        // abajo quedaba pegado al texto de la fila siguiente ("TP bonus"), el "solapamiento"
        // real reportado en la imagen. El texto se dibuja en y+2 (no y) para quedar centrado
        // dentro de esa caja más alta en vez de pegado a su borde superior.
        g.fill(x, y - 1, x + w, y + GRAVITY_ROW_H - 1, hovered ? ZenkaiPalette.ROW_HOVER : ZenkaiPalette.INSET_BG);
        g.fill(x, y - 1, x + w, y, ZenkaiPalette.BORDER_IN);
        g.fill(x, y + GRAVITY_ROW_H - 2, x + w, y + GRAVITY_ROW_H - 1, ZenkaiPalette.BORDER_IN);
        g.fill(x, y - 1, x + 1, y + GRAVITY_ROW_H - 1, ZenkaiPalette.BORDER_IN);
        g.fill(x + w - 1, y - 1, x + w, y + GRAVITY_ROW_H - 1, ZenkaiPalette.BORDER_IN);

        double gravityMult = WeightSystem.gravityMultiplier(player);
        PanelText.onPanel(g, this.font,
                Component.translatable("screen.zenkai.training_hub.panel.gravity"),
                x + 4, y + 2, ZenkaiPalette.LABEL_ON_PANEL);
        PanelText.rightOnPanel(g, this.font, Component.literal("x" + ZenkaiNumbers.fmt2(gravityMult)),
                x + w - PANEL_RIGHT_MARGIN - 4, y + 2, ZenkaiPalette.OK_ON_PANEL);
    }

    /** Fila del popup de gravedad. `bar >= 0` dibuja además una barra bajo el texto — mismo
     *  espíritu que StatsScreen.Row, versión reducida (este popup no necesita cabeceras). */
    private record GravityRow(Component label, Component value, int color, float bar) {
        static GravityRow of(Component l, Component v, int c) { return new GravityRow(l, v, c, -1f); }
        static GravityRow bar(Component l, Component v, int c, float pct) {
            return new GravityRow(l, v, c, pct);
        }
        boolean hasBar() { return bar >= 0f; }
    }

    /** Desglose SIN la fila "Source": esa se pinta aparte como línea propia a todo lo ancho
     *  (ver renderGravityPopup) — con nombres largos como "Hyperbolic Time Chamber" no cabía
     *  compartiendo línea con una etiqueta "Source" sin solaparse (feedback de imagen,
     *  2026-09-10). Multiplicador cosmético, equipo físico y gravedad ambiental por separado, y
     *  el TOTAL — pedido explícito del usuario, "que al final corresponda a la suma total".
     *  equipped + ambient == el numerador de la fila Total, siempre. El valor del Total YA NO
     *  repite el porcentaje entre paréntesis (la barra de debajo ya lo enseña) — ese texto
     *  extra era la otra mitad del solapamiento original. */
    private List<GravityRow> buildGravityRows(Player player) {
        List<GravityRow> out = new ArrayList<>();
        double equipped = WeightSystem.equippedTons(player);
        double ambient = WeightSystem.ambientTons(player);
        double capacity = WeightSystem.capacityTons(att.getPowerLevelRaw());
        double load = att.getWeightLoad();
        String sourceKey = WeightSystem.gravitySourceNameKey(player);

        if (sourceKey != null) {
            out.add(GravityRow.of(Component.translatable("screen.zenkai.training_hub.popup.gravity.multiplier"),
                    Component.literal("x" + ZenkaiNumbers.fmt2(WeightSystem.gravityMultiplier(player))),
                    ZenkaiPalette.VALUE));
        }
        out.add(GravityRow.of(Component.translatable("screen.zenkai.training_hub.popup.gravity.equipped"),
                Component.literal(String.format(Locale.ROOT, "%.2f t", equipped)), ZenkaiPalette.TEXT));
        out.add(GravityRow.of(Component.translatable("screen.zenkai.training_hub.popup.gravity.ambient"),
                Component.literal(String.format(Locale.ROOT, "%.2f t", ambient)), ZenkaiPalette.TEXT));
        out.add(GravityRow.bar(Component.translatable("screen.zenkai.training_hub.popup.gravity.total"),
                Component.literal(String.format(Locale.ROOT, "%.2f / %.2f t", equipped + ambient, capacity)),
                ZenkaiPalette.SECTION_LOAD, (float) Math.min(100.0, load * 100)));
        return out;
    }

    /** Popup de detalle, MISMO formato de colores/marco que los popups laterales de StatsScreen
     *  (tres anillos BORDER_IN/BORDER_MID/POPUP_BG, título en GOLD) — pedido explícito del
     *  usuario. Se abre a la derecha del panel, igual criterio de clamp que StatsScreen. */
    private void renderGravityPopup(GuiGraphics g, int mouseX, int mouseY) {
        var player = mc.player;
        if (player == null) return;
        List<GravityRow> rows = buildGravityRows(player);
        String sourceKey = WeightSystem.gravitySourceNameKey(player);

        final int rowH = 11, barExtra = 8, sourceLineH = 11;
        int h = 7 + 13 + sourceLineH;
        for (GravityRow r : rows) h += rowH + (r.hasBar() ? barExtra : 0);
        h += 8;

        int x = Mth.clamp(panelLeft + BG_W + GRAVITY_POPUP_GAP, 2, this.width - GRAVITY_POPUP_W - 2);
        int y = panelTop + GRAVITY_ROW_Y;

        g.fill(x - 2, y - 2, x + GRAVITY_POPUP_W + 2, y + h + 2, ZenkaiPalette.BORDER_IN);
        g.fill(x - 1, y - 1, x + GRAVITY_POPUP_W + 1, y + h + 1, ZenkaiPalette.BORDER_MID);
        g.fill(x, y, x + GRAVITY_POPUP_W, y + h, ZenkaiPalette.POPUP_BG);

        int tx = x + 8, tr = x + GRAVITY_POPUP_W - 8, ty = y + 6;
        g.drawString(this.font, ScreenTitle.styled(
                        Component.translatable("screen.zenkai.training_hub.panel.gravity")),
                tx, ty, ZenkaiPalette.GOLD, true);
        ty += 13;

        // Fuente a TODO lo ancho, sin etiqueta compartiendo línea — "Hyperbolic Time Chamber"
        // no cabía junto a "Source:" sin solaparse (feedback de imagen), ver buildGravityRows.
        Component sourceText = sourceKey != null
                ? Component.translatable(sourceKey)
                : Component.translatable("screen.zenkai.training_hub.popup.gravity.source.none");
        g.drawString(this.font, sourceText, tx,
                ty, sourceKey != null ? ZenkaiPalette.GOLD : ZenkaiPalette.TEXT_DIM, true);
        ty += sourceLineH;

        for (GravityRow r : rows) {
            g.drawString(this.font, r.label(), tx + 6, ty + 4, ZenkaiPalette.TEXT_DIM, true);
            g.drawString(this.font, r.value(), tr - this.font.width(r.value()), ty + 4, r.color(), true);
            ty += rowH;
            if (r.hasBar()) {
                StatBar.drawOnDark(g, tx + 6, ty + 5, tr - tx - 6, StatBar.H_THIN,
                        r.bar(), 100.0, ZenkaiPalette.BAR_CONTROL);
                ty += barExtra;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clickHub(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickHub(double mouseX, double mouseY) {
        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;
        if (mouseX < x || mouseX >= x + w) return false;

        if (mouseY >= panelTop + ROW1_Y && mouseY < panelTop + ROW1_Y + HUB_ROW_H) {
            mc.setScreen(new ShadowTrainingScreen());
            return true;
        }
        if (mouseY >= panelTop + ROW2_Y && mouseY < panelTop + ROW2_Y + HUB_ROW_H) {
            mc.setScreen(new MeditationScreen());
            return true;
        }
        if (mouseY >= panelTop + ROW3_Y && mouseY < panelTop + ROW3_Y + HUB_ROW_H) {
            mc.setScreen(new TargetPracticeScreen());
            return true;
        }
        int gravityY = panelTop + GRAVITY_ROW_Y;
        if (mouseY >= gravityY && mouseY < gravityY + GRAVITY_ROW_H) {
            showGravityPopup = !showGravityPopup;
            return true;
        }
        return false;
    }
}
