package com.hmc.zenkai.client.gui.screens.skilltree;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.AtlasIconButton;
import com.hmc.zenkai.client.gui.buttons.HelpIconButton;
import com.hmc.zenkai.client.gui.screens.ZenkaiMenuScreen;
import com.hmc.zenkai.client.gui.screens.ZenkaiTab;
import com.hmc.zenkai.client.gui.screens.skilltree.SkillTreeArt.Frame;
import com.hmc.zenkai.client.gui.widgets.TutorialOverlay;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.player.MindBudget;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.ForgetSkillPacket;
import com.hmc.zenkai.feature.skills.FormDrivenSkills;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillTogglePacket;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.util.ZenkaiNumbers;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pestaña HABILIDADES: el árbol de skills POR MAESTRO (diseño cerrado: {@code .claude/archivo/2026-10-25-skill-tree-cerrado/skill-tree.md} §5; guía: docs/gui-skill-trees.md).
 * Sustituye a la lista de texto con «+» (SkillsScreen) y NO comparte código con
 * {@link SkillTreeScreen}: aquella pantalla está atada a formas, God Ki y secretos y el usuario
 * aprobó su aspecto, así que aquí solo se copia lo genérico (pan/zoom en escalones, tooltip,
 * placas de TP/MND, tutorial, compra con dos clics).
 *
 *  - Cuatro ramas (Kami arriba, Korin a la izquierda, Kaio a la derecha, Tori-Bot abajo). En el
 *    centro de cada una, el retrato del maestro; de él salen, y solo de él, las skills que enseña.
 *    SIN orden entre skills ni tronco común: las que enseñan varios maestros se REPITEN bajo cada
 *    uno con el mismo nivel y estado (el dato vive en el attachment, no en el nodo).
 *  - Un maestro no conocido (nunca se le dio clic derecho, {@code PlayerStatsAttachment#hasMetMaster})
 *    es un «?» con pista de dónde está, y sus skills salen como siluetas «???», salvo las que el
 *    jugador ya tiene por otra vía.
 *  - El maestro ENSEÑA el nivel 1 (en persona, MasterScreen); subir de nivel es clic en el nodo
 *    (dos clics, como el otro árbol) por SkillBuyPacket sin maestro. Clic derecho (dos veces) olvida
 *    un nivel con reembolso, lo que hacía la X de la lista vieja.
 *  - Anillo de niveles alrededor del marco: un segmento por nivel.
 *  - Fondo «cielo de entrenamiento» (tools/gen_skills_sky.py): cielo FIJO al visor con fundido por
 *    rama, nubes lejanas y medias con paralaje y mar de nubes pegado abajo. Sin movimiento propio.
 *  - Puerta «Transformaciones» en la rama de Tori-Bot: UI/UE/God Ki viven en el otro árbol.
 *
 * Hasta la fase E (arte) los iconos son placeholders: el icono de reserva del atlas de nodos,
 * tintado con el color de la rama.
 *
 * Pan, zoom y fondo son STATIC por la misma razón que en SkillTreeScreen: createScreen() construye
 * una pantalla nueva en cada visita a la pestaña.
 */
public class SkillsTreeScreen extends TreeCanvasScreen {

    // ── Geometría (px de un panel de 256; el beige real va de x=12 a x=243, y de 13 a 242) ──

    private static final int PLATE_H = 14, HELP_BTN = 20;

    /** Medidas en coordenadas de MUNDO (zoom 1 = px de GUI). */
    private static final int NODE = 24, ICON = 16;
    private static final long CONFIRM_MIN_MS = 250L;
    private static final long HOVER_THEME_MS = 100L;
    private static final long FADE_MS = 3200L;
    private static final int MAX_LAYERS = 6;
    /** Margen de aire alrededor del contenido al limitar el pan. */
    private static final int PAN_MARGIN = 40;

    /** Radio del anillo de skills alrededor de su maestro y apertura máxima del abanico (grados). */
    private static final float RING_R = 84f;
    private static final float CENTER_SHIFT = 38f;
    private static final float RING_ARC = 200f;
    /** Celdas de icons.png: la lupa y el busto «yo» (como SkillTreeScreen), y la puerta (pestaña de formas). */
    private static final int ZOOM_U = 0, ZOOM_V = 100;
    /** Celda de icons.png del botón «Mis habilidades» (tools/gen_skills_mine_icon.py deja un marcador donde dibujarlo). */
    private static final int MINE_U = 140, MINE_V = 80;

    // ── Colores ──

    private static final int EDGE_OFF = 0xC0283048;
    private static final int EDGE_HALO = 0x70000000;
    private static final int RING_OFF = 0xB0182030;
    private static final int LABEL_BG = 0xD0100C1C;
    private static final int TIP_BG = 0xF0100C1C;
    private static final int TIP_BORDER = 0xFFE8901C;
    private static final int TIP_NAME = 0xFFFFE070;
    private static final int TIP_BODY = 0xFFE0DCEC;
    private static final int TIP_MUTED = 0xFFA8A4B8;
    private static final int TIP_SPECIAL = 0xFFC8A0FF;
    private static final int TIP_HINT = 0xFFB8C8E0;
    private static final int TIP_WRAP = 190;

    // ── Ramas ──

    private static final String[] MASTERS = {"kami", "korin", "kaio", "toribot"};
    /**
     * Fondo del retrato de cada maestro (mismo orden que MASTERS): tono medio-oscuro del matiz del cielo de su
     * rama (medido en las capturas) y aro interior más oscuro. Fijados a mano, no derivados de Sky.accent: el
     * acento no casa con el cielo (Korin salía marrón sobre cielo verde) y Kaio/Tori-Bot compartían casi el mismo.
     */
    private static final int[] PORTRAIT_BG = {0xFF4A74A8, 0xFF2F7D66, 0xFFB4642E, 0xFF2F6CB4};
    private static final int[] PORTRAIT_RIM = {0xFF2E4C78, 0xFF1E5545, 0xFF7A3E1C, 0xFF1A3F70};
    /** Centro de cada rama en el mundo y hacia dónde abre su abanico (grados; 0 = derecha, 90 = abajo). */
    private static final float[][] CENTERS = {{0, -170}, {-190, 0}, {190, 0}, {0, 170}};
    private static final float[] OUTWARD = {-90f, 180f, 0f, 90f};

    /** Cielo por rama (textura sky_*.png) y colores: núcleo claro de las aristas encendidas y anillos. */
    private enum Sky {
        NEUTRAL("neutral", 0xFFDCE8EE), KAMI("kami", 0xFFA8E0BC), KORIN("korin", 0xFFF8C284),
        KAIO("kaio", 0xFF98D2DC), TORIBOT("toribot", 0xFFA4E2EE);

        final ResourceLocation tex;
        final int accent;

        Sky(String name, int accent) {
            this.tex = bg("sky_" + name + ".png");
            this.accent = accent;
        }

        static Sky ofBranch(int b) { return values()[b + 1]; }
    }

    private static final ResourceLocation CLOUDS_FAR = bg("sky_clouds_far.png");
    private static final ResourceLocation CLOUDS_MID = bg("sky_clouds_mid.png");
    private static final ResourceLocation SEA = bg("sky_sea.png");
    private static final int SKY_TEX_W = 464, SEA_TEX_H = 80, CLOUD_TILE = 256;

    private static ResourceLocation bg(String file) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/skill_tree/backgrounds/" + file);
    }

    // ── Nodos ──

    private enum Kind { MASTER, SKILL, GATE, HEAD, JOIN }

    /** Un nodo del lienzo. {@code key} es único (una skill compartida se repite con otra clave por rama). */
    private record Node(String key, Kind kind, int branch, @Nullable String skillId, float x, float y) {}

    // ── Estado que sobrevive entre visitas a la pestaña ──

    /**
     * Vista «Mis habilidades» (botón junto al de centrar): tu cabeza en el centro y, en círculos desde ella, SOLO lo
     * que ya tienes. Las armas de ki cuelgan de Ki Infuse + Ki Fist (las dos líneas que se unen son la receta).
     * Estática por lo mismo que el pan: la pantalla se reconstruye en cada visita a la pestaña.
     */
    private static boolean mineMode;
    private static final float MINE_R1 = 74f, MINE_RING_STEP = 62f, MINE_NODE_SPACING = 44f;
    /** Armas: radio del abanico, paso entre centros (el letrero mide ~85 px, así que se escalonan en dos radios) y cuánto sube el escalón. */
    private static final float MINE_WEAPON_R = 178f, MINE_WEAPON_SPACING = 58f, MINE_WEAPON_STAGGER = 44f, MINE_JOIN_LIFT = 34f;
    private static final int MINE_FIRST_RING = 6;
    private static int zoomIdx = DEFAULT_ZOOM;
    private static float panX, panY;

    // Lo que pide TreeCanvasScreen: el pan y el zoom son static de ESTA pantalla (la vista sobrevive entre visitas a la pestaña).
    @Override protected float panX() { return panX; }
    @Override protected float panY() { return panY; }
    @Override protected void setPan(float x, float y) { panX = x; panY = y; }
    @Override protected int zoomIndex() { return zoomIdx; }
    @Override protected void onCanvasClick(double mx, double my) { onNodeClicked(nodeAt(mx, my)); }
    private static boolean centeredOnce;
    private static int centerCycle = -1;
    private static final List<Sky> layerSky = new ArrayList<>(List.of(Sky.NEUTRAL));
    private static final List<Float> layerAlpha = new ArrayList<>(List.of(1f));
    private static long layerClock;

    // ── Estado de la instancia ──

    private final List<Node> nodes = new ArrayList<>();
    /** Aristas de la vista «Mis habilidades» (claves de nodo origen → destino); la vista de maestros no las usa. */
    private final List<String[]> edges = new ArrayList<>();
    private @Nullable AtlasIconButton modeButton;
    private int defSignature = -1;
    private @Nullable String hoverKey;
    private @Nullable Sky hoverSky;
    private long hoverSince;
    private @Nullable String confirming;
    private @Nullable String forgetArmed;
    private long armedAt;
    /** Destino de la cámara (mundo) mientras el tutorial la lleva a un nodo; null = quieta. */

    /** Solo SkillTreeShots (modo perf): apagan una parte del dibujo para repartir el coste por capas. */
    public static boolean devSkipEdges, devSkipBg;

    /** Solo SkillTreeShots (modo perf): cambia a la vista pedida como lo haría el botón. */
    public void devSetMine(boolean mine) {
        if (mineMode != mine) onModePressed();
    }

    /** Solo para la herramienta de desarrollo SkillTreeShots: ratón simulado (null = el real). */
    public static int @Nullable [] devMouse;

    public SkillsTreeScreen() {
        super(Component.translatable(ZenkaiTab.SKILLS.titleKey()));
    }

    @Override
    protected ZenkaiTab currentTab() { return ZenkaiTab.SKILLS; }

    // ── Construcción ─────────────────────────────────────────────────────────

    @Override
    protected void initContent() {
        if (att == null || !att.isRaceChosen() || mc.player == null) {
            nodes.clear();
            return;
        }
        build();
        if (!centeredOnce) {
            centeredOnce = true;
            zoomIdx = DEFAULT_ZOOM;
            int first = 0;
            for (int i = 0; i < MASTERS.length; i++) {
                if (att.hasMetMaster(MASTERS[i])) { first = i; break; }
            }
            centerOnBranch(first);
            resetSky(Sky.NEUTRAL);
        }

        AtlasIconButton zoomReset = new AtlasIconButton(zoomBtnX(), btnY(), ZOOM_U, ZOOM_V,
                () -> zoomIdx = DEFAULT_ZOOM);
        zoomReset.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.skill_tree.reset_zoom")));
        addRenderableWidget(zoomReset);
        modeButton = new AtlasIconButton(modeBtnX(), btnY(), MINE_U, MINE_V, this::onModePressed);
        modeButton.setTooltip(modeTooltip());
        addRenderableWidget(modeButton);
        HelpIconButton help = new HelpIconButton(helpX(), helpY(), HELP_BTN, () -> { if (tutorial != null) tutorial.start(); });
        help.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.tutorial.replay")));
        addRenderableWidget(help);

        tutorial = new TutorialOverlay("skills_tree", tutorialSteps());
        tutorial.startIfFirstTime();
    }

    /** Skills que entran en este árbol: las que enseña un maestro y NO tienen nodo en el de transformaciones. */
    private static boolean listed(SkillDef d) {
        String id = d.id();
        return d.hasMaster() && !SkillTreeNodes.isTreeSkill(id) && !"god_ki".equals(id) && !"namek_giant".equals(id);
    }

    /** Cambia si cambia el catálogo o, en «Mis habilidades», lo que el jugador tiene (compra, olvido). */
    private int signature() {
        int sig = SkillDef.all().size() * 2 + (mineMode ? 1 : 0);
        PlayerStatsAttachment st = stats();
        if (mineMode && st != null) {
            for (SkillDef d : SkillDef.all()) sig = sig * 31 + (st.skills().level(d.id()) > 0 ? 1 : 0);
        }
        return sig;
    }

    private void build() {
        nodes.clear();
        edges.clear();
        defSignature = signature();
        if (mineMode) buildMine(); else buildMasters();
    }

    private static boolean isWeapon(@Nullable String id) {
        return id != null && (SkillToggles.KI_MAIN_WEAPONS.contains(id) || SkillEffects.KI_SHIELD.equals(id));
    }

    /** Rama de color de una skill en la vista propia: la de su primer maestro, o -1 (cielo neutro). */
    private static int branchOf(SkillDef d) {
        for (int b = 0; b < MASTERS.length; b++) if (d.masters().contains(MASTERS[b])) return b;
        return -1;
    }

    /**
     * «Mis habilidades»: cabeza en (0,0); las skills que se tienen en anillos concéntricos (6, luego 6 más por anillo);
     * Ki Infuse y Ki Fist abren el primer anillo y las armas de ki, que solo salen si se tienen las dos, forman un arco
     * hacia fuera justo detrás de ellas, cada una unida a las dos.
     */
    private void buildMine() {
        PlayerStatsAttachment st = stats();
        nodes.add(new Node("head", Kind.HEAD, -1, null, 0, 0));
        if (st == null) return;
        List<SkillDef> mine = new ArrayList<>();
        for (SkillDef d : SkillDef.all()) {
            if (listed(d) && !isWeapon(d.id()) && st.skills().level(d.id()) > 0) mine.add(d);
        }
        SkillDef infuse = SkillDef.get(SkillEffects.KI_INFUSE), fist = SkillDef.get(SkillEffects.KI_FIST);
        boolean recipe = infuse != null && fist != null && mine.contains(infuse) && mine.contains(fist);
        List<SkillDef> weapons = new ArrayList<>();
        if (recipe) {
            for (String id : SkillToggles.KI_MAIN_WEAPONS) {
                SkillDef d = SkillDef.get(id);
                if (d != null) weapons.add(d);
            }
            SkillDef shield = SkillDef.get(SkillEffects.KI_SHIELD);
            if (shield != null) weapons.add(shield);
            mine.remove(infuse);
            mine.remove(fist);
        }
        // Con las armas, arriba (-90°) queda reservado un sector: Infuse y Fist en el primer anillo, a los lados de
        // ese eje, y las armas en abanico al fondo, pegadas a sus dos padres. El resto de skills se reparte UNIFORME
        // por el arco que queda libre; cada anillo mete tantas como caben a MINE_NODE_SPACING px entre centros.
        double secHalf = recipe ? Math.toRadians(52) : 0.0;
        double from = -Math.PI / 2 + secHalf, span = 2 * Math.PI - 2 * secHalf;
        if (recipe) {
            double d = MINE_NODE_SPACING / 2.0 / MINE_R1;
            for (int k = 0; k < 2; k++) {
                SkillDef sd = k == 0 ? infuse : fist;
                double ang = -Math.PI / 2 + (k == 0 ? -d : d);
                String key = sd.id() + "@mine";
                nodes.add(new Node(key, Kind.SKILL, branchOf(sd), sd.id(), (float) (Math.cos(ang) * MINE_R1), (float) (Math.sin(ang) * MINE_R1)));
                edges.add(new String[]{"head", key});
            }
            // Nodo de fusión: Infuse y Fist confluyen en un punto y de ahí sale UNA línea a cada arma (2 + 6 líneas
            // en vez de 12 cruzadas). Sube hacia las armas por el eje del sector reservado.
            float jy = -(float) (Math.cos(Math.asin(MINE_NODE_SPACING / 2.0 / MINE_R1)) * MINE_R1) - MINE_JOIN_LIFT;
            nodes.add(new Node("join", Kind.JOIN, -1, null, 0f, jy));
            edges.add(new String[]{SkillEffects.KI_INFUSE + "@mine", "join"});
            edges.add(new String[]{SkillEffects.KI_FIST + "@mine", "join"});
            double step = MINE_WEAPON_SPACING / MINE_WEAPON_R;
            for (int i = 0; i < weapons.size(); i++) {
                double ang = -Math.PI / 2 + (i - (weapons.size() - 1) / 2.0) * step;
                float rw = MINE_WEAPON_R + (i % 2 == 1 ? MINE_WEAPON_STAGGER : 0f);
                SkillDef wd = weapons.get(i);
                String key = wd.id() + "@mine";
                nodes.add(new Node(key, Kind.SKILL, branchOf(wd), wd.id(), (float) (Math.cos(ang) * rw), (float) (Math.sin(ang) * rw)));
                edges.add(new String[]{"join", key});
            }
        }
        int placed = 0, ring = 0;
        while (placed < mine.size()) {
            float r = MINE_R1 + ring * MINE_RING_STEP;
            int cap = Math.max(1, (int) Math.floor(span * r / MINE_NODE_SPACING));
            int take = Math.min(cap, mine.size() - placed);
            for (int i = 0; i < take; i++) {
                // Los anillos pares e impares se desfasan medio paso para que las líneas al centro no se tapen.
                double ang = from + span * (i + 0.5 + (ring % 2 == 1 ? 0.5 : 0.0)) / (take + (ring % 2 == 1 ? 0.5 : 0.0));
                SkillDef d = mine.get(placed + i);
                String key = d.id() + "@mine";
                nodes.add(new Node(key, Kind.SKILL, branchOf(d), d.id(), (float) (Math.cos(ang) * r), (float) (Math.sin(ang) * r)));
                edges.add(new String[]{"head", key});
            }
            placed += take;
            ring++;
        }
    }

    private void buildMasters() {
        for (int b = 0; b < MASTERS.length; b++) {
            float cx = CENTERS[b][0], cy = CENTERS[b][1];
            nodes.add(new Node("m:" + MASTERS[b], Kind.MASTER, b, null, cx, cy));
            // Primero las exclusivas del maestro y al final las compartidas: orden estable de carga dentro de cada grupo.
            List<SkillDef> mine = new ArrayList<>();
            for (SkillDef d : SkillDef.all()) {
                if (listed(d) && d.masters().contains(MASTERS[b])) mine.add(d);
            }
            mine.sort(Comparator.comparingInt(d -> d.masters().size()));
            boolean hasGate = "toribot".equals(MASTERS[b]);
            int n = mine.size() + (hasGate ? 1 : 0);
            // La puerta va en el centro del abanico (justo debajo del maestro), no en una punta.
            int gateAt = hasGate ? mine.size() / 2 : -1;
            for (int i = 0, k = 0; i < n; i++) {
                float ang = n == 1 ? OUTWARD[b]
                        : OUTWARD[b] - RING_ARC / 2f + RING_ARC * i / (n - 1);
                float x = cx + (float) Math.cos(Math.toRadians(ang)) * RING_R;
                float y = cy + (float) Math.sin(Math.toRadians(ang)) * RING_R;
                if (i == gateAt) {
                    nodes.add(new Node("gate", Kind.GATE, b, null, x, y));
                } else {
                    String id = mine.get(k++).id();
                    nodes.add(new Node(id + "@" + MASTERS[b], Kind.SKILL, b, id, x, y));
                }
            }
        }
    }

    // ── Estado de un nodo ────────────────────────────────────────────────────

    /** Lo que hace falta para pintar y para decidir un clic; se calcula en cada frame del attachment vivo. */
    private record View(boolean silhouette, int level, int max, int cost, int mindDelta, boolean canLevel,
                        boolean needTp, boolean needMind, boolean canForget) {
        boolean owned() { return level > 0; }
        boolean maxed() { return max > 0 && level >= max; }
        boolean affordable() { return canLevel && !needTp && !needMind; }
    }

    private PlayerStatsAttachment stats() {
        return mc.player == null ? att : mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
    }

    private View view(Node n) {
        PlayerStatsAttachment st = stats();
        SkillDef def = n.skillId() == null ? null : SkillDef.get(n.skillId());
        if (st == null || def == null || mc.player == null) return new View(true, 0, 0, 0, 0, false, false, false, false);
        int level = st.skills().level(def.id());
        boolean silhouette = !mineMode && level <= 0 && !st.hasMetMaster(MASTERS[n.branch()]);
        int max = FormDrivenSkills.maxLevel(def, mc.player);
        // Sin maestro (las armas de ki) el nivel 1 también se compra aquí, con TP.
        boolean canLevel = def.purchasable() && (level >= 1 || !def.hasMaster()) && level < max;
        int cost = canLevel ? FormDrivenSkills.tpCostForLevel(def, mc.player, level + 1) : 0;
        int mindDelta = canLevel ? def.mindReqFor(level + 1) - def.mindReqFor(level) : 0;
        boolean canForget = def.purchasable() && level > 0 && st.skills().boughtLevels(def.id()) > 0;
        return new View(silhouette, level, max, cost, mindDelta, canLevel,
                canLevel && st.getTP() < cost, canLevel && st.mindFree() < mindDelta, canForget);
    }

    private Node byKey(@Nullable String key) {
        if (key == null) return null;
        for (Node n : nodes) if (n.key().equals(key)) return n;
        return null;
    }

    // ── Geometría derivada ───────────────────────────────────────────────────

    private int modeBtnX() { return zoomBtnX() - BTN_PLATE - 2; }
    /** Igual que en SkillTreeScreen: en la fila de los botones, a 6 px del de modo (la misma separación que modo-zoom). */
    private int helpX()    { return modeBtnX() - HELP_BTN - 6; }
    private int helpY()    { return btnY(); }


    /** Placas de TP/MND y botones de la franja superior: ahí no se elige nodo. */
    private boolean overHud(double x, double y) {
        if (y >= helpY() - 2 && y < helpY() + HELP_BTN + 2 && x >= helpX() - 3) return true;
        if (y >= vpTop() + 2 && y < vpTop() + 6 + BTN_PLATE) {
            if (x >= modeBtnX() - 3) return true;
            if (x < vpLeft() + 6 + plateWidth(tpText()) + 4 + plateWidth(mindText())) return true;
        }
        return false;
    }

    @Override
    protected void clampPan() {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, top = Float.MAX_VALUE, bot = -Float.MAX_VALUE;
        for (Node n : nodes) {
            lo = Math.min(lo, n.x()); hi = Math.max(hi, n.x());
            top = Math.min(top, n.y()); bot = Math.max(bot, n.y());
        }
        if (lo > hi) return;
        panX = Math.max(lo - PAN_MARGIN, Math.min(hi + PAN_MARGIN, panX));
        panY = Math.max(top - PAN_MARGIN, Math.min(bot + PAN_MARGIN, panY));
    }

    private void focusCamera(float wx, float wy) {
        camGoal = new float[]{wx, wy};
    }


    /** Centra la rama entera, no solo al maestro: sus skills salen hacia fuera y quedarían bajo las placas de arriba. */
    private void centerOnBranch(int b) {
        panX = CENTERS[b][0] + (float) Math.cos(Math.toRadians(OUTWARD[b])) * CENTER_SHIFT;
        panY = CENTERS[b][1] + (float) Math.sin(Math.toRadians(OUTWARD[b])) * CENTER_SHIFT;
        clampPan();
    }

    private Tooltip modeTooltip() {
        return Tooltip.create(Component.translatable(mineMode ? "screen.zenkai.skills_tree.mode_masters"
                : "screen.zenkai.skills_tree.mode_mine"));
    }

    /** Alterna entre el árbol por maestros y «Mis habilidades»; la cámara va al centro de la vista que entra. */
    private void onModePressed() {
        mineMode = !mineMode;
        confirming = null;
        forgetArmed = null;
        camGoal = null;
        centerCycle = -1;
        build();
        if (mineMode) {
            panX = 0;
            panY = 0;
        } else {
            centerOnBranch(0);
        }
        resetSky(Sky.NEUTRAL);
        clampPan();
        if (modeButton != null) modeButton.setTooltip(modeTooltip());
    }

    // ── Fondo ────────────────────────────────────────────────────────────────

    private static void resetSky(Sky s) {
        layerSky.clear();
        layerAlpha.clear();
        layerSky.add(s);
        layerAlpha.add(1f);
        layerClock = Util.getMillis();
    }

    /** Pide el cielo de una rama: se difumina lentamente sobre el actual, sin cortes. */
    private static void setSky(Sky s) {
        if (layerSky.get(layerSky.size() - 1) == s) return;
        if (layerSky.size() >= MAX_LAYERS) { layerSky.remove(0); layerAlpha.remove(0); layerAlpha.set(0, 1f); }
        layerSky.add(s);
        layerAlpha.add(0f);
    }

    private static void advanceSky() {
        long now = Util.getMillis();
        float dt = Math.min(100L, Math.max(0L, now - layerClock)) / (float) FADE_MS;
        layerClock = now;
        for (int i = 1; i < layerAlpha.size(); i++) layerAlpha.set(i, Math.min(1f, layerAlpha.get(i) + dt));
        for (int i = layerAlpha.size() - 1; i > 0; i--) {
            if (layerAlpha.get(i) >= 1f) {
                for (int k = 0; k < i; k++) { layerSky.remove(0); layerAlpha.remove(0); }
                break;
            }
        }
    }

    private void drawBackground(GuiGraphics g, int x1, int y1, int x2, int y2) {
        advanceSky();
        for (int i = 0; i < layerSky.size(); i++) {
            float a = layerAlpha.get(i);
            float eased = i == 0 ? 1f : a * a * (3f - 2f * a);
            // El cielo es opaco y no se repite: ocupa el visor entero (464x460 texeles = 232x230 px de GUI).
            quad(g, layerSky.get(i).tex, x1, y1, x2 - x1, y2 - y1, eased);
        }
        // Sin deriva propia (el usuario se mareaba): las nubes solo se mueven al hacer pan, cada capa a su paralaje.
        clouds(g, CLOUDS_FAR, 0.25f, x1, y1, x2, y2);
        clouds(g, CLOUDS_MID, 0.5f, x1, y1, x2, y2);
        int seaH = Math.round((x2 - x1) * (float) SEA_TEX_H / SKY_TEX_W);
        quad(g, SEA, x1, y2 - seaH, x2 - x1, seaH, 1f);
    }

    private void clouds(GuiGraphics g, ResourceLocation tex, float parallax, int x1, int y1, int x2, int y2) {
        int sx = Math.round(panX * zoom() * parallax), sy = Math.round(panY * zoom() * parallax);
        int startX = x1 + Math.floorMod(-sx, CLOUD_TILE) - CLOUD_TILE;
        int startY = y1 + Math.floorMod(-sy, CLOUD_TILE) - CLOUD_TILE;
        for (int x = startX; x < x2; x += CLOUD_TILE) {
            for (int y = startY; y < y2; y += CLOUD_TILE) quad(g, tex, x, y, CLOUD_TILE, CLOUD_TILE, 1f);
        }
    }

    /**
     * Textura entera estirada a un rectángulo, con alfa por COLOR DE VÉRTICE (no g.setColor + blit:
     * con eso el fundido entre fondos no se mezclaba; ver docs/gui-skill-trees.md). Pinta DENTRO del scissor del lienzo.
     */
    private static void quad(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, float alpha) {
        if (alpha <= 0.003f) return;
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(m, x, y + h, 0f).setUv(0f, 1f).setColor(1f, 1f, 1f, alpha);
        bb.addVertex(m, x + w, y + h, 0f).setUv(1f, 1f).setColor(1f, 1f, 1f, alpha);
        bb.addVertex(m, x + w, y, 0f).setUv(1f, 0f).setColor(1f, 1f, 1f, alpha);
        bb.addVertex(m, x, y, 0f).setUv(0f, 0f).setColor(1f, 1f, 1f, alpha);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    // ── Ratón ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tutorial != null && tutorial.mouseClicked()) return true;
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 1 && inViewport(mx, my) && !overHud(mx, my)) {
            onNodeRightClicked(nodeAt(mx, my));
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
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0 && inViewport(mx, my)) {
            // Zoom alrededor del cursor: el punto de mundo bajo el ratón se queda donde estaba.
            camGoal = null;
            float wx = toWorldX(mx), wy = toWorldY(my);
            int before = zoomIdx;
            zoomIdx = Math.max(0, Math.min(ZOOMS.length - 1, zoomIdx + (sy > 0 ? 1 : -1)));
            if (zoomIdx != before) {
                panX = wx - (float) ((mx - vpCx()) / zoom());
                panY = wy - (float) ((my - vpCy()) / zoom());
                clampPan();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private @Nullable Node nodeAt(double mx, double my) {
        if (!inViewport(mx, my) || overHud(mx, my)) return null;
        float wx = toWorldX(mx), wy = toWorldY(my), half = NODE / 2f;
        for (Node n : nodes) {
            if (n.kind() == Kind.JOIN) continue;
            if (Math.abs(wx - n.x()) <= half && Math.abs(wy - n.y()) <= half) return n;
        }
        return null;
    }

    // ── Compra y olvido ──────────────────────────────────────────────────────

    /**
     * Skill: primer clic en un nodo comprable lo arma, el segundo compra (pasados {@link #CONFIRM_MIN_MS}
     * para que un doble clic no cuente). El nivel 1 NO se compra aquí: lo enseña el maestro en persona.
     * Puerta: abre el árbol de transformaciones.
     */
    private void onNodeClicked(@Nullable Node n) {
        forgetArmed = null;
        if (n == null) { confirming = null; return; }
        if (n.kind() == Kind.GATE) {
            mc.setScreen(ZenkaiMenuScreen.createScreen(ZenkaiTab.TRANSFORMATIONS));
            return;
        }
        if (n.kind() != Kind.SKILL) { confirming = null; return; }
        View v = view(n);
        // Un arma de ki ya comprada se ENCIENDE y APAGA con un clic (no gasta nada; olvidarla es el clic derecho).
        if (mineMode && isWeapon(n.skillId()) && v.owned()) {
            confirming = null;
            PacketDistributor.sendToServer(new SkillTogglePacket(n.skillId()));
            return;
        }
        long now = Util.getMillis();
        if (v.affordable() && n.key().equals(confirming)) {
            if (now - armedAt < CONFIRM_MIN_MS) return;
            confirming = null;
            PacketDistributor.sendToServer(new SkillBuyPacket(n.skillId()));
            return;
        }
        confirming = v.affordable() ? n.key() : null;
        armedAt = now;
    }

    /** Clic derecho DOS veces: olvida un nivel (reembolsa TP y MND). El servidor valida el suelo del maestro. */
    private void onNodeRightClicked(@Nullable Node n) {
        confirming = null;
        if (n == null || n.kind() != Kind.SKILL || !view(n).canForget()) { forgetArmed = null; return; }
        long now = Util.getMillis();
        if (n.key().equals(forgetArmed)) {
            if (now - armedAt < CONFIRM_MIN_MS) return;
            forgetArmed = null;
            PacketDistributor.sendToServer(new ForgetSkillPacket(n.skillId()));
            return;
        }
        forgetArmed = n.key();
        armedAt = now;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        // El lienzo va AQUÍ y no en render(): así los botones de la esquina se dibujan encima.
        drawCanvas(g, devMouse != null ? devMouse[0] : mx, devMouse != null ? devMouse[1] : my);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        if (devMouse != null) { mx = devMouse[0]; my = devMouse[1]; }
        if (att != null && att.isRaceChosen() && defSignature != signature()) build();
        stepCamera();
        clampPan();
        Node hover = dragging ? null : nodeAt(mx, my);
        hoverKey = hover == null ? null : hover.key();
        if (hover != null && !mineMode) {
            Sky hs = Sky.ofBranch(hover.branch());
            long now = Util.getMillis();
            if (hs != hoverSky) { hoverSky = hs; hoverSince = now; }
            else if (now - hoverSince >= HOVER_THEME_MS) setSky(hs);
        } else {
            hoverSky = null;
        }
        // Lo armado se desarma si dejó de ser posible (ya comprado, sin TP...).
        Node armed = byKey(confirming);
        if (armed != null && !view(armed).affordable()) confirming = null;
        Node forgetting = byKey(forgetArmed);
        if (forgetting != null && !view(forgetting).canForget()) forgetArmed = null;

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
        if (hover != null && !isOverWidget(mx, my)) drawTooltip(g, hover, mx, my);
    }

    /** Solo SkillTreeShots: lanza el tutorial como el botón «?». */
    public void devStartTutorial() {
        if (tutorial != null) tutorial.start();
    }


    /** Solo SkillTreeShots: centra el lienzo en una rama y devuelve el centro en pantalla de un nodo (o null). */
    public int @Nullable [] devFocus(int branch, @Nullable String key) {
        centerOnBranch(branch);
        Node n = key == null ? nodes.get(0) : byKey(key);
        return n == null ? null : new int[]{Math.round(toScreenX(n.x())), Math.round(toScreenY(n.y()))};
    }

    private void drawCanvas(GuiGraphics g, int mx, int my) {
        if (nodes.isEmpty()) return;
        int x1 = vpLeft(), y1 = vpTop(), x2 = vpRight(), y2 = vpBottom();
        g.enableScissor(x1, y1, x2, y2);
        if (!devSkipBg) drawBackground(g, x1, y1, x2, y2);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(originX(), originY(), 0);
        pose.scale(zoom(), zoom(), 1f);
        if (!devSkipEdges) drawEdges(g);
        labels.clear();
        for (Node n : nodes) drawNode(g, n, n.key().equals(hoverKey));
        pose.popPose();
        drawLabels(g);
        drawHeader(g);
        g.disableScissor();
    }

    /** Una arista ya resuelta (coordenadas de mundo redondeadas, encendida o no, color de la rama). */
    private record EdgeSpec(int ax, int ay, int bx, int by, boolean lit, int accent) {}

    private @Nullable net.minecraft.client.renderer.texture.DynamicTexture edgeTex;
    private @Nullable ResourceLocation edgeId;
    private int edgeOx, edgeOy, edgeW, edgeH;
    private List<EdgeSpec> edgeCache = List.of();

    /** Las aristas de la vista actual. Una se enciende cuando lo que une está TENIDO (la de un arma, solo con el arma comprada). */
    private List<EdgeSpec> collectEdges() {
        List<EdgeSpec> out = new ArrayList<>();
        if (mineMode) {
            boolean anyWeapon = false;
            for (Node n : nodes) {
                if (n.kind() == Kind.SKILL && isWeapon(n.skillId()) && view(n).owned()) anyWeapon = true;
            }
            for (String[] e : edges) {
                Node a = byKey(e[0]), b = byKey(e[1]);
                if (a == null || b == null) continue;
                // Las dos que llegan al nodo de fusión se encienden en cuanto hay alguna arma.
                boolean lit = b.kind() == Kind.JOIN ? anyWeapon : b.kind() == Kind.SKILL && view(b).owned();
                out.add(new EdgeSpec(Math.round(a.x()), Math.round(a.y()), Math.round(b.x()), Math.round(b.y()), lit, Sky.NEUTRAL.accent));
            }
        } else {
            Node master = null;
            for (Node n : nodes) {
                if (n.kind() == Kind.MASTER) { master = n; continue; }
                if (master == null) continue;
                boolean lit = n.kind() == Kind.SKILL && view(n).owned();
                out.add(new EdgeSpec(Math.round(master.x()), Math.round(master.y()), Math.round(n.x()), Math.round(n.y()),
                        lit, Sky.ofBranch(n.branch()).accent));
            }
        }
        return out;
    }

    /**
     * Las aristas se pintan UNA vez en una textura dinámica (coordenadas de mundo, 1 texel = 1 px) y cada fotograma es un
     * único blit; solo se vuelve a rasterizar si cambia alguna arista (compra, olvido, cambio de vista). Medido el
     * 2026-10-29 con el modo perf de SkillTreeShots: dibujarlas con g.fill (unos 3 500 por fotograma) costaba ~2,5 ms de
     * screen.render y bajaba el menú de ~128 a ~66 FPS; y antes con un fill por píxel era peor.
     */
    private void drawEdges(GuiGraphics g) {
        List<EdgeSpec> list = collectEdges();
        if (edgeTex == null && !list.isEmpty() || !list.equals(edgeCache)) rebuildEdgeTexture(list);
        if (edgeTex != null && edgeId != null) {
            g.blit(edgeId, edgeOx, edgeOy, 0, 0, edgeW, edgeH, edgeW, edgeH);
        }
    }

    private void rebuildEdgeTexture(List<EdgeSpec> list) {
        edgeCache = list;
        releaseEdgeTexture();
        if (list.isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (EdgeSpec e : list) {
            minX = Math.min(minX, Math.min(e.ax(), e.bx())); maxX = Math.max(maxX, Math.max(e.ax(), e.bx()));
            minY = Math.min(minY, Math.min(e.ay(), e.by())); maxY = Math.max(maxY, Math.max(e.ay(), e.by()));
        }
        edgeOx = minX - 3;
        edgeOy = minY - 3;
        edgeW = maxX - minX + 6;
        edgeH = maxY - minY + 6;
        var img = new com.mojang.blaze3d.platform.NativeImage(edgeW, edgeH, true);
        for (EdgeSpec e : list) rasterEdge(img, e);
        edgeId = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "dynamic/skills_edges_" + System.identityHashCode(this));
        edgeTex = new net.minecraft.client.renderer.texture.DynamicTexture(img);
        edgeTex.setFilter(false, false);
        mc.getTextureManager().register(edgeId, edgeTex);
    }

    private void releaseEdgeTexture() {
        if (edgeTex != null && edgeId != null) mc.getTextureManager().release(edgeId);
        edgeTex = null;
    }

    @Override
    public void removed() {
        releaseEdgeTexture();
        super.removed();
    }

    /**
     * Línea recta a PÍXEL EXACTO sobre la textura (Bresenham), todo opaco: contorno oscuro de 1 px, cuerpo de 2 px y,
     * encendida, un filete claro de 1 px por el centro (aspecto de tubo). Apagada: cuerpo apagado sin filete. Las
     * primeras versiones (cuadros de 2 px con halo translúcido, luego un g.fill por píxel) manchaban la línea o
     * tiraban los FPS; ver {@link #drawEdges}.
     */
    private void rasterEdge(com.mojang.blaze3d.platform.NativeImage img, EdgeSpec e) {
        int x0 = e.ax() - edgeOx, y0 = e.ay() - edgeOy, x1 = e.bx() - edgeOx, y1 = e.by() - edgeOy;
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int[] px = new int[Math.max(dx, dy) + 1], py = new int[px.length];
        int n = 0, err = dx - dy, x = x0, y = y0;
        while (true) {
            px[n] = x; py[n] = y; n++;
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
        int[] cols = {e.lit() ? 0xFF05080F : 0xFF0A0E18, e.lit() ? 0xFF000000 | darken(e.accent(), 0.62f) : 0xFF283048, 0xFF000000 | e.accent()};
        int[] lo = {-2, -1, 0}, hi = {1, 0, 0};   // por capa (contorno, cuerpo, filete): desplazamientos desde el punto de la línea
        for (int layer = 0; layer < (e.lit() ? 3 : 2); layer++) {
            int c = cols[layer];
            int abgr = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
            for (int i = 0; i < n; i++) {
                for (int ox = lo[layer]; ox <= hi[layer]; ox++) {
                    for (int oy = lo[layer]; oy <= hi[layer]; oy++) {
                        int tx = px[i] + ox, ty = py[i] + oy;
                        if (tx >= 0 && ty >= 0 && tx < edgeW && ty < edgeH) img.setPixelRGBA(tx, ty, abgr);
                    }
                }
            }
        }
    }

    private void drawNode(GuiGraphics g, Node n, boolean hovered) {
        int x = Math.round(n.x() - NODE / 2f), y = Math.round(n.y() - NODE / 2f);
        Sky sky = Sky.ofBranch(n.branch());
        boolean fx = !ClientConfig.skillTreeReducedFx();
        PlayerStatsAttachment st = stats();
        if (st == null) return;

        if (n.kind() == Kind.JOIN) {
            int cx = Math.round(n.x()), cy = Math.round(n.y());
            boolean on = false;
            for (Node o : nodes) {
                if (o.kind() == Kind.SKILL && isWeapon(o.skillId()) && view(o).owned()) { on = true; break; }
            }
            int c = on ? sky.accent | 0xFF000000 : EDGE_OFF;
            for (int r = 0; r <= 5; r++) {
                g.fill(cx - (5 - r), cy - r, cx + (5 - r) + 1, cy - r + 1, r == 5 ? 0xFF000000 : (r == 4 ? EDGE_HALO : c));
                g.fill(cx - (5 - r), cy + r, cx + (5 - r) + 1, cy + r + 1, r == 5 ? 0xFF000000 : (r == 4 ? EDGE_HALO : c));
            }
            return;
        }

        if (n.kind() == Kind.HEAD) {
            // La cabeza del jugador (cara de su skin, capa de sombrero incluida) en un marco más grande que el de un nodo.
            int head = NODE + 8, hx = Math.round(n.x() - head / 2f), hy = Math.round(n.y() - head / 2f);
            g.fill(hx - 2, hy - 2, hx + head + 2, hy + head + 2, 0xFF000000 | darken(sky.accent, 0.6f));
            g.fill(hx - 1, hy - 1, hx + head + 1, hy + head + 1, 0xFF101828);
            if (mc.player != null) PlayerFaceRenderer.draw(g, mc.player.getSkin(), hx, hy, head);
            return;
        }

        if (n.kind() == Kind.MASTER) {
            boolean known = st.hasMetMaster(MASTERS[n.branch()]);
            frame(g, x, y, true, known ? 1f : 0.7f);
            if (known) {
                // Retrato del maestro: celda de 32 texeles a 16 px de GUI (1:1), centrada en el marco.
                // Solo si ya lo conoce: el desconocido sigue con «?» para no revelar quién es.
                int inset = (NODE - ICON) / 2;
                // Fondo propio del maestro: el interior del marco es casi negro y se comía el contorno
                // oscuro del retrato (la gorra de Tori-Bot, el pelo de Kaio). Aro + fondo planos (sin degradado).
                g.fill(x + inset, y + inset, x + NODE - inset, y + NODE - inset, PORTRAIT_RIM[n.branch()]);
                g.fill(x + inset + 1, y + inset + 1, x + NODE - inset - 1, y + NODE - inset - 1, PORTRAIT_BG[n.branch()]);
                SkillTreeArt.blit(g, SkillTreeArt.MASTER_PORTRAITS, x + inset, y + inset,
                        n.branch() * SkillTreeArt.ICON_TEX, 0, SkillTreeArt.ICON_TEX, SkillTreeArt.ICON_TEX,
                        SkillTreeArt.MASTER_PORTRAITS_W, SkillTreeArt.MASTER_PORTRAITS_H);
            } else {
                g.fill(x + 4, y + 4, x + NODE - 4, y + NODE - 4, 0xFF1A2236);
                labels.add(new Label(n.x(), n.y(), Component.literal("?"), 0xFFFFFFFF, true, 2f));
            }
            if (hovered) g.fill(x + 4, y + 4, x + NODE - 4, y + NODE - 4, ZenkaiPalette.HOVER_VEIL);
            labels.add(new Label(n.x(), y + NODE + 3, known
                    ? Component.translatable("master.zenkai." + MASTERS[n.branch()])
                    : Component.translatable("screen.zenkai.skills_tree.unknown"), sky.accent, known, 1f));
            return;
        }

        if (n.kind() == Kind.GATE) {
            // El mismo icono que la pestaña Transformaciones (un solo arte: tools/gen_transformations_tab_icon.py).
            // SIN marco: el icono mide 20 px y el hueco del marco 16, así que el arco se montaba sobre el
            // borde; la puerta es su propio marco (y se distingue de un nodo de skill). Tampoco softGlow ni
            // velo de hover: los dos rellenan el cuadrado entero y se verían en las esquinas transparentes.
            g.blit(ICON_ATLAS, x + 2, y + 2, GATE_U, GATE_V, 20, 20, 256, 256);
            if (hovered) ring(g, x, y, NODE, fx ? pulse(sky.accent, 900L) : sky.accent);
            labels.add(new Label(n.x(), y + NODE + 3, Component.translatable("screen.zenkai.tab.transformations"),
                    sky.accent, true, 1f));
            return;
        }

        View v = view(n);
        SkillDef def = SkillDef.get(n.skillId());
        if (def == null) return;
        int labelY = y + NODE + 5;
        if (v.owned()) softGlow(g, x, y, sky.accent, 0x50);
        if (n.key().equals(confirming)) {
            ring(g, x - 2, y - 2, NODE + 4, pulse(ZenkaiPalette.GOLD, 500L));
        } else if (n.key().equals(forgetArmed)) {
            ring(g, x - 2, y - 2, NODE + 4, pulse(ZenkaiPalette.ERROR, 500L));
        } else if (v.affordable()) {
            ring(g, x - 2, y - 2, NODE + 4, fx ? pulse(ZenkaiPalette.OK, 1200L) : ZenkaiPalette.OK);
        }
        boolean weaponOff = mineMode && isWeapon(def.id()) && v.owned() && mc.player != null && !SkillToggles.isOn(mc.player, def.id());
        boolean weaponOn = mineMode && isWeapon(def.id()) && v.owned() && !weaponOff;
        if (weaponOn) ring(g, x - 2, y - 2, NODE + 4, fx ? pulse(ZenkaiPalette.OK, 1400L) : ZenkaiPalette.OK);
        frame(g, x, y, v.maxed() && !isWeapon(def.id()), v.owned() ? (weaponOff ? 0.5f : 1f) : 0.55f);
        float ib = v.silhouette() ? 0.2f : weaponOff ? 0.35f : v.owned() ? 1f : 0.6f;
        tintedIcon(g, x, y, v.silhouette() ? 0xFF303850 : sky.accent, ib);
        if (hovered) g.fill(x + 4, y + 4, x + NODE - 4, y + NODE - 4, ZenkaiPalette.HOVER_VEIL);
        if (v.silhouette()) {
            SkillTreeArt.blit(g, SkillTreeArt.HUD, x + NODE - 8, y + NODE - 9, SkillTreeArt.LOCK_U, SkillTreeArt.LOCK_V,
                    SkillTreeArt.LOCK_W, SkillTreeArt.LOCK_H, SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
        } else if (v.max() > 1) {
            levelRing(g, x, y, v.max(), v.level(), v.maxed() ? ZenkaiPalette.GOLD : sky.accent);
        }
        Component label = v.silhouette() ? Component.translatable("screen.zenkai.skills_tree.unknown")
                : Component.translatable(def.nameKey());
        if (weaponOff) label = label.copy().append(Component.translatable("screen.zenkai.skills_tree.weapon_off_suffix"));
        labels.add(new Label(n.x(), labelY, label, sky.accent, v.owned() && !weaponOff, 1f));
    }

    private static final ResourceLocation ICON_ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int GATE_U = ZenkaiTab.TRANSFORMATIONS.u, GATE_V = ZenkaiTab.TRANSFORMATIONS.v;

    private static void frame(GuiGraphics g, int x, int y, boolean ornate, float b) {
        SkillTreeArt.blitTinted(g, SkillTreeArt.FRAMES, x, y, Frame.UNIVERSAL.ordinal() * SkillTreeArt.FRAME_TEX,
                ornate ? SkillTreeArt.FRAME_TEX : 0, SkillTreeArt.FRAME_TEX, SkillTreeArt.FRAME_TEX,
                SkillTreeArt.FRAMES_W, SkillTreeArt.FRAMES_H, b, b, b * 1.05f, 1f);
    }

    /** Icono de reserva ({@code unknown}) con el tinte pedido y brillo {@code b}. */
    private static void tintedIcon(GuiGraphics g, int x, int y, int argb, float b) {
        int inset = (NODE - ICON) / 2;
        SkillTreeArt.icon(g, SkillTreeArt.UNKNOWN_ICON, x + inset, y + inset,
                b * ((argb >> 16) & 0xFF) / 255f, b * ((argb >> 8) & 0xFF) / 255f, b * (argb & 0xFF) / 255f);
    }

    /**
     * Anillo de niveles: un segmento por nivel alrededor del marco (a 4 px), relleno hasta el nivel
     * comprado. Se recorre el perímetro píxel a píxel; el último de cada segmento queda vacío de separador.
     */
    private static void levelRing(GuiGraphics g, int x, int y, int levels, int filled, int on) {
        int side = NODE + 8 - 1, perimeter = side * 4, ox = x - 4, oy = y - 4;
        for (int t = 0; t < perimeter; t++) {
            int seg = t * levels / perimeter;
            if (levels > 1 && (t + 1) * levels / perimeter != seg) continue;
            int c = seg < filled ? 0xFF000000 | (on & 0xFFFFFF) : RING_OFF;
            // 2 px de grosor hacia FUERA del recorrido (el interior es del anillo de «disponible»).
            if (t < side) g.fill(ox + t, oy - 1, ox + t + 1, oy + 1, c);
            else if (t < 2 * side) g.fill(ox + side, oy + t - side, ox + side + 2, oy + t - side + 1, c);
            else if (t < 3 * side) g.fill(ox + side - (t - 2 * side), oy + side, ox + side - (t - 2 * side) + 1, oy + side + 2, c);
            else g.fill(ox - 1, oy + side - (t - 3 * side), ox + 1, oy + side - (t - 3 * side) + 1, c);
        }
    }

    private static void softGlow(GuiGraphics g, int x, int y, int rgb, int alpha) {
        if (alpha <= 0) return;
        for (int i = 3; i >= 1; i--) {
            int a = Math.clamp((long) alpha * (4 - i) / 4, 0, 255);
            int c = (a << 24) | (rgb & 0xFFFFFF);
            int x1 = x - i, y1 = y - i, x2 = x + NODE + i, y2 = y + NODE + i;
            g.fill(x1 + i + 1, y1, x2 - i - 1, y1 + 1, c);
            g.fill(x1 + i + 1, y2 - 1, x2 - i - 1, y2, c);
            g.fill(x1, y1 + i + 1, x1 + 1, y2 - i - 1, c);
            g.fill(x2 - 1, y1 + i + 1, x2, y2 - i - 1, c);
        }
        g.fill(x, y, x + NODE, y + NODE, ((Math.min(255, alpha) / 2) << 24) | (rgb & 0xFFFFFF));
    }




    /** Letrero pendiente de dibujar, con su ancla en coordenadas de mundo y su escala de letra. */
    private record Label(float wx, float wy, Component name, int accent, boolean owned, float scale) {}

    private final List<Label> labels = new ArrayList<>();

    /** Letreros en espacio de PANTALLA: con el zoom se mueven con su nodo pero la letra no crece. A 0,5x no se leen. */
    private void drawLabels(GuiGraphics g) {
        for (Label l : labels) {
            if (zoom() < 1f && l.scale() <= 1f) continue;
            if (l.scale() > 1f) {
                // El «?» del maestro desconocido: una letra grande centrada en su nodo.
                var pose = g.pose();
                pose.pushPose();
                pose.translate(toScreenX(l.wx()), toScreenY(l.wy()), 0);
                pose.scale(l.scale() * zoom(), l.scale() * zoom(), 1f);
                // Centrado por la TINTA del glifo (no por font.width, que cuenta 1 px de separación a la derecha):
                // 5 de ancho x 7 de alto, de ahí -2,5 / -3,5. Antes iba 3 px por encima del centro del marco.
                pose.translate(-(this.font.width(l.name()) - 1) / 2.0F, -3.5F, 0);
                g.drawString(this.font, l.name(), 0, 0, l.accent(), false); // sin sombra: con ella la caja de tinta+sombra quedaba 1 px (x2) abajo-derecha
                pose.popPose();
                continue;
            }
            Component name = PanelText.fit(this.font, l.name(), Math.round(80 * zoom()));
            int w = this.font.width(name) + 4;
            int lx = Math.round(toScreenX(l.wx()) - w / 2f);
            int ly = Math.round(toScreenY(l.wy()));
            g.fill(lx - 1, ly - 1, lx + w + 1, ly + 10, 0xFF000000 | darken(l.accent(), l.owned() ? 0.6f : 0.3f));
            g.fill(lx, ly, lx + w, ly + 9, LABEL_BG);
            g.drawString(this.font, name, lx + 2, ly + 1, l.owned() ? 0xFFFFFFFF : TIP_MUTED, false);
        }
        labels.clear();
    }


    // ── Placas de TP/MND ─────────────────────────────────────────────────────


    private Component mindText() {
        return att == null ? Component.empty() : Component.translatable("screen.zenkai.skills_tree.mnd",
                MindBudget.free(att), MindBudget.total(att));
    }


    private void drawHeader(GuiGraphics g) {
        int x = vpLeft() + 4, y = vpTop() + 5;
        x += drawPlate(g, x, y, tpText(), ZenkaiPalette.TP_ON_DARK) + 4;
        drawPlate(g, x, y, mindText(), MindBudget.free(att) < 0 ? ZenkaiPalette.DENIED : ZenkaiPalette.MIND_ON_DARK);
        for (int bx : new int[]{zoomBtnX(), modeBtnX()}) {
            SkillTreeArt.blit(g, SkillTreeArt.HUD, bx - (BTN_PLATE - ICON_BTN) / 2f, btnY() - (BTN_PLATE - ICON_BTN) / 2f,
                    SkillTreeArt.BUTTON_U, SkillTreeArt.BUTTON_V, SkillTreeArt.BUTTON_TEX, SkillTreeArt.BUTTON_TEX,
                    SkillTreeArt.HUD_W, SkillTreeArt.HUD_H);
        }
    }

    /** Placa de tres trozos (izquierda, centro repetido, derecha) con su texto. */
    private int drawPlate(GuiGraphics g, int x, int y, Component text, int color) {
        int w = plateWidth(text), piece = SkillTreeArt.PLATE_PIECE / 2;
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

    // ── Tooltip ──────────────────────────────────────────────────────────────


    private void drawTooltip(GuiGraphics g, Node n, int mx, int my) {
        List<TipLine> lines = new ArrayList<>();
        String master = n.branch() >= 0 ? MASTERS[n.branch()] : "";
        switch (n.kind()) {
            case HEAD, JOIN -> { return; }
            case MASTER -> masterLines(lines, master);
            case GATE -> {
                lines.add(new TipLine(Component.translatable("screen.zenkai.tab.transformations"), TIP_NAME));
                wrap(lines, Component.translatable("screen.zenkai.skills_tree.gate"), TIP_BODY, false);
                lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.gate.open"), ZenkaiPalette.OK));
            }
            case SKILL -> skillLines(lines, n, master);
        }
        drawTipBox(g, lines, mx, my);
    }

    private void masterLines(List<TipLine> lines, String master) {
        boolean known = stats() != null && stats().hasMetMaster(master);
        if (!known) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.unknown"), TIP_NAME));
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.master_unknown"), TIP_SPECIAL));
        } else {
            lines.add(new TipLine(Component.translatable("master.zenkai." + master), TIP_NAME));
            var names = Component.empty();
            for (SkillDef d : SkillDef.all()) {
                if (!listed(d) || !d.masters().contains(master)) continue;
                if (!names.getSiblings().isEmpty()) names.append(", ");
                names.append(Component.translatable(d.nameKey()));
            }
            wrap(lines, Component.translatable("screen.zenkai.skills_tree.teaches", names), TIP_BODY, false);
        }
        // La pista de dónde está sale SIEMPRE: conocerlo no hace que sepas volver.
        wrap(lines, Component.translatable("screen.zenkai.skills_tree.master." + master + ".hint"), TIP_HINT, true);
    }

    private void skillLines(List<TipLine> lines, Node n, String master) {
        SkillDef def = SkillDef.get(n.skillId());
        if (def == null) return;
        View v = view(n);
        if (v.silhouette()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.unknown"), TIP_NAME));
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.locked_master"), TIP_SPECIAL));
            return;
        }
        Component title = Component.translatable(def.nameKey());
        if (v.max() > 1) title = title.copy().append("  ")
                .append(Component.translatable("screen.zenkai.skill_tree.level", v.level(), v.max()));
        lines.add(new TipLine(title, TIP_NAME));
        if (!v.owned() && !def.hasMaster()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.next",
                    ZenkaiNumbers.format(v.cost()), v.mindDelta()),
                    v.affordable() ? ZenkaiPalette.VALUE : ZenkaiPalette.ERROR));
        } else if (!v.owned()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.learn",
                    Component.translatable("master.zenkai." + master)), TIP_SPECIAL));
        } else if (v.maxed()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skill_tree.state.maxed"), ZenkaiPalette.OK));
        } else if (v.canLevel()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.next",
                    ZenkaiNumbers.format(v.cost()), v.mindDelta()),
                    v.affordable() ? ZenkaiPalette.VALUE : ZenkaiPalette.ERROR));
        }
        wrap(lines, Component.translatable(def.descKey()), TIP_BODY, false);
        valueLines(lines, def, v);
        List<String> others = new ArrayList<>();
        for (String m : def.masters()) if (!m.equals(master)) others.add(m);
        if (!others.isEmpty()) {
            var names = Component.empty();
            for (int i = 0; i < others.size(); i++) {
                if (i > 0) names.append(", ");
                names.append(Component.translatable("master.zenkai." + others.get(i)));
            }
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.also_taught", names), TIP_MUTED));
        }
        if (mineMode && isWeapon(def.id()) && v.owned()) {
            boolean on = mc.player != null && SkillToggles.isOn(mc.player, def.id());
            lines.add(new TipLine(Component.translatable(on ? "screen.zenkai.skills_tree.weapon_on" : "screen.zenkai.skills_tree.weapon_off"),
                    on ? ZenkaiPalette.OK : TIP_MUTED));
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.weapon_toggle_hint"), TIP_HINT));
        }
        if (n.key().equals(confirming)) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.confirm"), ZenkaiPalette.GOLD));
        } else if (v.affordable()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.buy"), ZenkaiPalette.OK));
        }
        if (n.key().equals(forgetArmed)) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.forget_confirm"), ZenkaiPalette.ERROR));
        } else if (v.canForget()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills_tree.forget_hint"), TIP_MUTED));
        } else if (v.owned() && def.purchasable()) {
            lines.add(new TipLine(Component.translatable("screen.zenkai.skills.forget.locked"), TIP_MUTED));
        }
    }

    /** Cómo se escribe cada curva de {@code values}: el nombre de la clave decide la unidad (son las mismas en todas las skills). */
    private enum Fmt { NUM, MULT, PCT, SECONDS_FROM_TICKS }

    private static Fmt fmtOf(String key) {
        return switch (key) {
            case "speed_mult", "range_mult", "regen_mult", "charge_mult" -> Fmt.MULT;
            case "ki_cost_mult", "stamina_cost_mult", "cooldown_mult", "drain_factor", "block_reduction",
                 "damage_factor", "shield_block_bonus", "ki_return", "body_heal", "counter_bonus" -> Fmt.PCT;
            case "cooldown_ticks", "window_bonus" -> Fmt.SECONDS_FROM_TICKS;
            default -> Fmt.NUM;
        };
    }

    private static String fmt(Fmt f, double v) {
        return switch (f) {
            case MULT -> "\u00d7" + num(v);
            case PCT -> num(v * 100.0) + " %";
            case SECONDS_FROM_TICKS -> num(v / 20.0) + " s";
            case NUM -> num(v);
        };
    }

    /**
     * Efecto actual → siguiente de cada curva de la skill ({@code values} del JSON). El nombre sale del lang
     * ({@code skill.zenkai.<id>.value.<clave>}) y el valor se formatea según su unidad; sin clave de lang
     * cae al nombre interno, que es solo la red de seguridad para skills de datapack ajenas.
     * Sin la skill (nivel 0) enseña el efecto del nivel 1, el de «aprenderla».
     */
    private void valueLines(List<TipLine> lines, SkillDef def, View v) {
        int shown = 0;
        for (Map.Entry<String, List<Double>> e : def.values().entrySet()) {
            if (shown >= 4) break;
            Fmt f = fmtOf(e.getKey());
            double now = v.owned() ? def.value(e.getKey(), v.level(), Double.NaN) : Double.NaN;
            double next = v.level() < v.max() ? def.value(e.getKey(), v.level() + 1, Double.NaN) : Double.NaN;
            if (Double.isNaN(now) && Double.isNaN(next)) continue;
            String langKey = "skill.zenkai." + def.id() + ".value." + e.getKey();
            Component label = Language.getInstance().has(langKey) ? Component.translatable(langKey)
                    : Component.literal(e.getKey().replace('_', ' '));
            String text = Double.isNaN(now) ? "\u2192 " + fmt(f, next)
                    : fmt(f, now) + (Double.isNaN(next) ? "" : " \u2192 " + fmt(f, next));
            lines.add(new TipLine(Component.empty().append(label).append(": " + text), TIP_MUTED));
            shown++;
        }
    }

    private static String num(double v) {
        double r = Math.round(v * 100.0) / 100.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.format(Locale.ROOT, "%.2f", r).replaceAll("0+$", "");
    }

    private void wrap(List<TipLine> lines, Component text, int color, boolean italic) {
        Component c = italic ? text.copy().withStyle(ChatFormatting.ITALIC) : text;
        for (var seq : this.font.split(c, TIP_WRAP)) {
            StringBuilder sb = new StringBuilder();
            seq.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
            Component line = Component.literal(sb.toString());
            lines.add(new TipLine(italic ? line.copy().withStyle(ChatFormatting.ITALIC) : line, color));
        }
    }

    private void drawTipBox(GuiGraphics g, List<TipLine> lines, int mx, int my) {
        int w = 0;
        for (TipLine l : lines) w = Math.max(w, this.font.width(l.text()));
        w = Math.max(w, 90) + 8;
        int h = lines.size() * 10 + 6;
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
            TipLine l = lines.get(i);
            g.drawString(this.font, l.text(), x + 4, ly + (i == 0 ? 0 : 1), l.color(), true);
            ly += 10;
        }
        pose.popPose();
    }

    // ── Tutorial ─────────────────────────────────────────────────────────────

    /** Rama que enseña el tutorial: la del primer maestro conocido, o Kami. */
    private int tutorialBranch() {
        for (int i = 0; i < MASTERS.length; i++) {
            if (att != null && att.hasMetMaster(MASTERS[i])) return i;
        }
        return 0;
    }

    /** Skill de ejemplo del tutorial: la primera que el jugador ya tiene en esa rama, o la primera de la rama. */
    private @Nullable Node tutorialSkill() {
        Node first = null;
        for (Node n : nodes) {
            if (n.kind() != Kind.SKILL || n.branch() != tutorialBranch()) continue;
            if (first == null) first = n;
            if (view(n).owned()) return n;
        }
        return first;
    }

    /** Lleva la cámara al nodo (con el zoom por defecto, para que el recuadro sea legible). */
    private void focusNode(@Nullable Node n) {
        if (n == null) return;
        zoomIdx = DEFAULT_ZOOM;
        focusCamera(n.x(), n.y());
    }

    private List<TutorialOverlay.Step> tutorialSteps() {
        List<TutorialOverlay.Step> out = new ArrayList<>();
        // Cada paso que señala un nodo LLEVA LA CÁMARA a él al empezar: antes el recuadro se pintaba donde
        // «debería» estar el nodo aunque el lienzo estuviese desplazado y caía sobre otra cosa.
        out.add(step(1, () -> new int[]{vpLeft(), vpTop(), vpRight() - vpLeft(), vpBottom() - vpTop()},
                () -> { zoomIdx = DEFAULT_ZOOM; centerCamera(tutorialBranch()); }));
        out.add(step(2, () -> nodeRect("m:" + MASTERS[tutorialBranch()]),
                () -> focusNode(byKey("m:" + MASTERS[tutorialBranch()]))));
        out.add(step(3, () -> new int[]{vpLeft() + 2, vpTop() + 3,
                plateWidth(tpText()) + 6 + plateWidth(mindText()), PLATE_H + 4}, null));
        out.add(step(4, this::skillRect, () -> focusNode(tutorialSkill())));
        out.add(step(5, this::skillRect, () -> focusNode(tutorialSkill())));
        out.add(step(6, () -> nodeRect("gate"), () -> focusNode(byKey("gate"))));
        // «?», modo y zoom van ahora en la MISMA fila: un solo recuadro de ancho a ancho.
        out.add(step(7, () -> new int[]{helpX() - 3, btnY() - 3, zoomBtnX() + ICON_BTN + 3 - (helpX() - 3), ICON_BTN + 6}, null));
        return out;
    }

    private int @Nullable [] skillRect() {
        Node n = tutorialSkill();
        return n == null ? null : nodeRect(n.key());
    }

    /** Cámara a la rama entera (la misma posición que {@link #centerOnBranch}), con destino suave. */
    private void centerCamera(int b) {
        focusCamera(CENTERS[b][0] + (float) Math.cos(Math.toRadians(OUTWARD[b])) * CENTER_SHIFT,
                CENTERS[b][1] + (float) Math.sin(Math.toRadians(OUTWARD[b])) * CENTER_SHIFT);
    }

    private static TutorialOverlay.Step step(int n, java.util.function.Supplier<int[]> rect, @Nullable Runnable onEnter) {
        String k = "screen.zenkai.tutorial.skills_tree." + n;
        return new TutorialOverlay.Step(Component.translatable(k + ".title"), Component.translatable(k), rect, onEnter);
    }

    private int @Nullable [] nodeRect(String key) {
        Node n = byKey(key);
        if (n == null) return null;
        int s = Math.round(NODE * zoom()) + 10; // incluye el anillo de niveles y el letrero de abajo
        return new int[]{Math.round(toScreenX(n.x())) - s / 2, Math.round(toScreenY(n.y())) - s / 2 + 2, s, s + 4};
    }
}
