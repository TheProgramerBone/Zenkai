package com.hmc.zenkai.client.gui.screens.skilltree;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.AtlasIconButton;
import com.hmc.zenkai.client.gui.widgets.TutorialOverlay;
import com.hmc.zenkai.client.gui.screens.ZenkaiMenuScreen;
import com.hmc.zenkai.client.gui.screens.ZenkaiTab;
import com.hmc.zenkai.client.gui.screens.skilltree.SkillTreeArt.Frame;
import com.hmc.zenkai.client.gui.screens.skilltree.SkillTreeArt.Theme;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.PotentialUnlock;
import com.hmc.zenkai.feature.player.MindBudget;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.race.RacePassives;
import com.hmc.zenkai.feature.skilltree.SkillTreeBuyPacket;
import com.hmc.zenkai.feature.skilltree.SkillTreeData;
import com.hmc.zenkai.feature.skilltree.SkillTreeMigration;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes.Eval;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes.Kind;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes.Node;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes.State;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.util.ZenkaiNumbers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pestaña TRANSFORMACIONES: el skill tree de transformaciones de TU raza más las rutas
 * universales (diseño: {@code .claude/pendiente/skill-tree-transformaciones.md}; F2 la pantalla
 * base, F2b su aspecto, §9).
 *
 *  - El lienzo galáctico tapa el beige ENTERO del panel (x=12..243, y=13..242, ver docs/gui.md);
 *    no hay panel de información aparte: TP y MND van en placas dentro del lienzo y lo demás en
 *    el tooltip del nodo (maestría incluida, lo que era la pestaña MASTERY).
 *  - Fondo por RAMA (técnica de la pestaña de advancements de Alex's Caves, ver
 *    skill-tree-referencia-alexscaves.md): cada nodo pertenece a un {@link Theme}; pasar el
 *    ratón por cualquier nodo visible, seleccionarlo o centrar cambia el fondo con un fundido,
 *    y con el ratón en vacío se queda el último (pegajoso). Capas: fondo opaco a 1/4 del pan y
 *    plano medio recortado a 1/2; NO escalan con el zoom (el pixel-art del fondo no se estira).
 *  - Pan arrastrando; zoom con la rueda en ESCALONES enteros respecto del arte (0,5x/1x/2x: los
 *    iconos de 32x32 a 16 px de GUI salen 1:1 con GUI Scale 2, y 1,5x los deformaría).
 *  - Nodos SECRETOS (SkillTreeNodes.secret) no se dibujan ni dejan hueco hasta revelarse; la
 *    primera vez que aparecen, una animación de revelado (F3) que se recuerda en ClientConfig.
 *  - Mientras God Ki no está conseguido, una CADENA vertical con su sello cruza el lienzo y a su
 *    derecha solo hay fondo velado (muro). SELLADA sin audiencia: la cadena pasa por la columna
 *    de God Ki y lo tapa. HABILITADA (audiencia del maestro, F5): God Ki asoma delante de la
 *    cadena, que tiembla rompible; lo divino sigue detrás hasta conseguirlo.
 *  - Tooltip tipo advancement (F3): requisitos en verde/rojo y la pista con humor de la forma.
 *  - Clic derecho en un nodo = «marcar ruta» (F3): se iluminan los nodos que faltan hasta él, se
 *    atenúa el resto y su tooltip suma el TP total. Clic derecho en vacío lo quita.
 *  - Efectos animados (F6): resplandor suave de los nodos, deriva y titileo del fondo, pulso de
 *    los marcos según FormDef.Tier; ClientConfig.skillTreeReducedFx los apaga.
 *  - Tutorial (F7): la primera vez, y con el botón «?» de abajo a la izquierda.
 *  - Compra con dos clics: el primero sobre un nodo comprable lo arma (anillo dorado y el
 *    tooltip pide confirmar), el segundo compra. Sin reembolso, de ahí la confirmación; el
 *    segundo clic debe llegar pasado {@link #CONFIRM_MIN_MS} para que un doble clic no compre.
 *
 * El estado de cada nodo sale de SkillTreeNodes.evaluate, la MISMA función con la que el
 * servidor valida SkillTreeBuyPacket: lo que se pinta comprable es lo que se puede comprar.
 *
 * Pan, zoom, selección y fondo son STATIC (mismo motivo que TechniquesScreen.category):
 * ZenkaiMenuScreen.createScreen() construye una pantalla nueva en cada visita a la pestaña.
 */
public class SkillTreeScreen extends ZenkaiMenuScreen {

    // ── Geometría (píxeles de un panel de 256; el beige real va de x=12 a x=243, y de 13 a 242) ──

    private static final int VP_LEFT = 12, VP_RIGHT = 244, VP_TOP = 13, VP_BOTTOM = 243;
    private static final int ICON_BTN = 20, BTN_PLATE = 24;
    private static final int PLATE_H = 14;

    /** Medidas en coordenadas de MUNDO (zoom 1 = px de GUI). */
    private static final int NODE = 24;
    private static final int ICON = 16;
    private static final int COL = 46;
    private static final int ROW = 40;
    private static final float[] ZOOMS = {0.5f, 1f, 2f};
    private static final int DEFAULT_ZOOM = 1;
    private static final long DOUBLE_CLICK_MS = 300L;
    private static final long CONFIRM_MIN_MS = 250L;
    /** Duración de cada fundido entre fondos: lento y continuo, como un difuminado (petición del usuario). */
    /** Un fondo nuevo solo se pide si el ratón lleva este rato sobre un nodo de esa rama: al barrer el cursor
     *  por nodos de ramas distintas no se apilan fundidos a medias que parecen saltos. */
    private static final long HOVER_THEME_MS = 100L;
    private static final long FADE_MS = 3200L;
    /** Capas de fondo apiladas como máximo (cambios de rama muy seguidos). */
    private static final int MAX_LAYERS = 6;
    private static final double DRAG_SLOP = 3.0;
    private static final float MID_ALPHA = 0.7f;
    /** Media anchura de la cadena en px de GUI: ahí empieza su zona de muro. */
    private static final int CHAIN_HALF = 6;
    /** Mínimo que la cadena queda a la derecha del centro del lienzo al arrastrar. */
    private static final int CHAIN_STOP = 40;

    /** Celdas de icons.png: la lupa (gen_appearance_icons.py) y el busto "yo" de la fila v=80. */
    private static final int ZOOM_U = 0, ZOOM_V = 100;
    private static final int CENTER_U = 40, CENTER_V = 80;

    // ── Colores (lienzo oscuro: aquí no valen los *_ON_PANEL) ──

    private static final int EDGE_OFF = 0xC04A4462;
    /** Hilo de la rama de Ultra Ego (el aura_rgb de ultra_ego.json). */
    private static final int ULTRA_EGO_EDGE = 0xFF9A3CFF;
    private static final int AVAILABLE_RING = ZenkaiPalette.OK;
    private static final int SELECT_RING = 0xFFFFFFFF;
    private static final int CONFIRM_RING = ZenkaiPalette.GOLD;
    private static final int LABEL_BG = 0xD0100C1C;
    private static final int VEIL = 0xA8080410;
    /** Ruta marcada con clic derecho (F3): cian, ajeno a los acentos de raza. */
    private static final int ROUTE_COLOR = 0xFF40E0FF;
    private static final int TIP_BG = 0xF0100C1C;
    private static final int TIP_BORDER = 0xFFE8901C;
    private static final int TIP_NAME = 0xFFFFE070;
    private static final int TIP_BODY = 0xFFE0DCEC;
    private static final int TIP_MUTED = 0xFFA8A4B8;
    private static final int TIP_SPECIAL = 0xFFC8A0FF;
    private static final int TIP_HINT = 0xFFB8C8E0;
    /** Ancho máximo de las líneas largas del tooltip (pista, descripción de la pasiva). */
    private static final int TIP_WRAP = 190;

    // ── Estado que sobrevive entre visitas a la pestaña ──

    private static int zoomIdx = DEFAULT_ZOOM;
    private static float panX, panY;
    private static @Nullable Race viewRace;
    private static @Nullable ResourceLocation selected;
    /**
     * Pila de fondos: el de abajo se pinta opaco y cada capa superior aparece desde alfa 0 con su propio
     * fundido; al llegar una a 1 se descartan las de debajo. Un cambio de rama NUNCA corta ni reinicia el
     * fundido en curso: añade otra capa encima y todas siguen avanzando (cambios seguidos se encadenan).
     */
    private static final java.util.List<Theme> layerTheme = new java.util.ArrayList<>(java.util.List.of(Theme.COSMOS));
    private static final java.util.List<Float> layerAlpha = new java.util.ArrayList<>(java.util.List.of(1f));
    private static long layerClock;
    private Theme hoverTheme;
    private long hoverSince;
    /** Destino de «marcar ruta» (clic derecho), o null. */
    private static @Nullable ResourceLocation routeTarget;
    /** Animaciones de revelado en curso: nodo → instante de inicio. */
    private static final Map<ResourceLocation, Long> REVEALS = new HashMap<>();
    private static final long REVEAL_MS = 1600L;

    // ── Estado de la instancia ──

    private final List<Node> nodes = new ArrayList<>();
    private final Map<ResourceLocation, Node> byId = new HashMap<>();
    /** Centro de cada nodo VISIBLE en coordenadas de mundo. */
    private final Map<ResourceLocation, float[]> pos = new LinkedHashMap<>();
    /** Nodos del árbol de la raza (no de las doctrinas universales), para "centrar". */
    private final List<ResourceLocation> raceTree = new ArrayList<>();
    /** Doctrina universal de la que cuelga cada nodo (o él mismo si es una); fuera de las
     *  doctrinas no hay entrada. Decide su fondo y su marco plateado. */
    private final Map<ResourceLocation, ResourceLocation> doctrineOf = new HashMap<>();
    /** Nodos que cuelgan de God Ki: la parte divina que tapa la cadena. */
    private final Set<ResourceLocation> divine = new HashSet<>();
    /** Nodos que faltan en la ruta marcada (destino incluido); vacío sin ruta. */
    private final Set<ResourceLocation> route = new HashSet<>();
    private long routeCost;
    private @Nullable ResourceLocation confirming;
    private long armedAt;
    /** God Ki sin conseguir: la cadena hace de muro (se recalcula al construir y en cada frame). */
    private boolean sealed;
    /** Audiencia concedida sin God Ki todavía: la cadena sigue, pero God Ki asoma delante. */
    private boolean chainEnabled;
    private boolean pressedInViewport, dragging;
    private double pressX, pressY;
    private long lastCenterClick;
    /** Tutorial de la pantalla (F7): sale la primera vez y con el botón «?». */
    private @Nullable TutorialOverlay tutorial;
    /** Destino de la cámara (mundo) mientras el tutorial la lleva a un nodo; null = quieta. */
    private float @Nullable [] camGoal;
    private static final int HELP_BTN = 16;

    /** Solo para la herramienta de desarrollo SkillTreeShots: ratón simulado (null = el real). */
    public static int @Nullable [] devMouse;

    public SkillTreeScreen() {
        super(Component.translatable(ZenkaiTab.TRANSFORMATIONS.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.TRANSFORMATIONS; }

    // ── Construcción ─────────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        nodes.clear();
        byId.clear();
        pos.clear();
        raceTree.clear();
        doctrineOf.clear();
        divine.clear();
        if (att == null || !att.isRaceChosen() || mc.player == null) return;
        Race race = att.getRace();
        // Un nodo secreto sin revelar no existe para la pantalla, y lo que cuelga de él en el
        // dibujo tampoco. Se repite hasta que no entra nadie más: el padre de dibujo (SSJ4 bajo
        // Súper Oozaru) no tiene por qué venir antes que el hijo en el orden de build().
        List<Node> pending = new ArrayList<>();
        for (Node n : SkillTreeNodes.build(race)) {
            if (SkillTreeNodes.revealed(mc.player, n)) pending.add(n);
        }
        boolean added = true;
        while (added) {
            added = false;
            for (var it = pending.iterator(); it.hasNext(); ) {
                Node n = it.next();
                if (n.parent() != null && !byId.containsKey(n.parent())) continue;
                nodes.add(n);
                byId.put(n.id(), n);
                it.remove();
                added = true;
            }
        }
        layout();
        sealed = computeSealed();
        startReveals();
        if (routeTarget != null && !byId.containsKey(routeTarget)) routeTarget = null;
        computeRoute();

        if (viewRace != race || selected == null || !byId.containsKey(selected)) {
            viewRace = race;
            zoomIdx = DEFAULT_ZOOM;
            ResourceLocation target = centerTarget();
            selected = target;
            centerOn(target);
            Node t = byId.get(target);
            if (t != null) {
                resetThemes(themeOf(t));
            }
        }

        AtlasIconButton zoomReset = new AtlasIconButton(zoomBtnX(), btnY(), ZOOM_U, ZOOM_V,
                () -> setZoom(DEFAULT_ZOOM));
        zoomReset.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.skill_tree.reset_zoom")));
        addRenderableWidget(zoomReset);
        AtlasIconButton center = new AtlasIconButton(centerBtnX(), btnY(), CENTER_U, CENTER_V,
                this::onCenterPressed);
        center.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.skill_tree.center")));
        addRenderableWidget(center);

        var help = new com.hmc.zenkai.client.gui.buttons.HelpIconButton(helpX(), helpY(), HELP_BTN,
                () -> { if (tutorial != null) tutorial.start(); });
        help.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.tutorial.replay")));
        addRenderableWidget(help);
        tutorial = new TutorialOverlay("skill_tree", tutorialSteps());
        tutorial.startIfFirstTime();
    }

    /** El «?» vive en la franja de arriba, junto a los botones de centrar y zoom (donde ya no hay nodos que se le
     *  solapen): abajo a la izquierda los letreros de los nodos pasaban por debajo al hacer pan. */
    private int helpX() { return centerBtnX() - BTN_PLATE / 2 - HELP_BTN - 2; }
    private int helpY() { return btnY() + (ICON_BTN - HELP_BTN) / 2; }

    /** Pasos del tutorial (F7). Las zonas se recalculan en cada frame: el panel puede moverse. */
    private List<TutorialOverlay.Step> tutorialSteps() {
        List<TutorialOverlay.Step> out = new ArrayList<>();
        out.add(step(1, () -> new int[]{vpLeft(), vpTop(), vpRight() - vpLeft(), vpBottom() - vpTop()}));
        // Los pasos que señalan un nodo LLEVAN LA CÁMARA a él al empezar (si no, el recuadro caía donde «debería»
        // estar el nodo aunque el lienzo estuviese desplazado y resaltaba otra cosa o nada).
        out.add(step(2, () -> nodeRect(SkillTreeNodes.ROOT), () -> focusTutorialNode(SkillTreeNodes.ROOT)));
        out.add(step(3, () -> new int[]{vpLeft() + 2, vpTop() + 3,
                plateWidth(tpText()) + 6 + plateWidth(mindText()), PLATE_H + 4}));
        out.add(step(4, () -> new int[]{vpLeft(), vpTop(), vpRight() - vpLeft(), vpBottom() - vpTop()}));
        out.add(step(5, () -> new int[]{vpLeft(), vpTop(), vpRight() - vpLeft(), vpBottom() - vpTop()}));
        out.add(step(6, () -> new int[]{centerBtnX() - 3, btnY() - 3, zoomBtnX() + ICON_BTN + 3 - (centerBtnX() - 3), ICON_BTN + 6}));
        if (byId.containsKey(SkillTreeMigration.NODE_GOD_KI)) {
            out.add(step(7, () -> sealed ? new int[]{chainX() - 8, vpTop(), 16, vpBottom() - vpTop()}
                    : nodeRect(SkillTreeMigration.NODE_GOD_KI), () -> focusTutorialGodKi()));
        }
        out.add(step(8, () -> new int[]{helpX() - 2, helpY() - 2, HELP_BTN + 4, HELP_BTN + 4}));
        return out;
    }

    private static TutorialOverlay.Step step(int n, java.util.function.Supplier<int[]> rect) {
        return step(n, rect, null);
    }

    private static TutorialOverlay.Step step(int n, java.util.function.Supplier<int[]> rect, @Nullable Runnable onEnter) {
        String k = "screen.zenkai.tutorial.skill_tree." + n;
        return new TutorialOverlay.Step(Component.translatable(k + ".title"), Component.translatable(k), rect, onEnter);
    }

    /** Lleva la cámara a un nodo con el zoom por defecto (el recuadro del tutorial es legible a 1x). */
    private void focusTutorialNode(ResourceLocation id) {
        float[] p = pos.get(id);
        if (p == null) return;
        zoomIdx = DEFAULT_ZOOM;
        camGoal = new float[]{p[0], p[1]};
    }

    /** God Ki sellado: la cámara va a la cadena (a la altura de God Ki); si no, al propio nodo. */
    private void focusTutorialGodKi() {
        float[] p = pos.get(SkillTreeMigration.NODE_GOD_KI);
        if (p == null) return;
        zoomIdx = DEFAULT_ZOOM;
        camGoal = new float[]{sealed ? chainWorldX() - CHAIN_STOP / zoom() : p[0], p[1]};
    }

    /** Avanza la cámara hacia su destino (fracción por frame, suave) y la suelta al llegar. */
    private void stepCamera() {
        if (camGoal == null) return;
        float dx = camGoal[0] - panX, dy = camGoal[1] - panY;
        if (Math.abs(dx) < 0.6f && Math.abs(dy) < 0.6f) {
            panX = camGoal[0];
            panY = camGoal[1];
            camGoal = null;
        } else {
            panX += dx * 0.22f;
            panY += dy * 0.22f;
        }
        clampPan();
    }

    /** Rectángulo en pantalla de un nodo visible, o null. */
    private int @Nullable [] nodeRect(ResourceLocation id) {
        float[] p = pos.get(id);
        if (p == null) return null;
        int s = Math.round(NODE * zoom()) + 4;
        return new int[]{Math.round(toScreenX(p[0])) - s / 2, Math.round(toScreenY(p[1])) - s / 2, s, s};
    }

    /**
     * Columnas por profundidad y filas por hojas: cada hoja ocupa su fila y un padre se centra
     * entre su primer y su último hijo. Las doctrinas universales cuelgan del nodo que las abre
     * (SkillTreeNodes.build), así que su columna sale de su tier; si quedara más de una raíz
     * (datapack raro), cada una se apila debajo de la anterior con media fila de aire.
     */
    private void layout() {
        Map<ResourceLocation, List<Node>> kids = new HashMap<>();
        List<Node> roots = new ArrayList<>();
        for (Node n : nodes) {
            if (n.parent() == null || !byId.containsKey(n.parent())) roots.add(n);
            else kids.computeIfAbsent(n.parent(), k -> new ArrayList<>()).add(n);
        }
        float[] cursor = {0f};
        for (Node r : roots) {
            place(r, null, 0, cursor, kids, false);
            cursor[0] += 0.5f;
        }
        pushDivinePastMortals();
    }

    /**
     * Lo divino (God Ki y lo que cuelga de él) empieza SIEMPRE una columna por detrás de la última
     * forma mortal. La cadena de God Ki es un muro vertical a lo alto del lienzo en la columna de
     * God Ki, y esa columna la comparten a veces formas mortales hermanas de God Ki (Indomitable
     * Spirit del Humano, Harmony/Chaos del Majin) o la rama de Mystic: sin este desplazamiento
     * quedaban tapadas e imposibles de pulsar.
     */
    private void pushDivinePastMortals() {
        float[] g = pos.get(SkillTreeMigration.NODE_GOD_KI);
        if (g == null) return;
        float maxMortal = Float.NEGATIVE_INFINITY;
        for (var e : pos.entrySet()) {
            if (SkillTreeMigration.NODE_GOD_KI.equals(e.getKey()) || divine.contains(e.getKey())) continue;
            maxMortal = Math.max(maxMortal, e.getValue()[0]);
        }
        float shift = maxMortal + COL - g[0];
        if (shift <= 0) return;
        g[0] += shift;
        for (ResourceLocation id : divine) pos.get(id)[0] += shift;
    }

    private float place(Node n, @Nullable ResourceLocation doctrine, int depth, float[] cursor,
                        Map<ResourceLocation, List<Node>> kids, boolean underGodKi) {
        if (isDoctrine(n.id())) doctrine = n.id();
        if (doctrine == null) raceTree.add(n.id());
        else doctrineOf.put(n.id(), doctrine);
        if (underGodKi) divine.add(n.id());
        boolean childDivine = underGodKi || SkillTreeMigration.NODE_GOD_KI.equals(n.id());
        List<Node> children = kids.getOrDefault(n.id(), List.of());
        float row;
        if (children.isEmpty()) {
            row = cursor[0]++;
        } else {
            float first = Float.NaN, last = 0f;
            for (Node c : children) {
                float r = place(c, doctrine, depth + 1, cursor, kids, childDivine);
                if (Float.isNaN(first)) first = r;
                last = r;
            }
            row = (first + last) / 2f;
        }
        pos.put(n.id(), new float[]{depth * COL, row * ROW});
        return row;
    }

    // ── Clasificación de nodos (tema, marco, ornamento) ──────────────────────

    private static boolean isDoctrine(ResourceLocation id) {
        return SkillTreeMigration.NODE_POTENTIAL_UNLOCK.equals(id) || SkillTreeMigration.NODE_ULTRA_INSTINCT.equals(id)
                || SkillTreeMigration.NODE_ULTRA_EGO.equals(id);
    }

    private Theme themeOf(Node n) {
        ResourceLocation root = doctrineOf.get(n.id());
        if (SkillTreeMigration.NODE_POTENTIAL_UNLOCK.equals(root)) return Theme.MYSTIC;
        if (SkillTreeMigration.NODE_ULTRA_INSTINCT.equals(root)) return Theme.ULTRA_INSTINCT;
        if (SkillTreeMigration.NODE_ULTRA_EGO.equals(root)) return Theme.ULTRA_EGO;
        if (n.kind() == Kind.ROOT || SkillTreeMigration.NODE_ARCOSIAN_POWER.equals(n.id())
                || FormIds.OOZARU.equals(n.form()) || FormIds.SUPER_OOZARU.equals(n.form())) {
            return Theme.COSMOS;
        }
        if (n.form() != null && n.form().getPath().startsWith("ssj_green")) return Theme.LEGENDARY;
        if (SkillTreeMigration.NODE_GOD_KI.equals(n.id()) || divine.contains(n.id())) return Theme.DIVINE;
        FormDef d = n.form() == null ? null : FormDef.get(n.form());
        if (d != null && d.divineTier()) return Theme.DIVINE;
        if (d != null && d.tier() != FormDef.Tier.NONE) return Theme.ASCENDED;
        return Theme.energy(att.getRace());
    }

    private Frame frameOf(Node n) {
        if (SkillTreeNodes.secret(n)) return Frame.SECRET;
        // La rama de Ultra Ego (doctrina y sus formas) lleva marco morado propio.
        if (SkillTreeMigration.NODE_ULTRA_EGO.equals(doctrineOf.get(n.id()))) return Frame.ULTRA_EGO;
        return raceTree.contains(n.id()) ? SkillTreeArt.frameOf(att.getRace()) : Frame.UNIVERSAL;
    }

    /**
     * Color del hilo encendido que llega a un nodo: el acento de su marco, salvo la rama de
     * Ultra Ego, que va en morado (petición del usuario: el blanco/plateado es de Ultra Instinct).
     */
    private int edgeColor(Node n) {
        if (SkillTreeMigration.NODE_ULTRA_EGO.equals(doctrineOf.get(n.id()))) return ULTRA_EGO_EDGE;
        return SkillTreeArt.accent(frameOf(n));
    }

    /** Marco ornamentado: formas con tier, divinas, y los nodos con niveles (doctrinas, God Ki). */
    private static boolean ornate(Node n) {
        if (n.kind() == Kind.LEVELED || n.kind() == Kind.PASSIVE) return true;
        FormDef d = n.form() == null ? null : FormDef.get(n.form());
        return d != null && (d.divineTier() || d.tier() != FormDef.Tier.NONE);
    }

    // ── Geometría derivada ───────────────────────────────────────────────────

    /** El panel puede encoger en ventanas pequeñas (CommonPanel): el lienzo encoge con él. */
    private int px(int v) { return panelLeft + v * BG_W / 256; }
    private int py(int v) { return panelTop + v * BG_H / 256; }
    private int vpLeft()   { return px(VP_LEFT); }
    private int vpRight()  { return px(VP_RIGHT); }
    private int vpTop()    { return py(VP_TOP); }
    private int vpBottom() { return py(VP_BOTTOM); }
    private int btnY()     { return vpTop() + 4; }
    private int zoomBtnX() { return vpRight() - 4 - BTN_PLATE + (BTN_PLATE - ICON_BTN) / 2; }
    private int centerBtnX() { return zoomBtnX() - BTN_PLATE - 2; }
    private float zoom()   { return ZOOMS[zoomIdx]; }
    private float vpCx()   { return (vpLeft() + vpRight()) / 2f; }
    private float vpCy()   { return (vpTop() + vpBottom()) / 2f; }
    /** Origen del mundo en pantalla, REDONDEADO: un desplazamiento fraccionario reparte mal
     *  los texeles del arte al pan (filas que se duplican y otras que se pierden). */
    private float originX() { return Math.round(vpCx() - panX * zoom()); }
    private float originY() { return Math.round(vpCy() - panY * zoom()); }
    private float toScreenX(float wx) { return originX() + wx * zoom(); }
    private float toScreenY(float wy) { return originY() + wy * zoom(); }
    private float toWorldX(double sx) { return (float) ((sx - originX()) / zoom()); }
    private float toWorldY(double sy) { return (float) ((sy - originY()) / zoom()); }

    private boolean inViewport(double x, double y) {
        return x >= vpLeft() && x < vpRight() && y >= vpTop() && y < vpBottom();
    }

    /** Zona de las placas de TP/MND y de los botones de la esquina: ahí no se elige nodo. */
    private boolean overHud(double x, double y) {
        if (y >= vpTop() + 2 && y < vpTop() + 6 + BTN_PLATE) {
            if (x >= helpX() - 3) return true;
            if (x < vpLeft() + 6 + plateWidth(tpText()) + 4 + plateWidth(mindText())) return true;
        }
        return false;
    }

    // ── Centrar, zoom y fondo ────────────────────────────────────────────────

    /** Mi forma actual si tiene nodo; si estoy en Base, el nodo poseído más profundo del árbol
     *  de mi raza (el "último comprado": la cadena es lineal, así que coinciden). */
    private ResourceLocation centerTarget() {
        PlayerFormAttachment form = mc.player == null ? null
                : mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        ResourceLocation cur = form == null ? null : form.getFormId();
        if (cur != null && !FormIds.BASE.equals(cur) && byId.containsKey(cur)) return cur;
        if (FormIds.POTENTIAL_UNLOCK.equals(cur) && byId.containsKey(SkillTreeMigration.NODE_POTENTIAL_UNLOCK)) {
            return SkillTreeMigration.NODE_POTENTIAL_UNLOCK;
        }
        ResourceLocation best = SkillTreeNodes.ROOT;
        float bestX = -1f;
        for (ResourceLocation id : raceTree) {
            Node n = byId.get(id);
            float[] p = pos.get(id);
            if (n == null || p == null || n.kind() == Kind.ROOT) continue;
            if (SkillTreeNodes.owned(mc.player, n) && p[0] > bestX) {
                bestX = p[0];
                best = id;
            }
        }
        return best;
    }

    private void centerOn(ResourceLocation id) {
        float[] p = pos.get(id);
        if (p == null) return;
        panX = p[0];
        panY = p[1];
        clampPan();
    }

    // ── Revelado y ruta (F3) ─────────────────────────────────────────────────

    /** Los nodos secretos que se ven por primera vez arrancan su animación de revelado. */
    private void startReveals() {
        long now = Util.getMillis();
        for (Node n : nodes) {
            if (!SkillTreeNodes.secret(n)) continue;
            String key = n.id().toString();
            if (ClientConfig.treeRevealSeen(key)) continue;
            ClientConfig.markTreeRevealSeen(key);
            REVEALS.put(n.id(), now);
            selected = n.id();
            centerOn(n.id());
        }
    }

    /** 0..1 del revelado de ese nodo (1 = sin animación o terminada). */
    private static float revealProgress(ResourceLocation id) {
        Long t = REVEALS.get(id);
        if (t == null) return 1f;
        float p = (Util.getMillis() - t) / (float) REVEAL_MS;
        if (p >= 1f) {
            REVEALS.remove(id);
            return 1f;
        }
        return p;
    }

    /** Nodos que faltan desde lo poseído hasta el destino, por los padres del dibujo. */
    private void computeRoute() {
        route.clear();
        routeCost = 0;
        if (routeTarget == null || mc.player == null) return;
        List<Node> path = new ArrayList<>();
        Node n = byId.get(routeTarget);
        while (n != null) {
            boolean owned = SkillTreeNodes.evaluate(mc.player, n).state() == State.OWNED;
            if (owned && n.kind() != Kind.PASSIVE) break;
            if (!owned) path.add(0, n);
            n = n.parent() == null ? null : byId.get(n.parent());
        }
        for (Node r : path) route.add(r.id());
        routeCost = SkillTreeNodes.routeCost(mc.player, att.getRace(), path);
    }

    private void toggleRoute(@Nullable ResourceLocation id) {
        routeTarget = id == null || id.equals(routeTarget) ? null : id;
        computeRoute();
    }

    private void onCenterPressed() {
        long now = Util.getMillis();
        if (now - lastCenterClick < DOUBLE_CLICK_MS) zoomIdx = DEFAULT_ZOOM;
        lastCenterClick = now;
        ResourceLocation t = centerTarget();
        centerOn(t);
        select(t);
    }

    private void setZoom(int idx) {
        zoomIdx = Math.max(0, Math.min(ZOOMS.length - 1, idx));
    }

    private void select(@Nullable ResourceLocation id) {
        selected = id;
        Node n = id == null ? null : byId.get(id);
        if (n != null) setTheme(themeOf(n));
    }

    /** Fondo inicial sin fundido (al abrir la pantalla o cambiar de raza). */
    private static void resetThemes(Theme t) {
        layerTheme.clear();
        layerAlpha.clear();
        layerTheme.add(t);
        layerAlpha.add(1f);
        layerClock = Util.getMillis();
    }

    /** Pide el fondo de una rama: se difumina lentamente sobre el actual, sin cortes ni esperas. */
    private static void setTheme(Theme t) {
        if (layerTheme.get(layerTheme.size() - 1) == t) return;
        if (layerTheme.size() >= MAX_LAYERS) { layerTheme.remove(0); layerAlpha.remove(0); layerAlpha.set(0, 1f); }
        layerTheme.add(t);
        layerAlpha.add(0f);
    }

    /** Avanza el fundido de cada capa superior con el tiempo real y descarta lo que ya queda tapado. */
    private static void advanceThemes() {
        long now = Util.getMillis();
        float dt = Math.min(100L, Math.max(0L, now - layerClock)) / (float) FADE_MS;
        layerClock = now;
        for (int i = 1; i < layerAlpha.size(); i++) layerAlpha.set(i, Math.min(1f, layerAlpha.get(i) + dt));
        for (int i = layerAlpha.size() - 1; i > 0; i--) {
            if (layerAlpha.get(i) >= 1f) {
                for (int k = 0; k < i; k++) { layerTheme.remove(0); layerAlpha.remove(0); }
                break;
            }
        }
    }

    // ── Ratón ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tutorial != null && tutorial.mouseClicked()) return true;
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 1 && inViewport(mx, my) && !overHud(mx, my)) {
            toggleRoute(nodeAt(mx, my));
            return true;
        }
        if (button == 0 && inViewport(mx, my) && !overHud(mx, my)) {
            pressedInViewport = true;
            dragging = false;
            pressX = mx;
            pressY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tutorial != null && tutorial.keyPressed(key)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pressedInViewport && button == 0) {
            if (!dragging && Math.hypot(mx - pressX, my - pressY) > DRAG_SLOP) dragging = true;
            if (dragging) {
                camGoal = null;
                panX -= (float) (dx / zoom());
                panY -= (float) (dy / zoom());
                clampPan();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pressedInViewport && button == 0) {
            pressedInViewport = false;
            if (!dragging) onNodeClicked(nodeAt(mx, my));
            dragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0 && inViewport(mx, my)) {
            // Zoom alrededor del cursor: el punto de mundo bajo el ratón se queda donde estaba.
            camGoal = null;
            float wx = toWorldX(mx), wy = toWorldY(my);
            int before = zoomIdx;
            setZoom(zoomIdx + (sy > 0 ? 1 : -1));
            if (zoomIdx != before) {
                panX = wx - (float) ((mx - vpCx()) / zoom());
                panY = wy - (float) ((my - vpCy()) / zoom());
                clampPan();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private @Nullable ResourceLocation nodeAt(double mx, double my) {
        if (!inViewport(mx, my) || overHud(mx, my) || pastChain(mx)) return null;
        float wx = toWorldX(mx), wy = toWorldY(my);
        float half = NODE / 2f;
        for (var e : pos.entrySet()) {
            if (behindChain(e.getKey())) continue;
            float[] p = e.getValue();
            if (Math.abs(wx - p[0]) <= half && Math.abs(wy - p[1]) <= half) return e.getKey();
        }
        return null;
    }

    // ── Compra ───────────────────────────────────────────────────────────────

    /** Primer clic en un nodo comprable: lo arma. Segundo clic en el mismo: compra. */
    private void onNodeClicked(@Nullable ResourceLocation id) {
        Node n = id == null ? null : byId.get(id);
        if (n == null || mc.player == null) {
            confirming = null;
            return;
        }
        boolean purchasable = SkillTreeNodes.evaluate(mc.player, n).purchasable();
        long now = Util.getMillis();
        if (purchasable && id.equals(confirming)) {
            if (now - armedAt < CONFIRM_MIN_MS) return; // doble clic: no cuenta como confirmación
            confirming = null;
            PacketDistributor.sendToServer(new SkillTreeBuyPacket(id));
            return;
        }
        select(id);
        confirming = purchasable ? id : null;
        armedAt = now;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        // El lienzo va AQUÍ y no en render(): así los botones de la esquina (lupa, centrar) se
        // dibujan encima al pintarse los widgets en super.render().
        drawCanvas(g, mx, my);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        if (devMouse != null) {
            mx = devMouse[0];
            my = devMouse[1];
        }
        sealed = computeSealed();
        if (routeTarget != null) computeRoute(); // tras una compra la ruta encoge sola
        stepCamera();
        clampPan();
        ResourceLocation hover = dragging ? null : nodeAt(mx, my);
        if (hover != null && byId.containsKey(hover)) {
            Theme ht = themeOf(byId.get(hover));
            long nowMs = Util.getMillis();
            if (ht != hoverTheme) { hoverTheme = ht; hoverSince = nowMs; }
            else if (nowMs - hoverSince >= HOVER_THEME_MS) setTheme(ht);
        } else {
            hoverTheme = null;
        }
        if (confirming != null && (mc.player == null || byId.get(confirming) == null
                || !SkillTreeNodes.evaluate(mc.player, byId.get(confirming)).purchasable())) {
            confirming = null; // ya comprado, o dejó de poder comprarse
        }

        super.render(g, mx, my, pt);
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + BG_W / 2, panelTop);
        if (att == null || !att.isRaceChosen()) {
            PanelText.centeredOnPanel(g, this.font, Component.translatable("screen.zenkai.skill_tree.empty"),
                    panelLeft + BG_W / 2, panelTop + BG_H / 2 - 4, ZenkaiPalette.MUTED_ON_PANEL);
            return;
        }
        if (tutorial != null && tutorial.active()) {
            tutorial.render(g, this.font, this.width, this.height);
            return;
        }
        if (hover != null && !isOverWidget(mx, my)) {
            Node hn = byId.get(hover);
            if (hn != null) drawTooltip(g, hn, mx, my);
        } else if (overSeal(mx, my)) {
            drawSealTooltip(g, mx, my);
        }
    }

    /** Solo SkillTreeShots: centra el lienzo en un nodo visible y devuelve su centro en
     *  pantalla (para simular hover sobre él), o null si no está a la vista. */
    public int @Nullable [] devFocus(ResourceLocation id) {
        centerOn(id);
        float[] p = pos.get(id);
        return p == null ? null : new int[]{Math.round(toScreenX(p[0])), Math.round(toScreenY(p[1]))};
    }

    private boolean isOverWidget(double mx, double my) {
        for (var c : children()) if (c.isMouseOver(mx, my)) return true;
        return false;
    }

    // ── Lienzo ───────────────────────────────────────────────────────────────

    private void drawCanvas(GuiGraphics g, int mx, int my) {
        if (nodes.isEmpty()) return;
        int x1 = vpLeft(), y1 = vpTop(), x2 = vpRight(), y2 = vpBottom();
        g.enableScissor(x1, y1, x2, y2);
        drawBackground(g, x1, y1, x2, y2);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(originX(), originY(), 0);
        pose.scale(zoom(), zoom(), 1f);
        for (Node n : nodes) {
            if (n.parent() == null || behindChain(n.id())) continue;
            float[] a = pos.get(n.parent()), b = pos.get(n.id());
            if (a == null || b == null) continue;
            boolean lit = SkillTreeNodes.owned(mc.player, n);
            drawEdge(g, a, b, lit ? edgeColor(n) : EDGE_OFF, lit);
        }
        ResourceLocation hover = dragging ? null : nodeAt(mx, my);
        labels.clear();
        for (Node n : nodes) {
            if (!behindChain(n.id())) drawNode(g, n, n.id().equals(hover));
        }
        pose.popPose();
        drawLabels(g);

        drawGodKiSeal(g, x1, y1, x2, y2);
        drawHeader(g);
        g.disableScissor();
    }

    /** Dos temas superpuestos con fundido; cada uno, fondo opaco a 1/4 y plano medio a 1/2. */
    private void drawBackground(GuiGraphics g, int x1, int y1, int x2, int y2) {
        advanceThemes();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < layerTheme.size(); i++) {
            float a = layerAlpha.get(i);
            float eased = i == 0 ? 1f : a * a * (3f - 2f * a); // smoothstep: entra y sale suave
            drawTheme(g, layerTheme.get(i), eased, x1, y1, x2, y2);
        }
    }

    private void drawTheme(GuiGraphics g, Theme t, float alpha, int x1, int y1, int x2, int y2) {
        drawLayer(g, t.back, 0.25f, alpha, x1, y1, x2, y2);
        // El plano medio a 3/4: opaco robaba protagonismo a los nodos (filamentos casi blancos).
        drawLayer(g, t.mid, 0.5f, alpha * MID_ALPHA, x1, y1, x2, y2);
        if (!ClientConfig.skillTreeReducedFx()) {
            twinkle(g, t, alpha, x1, y1, x2, y2);
            spinStars(g, t, alpha, x1, y1, x2, y2);
        }
    }

    /**
     * Estrellas que titilan (F6): puntos de 1 px repartidos con una semilla fija por tema, que se
     * encienden y apagan cada uno a su ritmo. Por código y no en el PNG: el arte aprobado no se toca.
     */
    private void twinkle(GuiGraphics g, Theme t, float alpha, int x1, int y1, int x2, int y2) {
        long now = Util.getMillis();
        int sx = Math.round(panX * zoom() * 0.35f), sy = Math.round(panY * zoom() * 0.35f);
        int w = x2 - x1, h = y2 - y1;
        java.util.Random rnd = new java.util.Random(t.ordinal() * 7919L);
        for (int i = 0; i < 90; i++) {
            int px = x1 + Math.floorMod(rnd.nextInt(4096) - sx, Math.max(1, w));
            int py = y1 + Math.floorMod(rnd.nextInt(4096) - sy, Math.max(1, h));
            // Pulso lento y suave (nada de parpadeo seco): periodo de 2,5 a 6 s.
            double phase = rnd.nextDouble() * Math.PI * 2, speed = 400 + rnd.nextInt(550);
            boolean big = rnd.nextInt(6) == 0;
            double b = 0.5 + 0.5 * Math.sin(now / speed + phase);
            int a = (int) (alpha * 200 * b * b);
            if (a < 16) continue;
            int col = (a << 24) | t.star;
            g.fill(px, py, px + 1, py + 1, col);
            if (big && a > 90) { // las estrellas grandes sueltan un destello en cruz al encenderse
                int h2 = (a / 3) << 24 | t.star;
                g.fill(px - 1, py, px, py + 1, h2);
                g.fill(px + 1, py, px + 2, py + 1, h2);
                g.fill(px, py - 1, px + 1, py, h2);
                g.fill(px, py + 1, px + 1, py + 2, h2);
            }
        }
    }

    /**
     * Estrellas grandes que giran despacio y laten (petición del usuario 2026-10-22): cruz de cuatro
     * puntas con sus diagonales cortas, cuya inclinación avanza con el tiempo y cuyo brillo y largo
     * pulsan a su ritmo. Cada punto se redondea a píxel de GUI para que siga siendo pixel-art. Semilla
     * fija por tema; se mueven con el pan a un parallax intermedio entre fondo y plano medio.
     */
    private void spinStars(GuiGraphics g, Theme t, float alpha, int x1, int y1, int x2, int y2) {
        long now = Util.getMillis();
        int sx = Math.round(panX * zoom() * 0.4f), sy = Math.round(panY * zoom() * 0.4f);
        int w = Math.max(1, x2 - x1), h = Math.max(1, y2 - y1);
        java.util.Random rnd = new java.util.Random(t.ordinal() * 104729L + 17);
        for (int i = 0; i < 14; i++) {
            int px = x1 + Math.floorMod(rnd.nextInt(4096) - sx, w);
            int py = y1 + Math.floorMod(rnd.nextInt(4096) - sy, h);
            double phase = rnd.nextDouble() * Math.PI * 2;
            double pulse = 1800 + rnd.nextInt(2600);
            double turn = (rnd.nextBoolean() ? 1 : -1) * (9000 + rnd.nextInt(9000));
            int reach = 3 + rnd.nextInt(4);
            double b = 0.5 + 0.5 * Math.sin(now / pulse + phase);
            int a = (int) (alpha * (60 + 170 * b * b));
            if (a < 20) continue;
            int len = Math.max(2, (int) Math.round(reach * (0.55 + 0.45 * b)));
            double ang = now / turn * Math.PI * 2 + phase;
            int arm = (a << 24) | t.star;
            for (int k = 0; k < 4; k++) {
                double aa = ang + k * Math.PI / 2;
                for (int d = 1; d <= len; d++) {
                    int fade = (int) (a * (1.0 - 0.6 * d / (len + 1)));
                    g.fill(px + (int) Math.round(Math.cos(aa) * d), py + (int) Math.round(Math.sin(aa) * d),
                            px + (int) Math.round(Math.cos(aa) * d) + 1, py + (int) Math.round(Math.sin(aa) * d) + 1,
                            (fade << 24) | t.star);
                }
                // diagonal corta entre brazos
                double ad = aa + Math.PI / 4;
                int dx = (int) Math.round(Math.cos(ad) * 1.5), dy = (int) Math.round(Math.sin(ad) * 1.5);
                g.fill(px + dx, py + dy, px + dx + 1, py + dy + 1, ((a / 3) << 24) | t.star);
            }
            g.fill(px, py, px + 1, py + 1, (Math.min(255, a + 40) << 24) | 0xFFFFFF);
        }
    }

    private void drawLayer(GuiGraphics g, ResourceLocation tex, float parallax, float alpha,
                           int x1, int y1, int x2, int y2) {
        final int tile = SkillTreeArt.BG_TILE;
        // Sin deriva propia (el usuario se mareaba, 2026-10-21): el fondo solo se mueve cuando se
        // hace pan, con el parallax de cada capa. La vida la dan las estrellas que titilan en su sitio.
        int sx = Math.round(panX * zoom() * parallax), sy = Math.round(panY * zoom() * parallax);
        int startX = x1 + Math.floorMod(-sx, tile) - tile;
        int startY = y1 + Math.floorMod(-sy, tile) - tile;
        // Alfa por COLOR DE VÉRTICE y no con g.setColor + g.blit: con eso el fundido entre fondos no se
        // mezclaba (el color cambiaba de golpe; medido con SkillTreeShots «fade»). Ver docs/gui.md.
        if (alpha <= 0.003f) return;
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        org.joml.Matrix4f m = g.pose().last().pose();
        com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int x = startX; x < x2; x += tile) {
            for (int y = startY; y < y2; y += tile) {
                bb.addVertex(m, x, y + tile, 0f).setUv(0f, 1f).setColor(1f, 1f, 1f, alpha);
                bb.addVertex(m, x + tile, y + tile, 0f).setUv(1f, 1f).setColor(1f, 1f, 1f, alpha);
                bb.addVertex(m, x + tile, y, 0f).setUv(1f, 0f).setColor(1f, 1f, 1f, alpha);
                bb.addVertex(m, x, y, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, alpha);
            }
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    /**
     * Codo: horizontal hasta la mitad, vertical, horizontal hasta el hijo. 2 px de mundo; la
     * arista encendida lleva un halo translúcido a cada lado (la "constelación" de la referencia).
     */
    private void drawEdge(GuiGraphics g, float[] a, float[] b, int color, boolean lit) {
        int ax = Math.round(a[0] + NODE / 2f), ay = Math.round(a[1]) - 1;
        int bx = Math.round(b[0] - NODE / 2f), by = Math.round(b[1]) - 1;
        int mid = (ax + bx) / 2;
        if (lit) {
            int glow = (0x50 << 24) | (color & 0xFFFFFF);
            g.fill(ax, ay - 1, mid + 3, ay + 3, glow);
            g.fill(mid - 1, Math.min(ay, by) - 1, mid + 3, Math.max(ay, by) + 3, glow);
            g.fill(mid - 1, by - 1, bx, by + 3, glow);
        }
        g.fill(ax, ay, mid + 2, ay + 2, color);
        g.fill(mid, Math.min(ay, by), mid + 2, Math.max(ay, by) + 2, color);
        g.fill(mid, by, bx, by + 2, color);
    }

    private void drawNode(GuiGraphics g, Node n, boolean hovered) {
        float[] p = pos.get(n.id());
        if (p == null) return;
        Eval e = SkillTreeNodes.evaluate(mc.player, n);
        boolean owned = e.state() == State.OWNED || e.level() > 0;
        Frame frame = frameOf(n);
        int accent = SkillTreeArt.accent(frame);
        int x = Math.round(p[0] - NODE / 2f), y = Math.round(p[1] - NODE / 2f);
        boolean fx = !ClientConfig.skillTreeReducedFx();
        float reveal = revealProgress(n.id());
        boolean dimmed = !route.isEmpty() && !route.contains(n.id());

        // Resplandor suave (F6, «difuminar los cuadrados»): capas de alfa decreciente con las
        // esquinas recortadas en vez de un cuadrado plano detrás del marco.
        if (owned) softGlow(g, x, y, accent, 0x50);
        int tier = tierLevel(n);
        if (tier > 0 && (owned || e.state() == State.AVAILABLE)) {
            // Formas poderosas: el halo late, más fuerte y más rápido cuanto más alto el tier.
            int a = fx ? (int) (0x28 + tier * 0x14 * (0.5 + 0.5 * Math.sin(Util.getMillis() / (900.0 - tier * 180.0))))
                    : 0x30 + tier * 0x0C;
            softGlow(g, x - 1, y - 1, accent, a);
        }
        if (route.contains(n.id())) softGlow(g, x, y, ROUTE_COLOR, fx ? pulseAlpha(1000L, 0x40, 0x90) : 0x70);

        if (n.id().equals(confirming)) {
            ring(g, x - 2, y - 2, NODE + 4, pulse(CONFIRM_RING, 500L));
        } else if (n.id().equals(selected)) {
            ring(g, x - 2, y - 2, NODE + 4, SELECT_RING);
        } else if (route.contains(n.id())) {
            ring(g, x - 2, y - 2, NODE + 4, ROUTE_COLOR);
        } else if (e.state() == State.AVAILABLE) {
            ring(g, x - 2, y - 2, NODE + 4, pulse(AVAILABLE_RING, 1200L));
        }

        // Marco (oscurecido si no se posee) e icono (más oscuro cuanto más lejos esté).
        float fb = owned ? 1f : 0.55f;
        if (dimmed) fb *= 0.45f;
        SkillTreeArt.blitTinted(g, SkillTreeArt.FRAMES, x, y, frame.ordinal() * SkillTreeArt.FRAME_TEX,
                ornate(n) ? SkillTreeArt.FRAME_TEX : 0, SkillTreeArt.FRAME_TEX, SkillTreeArt.FRAME_TEX,
                SkillTreeArt.FRAMES_W, SkillTreeArt.FRAMES_H, fb, fb, fb * 1.05f, reveal);
        float ib = owned ? 1f : switch (e.state()) {
            case AVAILABLE, NEED_TP, NEED_MIND -> 0.8f;
            case CONDITION, NEED_REQS -> 0.55f;
            default -> 0.35f;
        };
        if (dimmed) ib *= 0.45f;
        int icon = SkillTreeArt.iconIndex(n, att.getRace());
        float ir = ib, ig = ib, ibl = ib;
        if (icon == SkillTreeArt.UNKNOWN_ICON) {
            int tint = n.kind() == Kind.PASSIVE ? accent : SkillTreeArt.unknownTint(n);
            ir *= ((tint >> 16) & 0xFF) / 255f;
            ig *= ((tint >> 8) & 0xFF) / 255f;
            ibl *= (tint & 0xFF) / 255f;
        }
        int inset = (NODE - ICON) / 2;
        if (n.kind() == Kind.ROOT && mc.player != null) {
            // La Base es la cara de la SKIN del jugador (no la del modelo de su raza), pedido del usuario.
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, mc.player.getSkin(), x + inset, y + inset, ICON);
        } else {
            SkillTreeArt.icon(g, icon, x + inset, y + inset, ir, ig, ibl);
        }
        if (hovered) g.fill(x + inset, y + inset, x + inset + ICON, y + inset + ICON, ZenkaiPalette.HOVER_VEIL);

        // Destellos que orbitan los marcos de tier alto (ULTRA el que más).
        if (fx && tier >= 2 && owned) sparkles(g, x, y, tier, accent, n.id().hashCode());

        // Revelado (F3): el nodo aparece con un destello que se abre y se apaga.
        if (reveal < 1f) {
            int grow = Math.round(reveal * 14f);
            int a = (int) (0xC0 * (1f - reveal));
            ring(g, x - 2 - grow, y - 2 - grow, NODE + 4 + grow * 2, (a << 24) | 0xFFFFFF);
            softGlow(g, x, y, 0xFFFFFF, (int) (0x90 * (1f - reveal)));
        }

        // Insignia de estado en la esquina inferior derecha.
        if (e.state() == State.LOCKED || e.state() == State.NEED_MASTER || e.state() == State.NEED_REQS) {
            SkillTreeArt.blit(g, SkillTreeArt.HUD, x + NODE - 8, y + NODE - 9, SkillTreeArt.LOCK_U,
                    SkillTreeArt.LOCK_V, SkillTreeArt.LOCK_W, SkillTreeArt.LOCK_H,
                    SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
        } else if (e.state() == State.CONDITION && !owned) {
            SkillTreeArt.blit(g, SkillTreeArt.HUD, x + NODE - 8, y + NODE - 9, SkillTreeArt.QUESTION_U,
                    SkillTreeArt.QUESTION_V, SkillTreeArt.QUESTION_W, SkillTreeArt.QUESTION_H,
                    SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
        }

        // Progreso de un nodo con niveles (doctrinas, pasiva): barrita bajo el marco.
        int labelY = y + NODE + 2;
        if ((n.kind() == Kind.LEVELED || n.kind() == Kind.PASSIVE) && e.maxLevel() > 1) {
            int w = NODE - 4;
            g.fill(x + 2, labelY, x + 2 + w, labelY + 2, 0xFF000000);
            int filled = Math.round(w * Math.min(1f, e.level() / (float) e.maxLevel()));
            if (filled > 0) g.fill(x + 2, labelY, x + 2 + filled, labelY + 2, accent);
            labelY += 3;
        }

        if (zoom() >= 1f) labels.add(new Label(p[0], labelY, shortName(n), accent, owned && !dimmed));
    }

    /** Resplandor sin esquinas duras: tres marcos concéntricos de alfa decreciente, cada uno con
     *  las esquinas recortadas, alrededor del nodo, y un velo tenue detrás. */
    private static void softGlow(GuiGraphics g, int x, int y, int rgb, int alpha) {
        if (alpha <= 0) return;
        for (int i = 3; i >= 1; i--) {
            int a = Math.max(0, Math.min(255, alpha * (4 - i) / 4));
            int c = (a << 24) | (rgb & 0xFFFFFF);
            int x1 = x - i, y1 = y - i, x2 = x + NODE + i, y2 = y + NODE + i;
            g.fill(x1 + i + 1, y1, x2 - i - 1, y1 + 1, c);
            g.fill(x1 + i + 1, y2 - 1, x2 - i - 1, y2, c);
            g.fill(x1, y1 + i + 1, x1 + 1, y2 - i - 1, c);
            g.fill(x2 - 1, y1 + i + 1, x2, y2 - i - 1, c);
        }
        g.fill(x, y, x + NODE, y + NODE, ((Math.min(255, alpha) / 2) << 24) | (rgb & 0xFFFFFF));
    }

    /** Píxeles brillantes que dan la vuelta al marco; más y más rápidos con el tier. */
    private static void sparkles(GuiGraphics g, int x, int y, int tier, int rgb, int seed) {
        int count = tier * 2;
        double t = Util.getMillis() / (2600.0 - tier * 500.0);
        int per = 4 * (NODE + 2);
        for (int i = 0; i < count; i++) {
            int d = Math.floorMod((int) ((t + i / (double) count) * per) + seed, per);
            int sx, sy;
            if (d < NODE + 2) { sx = x - 1 + d; sy = y - 1; }
            else if (d < 2 * (NODE + 2)) { sx = x + NODE; sy = y - 1 + d - (NODE + 2); }
            else if (d < 3 * (NODE + 2)) { sx = x + NODE - (d - 2 * (NODE + 2)); sy = y + NODE; }
            else { sx = x - 1; sy = y + NODE - (d - 3 * (NODE + 2)); }
            g.fill(sx, sy, sx + 1, sy + 1, 0xFFFFFFFF);
            g.fill(sx - 1, sy, sx, sy + 1, (0x90 << 24) | (rgb & 0xFFFFFF));
        }
    }

    /** Intensidad de los efectos de un nodo según el tier de su forma (0 sin tier). */
    private static int tierLevel(Node n) {
        FormDef d = n.form() == null ? null : FormDef.get(n.form());
        if (d == null) return 0;
        return switch (d.tier()) {
            case GOD -> 1;
            case EVOLUTION -> 2;
            case ULTRA -> 3;
            default -> 0;
        };
    }

    private static int pulseAlpha(long periodMs, int min, int max) {
        double t = (Util.getMillis() % periodMs) / (double) periodMs;
        return (int) (min + (max - min) * (0.5 + 0.5 * Math.sin(t * Math.PI * 2)));
    }

    /** Letrero pendiente de dibujar, con su ancla en coordenadas de mundo. */
    private record Label(float wx, float wy, Component name, int accent, boolean owned) {}

    private final List<Label> labels = new ArrayList<>();

    /**
     * Letreros con el nombre, en espacio de PANTALLA: con el zoom se mueven con su nodo pero la
     * letra no crece (a 2x salían enormes y se pisaban). A 0,5x no se leen y no se dibujan.
     */
    private void drawLabels(GuiGraphics g) {
        for (Label l : labels) {
            Component name = PanelText.fit(this.font, l.name(), Math.round(COL * zoom()) - 6);
            int w = this.font.width(name) + 4;
            int lx = Math.round(toScreenX(l.wx()) - w / 2f);
            int ly = Math.round(toScreenY(l.wy()));
            g.fill(lx - 1, ly - 1, lx + w + 1, ly + 10, (0xFF << 24) | darken(l.accent(), l.owned() ? 0.6f : 0.3f));
            g.fill(lx, ly, lx + w, ly + 9, LABEL_BG);
            g.drawString(this.font, name, lx + 2, ly + 1, l.owned() ? 0xFFFFFFFF : TIP_MUTED, false);
        }
        labels.clear();
    }

    private static void ring(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x, y, x + size, y + 1, color);
        g.fill(x, y + size - 1, x + size, y + size, color);
        g.fill(x, y + 1, x + 1, y + size - 1, color);
        g.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
    }

    private static int pulse(int rgb, long periodMs) {
        double t = (Util.getMillis() % periodMs) / (double) periodMs;
        int a = (int) (110 + 145 * (0.5 + 0.5 * Math.sin(t * Math.PI * 2)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int darken(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f), gr = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
        return (r << 16) | (gr << 8) | b;
    }

    // ── Cadena de God Ki ─────────────────────────────────────────────────────

    /**
     * ¿Hay cadena? Mientras falte God Ki. Con la audiencia concedida (F5) queda HABILITADA: la
     * cadena se corre media columna a la derecha, God Ki asoma delante (se puede mirar, su
     * tooltip dice cómo conseguirlo) y lo divino sigue detrás.
     */
    private boolean computeSealed() {
        Node gk = byId.get(SkillTreeMigration.NODE_GOD_KI);
        boolean chained = gk != null && mc.player != null && pos.containsKey(gk.id())
                && SkillTreeNodes.evaluate(mc.player, gk).level() <= 0;
        chainEnabled = chained && att != null && att.hasGodKiAudience();
        return chained;
    }

    /** X de MUNDO por donde pasa la cadena. */
    private float chainWorldX() {
        float gx = pos.get(SkillTreeMigration.NODE_GOD_KI)[0];
        return chainEnabled ? gx + COL / 2f : gx;
    }

    /**
     * God Ki, lo que cuelga de él y cualquier nodo de su columna o más allá, mientras la cadena
     * está puesta: ni se dibuja ni se toca. Lo de la columna cubre a un hermano de God Ki (una
     * rama de datapack que cuelgue del mismo peldaño) que si no asomaría tras el velo.
     */
    private boolean behindChain(ResourceLocation id) {
        if (!sealed) return false;
        boolean godKi = SkillTreeMigration.NODE_GOD_KI.equals(id);
        if (godKi) return !chainEnabled;
        if (divine.contains(id)) return true;
        float[] p = pos.get(id);
        return p != null && p[0] >= chainWorldX();
    }

    private int chainX() {
        return Math.round(toScreenX(chainWorldX()));
    }

    /** De la cadena a la derecha el ratón no encuentra nada: es un muro, no un velo. */
    private boolean pastChain(double mx) {
        return sealed && mx >= chainX() - CHAIN_HALF;
    }

    /** Con la cadena puesta el lienzo no se lleva más allá de ella: se queda, como mucho, un
     *  poco a la derecha del centro. */
    private void clampPan() {
        if (!sealed) return;
        float max = chainWorldX() - CHAIN_STOP / zoom();
        if (panX > max) panX = max;
    }

    private boolean overSeal(double mx, double my) {
        if (!sealed || !inViewport(mx, my) || overHud(mx, my)) return false;
        int cy = Math.round(toScreenY(pos.get(SkillTreeMigration.NODE_GOD_KI)[1])) + (chainEnabled ? 18 : 0);
        return Math.abs(mx - chainX()) <= SkillTreeArt.SEAL_W / 4 + 1
                && Math.abs(my - cy) <= SkillTreeArt.SEAL_H / 4 + 1;
    }

    /** Lo único que enseña el sello: de quién es y que está sellado (nada de lo de detrás). */
    private void drawSealTooltip(GuiGraphics g, int mx, int my) {
        Node gk = byId.get(SkillTreeMigration.NODE_GOD_KI);
        List<TipLine> lines = List.of(new TipLine(nameOf(gk), TIP_NAME),
                new TipLine(Component.translatable(chainEnabled ? "screen.zenkai.skill_tree.chain_enabled"
                        : "screen.zenkai.skill_tree.sealed"), TIP_SPECIAL));
        drawTipBox(g, lines, -1f, -1, mx, my);
    }

    /**
     * Cadena de God Ki (visual de F5 adelantado a F2b): mientras God Ki no está conseguido, la
     * cadena cruza el lienzo de arriba abajo por la columna de God Ki con el sello a su altura, y
     * a su derecha solo queda fondo velado: ni God Ki ni la parte divina se dibujan ni responden
     * al ratón, para ninguna raza (petición del usuario: que actúe como cadena de verdad). Va en
     * espacio de PANTALLA (no escala con el zoom: es arte de eslabones), anclada a la columna del
     * nodo y desplazándose con el pan vertical.
     */
    private void drawGodKiSeal(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (!sealed) return;
        float[] gp = pos.get(SkillTreeMigration.NODE_GOD_KI);
        int cx = chainX();
        if (cx < x2) g.fill(Math.max(x1, cx), y1, x2, y2, VEIL);
        // Habilitada: la cadena tiembla y se ve más tenue (ya se puede romper).
        if (chainEnabled) {
            if (!ClientConfig.skillTreeReducedFx()) cx += (int) Math.round(Math.sin(Util.getMillis() / 70.0) * 0.8);
            g.setColor(1f, 1f, 1f, 0.75f);
        }

        // Eslabones: los de canto primero, los de frente encima (asoman por su hueco).
        int linkW = SkillTreeArt.LINK_W / 2;
        int step = 12;
        int phase = Math.floorMod(Math.round(toScreenY(0)), step * 2);
        for (int pass = 0; pass < 2; pass++) {
            for (int y = y1 - step * 2 + phase + (pass == 0 ? step : 0); y < y2; y += step * 2) {
                if (pass == 0) {
                    SkillTreeArt.blit(g, SkillTreeArt.HUD, cx - SkillTreeArt.LINK_SIDE_W / 4f, y,
                            SkillTreeArt.LINK_SIDE_U, SkillTreeArt.LINK_V, SkillTreeArt.LINK_SIDE_W,
                            SkillTreeArt.LINK_H, SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
                } else {
                    SkillTreeArt.blit(g, SkillTreeArt.HUD, cx - linkW / 2f, y, SkillTreeArt.LINK_U,
                            SkillTreeArt.LINK_V, SkillTreeArt.LINK_W, SkillTreeArt.LINK_H,
                            SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
                }
            }
        }
        int sy = Math.round(toScreenY(gp[1]));
        if (chainEnabled) sy += 18; // el sello se aparta: God Ki queda a la vista delante
        SkillTreeArt.blit(g, SkillTreeArt.HUD, cx - SkillTreeArt.SEAL_W / 4f, sy - SkillTreeArt.SEAL_H / 4f,
                SkillTreeArt.SEAL_U, SkillTreeArt.SEAL_V, SkillTreeArt.SEAL_W, SkillTreeArt.SEAL_H,
                SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
        g.setColor(1f, 1f, 1f, 1f);
    }

    // ── Placas de TP/MND y botones (dentro del lienzo) ───────────────────────

    /** Con el NOMBRE delante ("TP: 400K", "MND 210/300"): con solo un glifo el jugador no sabía
     *  qué número era cuál (petición del usuario). */
    private Component tpText() {
        return Component.translatable("screen.zenkai.technique.tp",
                ZenkaiNumbers.format(att == null ? 0 : att.getTP()));
    }

    private Component mindText() {
        return att == null ? Component.empty() : Component.translatable("screen.zenkai.physical.mnd_free",
                MindBudget.free(att), MindBudget.total(att));
    }

    private int plateWidth(Component text) { return 5 + this.font.width(text) + 5; }

    private void drawHeader(GuiGraphics g) {
        int x = vpLeft() + 4, y = vpTop() + 5;
        x += drawPlate(g, x, y, tpText(), ZenkaiPalette.TP_ON_DARK) + 4;
        int free = MindBudget.free(att);
        drawPlate(g, x, y, mindText(), free < 0 ? ZenkaiPalette.DENIED : ZenkaiPalette.MIND_ON_DARK);
        // Platos redondos detrás de la lupa y del botón de centrar (los widgets van encima).
        for (int bx : new int[]{zoomBtnX(), centerBtnX()}) {
            SkillTreeArt.blit(g, SkillTreeArt.HUD, bx - (BTN_PLATE - ICON_BTN) / 2f, btnY() - (BTN_PLATE - ICON_BTN) / 2f,
                    SkillTreeArt.BUTTON_U, SkillTreeArt.BUTTON_V, SkillTreeArt.BUTTON_TEX, SkillTreeArt.BUTTON_TEX,
                    SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
        }
    }

    /** Placa de tres trozos (izquierda, centro repetido, derecha) con su texto. */
    private int drawPlate(GuiGraphics g, int x, int y, Component text, int color) {
        int w = plateWidth(text);
        int piece = SkillTreeArt.PLATE_PIECE / 2;
        SkillTreeArt.blit(g, SkillTreeArt.HUD, x, y, SkillTreeArt.PLATE_U, SkillTreeArt.PLATE_V,
                SkillTreeArt.PLATE_PIECE, SkillTreeArt.PLATE_H, SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
        for (int mx = x + piece; mx < x + w - piece; mx += piece) {
            int cut = Math.min(piece, x + w - piece - mx);
            SkillTreeArt.blit(g, SkillTreeArt.HUD, mx, y, SkillTreeArt.PLATE_U + SkillTreeArt.PLATE_PIECE,
                    SkillTreeArt.PLATE_V, cut * 2, SkillTreeArt.PLATE_H, SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
        }
        SkillTreeArt.blit(g, SkillTreeArt.HUD, x + w - piece, y, SkillTreeArt.PLATE_U + 2 * SkillTreeArt.PLATE_PIECE,
                SkillTreeArt.PLATE_V, SkillTreeArt.PLATE_PIECE, SkillTreeArt.PLATE_H, SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
        g.drawString(this.font, text, x + 5, y + 3, color, true);
        return w;
    }

    // ── Tooltip del nodo ─────────────────────────────────────────────────────

    private record TipLine(Component text, int color) {}

    /**
     * Tooltip propio (no el vanilla): lleva la barra de maestría. Contiene lo que antes iba en
     * el panel inferior: estado, coste, números de la forma y su maestría, y la confirmación de
     * compra.
     */
    private void drawTooltip(GuiGraphics g, Node n, int mx, int my) {
        Eval e = SkillTreeNodes.evaluate(mc.player, n);
        List<TipLine> lines = new ArrayList<>();
        Component level = (n.kind() == Kind.LEVELED || n.kind() == Kind.PASSIVE) && e.maxLevel() > 1
                ? Component.translatable("screen.zenkai.skill_tree.level", e.level(), e.maxLevel())
                : null;
        lines.add(new TipLine(level == null ? nameOf(n) : nameOf(n).copy().append("  ").append(level), TIP_NAME));
        lines.add(new TipLine(stateLine(n, e), stateColor(e.state())));
        if (n.kind() == Kind.PASSIVE) addPassiveLines(lines, e);
        if (SkillTreeMigration.NODE_ARCOSIAN_POWER.equals(n.id()) && e.level() > 0) addPowerPointLines(lines);

        // Requisitos tipo advancement (F3): verde lo cumplido, rojo lo que falta.
        for (SkillTreeNodes.ReqLine r : SkillTreeNodes.requirementLines(mc.player, n)) {
            lines.add(new TipLine(Component.literal(r.met() ? "\u2714 " : "\u2718 ").append(r.text()),
                    r.met() ? ZenkaiPalette.OK : ZenkaiPalette.ERROR));
        }
        if (SkillTreeMigration.NODE_GOD_KI.equals(n.id()) && e.state() != State.OWNED) addGodKiLines(lines);

        float mastery = -1f;
        ResourceLocation form = masteryForm(n);
        if (form != null) mastery = addMasteryLines(lines, n, form);
        int barAfter = lines.size();

        // Pista con humor (F3): visible desde el inicio en los nodos con candado; los secretos
        // solo llegan aquí ya revelados.
        String hint = hintKey(n);
        if (hint != null) {
            for (var seq : this.font.split(Component.translatable(hint).withStyle(net.minecraft.ChatFormatting.ITALIC), TIP_WRAP)) {
                lines.add(new TipLine(seqToComponent(seq), TIP_HINT));
            }
        }
        if (n.id().equals(routeTarget) && !route.isEmpty()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.route_cost",
                    ZenkaiNumbers.format(routeCost), route.size()), ROUTE_COLOR));
        }

        if (n.id().equals(confirming)) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.confirm"), CONFIRM_RING));
        } else if (e.purchasable()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.buy"), ZenkaiPalette.OK));
        }
        if (routeTarget == null || !routeTarget.equals(n.id())) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.route_hint"), TIP_MUTED));
        }
        drawTipBox(g, lines, mastery, barAfter, mx, my);
    }

    /** Clave de la pista del nodo si el idioma la tiene (God Ki cambia de pista al habilitarse). */
    private @Nullable String hintKey(Node n) {
        String key = SkillTreeData.hintKey(n.id());
        if (SkillTreeMigration.NODE_GOD_KI.equals(n.id()) && chainEnabled) key = key + "_enabled";
        return Language.getInstance().has(key) ? key : null;
    }

    private static Component seqToComponent(net.minecraft.util.FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
        return Component.literal(sb.toString()).withStyle(net.minecraft.ChatFormatting.ITALIC);
    }

    /** Pasiva de raza (F4): qué hace a su nivel actual y qué hará en el siguiente, con los
     *  números reales de su curva. */
    /** Power Points del arcosiano en cifras (en la HUD solo se ve el porcentaje): tanque actual / máximo y cómo se carga y se gasta. */
    private void addPowerPointLines(List<TipLine> lines) {
        double max = com.hmc.zenkai.event.tick.ArcosianPowerSystem.ppMax(mc.player);
        if (max <= 0.0) return;
        double pp = att == null ? 0.0 : Math.min(max, att.getArcosianPp());
        lines.add(new TipLine(Component.translatable("skill_tree.zenkai.pp.tank",
                Math.round(pp), Math.round(max), Math.round(pp / max * 100.0)), ZenkaiPalette.OK));
        for (var seq : this.font.split(Component.translatable("skill_tree.zenkai.pp.how"), TIP_WRAP)) {
            lines.add(new TipLine(seqToComponent(seq), TIP_MUTED));
        }
    }

    private void addPassiveLines(List<TipLine> lines, Eval e) {
        Race race = att.getRace();
        for (var seq : this.font.split(Component.translatable(RacePassives.descKey(race)), TIP_WRAP)) {
            lines.add(new TipLine(seqToComponent(seq).copy().withStyle(s -> s.withItalic(false)), TIP_BODY));
        }
        lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.passive.now",
                passiveValues(race, e.level())), TIP_BODY));
        if (e.level() < e.maxLevel()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.passive.next",
                    passiveValues(race, e.level() + 1)), ZenkaiPalette.VALUE));
        }
    }

    /** Los números de la pasiva de esa raza en ese nivel, en una línea. */
    public static Component passiveValues(Race race, int level) {
        String key = "screen.zenkai.skill_tree.passive." + race.name().toLowerCase(Locale.ROOT);
        return switch (race) {
            case SAIYAN -> Component.translatable(key,
                    pct(RacePassives.valueAt(race, "zenkai_bonus", level, RacePassives.ZENKAI_BONUS)),
                    num(RacePassives.valueAt(race, "zenkai_duration_s", level, RacePassives.ZENKAI_DURATION / 20.0)),
                    num(RacePassives.valueAt(race, "zenkai_cooldown_s", level, RacePassives.ZENKAI_COOLDOWN / 20.0) / 60.0));
            case NAMEKIAN -> Component.translatable(key,
                    num(RacePassives.valueAt(race, "regen_mult", level, RacePassives.NAMEKIAN_REGEN_MULT)),
                    num(RacePassives.valueAt(race, "regen_ki_per_point", level, RacePassives.NAMEKIAN_REGEN_KI_COST)));
            case MAJIN -> Component.translatable(key,
                    pct(RacePassives.valueAt(race, "phys_reduction", level, RacePassives.MAJIN_PHYSICAL_REDUCTION)),
                    num(RacePassives.valueAt(race, "regen_per_second", level, RacePassives.MAJIN_PASSIVE_REGEN) * 100.0));
            case ARCOSIAN -> Component.translatable(key,
                    pct(RacePassives.valueAt(race, "ki_discount", level, 1.0 - RacePassives.ARCOSIAN_KI_DISCOUNT)));
            case HUMAN -> Component.translatable(key,
                    pct(RacePassives.valueAt(race, "tp_gain", level, RacePassives.HUMAN_TP_GAIN - 1.0)),
                    num(RacePassives.valueAt(race, "stamina_regen", level, RacePassives.HUMAN_STAMINA_REGEN)));
        };
    }

    private static String pct(double frac) { return Math.round(frac * 1000.0) / 10.0 + "%"; }

    private static String num(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }

    /** God Ki sin conseguir: las tres vías (§5) y en cuál está el jugador. */
    private void addGodKiLines(List<TipLine> lines) {
        boolean audience = att.hasGodKiAudience();
        lines.add(new TipLine(Component.literal(audience ? "\u2714 " : "\u2718 ")
                .append(Component.translatable("skill_tree.zenkai.req.flag.god_ki_audience")),
                audience ? ZenkaiPalette.OK : ZenkaiPalette.ERROR));
        lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.god_ki.via_trial"), TIP_SPECIAL));
        lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.god_ki.via_ritual",
                com.hmc.zenkai.config.ServerConfig.godKiRitualParticipants()), TIP_SPECIAL));
    }

    /** Caja del tooltip; {@code mastery} >= 0 pinta su barra antes de la línea {@code barAfter}. */
    private void drawTipBox(GuiGraphics g, List<TipLine> lines, float mastery, int barAfter, int mx, int my) {
        int w = 0;
        for (TipLine l : lines) w = Math.max(w, this.font.width(l.text()));
        w = Math.max(w, 90) + 8;
        int h = lines.size() * 10 + 6 + (mastery >= 0 ? 6 : 0);
        int x = mx + 12, y = my - 4;
        if (x + w > this.width - 2) x = mx - 12 - w;
        if (y + h > this.height - 2) y = this.height - 2 - h;
        y = Math.max(2, y);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF1A0C04);
        g.fill(x, y, x + w, y + h, TIP_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, TIP_BG);
        g.fill(x + 3, y + 12, x + w - 3, y + 13, 0x60E8901C); // filete bajo el nombre
        int ly = y + 3;
        for (int i = 0; i < lines.size(); i++) {
            if (i == barAfter && mastery >= 0) {
                int bw = w - 8;
                boolean maxed = mastery >= 100f;
                g.fill(x + 4, ly, x + 4 + bw, ly + 4, 0xFF000000);
                g.fill(x + 5, ly + 1, x + 4 + bw - 1, ly + 3, 0xFF2A2438);
                int filled = Math.round((bw - 2) * Math.min(1f, mastery / 100f));
                if (filled > 0) g.fill(x + 5, ly + 1, x + 5 + filled, ly + 3,
                        maxed ? ZenkaiPalette.MAXED : 0xFFF0A020);
                ly += 6;
            }
            TipLine l = lines.get(i);
            g.drawString(this.font, l.text(), x + 4, ly + (i == 0 ? 0 : 1), l.color(), true);
            ly += 10;
        }
        pose.popPose();
    }

    /** Forma cuya maestría se enseña en el tooltip del nodo (la doctrina de Mystic ES su forma). */
    private static @Nullable ResourceLocation masteryForm(Node n) {
        if (n.kind() == Kind.FORM) return n.form();
        if (SkillTreeMigration.NODE_POTENTIAL_UNLOCK.equals(n.id())) return FormIds.POTENTIAL_UNLOCK;
        return null;
    }

    /**
     * Lo que antes era una fila de la pestaña MASTERY: multiplicador y drenaje actual → al 100 %,
     * tiempo de transformación, y la maestría con su barra. Sin poseerla se enseñan igual los
     * números (es lo que dará), pero no la maestría. Devuelve la maestría si hay que pintar la
     * barra, o -1.
     */
    private float addMasteryLines(List<TipLine> lines, Node n, ResourceLocation formId) {
        PlayerFormAttachment form = mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        float m = form.getFormMastery(formId);
        boolean owned = SkillTreeNodes.owned(mc.player, n) || SkillTreeNodes.evaluate(mc.player, n).level() > 0;
        if (FormIds.POTENTIAL_UNLOCK.equals(formId)) {
            double ceiling = form.getPuCeiling();
            lines.add(new TipLine(Component.literal(statsRange(PotentialUnlock.statPercent(ceiling, m),
                    ceiling * PotentialUnlock.MASTERY_CEIL_FACTOR) + "  0 ki/s"), TIP_BODY));
        } else {
            FormDef d = FormDef.get(formId);
            if (d == null) return -1f;
            // Con la escala universal de la raza (F8): UI/UE/Beast rinden según el techo de cada raza.
            lines.add(new TipLine(Component.literal(statsRange(d.statPercent(att.getRace(), m),
                    d.statPercentMastered(att.getRace()))),
                    TIP_BODY));
            lines.add(new TipLine(Component.literal(ZenkaiNumbers.format(d.kiDrainPerSecond(m, att)) + " → "
                    + ZenkaiNumbers.format(d.kiDrainPerSecond(100.0, att)) + " ki/s"), TIP_MUTED));
            if (owned && d.holdTicks() > 0) {
                double mastered = Math.max(1, Math.round(d.holdTicks() * PlayerFormAttachment.MASTERED_HOLD_FACTOR));
                lines.add(new TipLine(Component.translatable("screen.zenkai.mastery.hold",
                        f1(form.holdTicksFor(d) / 20.0), f1(mastered / 20.0)), TIP_MUTED));
            }
        }
        if (!owned) return -1f;
        lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.mastery", f1(m) + "%"),
                m >= 100f ? ZenkaiPalette.MAXED : TIP_BODY));
        return m;
    }

    private Component nameOf(Node n) {
        if (n.kind() == Kind.ROOT) {
            Race r = att.getRace();
            return Component.translatable("screen.zenkai.skill_tree.root",
                    Component.translatable("screen.zenkai.race." + r.name().toLowerCase(Locale.ROOT)));
        }
        if (n.kind() == Kind.PASSIVE) return Component.translatable(RacePassives.nameKey(att.getRace()));
        if (n.form() != null) return Component.translatable("form.zenkai." + n.form().getPath());
        SkillDef def = n.skill() == null ? null : SkillDef.get(n.skill());
        return def == null ? Component.literal(n.id().toString()) : Component.translatable(def.nameKey());
    }

    /** Letrero bajo el nodo: la raíz se llama "Base" a secas (el nombre largo va en el tooltip). */
    private Component shortName(Node n) {
        if (n.kind() == Kind.ROOT) return Component.translatable("form.zenkai.base");
        return nameOf(n);
    }

    private Component stateLine(Node n, Eval e) {
        return switch (e.state()) {
            case OWNED -> Component.translatable(n.kind() == Kind.LEVELED || n.kind() == Kind.PASSIVE
                    ? "screen.zenkai.skill_tree.state.maxed" : "screen.zenkai.skill_tree.state.owned");
            case AVAILABLE -> Component.translatable("screen.zenkai.skill_tree.state.available",
                    ZenkaiNumbers.format(e.cost()), e.mindDelta());
            case NEED_TP -> Component.translatable("screen.zenkai.skill_tree.state.need_tp",
                    ZenkaiNumbers.format(e.cost()));
            case NEED_MIND -> Component.translatable("screen.zenkai.skill_tree.state.need_mind", e.mindDelta());
            case NEED_MASTER -> Component.translatable("screen.zenkai.skill_tree.state.need_master", mastersOf(e));
            case NEED_REQS -> Component.translatable("screen.zenkai.skill_tree.state.need_reqs");
            case LOCKED -> Component.translatable("screen.zenkai.skill_tree.state.locked");
            case CONDITION -> conditionLine(n);
        };
    }

    private Component conditionLine(Node n) {
        if (SkillTreeMigration.NODE_GOD_KI.equals(n.id())) {
            return Component.translatable(chainEnabled ? "screen.zenkai.skill_tree.state.condition_god_ki_enabled"
                    : "screen.zenkai.skill_tree.state.condition_god_ki");
        }
        SkillTreeData data = SkillTreeData.get(n.id());
        if (data != null && data.hasSpecialVia() && !data.purchasableWithTp()) {
            return Component.translatable("screen.zenkai.skill_tree.state.condition_via."
                    + data.acquisition().iterator().next());
        }
        if (FormIds.OOZARU.equals(n.form())) {
            return Component.translatable("screen.zenkai.skill_tree.state.condition_oozaru");
        }
        if (n.form() != null) {
            int lvl = SkillTreeNodes.doctrineLevelFor(n.form());
            Node parent = n.parent() == null ? null : byId.get(n.parent());
            if (lvl > 0) {
                // La doctrina es el nodo raíz de esta rama (Sign cuelga de UI, UI de Sign).
                Node doctrine = parent;
                while (doctrine != null && doctrine.kind() != Kind.LEVELED) {
                    doctrine = doctrine.parent() == null ? null : byId.get(doctrine.parent());
                }
                if (doctrine != null) {
                    return Component.translatable("screen.zenkai.skill_tree.state.condition_doctrine",
                            nameOf(doctrine), lvl);
                }
            }
        }
        return Component.translatable("screen.zenkai.skill_tree.state.condition");
    }

    private static Component mastersOf(Eval e) {
        SkillDef def = e.skill() == null ? null : SkillDef.get(e.skill());
        if (def == null || def.masters() == null || def.masters().isEmpty()) return Component.literal("?");
        var out = Component.empty();
        for (int i = 0; i < def.masters().size(); i++) {
            if (i > 0) out.append(", ");
            out.append(Component.translatable("master.zenkai." + def.masters().get(i)));
        }
        return out;
    }

    private static int stateColor(State s) {
        return switch (s) {
            case OWNED -> ZenkaiPalette.OK;
            case AVAILABLE -> ZenkaiPalette.VALUE;
            case NEED_TP, NEED_MIND, NEED_REQS -> ZenkaiPalette.ERROR;
            case NEED_MASTER, CONDITION -> TIP_SPECIAL;
            case LOCKED -> TIP_MUTED;
        };
    }

    /** "x1.40 → x2.50": multiplicador de stats actual y con la forma dominada. */
    private static String statsRange(double now, double mastered) {
        return "x" + String.format(Locale.ROOT, "%.2f", 1.0 + now) + " → x"
                + String.format(Locale.ROOT, "%.2f", 1.0 + mastered);
    }

    private static String f1(double d) { return String.format(Locale.ROOT, "%.1f", d); }
}
