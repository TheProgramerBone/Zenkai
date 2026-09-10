package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.AcceptIconButton;
import com.hmc.zenkai.client.gui.buttons.AtlasIconButton;
import com.hmc.zenkai.client.gui.buttons.BackIconButton;
import com.hmc.zenkai.client.gui.buttons.MinusIconButton;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.PlayIconButton;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.client.training.CuratedSong;
import com.hmc.zenkai.client.training.MeditationChart;
import com.hmc.zenkai.client.training.MeditationChartLoader;
import com.hmc.zenkai.client.training.MeditationDiscSound;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.training.MeditationSessionPacket;
import com.hmc.zenkai.feature.training.TrainingInfoRequestPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * "Meditation" (TrainingHubScreen): ritmo estilo FNF — 4 carriles (A/S/D/F), las notas caen
 * hacia una zona de impacto fija; acertar en ventana sube la racha, fallar la corta. Dos modos:
 *
 *  - PRÁCTICA LIBRE (por defecto): generador procedural (spawnea una nota en un carril al azar a
 *    intervalo fijo) — evita tener que autorar charts, duración elegible en OVERVIEW
 *    ({@link #DURATION_STEPS_SEC}).
 *  - MODO CANCIÓN: elige un disco vanilla curado ({@link CuratedSong}) y las notas siguen un
 *    chart real generado por tools/gen_meditation_chart.py (detección de onsets sobre el .ogg del
 *    disco) — la sesión dura lo que dura la canción, que suena de verdad ({@link SoundEvents}
 *    MUSIC_DISC_*) mientras se juega. La dificultad ES la canción elegida (Pigstep = difícil).
 *
 * Cuatro estados, SIEMPRE en el mismo orden (OVERVIEW -> SONG_LIST -> PLAYING -> RESULTS) —
 * SEGUNDO rediseño 2026-09-09, pedido explícito del usuario tras ver el primero en capturas: el
 * rediseño anterior metía la lista de canciones Y los steppers de dificultad/duración Y el TP
 * potencial en la MISMA pantalla (SONG_SELECT), lo que no escala a "bastantes discos" — con más
 * de 3-4 filas la pantalla se queda sin sitio para lo demás. Ahora:
 *  - OVERVIEW es la pantalla de resumen (antes "INTRO"): párrafo explicativo del minijuego, una
 *    fila clicable con la canción elegida ahora mismo (o "Free Practice"), los steppers de
 *    dificultad/duración SOLO si es Free Practice (una canción ya trae su propia dificultad y
 *    duración fijas, ver renderOverview()), TP potencial + récord, y Back/Start — este es el
 *    ÚNICO sitio con Start, y el ÚNICO que pide TrainingInfoPacket (ver requestInfo()).
 *  - SONG_LIST (antes "SONG_SELECT") es SOLO la lista, con scroll (ver visibleRows()/
 *    maxScroll()) para que quepan tantos discos como se añadan sin reventar el layout — pulsar
 *    una fila selecciona Y VUELVE a OVERVIEW en el mismo clic (selectRow()), no hace falta un
 *    segundo botón de confirmar. Back aquí vuelve a OVERVIEW sin tocar la selección.
 * PLAYING es el minijuego a pantalla completa, con un único botón "Finish" (esquina superior
 * izquierda) — Escape hace LO MISMO (ver onClose()/finishSessionEarly()): terminar YA y pasar a
 * RESULTS con el reward real, SIN PERDER lo ganado hasta ese punto. Antes Escape saltaba directo
 * al hub sin enseñar nada (queja explícita del usuario: "se salen y no terminan mostrando el TP
 * conseguido"), lo que hacía indistinguible salir con TP concedido de no haber jugado nada.
 * RESULTS enseña el reward REAL (no una estimación del cliente) + récord + Retry/Back.
 *
 * ANTI-TRAMPA: la sesión entera se reporta como desempeño CRUDO (notas acertadas + racha máxima
 * + duración), nunca como un TP ya calculado — el servidor decide cuánto vale eso
 * (MeditationSessionPacket) y responde con el TP REAL vía TrainingSessionRewardPacket
 * (ver TrainingMinigameScreen/ClientPayloadHandlers.onTrainingReward), que es lo que RESULTS
 * enseña — nunca una cifra adivinada en el cliente.
 *
 * `extends Screen` directamente (canvas propio) pero reusa el fondo/tamaño de panel de
 * ZenkaiMenuScreen (BG_TEX/BG_W/BG_H, protected + mismo paquete = accesible) para OVERVIEW/
 * SONG_LIST/RESULTS, sin heredar de ella — la fase PLAYING necesita la pantalla ENTERA para los
 * carriles, cosa que ZenkaiMenuScreen (con su barra de pestañas fija) no puede dar. Sigue el
 * convenio de orden de render de CLAUDE.md: renderBackground() pinta DEBAJO de super.render(), el
 * contenido va DESPUÉS.
 */
public class MeditationScreen extends Screen implements TrainingMinigameScreen {

    private enum State { OVERVIEW, SONG_LIST, PLAYING, RESULTS }

    private static final int LANES = 4;
    /** Códigos de tecla GLFW por carril — YA NO son una constante fija: pedido explícito del
     *  usuario ("un ajuste para poder cambiar las teclas a gusto del jugador", ver el popup de
     *  configuración/engranaje más abajo). Cargados de ClientConfig en init() y refrescados tras
     *  confirmar el popup — instancia, no static, porque el valor puede cambiar en caliente sin
     *  reabrir la pantalla. GLFW_KEY_A/S/D/F siguen siendo el default de ClientConfig. */
    private final int[] keys = new int[LANES];

    private static final long BASE_SPAWN_INTERVAL_MS = 450;
    private static final long TRAVEL_MS = 1400;
    /** Ventana de acierto: |ratio-1.0| <= esto se cuenta como golpe válido — 0.15 de TRAVEL_MS
     *  son ±210ms reales, en la banda alta de lo que usa Friday Night Funkin' (referencia
     *  explícita del usuario: "compara con FNF... a ver cómo funciona") para su ventana total de
     *  acierto (ahí ronda los ±166ms, pero FNF es un juego de ritmo dedicado; este es un
     *  minijuego de entrenamiento, de propósito más permisivo).
     *  FIJA para las dos dificultades — pedido explícito del usuario tras reportar que a
     *  dificultad alta "no lo detecta"/"lo toma como incorrecto": antes esto se ENCOGÍA con la
     *  dificultad (ver el `applyDifficulty()` viejo, clamp 0.06-0.22), así que un chart más
     *  denso también castigaba CADA nota individual con menos margen — doble penalización a la
     *  vez. FNF (y los juegos de ritmo en general) mantienen la ventana de acierto CONSTANTE
     *  sin importar la dificultad del chart: lo que cambia con la dificultad es la DENSIDAD de
     *  notas (`spawnIntervalMs`), nunca cuánto margen tiene cada nota individual — ver
     *  applyDifficulty(). */
    private static final double HIT_WINDOW = 0.15;

    /** Colchón de silencio tras la primera tecla, antes de que "empiece de verdad" la sesión
     *  (elapsed llega a 0) — pedido explícito del usuario tras un playtest real: en modo Canción,
     *  la primera nota del chart (timeMs≈0) se spawneaba YA vencida en el primer tick (sesión
     *  arrancaba en elapsed=0, y esa nota necesitaba TRAVEL_MS de aviso), así que caía en MISS
     *  casi seguro sin que el jugador tuviera ninguna oportunidad real. Al ser mayor que
     *  TRAVEL_MS, las notas más tempranas del chart tienen sus TRAVEL_MS completos de caída
     *  ANTES de sonar de verdad — ver actuallyStart()/tick(). Aplica a los dos modos por igual
     *  (pedido explícito), aunque Práctica Libre nunca tuvo el bug (su generador solo empieza a
     *  spawnear tras spawnIntervalMs desde el arranque real). */
    private static final long LEAD_IN_MS = 2000;

    /** Dificultad progresiva de PRÁCTICA LIBRE (pedido explícito del usuario, Pista C del plan
     *  de pulido de Training) — modo Canción NO usa esto, ahí la dificultad ES la canción
     *  elegida (ver startSongSession(), que resetea estos dos a su base). */
    private static final int[] STEPS_PCT = {50, 75, 100, 125, 150, 175, 200};
    private int stepIndex = 2; // arranca en 100%
    private long spawnIntervalMs = BASE_SPAWN_INTERVAL_MS;

    /** Duración de sesión de PRÁCTICA LIBRE, elegible en OVERVIEW (pedido explícito del usuario:
     *  "un tiempo configurable para que el jugador pueda conseguir recompensas") — modo Canción
     *  sigue sin usar esto, ahí la duración ES lo que dure el disco (ver sessionDurationMs()). */
    private static final int[] DURATION_STEPS_SEC = {15, 30, 45, 60, 90, 120};
    private int durationIndex = 1; // arranca en 30s, la duración fija que tenía antes

    private static final int LANE_W = 40;
    private static final int LANE_GAP = 8;
    /** Nota = cuadrado plano (fill + borde), no un sprite estirado — el sprite de corchea
     *  (ver tools/gen_training_gameplay_icons.py, fila v=120) se dibujaba deliberadamente ANCHO/
     *  BAJO (36x14 sobre una celda cuadrada de 20x20) y esa deformación se veía "achatada"
     *  (queja explícita del usuario con captura). Un cuadrado sin textura no tiene proporción
     *  que deformar, así que vuelve a la solución de fill() plano que ya usaban orbe/bomba de
     *  Ki Target Practice ANTES de que existieran esos sprites. */
    private static final int NOTE_SIZE = 22;
    private static final int NOTE_OUTLINE = 0xFF8C6014; // ámbar oscuro, mismo tono que el borde del sprite retirado
    private static final int HIT_ZONE_Y_OFFSET = 60; // desde el borde inferior

    /** Colchón mínimo entre dos notas seguidas del MISMO carril en modo Canción — defensa contra
     *  charts demasiado densos (tools/gen_meditation_chart.py es detección automática de onsets
     *  sin afinar a mano todavía, ver su javadoc de clase; Pigstep en particular amontona notas en
     *  el mismo carril, confirmado por captura real del usuario). Una nota que cae dentro de este
     *  margen del anterior golpe del mismo carril simplemente NO se spawnea — nunca penaliza al
     *  jugador (una nota que no aparece no puede fallarse) y no toca los archivos de chart. */
    private static final long MIN_LANE_GAP_MS = 150;
    private final long[] lastLaneNoteMs = new long[LANES];

    /** Mismo filtro anti-amontonamiento que MIN_LANE_GAP_MS, pero para el generador ALEATORIO
     *  de Práctica libre (que nunca lo tuvo) y con SU PROPIO umbral, calculado en vez de copiado:
     *  el mínimo real para que dos notas del MISMO carril nunca terminen con ventanas de acierto
     *  solapadas es 2×HIT_WINDOW·TRAVEL_MS. Antes de ensanchar HIT_WINDOW (pedido explícito del
     *  usuario, comparación con FNF) esto no hacía falta porque la ventana era más estrecha que
     *  el hueco típico entre spawns aleatorios del mismo carril; con la ventana más ancha, dos
     *  notas del mismo carril podían caer más juntas que 2×HIT_WINDOW a dificultad alta
     *  (spawnIntervalMs bajo) y robarse el acierto entre ellas. Reusa lastLaneNoteMs (ya existía
     *  para el modo Canción) — nunca penaliza al jugador, una nota que no aparece no puede
     *  fallarse. */
    private static final long FREE_PRACTICE_MIN_LANE_GAP_MS = Math.round(2 * HIT_WINDOW * TRAVEL_MS);

    // El beige real de common_screen.png va de x=12 a x=244 (muestreado píxel a píxel, ver
    // TrainingHubScreen) — 10/245 se metían 2px dentro del marco por cada lado.
    private static final int IN_X1 = 12;
    private static final int IN_X2 = 243;
    private static final int SONG_ROW_H = 24;
    private static final int SONG_ROW_GAP = 6;

    /** Ícono de la fila "Free Practice" (fila-resumen de OVERVIEW y SONG_LIST): la MISMA celda
     *  de icons.png que ya usa la fila "Meditation" del hub (TrainingHubScreen.ICON_MEDITATION_U/
     *  V) — no hay ítem que renderizar (no es un disco), así que se blitea el ícono del atlas en
     *  su lugar. */
    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICON_FREE_PRACTICE_U = 160, ICON_FREE_PRACTICE_V = 80;
    private static final int ICON_CELL = 20;
    private static final int ICONS_ATLAS = 256;

    /** SONG_LIST: canalón de scroll reservado SIEMPRE a la derecha de cada fila (con scroll o
     *  sin él, mismo criterio que TechniquesScreen.CONTENT_INSET/rightEdge() — así una fila no
     *  cambia de ancho el día que haya más discos de los que caben sin scroll). */
    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_GUTTER = 8;
    private static final int LIST_TOP = 30;
    private static final int LIST_BOTTOM = ZenkaiMenuScreen.BG_H - 12 - PanelButton.H - 8;

    private record Note(int lane, long spawnMs) {}

    private final Random random = new Random();
    private final List<Note> notes = new ArrayList<>();

    private State state = State.OVERVIEW;
    private long sessionStartMs = -1;
    private long lastSpawnMs = -1;
    private int notesHit = 0;
    private int combo = 0;
    private int maxCombo = 0;
    /** Desglose por tier de cada golpe/fallo — pedido explícito del usuario ("precisión de 0 a
     *  100% como en FNF... si son SICK son perfectos"). Por construcción, notesHit ==
     *  sickCount+goodCount+okCount siempre (un golpe cae en exactamente un tier, ver
     *  addHitJudgment()) — se reportan los 4 por separado al servidor (MeditationSessionPacket)
     *  en vez de un % ya calculado, mismo principio anti-trampa que el resto del reporte. */
    private int sickCount = 0;
    private int goodCount = 0;
    private int okCount = 0;
    private int missCount = 0;
    private boolean reported = false;
    private Integer resultReward = null;
    private Integer resultRecord = null;

    /** Récord/potencial mostrados en OVERVIEW (TrainingInfoPacket), null hasta que responde el
     *  servidor — ver requestInfo(). A diferencia del primer rediseño, esto SÍ se recalcula al
     *  cambiar de canción o de duración de Práctica libre (el techo de sesión escala con la
     *  duración real, ver TrainingHooks.SESSION_CAP_BASELINE_TICKS), así el número que ve el
     *  jugador antes de jugar coincide con el que puede ganar de verdad. */
    private Integer introRecord = null;
    private Integer introPotential = null;

    /** null = Práctica libre (generador aleatorio). No null = modo Canción, alimentado por este
     *  chart en vez de random.nextInt(LANES) — ver tick(). */
    private MeditationChart activeChart;
    private CuratedSong activeSong;
    private int chartCursor;
    private MeditationDiscSound discSound;

    /** Volumen ORIGINAL de la categoría MUSIC de Opciones antes de silenciarla para el modo
     *  Canción — ver muteVanillaMusic()/restoreVanillaMusic(). -1 = no está silenciada ahora
     *  mismo (evita restaurar dos veces o restaurar sin haber muteado). */
    private float savedMusicVolume = -1f;

    /** Cargados UNA VEZ en init() (antes se cargaban al abrir la lista) — OVERVIEW necesita
     *  saber la duración real del disco elegido para requestInfo() sin esperar a que el jugador
     *  visite SONG_LIST primero. */
    private Map<String, MeditationChart> songCharts;
    private List<CuratedSong> songRows;

    private record Judgment(int lane, String text, int color, long shownAtMs) {}
    private final List<Judgment> judgments = new ArrayList<>();
    private static final long JUDGMENT_LIFETIME_MS = 500;

    /** Timestamp hasta el que cada carril debe pintarse resaltado (verde=acierto, rojo=fallo) en
     *  vez del fondo neutro — pura cosmética, ver renderPlaying(). */
    private final long[] laneGoodUntil = new long[LANES];
    private final long[] laneBadUntil = new long[LANES];

    private int panelLeft, panelTop;

    /** Canción elegida (no confundir con `activeSong`, la sesión YA en marcha): null = Free
     *  Practice, la selección por defecto al entrar. Decide qué muestra OVERVIEW (steppers vs.
     *  dificultad/duración fijas de la canción) y qué arranca el botón de play — ver
     *  renderOverview()/beginSelected(). */
    @Nullable private CuratedSong selectedSong;

    // ── Layout de OVERVIEW, medido UNA VEZ en init() a partir del párrafo real (varía por
    // idioma) — mismo gotcha ya documentado en ShadowTrainingScreen/TargetPracticeScreen: reservar
    // una franja FIJA para TP potencial/récord aunque el packet no haya respondido todavía, para
    // que los botones no salten un frame después de que llegue. ────────────────────────────────
    private List<FormattedCharSequence> overviewDescLines;
    private int descY;
    private int songRowY;
    private int stepperY;
    private int infoY;

    /** SONG_LIST: primera fila visible (0 = Free Practice, 1..N = songRows). */
    private int scrollRow = 0;

    /** true = la sesión ya arrancó (beginPlaying) pero espera a que el jugador pulse una tecla
     *  para que empiecen a caer notas/sonar la canción — pedido explícito del usuario ("hasta
     *  que el jugador no pulse... no comience la canción"). Aplica a los dos modos por igual
     *  (más simple que gatear solo Canción, y Práctica libre tampoco pierde nada por dar un
     *  respiro antes de la primera nota). */
    private boolean waitingForStart;

    // ── Popup de reasignación de teclas (icons.png 80,60), OVERVIEW únicamente ─────────────────
    /** true = el popup está abierto — mientras tanto Back/Start/steppers se ocultan (mismo
     *  criterio que PartyScreen.configOpen) y el clic/tecla se enruta al popup en vez de al
     *  resto de la pantalla. */
    private boolean keybindPopupOpen = false;
    /** Copia de trabajo de `keys` mientras el popup está abierto — Cancelar la descarta,
     *  Confirmar la vuelca a ClientConfig Y a `keys`. Nunca se edita `keys` directamente desde
     *  el popup para que Cancelar pueda deshacer de verdad. */
    private final int[] pendingKeys = new int[LANES];
    /** Carril esperando la PRÓXIMA tecla física para reasignarse, o -1 si ninguno — ver
     *  keyPressed(). */
    private int awaitingLane = -1;

    public MeditationScreen() {
        super(Component.translatable("screen.zenkai.training_hub.row.meditation"));
    }

    @Override
    protected void init() {
        panelLeft = (this.width - ZenkaiMenuScreen.BG_W) / 2;
        panelTop = (this.height - ZenkaiMenuScreen.BG_H) / 2;

        for (int i = 0; i < LANES; i++) keys[i] = ClientConfig.meditationLaneKey(i);

        songCharts = MeditationChartLoader.loadAll();
        // Ordenado por dificultad (EASY -> MEDIUM -> HARD, el orden de declaración del enum
        // Difficulty) — pedido explícito del usuario. sorted() es estable: dentro de la MISMA
        // dificultad, las canciones conservan el orden de declaración de CuratedSong (los 3
        // curados a mano primero, luego los 16 nuevos), sin necesidad de un segundo criterio.
        songRows = Arrays.stream(CuratedSong.values())
                .filter(s -> songCharts.containsKey(s.discId))
                .sorted(Comparator.comparing(s -> s.difficulty))
                .toList();
        selectedSong = null; // Free Practice, la selección por defecto al entrar

        overviewDescLines = this.font.split(
                Component.translatable("screen.zenkai.meditation.overview"), ZenkaiMenuScreen.BG_W - 24);
        descY = 26;
        songRowY = descY + overviewDescLines.size() * 10 + 8;
        stepperY = songRowY + SONG_ROW_H + 10;
        // TRES líneas SIEMPRE reservadas aquí (difficulty + etiqueta "Session length" + su valor
        // en Free Practice, o dos líneas de solo lectura + una vacía con una canción elegida —
        // ver renderOverview()) — el mismo total en los dos casos para que "TP potencial"/
        // "Record" no salten de sitio al cambiar de selección.
        infoY = stepperY + 48;

        buildOverviewWidgets();
        requestInfo();
    }

    /** (Re)pide récord + TP potencial — al entrar Y cada vez que cambia algo que afecta el techo
     *  de sesión: duración de Práctica libre, la canción elegida (cada disco dura lo suyo), Y
     *  AHORA TAMBIÉN la dificultad de Práctica libre — dejó de ser inofensiva para este número
     *  el día que TrainingHooks.meditationAchievableRawTp() empezó a depender de ella (más
     *  difícil = notas más seguidas = más notas caben en la misma duración = techo más alto). */
    private void requestInfo() {
        introRecord = null;
        introPotential = null;
        int durationTicks;
        float difficultyFraction;
        if (selectedSong != null && songCharts.containsKey(selectedSong.discId)) {
            durationTicks = (int) (songCharts.get(selectedSong.discId).durationMs() / 50);
            // -1 = modo Canción: sin chart en servidor, no hay segundo techo que calcular ahí
            // (ver el javadoc de TrainingInfoRequestPacket) — el plano ya escalado por la
            // duración real de la canción es la mejor aproximación disponible.
            difficultyFraction = -1f;
        } else {
            durationTicks = DURATION_STEPS_SEC[durationIndex] * 20;
            difficultyFraction = STEPS_PCT[stepIndex] / 100f;
        }
        PacketDistributor.sendToServer(
                new TrainingInfoRequestPacket(TrainingInfoRequestPacket.MEDITATION, durationTicks, difficultyFraction));
    }

    /** OVERVIEW: Back/Start siempre presentes, más los steppers de dificultad/duración SOLO si
     *  la selección actual es Free Practice (una canción ya trae su propia dificultad/duración
     *  fijas, ver renderOverview()) — reconstruir en cada cambio de selección (selectRow()) en
     *  vez de ocultar/desactivar widgets ya creados, mismo idioma que TechniqueEditScreen.
     *  switchTab(). También sirve como target de "Back" desde SONG_LIST (ver openSongList()).
     *  El engranaje de reasignar teclas (icons.png 80,60, centrado en la fila de Back/Start) va
     *  SIEMPRE, incluso con el popup abierto — un segundo clic lo cierra, mismo gesto que
     *  PartyScreen.config — y mientras está abierto oculta Back/Start/steppers (mismo criterio
     *  que PartyScreen.configOpen: un popup modal no convive con más controles interactivos). */
    private void buildOverviewWidgets() {
        state = State.OVERVIEW;
        this.clearWidgets();
        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;

        AtlasIconButton keysBtn = new AtlasIconButton(
                panelLeft + ZenkaiMenuScreen.BG_W / 2 - 10, y + (PanelButton.H - 20) / 2, 80, 60,
                this::toggleKeybindPopup);
        keysBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.meditation.keybinds.tooltip")));
        addRenderableWidget(keysBtn);

        if (keybindPopupOpen) {
            initKeybindPopupWidgets();
            return;
        }

        BackIconButton backBtn = new BackIconButton(
                panelLeft + IN_X1, y + (PanelButton.H - 20) / 2, 20, this::onClose);
        backBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.back")));
        addRenderableWidget(backBtn);

        PlayIconButton startBtn = new PlayIconButton(
                panelLeft + IN_X2 - 24, y + (PanelButton.H - 24) / 2, 20, this::beginSelected);
        startBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.training_hub.shadow.start")));
        addRenderableWidget(startBtn);

        if (selectedSong == null) {
            int cx = panelLeft + ZenkaiMenuScreen.BG_W / 2;
            addRenderableWidget(new MinusIconButton(cx - 60, panelTop + stepperY, this::decreaseDifficulty));
            addRenderableWidget(new PlusIconButton(cx + 48, panelTop + stepperY, this::increaseDifficulty));
            // Duración de la sesión de Práctica libre (no aplica a modo Canción, ver
            // DURATION_STEPS_SEC) — SU PROPIA línea de etiqueta arriba (sin botones, renderOverview())
            // y los botones en la línea de abajo (+32, no +20): "Session length: 30s" combinado en
            // una sola línea centrada se montaba con el botón + (bug real, captura del usuario;
            // "Difficulty: 100%" es más corto y cabía, "Session length: 30s" no) — mismo arreglo
            // que "Opponent form" en ShadowTrainingScreen, ver su javadoc de clase.
            addRenderableWidget(new MinusIconButton(cx - 60, panelTop + stepperY + 32, this::decreaseDuration));
            addRenderableWidget(new PlusIconButton(cx + 48, panelTop + stepperY + 32, this::increaseDuration));
        }
    }

    private void toggleKeybindPopup() {
        keybindPopupOpen = !keybindPopupOpen;
        if (keybindPopupOpen) {
            awaitingLane = -1;
            System.arraycopy(keys, 0, pendingKeys, 0, LANES);
        }
        buildOverviewWidgets();
    }

    // ── Layout del popup de reasignación de teclas — mismo criterio que PartyScreen.PartyConfig
    // (popup oscuro FUERA del panel, a su izquierda) y StatsScreen.renderPopup: popupLeft()/
    // popupTop() son la ÚNICA fuente de esta posición, compartida por el builder de widgets, el
    // render del fondo, el del contenido y el hit-test de "clic fuera cierra". ─────────────────
    private static final int KEYBIND_POPUP_W = 140;
    private static final int KEYBIND_POPUP_H = 132;
    private static final int KEYBIND_POPUP_GAP = 8;
    private static final int KEYBIND_ROW_H = 16;

    private int keybindPopupLeft() {
        return Mth.clamp(
                panelLeft - KEYBIND_POPUP_W - KEYBIND_POPUP_GAP, 2, this.width - KEYBIND_POPUP_W - 2);
    }

    private int keybindPopupTop() { return panelTop + 20; }

    private int keybindRowTop(int lane) { return keybindPopupTop() + 24 + lane * KEYBIND_ROW_H; }

    /** Botones del popup: Confirmar/Cancelar (checkmark verde / X roja de icons.png, pedido
     *  explícito del usuario, celdas 0,60/20,60 — ver AcceptIconButton/BackIconButton). Las 4
     *  filas de carril NO son widgets — se dibujan y se hit-testean por coordenadas
     *  (renderKeybindPopup()/clickKeybindRow()), mismo criterio que el resto de filas
     *  clicables de esta clase (nunca reejecutar el render como efecto secundario de un clic). */
    private void initKeybindPopupWidgets() {
        int cx = keybindPopupLeft() + KEYBIND_POPUP_W / 2;
        int py = keybindPopupTop();

        BackIconButton cancel = new BackIconButton(cx - 12 - 18, py + KEYBIND_POPUP_H - 26, 16,
                () -> { keybindPopupOpen = false; buildOverviewWidgets(); });
        cancel.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.gui.cancel")));
        addRenderableWidget(cancel);

        AcceptIconButton confirm = new AcceptIconButton(cx + 18, py + KEYBIND_POPUP_H - 26, 16,
                () -> {
                    System.arraycopy(pendingKeys, 0, keys, 0, LANES);
                    ClientConfig.setMeditationLaneKeys(keys[0], keys[1], keys[2], keys[3]);
                    keybindPopupOpen = false;
                    buildOverviewWidgets();
                });
        confirm.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.gui.confirm")));
        addRenderableWidget(confirm);
    }

    private void decreaseDifficulty() { stepIndex = Math.max(0, stepIndex - 1); requestInfo(); }
    private void increaseDifficulty() { stepIndex = Math.min(STEPS_PCT.length - 1, stepIndex + 1); requestInfo(); }

    private void decreaseDuration() { durationIndex = Math.max(0, durationIndex - 1); requestInfo(); }
    private void increaseDuration() { durationIndex = Math.min(DURATION_STEPS_SEC.length - 1, durationIndex + 1); requestInfo(); }

    /** Más difícil = notas más seguidas y ventana de acierto más estrecha — los dos números que
     *  ya hacían de "perilla" implícita de Práctica libre, ahora escalados por el stepper en vez
     *  de fijos. TRAVEL_MS se queda constante a propósito (la velocidad de caída no cambia, solo
     *  cuánto margen de error y cuántas notas hay que leer). */
    private void applyDifficulty() {
        double fraction = STEPS_PCT[stepIndex] / 100.0;
        spawnIntervalMs = clampLong(Math.round(BASE_SPAWN_INTERVAL_MS / fraction), 220, 900);
        // HIT_WINDOW ya NO escala aquí — ver su javadoc (comparación con FNF, pedido explícito
        // del usuario). La dificultad solo cambia la densidad de notas.
    }

    private static long clampLong(long v, long min, long max) { return Math.max(min, Math.min(max, v)); }

    /** SONG_LIST: solo la lista + Back (vuelve a OVERVIEW sin tocar la selección). Pulsar una
     *  fila selecciona Y VUELVE a OVERVIEW en el mismo clic (ver selectRow()/clickSongRow()) —
     *  no hace falta un botón de confirmar aparte. */
    private void openSongList() {
        state = State.SONG_LIST;
        scrollRow = 0;
        this.clearWidgets();
        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;
        BackIconButton backBtn = new BackIconButton(
                panelLeft + IN_X1, y + (PanelButton.H - 20) / 2, 20, this::buildOverviewWidgets);
        backBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.back")));
        addRenderableWidget(backBtn);
    }

    private void selectRow(@Nullable CuratedSong song) {
        selectedSong = song;
        requestInfo();
        buildOverviewWidgets();
    }

    private void beginSelected() {
        if (selectedSong == null) startFreePractice();
        else startSongSession(selectedSong);
    }

    private void buildResultsWidgets() {
        this.clearWidgets();
        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;
        addRenderableWidget(PanelButton.secondary(panelLeft + IN_X1, y,
                Component.translatable("screen.zenkai.back"), this::onClose));
        // Retry repite el MISMO modo que se acaba de jugar — si fue modo Canción, reintentar no
        // debe caer silenciosamente en Práctica libre (activeSong sigue apuntando a la canción
        // hasta la próxima startFreePractice/startSongSession).
        Runnable retry = activeSong != null ? () -> startSongSession(activeSong) : this::startFreePractice;
        addRenderableWidget(PanelButton.primary(panelLeft + IN_X2 - PanelButton.W, y,
                Component.translatable("screen.zenkai.meditation.retry"), retry));
    }

    private void startFreePractice() {
        activeChart = null;
        activeSong = null;
        applyDifficulty();
        beginPlaying();
    }

    private void startSongSession(CuratedSong song) {
        activeSong = song;
        activeChart = songCharts.get(song.discId);
        chartCursor = 0;
        // La dificultad ES la canción elegida, no el stepper de Práctica libre — resetear a base
        // por si el jugador vino de jugar Práctica libre en difícil y luego elige una canción.
        // HIT_WINDOW ya es una constante fija, no hace falta resetearla (ver su javadoc).
        spawnIntervalMs = BASE_SPAWN_INTERVAL_MS;
        beginPlaying();
    }

    /** Categoría RECORDS (MeditationDiscSound) en vez de MUSIC a propósito: así se puede apagar
     *  la música ambiente vanilla SIN silenciar el propio disco — queja explícita del usuario
     *  ("se escuchan sobreescuchadas las 2"). Restaurado siempre en stopSongIfAny(), llamado
     *  tanto desde onClose() como desde removed() (ver su javadoc) para que sobreviva a
     *  cualquier forma de cerrar la pantalla, no solo Back/Escape. */
    private void muteVanillaMusic() {
        if (savedMusicVolume >= 0f) return; // ya muteada, no pisar el valor guardado
        var musicOption = Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        savedMusicVolume = musicOption.get().floatValue();
        musicOption.set(0.0);
    }

    private void restoreVanillaMusic() {
        if (savedMusicVolume < 0f) return;
        Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MUSIC)
                .set((double) savedMusicVolume);
        savedMusicVolume = -1f;
    }

    private void beginPlaying() {
        state = State.PLAYING;
        this.clearWidgets();
        notes.clear();
        judgments.clear();
        Arrays.fill(laneGoodUntil, 0L);
        Arrays.fill(laneBadUntil, 0L);
        notesHit = 0;
        combo = 0;
        maxCombo = 0;
        sickCount = 0;
        goodCount = 0;
        okCount = 0;
        missCount = 0;
        // Long.MIN_VALUE/2, NO Long.MIN_VALUE: el filtro hace `n.timeMs() - lastLaneNoteMs[...]`,
        // y timeMs() - Long.MIN_VALUE desborda un long (se convierte en un negativo enorme, así
        // que el filtro creería que la PRIMERA nota de cada carril está "demasiado cerca" y la
        // descartaría). /2 deja margen de sobra sin arriesgar el desbordamiento.
        Arrays.fill(lastLaneNoteMs, Long.MIN_VALUE / 2);
        reported = false;
        resultReward = null;
        resultRecord = null;
        // No arranca el cronómetro/las notas/la canción todavía — ver actuallyStart(), disparado
        // por la primera tecla que pulse el jugador (keyPressed()).
        waitingForStart = true;
        sessionStartMs = -1;
        lastSpawnMs = -1;
    }

    /** Disparado por la primera tecla tras entrar en PLAYING (ver keyPressed()) — pedido
     *  explícito del usuario: hasta este momento no cae ninguna nota ni suena la canción, solo
     *  se ve el prompt "PRESS ANY KEY TO START" (renderPlaying()). El arranque REAL (elapsed=0)
     *  no es este instante, sino LEAD_IN_MS después (ver su javadoc) — sessionStartMs ya queda
     *  desplazado al futuro, así que elapsed empieza en -LEAD_IN_MS y el resto de tick() no
     *  necesita saber nada de este colchón. La música ambiente vanilla SÍ se calla ya aquí (así
     *  el colchón es silencio de verdad); el disco se reproduce más tarde, en tick(), justo
     *  cuando elapsed cruza 0 (ver ahí el porqué). */
    private void actuallyStart() {
        waitingForStart = false;
        sessionStartMs = System.currentTimeMillis() + LEAD_IN_MS;
        lastSpawnMs = sessionStartMs;
        if (activeChart != null) muteVanillaMusic();

        // Botón "Finish" (pedido explícito del usuario tras la queja de que ESC salía sin
        // enseñar el TP conseguido): visible desde que la sesión arranca de verdad, NO durante
        // "PRESS ANY KEY TO START" (nada que finalizar todavía). Reusa BackIconButton por pedido
        // explícito ("usando el mismo BackIconButton") aunque la acción sea distinta de Back —
        // sigue siendo "salir de esta sesión", el icono ya comunica eso. Esquina superior
        // izquierda: lanesLeft() deja ese hueco libre de sobra en cualquier resolución razonable
        // (ver su fórmula), combo/accuracy se dibujan pegados a la izquierda de los carriles, no
        // del borde de la pantalla.
        BackIconButton finishBtn = new BackIconButton(8, 8, 16, this::finishSessionEarly);
        finishBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.training_hub.finish")));
        addRenderableWidget(finishBtn);
    }

    /** Termina la sesión YA (botón Finish o Escape, ver onClose()) y pasa a RESULTS con el reward
     *  REAL — mismo camino que una sesión completada del todo (tick() al agotar la duración),
     *  nunca el "silencio" que antes solo devolvía al hub sin enseñar nada. Único sitio que sabe
     *  terminar una sesión a medias, para que los dos disparadores no diverjan. */
    private void finishSessionEarly() {
        if (state != State.PLAYING || waitingForStart) return;
        stopSongIfAny();
        sendSessionReport();
        state = State.RESULTS;
        buildResultsWidgets();
    }

    private void stopSongIfAny() {
        if (discSound != null) {
            Minecraft.getInstance().getSoundManager().stop(discSound);
            discSound = null;
        }
        restoreVanillaMusic();
    }

    private long sessionDurationMs() {
        return activeChart != null ? activeChart.durationMs() : DURATION_STEPS_SEC[durationIndex] * 1000L;
    }

    @Override
    public void tick() {
        super.tick();
        if (state != State.PLAYING || waitingForStart) return;
        long now = System.currentTimeMillis();
        long elapsed = now - sessionStartMs;

        if (elapsed >= sessionDurationMs()) {
            finishSessionEarly();
            return;
        }

        // El disco arranca justo cuando el colchón de LEAD_IN_MS termina (elapsed cruza 0), no en
        // el instante de la tecla (ver actuallyStart()) — así coincide exactamente con el momento
        // en que las notas pre-spawneadas durante el colchón llegan a la línea de acierto.
        if (activeChart != null && discSound == null && elapsed >= 0) {
            discSound = new MeditationDiscSound(activeSong.sound.value(), 1.0f);
            Minecraft.getInstance().getSoundManager().play(discSound);
        }

        if (activeChart != null) {
            List<MeditationChart.MeditationNote> chartNotes = activeChart.notes();
            // La nota debe APARECER TRAVEL_MS antes de su instante de golpe real, para que
            // llegue a la zona de impacto exactamente cuando suena — mismo mecanismo de
            // carriles/tiempo de viaje que Práctica libre, solo cambia CUÁNDO se decide spawnear.
            while (chartCursor < chartNotes.size()
                    && chartNotes.get(chartCursor).timeMs() - TRAVEL_MS <= elapsed) {
                var n = chartNotes.get(chartCursor);
                // Filtro anti-amontonamiento: si el chart mete dos notas del MISMO carril más
                // juntas de lo humanamente jugable (ver MIN_LANE_GAP_MS), la segunda simplemente
                // no se spawnea — nunca penaliza al jugador, ninguna nota "perdida" cuenta como
                // fallo porque nunca llegó a existir.
                if (n.timeMs() - lastLaneNoteMs[n.lane()] >= MIN_LANE_GAP_MS) {
                    notes.add(new Note(n.lane(), sessionStartMs + n.timeMs() - TRAVEL_MS));
                    lastLaneNoteMs[n.lane()] = n.timeMs();
                }
                chartCursor++;
            }
        } else if (now - lastSpawnMs >= spawnIntervalMs) {
            lastSpawnMs = now;
            int lane = random.nextInt(LANES);
            // Ver FREE_PRACTICE_MIN_LANE_GAP_MS: descarta el spawn si caería con la ventana de
            // acierto solapada a la última nota de ESTE carril (nunca penaliza — una nota que no
            // aparece no puede fallarse).
            if (now - lastLaneNoteMs[lane] >= FREE_PRACTICE_MIN_LANE_GAP_MS) {
                notes.add(new Note(lane, now));
                lastLaneNoteMs[lane] = now;
            }
        }

        // Notas que ya pasaron la ventana de acierto sin pulsarse: fallo, corta la racha.
        notes.removeIf(n -> {
            double ratio = (now - n.spawnMs()) / (double) TRAVEL_MS;
            if (ratio > 1.0 + HIT_WINDOW) {
                if (activeChart != null && discSound != null) {
                    discSound.duck(220); // el disco "se apaga un poco" — ver MeditationDiscSound
                } else if (combo > 0) {
                    playMiss(); // Práctica libre: sin música real que clashee, sí lleva pitido
                }
                // Un solo judgment vivo por carril: si ya había uno (ej. dos MISS seguidas antes
                // de que se apague el anterior), se sustituye en vez de apilarse ilegible encima
                // (bug real visto en captura: dos "MISS" superpuestas en el mismo carril).
                judgments.removeIf(j -> j.lane() == n.lane());
                judgments.add(new Judgment(n.lane(), "MISS", ZenkaiPalette.ERROR, now));
                laneBadUntil[n.lane()] = now + 150;
                combo = 0;
                missCount++;
                return true;
            }
            return false;
        });
        judgments.removeIf(j -> now - j.shownAtMs() > JUDGMENT_LIFETIME_MS);
    }

    private void sendSessionReport() {
        if (reported) return;
        reported = true;
        long durationTicks = Math.max(1, Math.round(
                (System.currentTimeMillis() - sessionStartMs) / 50.0));
        PacketDistributor.sendToServer(new MeditationSessionPacket(
                sickCount, goodCount, okCount, missCount, maxCombo, (int) durationTicks));
    }

    @Override
    public void onRewardReceived(int tpGranted, int record) {
        resultReward = tpGranted;
        resultRecord = record;
    }

    @Override
    public void onTrainingInfoReceived(int record, int potentialTp) {
        introRecord = record;
        introPotential = potentialTp;
    }

    @Override
    public void onClose() {
        // Escape a mitad de partida ahora pasa por RESULTS igual que el botón Finish (pedido
        // explícito del usuario: "cuando se pulsa ESC se salen y no terminan mostrando el TP
        // conseguido... para evitar confusiones") — antes reportaba lo hecho pero saltaba directo
        // al hub SIN enseñar el reward, así que salir con ESC se sentía indistinguible de "no
        // pasó nada" aunque sí se hubiera concedido TP de verdad. waitingForStart (aún en "PRESS
        // ANY KEY TO START") sigue saliendo directo: no hay nada que reportar todavía.
        if (state == State.PLAYING && !waitingForStart) {
            finishSessionEarly();
            return;
        }
        Minecraft.getInstance().setScreen(new TrainingHubScreen());
    }

    /** Red de seguridad de removed(): Screen.removed() lo llama Minecraft.setScreen() SIEMPRE
     *  que esta pantalla deja de ser la activa, pase lo que pase (Back, Escape, otro código que
     *  reemplace la pantalla sin pasar por onClose) — a diferencia de onClose(), que solo corre
     *  si el cierre fue "normal". Sin esto, salir de Meditation de cualquier otra forma dejaba el
     *  disco sonando y la música vanilla muteada para siempre. onClose() ya llama a
     *  Minecraft.setScreen() de todos modos, así que esto también cubre ese camino — llamar
     *  stopSongIfAny() dos veces es inofensivo (discSound/savedMusicVolume quedan en su estado
     *  "ya limpio" tras la primera). */
    @Override
    public void removed() {
        stopSongIfAny();
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state == State.OVERVIEW && keybindPopupOpen) {
            if (awaitingLane >= 0) {
                // ESCAPE deja de esperar SIN reasignar (arrepentirse de reasignar esta fila
                // concreta), no cierra el popup entero — un segundo Escape sí lo cierra (ver la
                // rama de abajo la próxima vez que se pulse). Cualquier otra tecla ya en uso por
                // OTRO carril se ignora en silencio: nunca dos carriles con la misma tecla.
                if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                    boolean taken = false;
                    for (int i = 0; i < LANES; i++) {
                        if (i != awaitingLane && pendingKeys[i] == keyCode) { taken = true; break; }
                    }
                    if (!taken) pendingKeys[awaitingLane] = keyCode;
                }
                awaitingLane = -1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                keybindPopupOpen = false;
                buildOverviewWidgets();
                return true;
            }
            return true; // popup abierto: ninguna otra tecla debe colarse al resto de la pantalla
        }

        if (state == State.PLAYING) {
            // ESCAPE no cuenta como "cualquier tecla" para arrancar: sin este caso especial, un
            // jugador que se arrepiente justo en el prompt "PRESS ANY KEY TO START" y pulsa Esc
            // esperando volver al hub arrancaba la sesión por accidente en su lugar — Esc cae a
            // super.keyPressed(), que es quien de verdad gestiona el cierre (llama a onClose()).
            if (waitingForStart && keyCode != GLFW.GLFW_KEY_ESCAPE) {
                actuallyStart();
                return true;
            }
            for (int lane = 0; lane < LANES; lane++) {
                if (keyCode == keys[lane]) {
                    tryHit(lane);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Nombre legible de una tecla física (p.ej. "A", "Space", "F5") — mismo mecanismo que usa
     *  la pantalla vainilla de Controles, EXCEPTO las 4 flechas: vainilla las localiza como
     *  texto ("Up"/"Down"/"Left"/"Right" en en_us), y pedido explícito del usuario ("que se vean
     *  los símbolos y no el 'Key Up' etc") las sustituye por el glifo real (↑↓←→) — el mismo
     *  idioma que ya usa `AppearanceScreen` con "‹" para su botón Back, así que el font del mod
     *  ya renderiza Unicode fuera de ASCII sin problema. Usado tanto en el carril durante
     *  PLAYING como en las filas del popup de reasignación. */
    private static Component keyLabel(int glfwKeyCode) {
        return switch (glfwKeyCode) {
            case GLFW.GLFW_KEY_UP -> Component.literal("↑");
            case GLFW.GLFW_KEY_DOWN -> Component.literal("↓");
            case GLFW.GLFW_KEY_LEFT -> Component.literal("←");
            case GLFW.GLFW_KEY_RIGHT -> Component.literal("→");
            default -> InputConstants.Type.KEYSYM.getOrCreate(glfwKeyCode).getDisplayName();
        };
    }

    private void tryHit(int lane) {
        long now = System.currentTimeMillis();
        Note best = null;
        double bestDelta = Double.MAX_VALUE;
        for (Note n : notes) {
            if (n.lane() != lane) continue;
            double ratio = (now - n.spawnMs()) / (double) TRAVEL_MS;
            double delta = Math.abs(ratio - 1.0);
            if (delta <= HIT_WINDOW && delta < bestDelta) {
                best = n;
                bestDelta = delta;
            }
        }
        if (best != null) {
            notes.remove(best);
            notesHit++;
            combo++;
            maxCombo = Math.max(maxCombo, combo);
            addHitJudgment(lane, bestDelta);
            // Solo en Práctica libre: en modo Canción el disco YA es el feedback musical, un
            // pitido de arpa encima sonaría como "otra música aparte" (queja explícita del
            // usuario) — ahí el acierto simplemente no interrumpe la canción, el fallo sí (duck).
            if (activeChart == null) playHit();
        }
        // Pulsar sin nota en ventana no rompe la racha a propósito: castigar solo dejar pasar
        // una nota (el "miss" real), no un roce de tecla de más en un ritmo de 4 carriles.
    }

    /** Clasificación estilo FNF por precisión del golpe (SICK/GOOD/OK) — pedido explícito del
     *  usuario ("no hay marcadores tipo good, sick, etc. por tecla"). Los umbrales son fracciones
     *  de HIT_WINDOW, no valores absolutos, así que escalan solos si esa ventana cambia. */
    private void addHitJudgment(int lane, double delta) {
        String text;
        int color;
        if (delta <= HIT_WINDOW * 0.35) {
            text = "SICK!";
            color = ZenkaiPalette.VALUE;
            sickCount++;
        } else if (delta <= HIT_WINDOW * 0.7) {
            text = "GOOD";
            color = ZenkaiPalette.OK;
            goodCount++;
        } else {
            text = "OK";
            color = ZenkaiPalette.TEXT;
            okCount++;
        }
        // Un solo judgment vivo por carril — ver el mismo comentario en la rama de MISS de tick().
        judgments.removeIf(j -> j.lane() == lane);
        judgments.add(new Judgment(lane, text, color, System.currentTimeMillis()));
        laneGoodUntil[lane] = System.currentTimeMillis() + 150;
    }

    /** % de precisión estilo FNF (SICK = perfecto) — pedido explícito del usuario, y factor real
     *  del TP otorgado, no solo cosmético (ver el mismo cálculo, con las mismas constantes de
     *  peso, en MeditationSessionPacket.handle()). En el caso honesto el número que ve el jugador
     *  aquí coincide exactamente con el factor que aplicó el servidor; solo puede divergir si el
     *  servidor tuvo que recortar un reporte imposible (cliente modificado). */
    private int accuracyPercent() {
        int judged = sickCount + goodCount + okCount + missCount;
        if (judged == 0) return 100;
        double weighted = sickCount * MeditationSessionPacket.WEIGHT_SICK
                + goodCount * MeditationSessionPacket.WEIGHT_GOOD
                + okCount * MeditationSessionPacket.WEIGHT_OK;
        return (int) Math.round(weighted / judged * 100);
    }

    /** Notas musicales de Minecraft como feedback de Práctica libre (pedido explícito del
     *  usuario) — arpa aguda y subiendo con el combo para un acierto, bajo grave y fijo para un
     *  fallo. Cero asset nuevo: son los mismos SoundEvent que un bloque de nota vanilla. Modo
     *  Canción NO usa esto — ver el comentario en tryHit()/el duck() de MeditationDiscSound. */
    private void playHit() {
        float pitch = 1.0f + Math.min(combo, 20) * 0.02f;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP.value(), pitch, 0.7f));
    }

    private void playMiss() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.6f));
    }

    private int lanesLeft() {
        int totalW = LANES * LANE_W + (LANES - 1) * LANE_GAP;
        return (this.width - totalW) / 2;
    }

    private int hitZoneY() { return this.height - HIT_ZONE_Y_OFFSET; }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        if (state != State.PLAYING) {
            g.blit(ZenkaiMenuScreen.BG_TEX, panelLeft, panelTop, 0, 0,
                    ZenkaiMenuScreen.BG_W, ZenkaiMenuScreen.BG_H);
        }
        if (state == State.OVERVIEW && keybindPopupOpen) {
            // Marco de tres anillos + relleno POPUP_BG, mismo idioma que el popup lateral de
            // PartyConfig/StatsScreen — pintado ANTES de super.render() (bueno, de los widgets:
            // esto corre en renderBackground(), que sí es ANTES) para que Confirmar/Cancelar
            // queden encima de la caja y no al revés.
            int x0 = keybindPopupLeft();
            int y0 = keybindPopupTop();
            g.fill(x0 - 2, y0 - 2, x0 + KEYBIND_POPUP_W + 2, y0 + KEYBIND_POPUP_H + 2, ZenkaiPalette.BORDER_IN);
            g.fill(x0 - 1, y0 - 1, x0 + KEYBIND_POPUP_W + 1, y0 + KEYBIND_POPUP_H + 1, ZenkaiPalette.BORDER_MID);
            g.fill(x0, y0, x0 + KEYBIND_POPUP_W, y0 + KEYBIND_POPUP_H, ZenkaiPalette.POPUP_BG);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        switch (state) {
            case OVERVIEW -> renderOverview(g, mouseX, mouseY);
            case SONG_LIST -> renderSongList(g, mouseX, mouseY);
            case PLAYING -> renderPlaying(g);
            case RESULTS -> renderResults(g);
        }
        if (state == State.OVERVIEW && keybindPopupOpen) renderKeybindPopup(g, mouseX, mouseY);
    }

    /** Contenido del popup de reasignación de teclas: título, 4 filas "Lane N: TECLA" (la fila
     *  `awaitingLane` muestra un aviso de "pulsa una tecla" en vez del nombre, y un borde dorado
     *  fijo mientras espera), y una pista de Escape. Confirmar/Cancelar son widgets (ver
     *  initKeybindPopupWidgets()), así que no se dibujan aquí. */
    private void renderKeybindPopup(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = keybindPopupLeft();
        int cx = x0 + KEYBIND_POPUP_W / 2;
        int py = keybindPopupTop();

        PanelText.centeredOnDark(g, this.font,
                Component.translatable("screen.zenkai.meditation.keybinds.title"),
                cx, py + 8, ZenkaiPalette.GOLD);

        for (int lane = 0; lane < LANES; lane++) {
            int ry = keybindRowTop(lane);
            boolean waiting = awaitingLane == lane;
            boolean hovered = mouseX >= x0 + 8 && mouseX < x0 + KEYBIND_POPUP_W - 8
                    && mouseY >= ry && mouseY < ry + KEYBIND_ROW_H - 2;
            int bg = waiting ? ZenkaiPalette.VALUE : (hovered ? ZenkaiPalette.BAR_BG_DARK : 0);
            if (bg != 0) g.fill(x0 + 6, ry, x0 + KEYBIND_POPUP_W - 6, ry + KEYBIND_ROW_H - 2,
                    (bg & 0x00FFFFFF) | 0x60000000);

            Component laneLabel = Component.translatable("screen.zenkai.meditation.keybinds.lane", lane + 1);
            PanelText.onDark(g, this.font, laneLabel, x0 + 10, ry + 4, ZenkaiPalette.TEXT_DIM);

            Component valueLabel = waiting
                    ? Component.translatable("screen.zenkai.meditation.keybinds.waiting")
                    : keyLabel(pendingKeys[lane]);
            PanelText.rightOnDark(g, this.font, valueLabel, x0 + KEYBIND_POPUP_W - 10, ry + 4,
                    waiting ? ZenkaiPalette.GOLD : ZenkaiPalette.VALUE);
        }

        PanelText.centeredOnDark(g, this.font,
                Component.translatable("screen.zenkai.meditation.keybinds.hint"),
                cx, py + KEYBIND_POPUP_H - 40, ZenkaiPalette.TEXT_DIM);
    }

    /** Párrafo + fila-resumen de la canción (clicable, abre SONG_LIST) + steppers/valores fijos +
     *  TP potencial/récord — ver el javadoc de clase para el porqué de este reparto. */
    private void renderOverview(GuiGraphics g, int mouseX, int mouseY) {
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + ZenkaiMenuScreen.BG_W / 2, panelTop);
        int cx = panelLeft + ZenkaiMenuScreen.BG_W / 2;

        int ty = panelTop + descY;
        for (var line : overviewDescLines) {
            PanelText.onPanel(g, this.font, line, cx - this.font.width(line) / 2, ty, ZenkaiPalette.MUTED_ON_PANEL);
            ty += 10;
        }

        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;
        int y = panelTop + songRowY;
        Component songLabel = selectedSong != null
                ? Component.translatable(selectedSong.nameKey())
                : Component.translatable("screen.zenkai.meditation.free_practice");
        Component rightLabel = selectedSong != null
                ? Component.translatable(selectedSong.difficulty.translationKey) : null;
        renderSelectableRow(g, x, y, w, mouseX, mouseY, false,
                ICON_FREE_PRACTICE_U, ICON_FREE_PRACTICE_V, selectedSong != null ? selectedSong.item : null,
                Component.translatable("screen.zenkai.meditation.song_label", songLabel), rightLabel);

        if (selectedSong == null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.shadow.difficulty", STEPS_PCT[stepIndex]),
                    cx, panelTop + stepperY + 2, ZenkaiPalette.LABEL_ON_PANEL);
            // "Session length" en SU PROPIA línea (sin botones, puede ser tan ancha como haga
            // falta) + el VALOR SOLO ("30s") en la línea de los botones — bug real de captura del
            // usuario: combinado en una sola línea ("Session length: 30s") se montaba con el
            // botón +, a diferencia de "Difficulty: 100%" que sí cabía. Mismo arreglo que
            // "Opponent form" en ShadowTrainingScreen, ver su javadoc de clase.
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.meditation.session_length_label"),
                    cx, panelTop + stepperY + 20, ZenkaiPalette.LABEL_ON_PANEL);
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.meditation.session_length_value", DURATION_STEPS_SEC[durationIndex]),
                    cx, panelTop + stepperY + 34, ZenkaiPalette.LABEL_ON_PANEL);
        } else {
            // Una canción trae su propia dificultad/duración fijas — sin steppers, solo lo que
            // hay (pedido explícito del usuario: "la dificultad seleccionada, la duración de la
            // sesión"). Sin botones en esta rama, así que el texto combinado no tiene nada con lo
            // que solapar — se salta la línea de etiqueta y va directo a la Y del VALOR de la
            // rama de arriba, para que "TP potencial"/"Record" no salten de sitio al cambiar.
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.meditation.song_difficulty",
                            Component.translatable(selectedSong.difficulty.translationKey)),
                    cx, panelTop + stepperY + 2, ZenkaiPalette.LABEL_ON_PANEL);
            long songSeconds = songCharts.containsKey(selectedSong.discId)
                    ? songCharts.get(selectedSong.discId).durationMs() / 1000L : 0L;
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.meditation.fixed_duration", songSeconds),
                    cx, panelTop + stepperY + 34, ZenkaiPalette.LABEL_ON_PANEL);
        }

        if (introPotential != null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.potential", introPotential),
                    cx, panelTop + infoY, ZenkaiPalette.VALUE_ON_PANEL);
        }
        if (introRecord != null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.record", introRecord),
                    cx, panelTop + infoY + 12, ZenkaiPalette.MUTED_ON_PANEL);
        }
    }

    private int visibleRows() {
        return Math.max(1, (LIST_BOTTOM - LIST_TOP + SONG_ROW_GAP) / (SONG_ROW_H + SONG_ROW_GAP));
    }

    private int totalSongRows() { return 1 + songRows.size(); }

    private int maxScroll() { return Math.max(0, totalSongRows() - visibleRows()); }

    /** Ancho de fila en SONG_LIST: el ancho total del panel MENOS el canalón de scroll,
     *  reservado siempre haya o no scroll (ver SCROLLBAR_GUTTER) — para que las filas no cambien
     *  de ancho el día que un disco de más haga aparecer la barra. */
    private int songListRowWidth() { return (IN_X2 - IN_X1) - SCROLLBAR_GUTTER; }

    private void renderSongList(GuiGraphics g, int mouseX, int mouseY) {
        ScreenTitle.drawAbovePanel(g, this.font,
                Component.translatable("screen.zenkai.meditation.song_select.title"),
                panelLeft + ZenkaiMenuScreen.BG_W / 2, panelTop);
        int x = panelLeft + IN_X1;
        int w = songListRowWidth();
        int y = panelTop + LIST_TOP;

        int total = totalSongRows();
        int visible = visibleRows();
        int last = Math.min(total, scrollRow + visible);
        for (int i = scrollRow; i < last; i++) {
            if (i == 0) {
                y = renderSelectableRow(g, x, y, w, mouseX, mouseY, selectedSong == null,
                        ICON_FREE_PRACTICE_U, ICON_FREE_PRACTICE_V, null,
                        Component.translatable("screen.zenkai.meditation.free_practice"), null);
            } else {
                CuratedSong song = songRows.get(i - 1);
                y = renderSelectableRow(g, x, y, w, mouseX, mouseY, selectedSong == song,
                        -1, -1, song.item, Component.translatable(song.nameKey()),
                        Component.translatable(song.difficulty.translationKey));
            }
        }
        if (songRows.isEmpty()) {
            PanelText.onPanel(g, this.font,
                    Component.translatable("screen.zenkai.meditation.song_select.empty"),
                    x, y, ZenkaiPalette.MUTED_ON_PANEL);
        }

        if (maxScroll() > 0) {
            int barX = x + w + 2;
            int trackTop = panelTop + LIST_TOP;
            int trackH = LIST_BOTTOM - LIST_TOP;
            g.fill(barX, trackTop, barX + SCROLLBAR_W, trackTop + trackH, ZenkaiPalette.BAR_BG);
            int thumbH = Math.max(10, trackH * visible / total);
            int thumbY = trackTop + (trackH - thumbH) * scrollRow / maxScroll();
            g.fill(barX, thumbY, barX + SCROLLBAR_W, thumbY + thumbH, ZenkaiPalette.VALUE_ON_PANEL);
        }
    }

    /** Una fila seleccionable (Free Practice o una canción curada) — ícono desde el atlas
     *  (u/v >= 0) o un ítem real (item != null), nunca los dos. `selected` pinta un borde dorado
     *  FIJO (persiste sin el ratón encima); el tinte de hover se suma aparte y es independiente
     *  — así una fila puede estar elegida Y resaltada por el ratón a la vez sin que un estado
     *  tape al otro. Usada tanto por la fila-resumen de OVERVIEW (selected siempre false, es un
     *  botón de "cambiar", no una opción marcable) como por cada fila de SONG_LIST. PURA
     *  renderización, sin efectos de clic (ver clickSongSummaryRow()/clickSongRow() para el
     *  hit-test, que recorre la MISMA disposición por coordenadas en vez de reejecutar el render
     *  — nunca reejecutar el render como efecto secundario de un clic, ver el historial de esta
     *  clase). Devuelve la Y de la siguiente fila. */
    private int renderSelectableRow(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY,
                                    boolean selected, int iconU, int iconV, @Nullable Item item,
                                    Component label, @Nullable Component rightLabel) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SONG_ROW_H;

        g.fill(x, y, x + w, y + SONG_ROW_H, hovered ? ZenkaiPalette.ROW_HOVER : ZenkaiPalette.INSET_BG);
        int border = selected ? ZenkaiPalette.VALUE_ON_PANEL : ZenkaiPalette.BORDER_IN;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + SONG_ROW_H - 1, x + w, y + SONG_ROW_H, border);
        g.fill(x, y, x + 1, y + SONG_ROW_H, border);
        g.fill(x + w - 1, y, x + w, y + SONG_ROW_H, border);

        if (item != null) {
            g.renderItem(new ItemStack(item), x + 4, y + (SONG_ROW_H - 16) / 2);
        } else {
            g.blit(ICONS_TEX, x + 2, y + (SONG_ROW_H - ICON_CELL) / 2, iconU, iconV,
                    ICON_CELL, ICON_CELL, ICONS_ATLAS, ICONS_ATLAS);
        }

        // El nombre de ítem de CUALQUIER disco vanilla es genérico ("Music Disc") — el nombre
        // real de la canción vive en una traducción propia (ver CuratedSong.nameKey()), no en
        // el ItemStack, para que las filas se distingan de verdad.
        PanelText.onPanel(g, this.font, label, x + 26, y + 5, ZenkaiPalette.LABEL_ON_PANEL);
        if (rightLabel != null) {
            PanelText.rightOnPanel(g, this.font, rightLabel, x + w - 6, y + 5, ZenkaiPalette.MUTED_ON_PANEL);
        }
        return y + SONG_ROW_H + SONG_ROW_GAP;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state == State.OVERVIEW && keybindPopupOpen) {
            // super PRIMERO: deja actuar a los widgets del propio popup (Confirmar/Cancelar) y
            // al engranaje, que lo cerraría por su cuenta — mismo orden que PartyScreen.
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            if (button != 0) return false;
            if (clickKeybindRow(mouseX, mouseY)) return true;
            // Clic fuera de la caja del popup = cerrar sin aplicar nada (gesto estándar de
            // modal), igual que PartyScreen.configOpen.
            int x0 = keybindPopupLeft(), y0 = keybindPopupTop();
            boolean inside = mouseX >= x0 && mouseX < x0 + KEYBIND_POPUP_W
                    && mouseY >= y0 && mouseY < y0 + KEYBIND_POPUP_H;
            if (!inside) {
                keybindPopupOpen = false;
                buildOverviewWidgets();
                return true;
            }
            return false;
        }
        if (state == State.OVERVIEW && button == 0 && clickSongSummaryRow(mouseX, mouseY)) {
            openSongList();
            return true;
        }
        if (state == State.SONG_LIST && button == 0 && clickSongRow(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Hit-test de las 4 filas del popup de reasignación — un clic en una fila la pone a
     *  "esperando tecla" (ver keyPressed()), nunca reasigna directamente por sí solo. */
    private boolean clickKeybindRow(double mouseX, double mouseY) {
        int x0 = keybindPopupLeft();
        for (int lane = 0; lane < LANES; lane++) {
            int ry = keybindRowTop(lane);
            if (mouseX >= x0 + 8 && mouseX < x0 + KEYBIND_POPUP_W - 8
                    && mouseY >= ry && mouseY < ry + KEYBIND_ROW_H - 2) {
                awaitingLane = lane;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (state == State.SONG_LIST && maxScroll() > 0 && scrollY != 0) {
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Hit-test de la fila-resumen clicable de OVERVIEW — misma disposición que renderOverview(). */
    private boolean clickSongSummaryRow(double mouseX, double mouseY) {
        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;
        int y = panelTop + songRowY;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SONG_ROW_H;
    }

    /** Hit-test de clic en SONG_LIST: misma disposición por coordenadas que renderSongList(),
     *  acotada a las filas realmente visibles (scrollRow..scrollRow+visibleRows()). */
    private boolean clickSongRow(double mouseX, double mouseY) {
        int x = panelLeft + IN_X1;
        int w = songListRowWidth();
        if (mouseX < x || mouseX >= x + w) return false;

        int y = panelTop + LIST_TOP;
        int total = totalSongRows();
        int last = Math.min(total, scrollRow + visibleRows());
        for (int i = scrollRow; i < last; i++) {
            if (mouseY >= y && mouseY < y + SONG_ROW_H) {
                selectRow(i == 0 ? null : songRows.get(i - 1));
                return true;
            }
            y += SONG_ROW_H + SONG_ROW_GAP;
        }
        return false;
    }

    private void renderPlaying(GuiGraphics g) {
        int left = lanesLeft();
        int hitY = hitZoneY();
        long now = System.currentTimeMillis();

        for (int lane = 0; lane < LANES; lane++) {
            int x = left + lane * (LANE_W + LANE_GAP);
            g.fill(x, 20, x + LANE_W, this.height - 20, ZenkaiPalette.POPUP_BG);
            // Flash breve de carril al acertar/fallar (pedido explícito del usuario: "no hay
            // marcadores... tampoco hay un efecto para cuando te equivocas") — mismo color que
            // el judgment de texto, pero MUY translúcido para no tapar las notas que caen. Solo
            // en una banda pegada a la línea de acierto (no la columna entera): pintar todo el
            // carril era demasiado ruido visual justo encima de las notas que aún hay que leer
            // (feedback real de captura de juego).
            int flash = 0;
            if (now < laneGoodUntil[lane]) flash = (ZenkaiPalette.OK & 0x00FFFFFF) | 0x50000000;
            else if (now < laneBadUntil[lane]) flash = (ZenkaiPalette.ERROR & 0x00FFFFFF) | 0x50000000;
            if (flash != 0) g.fill(x, Math.max(20, hitY - 50), x + LANE_W, this.height - 20, flash);
            g.fill(x, hitY, x + LANE_W, hitY + 4, ZenkaiPalette.OK);
            // font.width(), no un offset fijo de "-3": una tecla reasignada puede tener un
            // nombre más largo que una sola letra (p.ej. "Space"), y un offset pensado para
            // "A"/"S"/"D"/"F" la descentraría.
            Component label = keyLabel(keys[lane]);
            PanelText.onDark(g, this.font, label,
                    x + LANE_W / 2 - this.font.width(label) / 2, hitY + 8, ZenkaiPalette.TEXT);
        }

        if (waitingForStart) {
            // Pedido explícito del usuario: ni la canción ni las notas arrancan hasta la
            // primera tecla — este prompt es la única señal de que la sesión ya está "abierta".
            g.fill(0, this.height / 2 - 14, this.width, this.height / 2 + 14, ZenkaiPalette.POPUP_BG);
            PanelText.centeredOnDark(g, this.font,
                    Component.translatable("screen.zenkai.meditation.press_to_start")
                            .copy().withStyle(ChatFormatting.BOLD),
                    this.width / 2, this.height / 2 - 4, ZenkaiPalette.TEXT);
            return;
        }

        for (Note n : notes) {
            double ratio = (now - n.spawnMs()) / (double) TRAVEL_MS;
            int top = (int) (20 + ratio * (hitY - 20));
            int x = left + n.lane() * (LANE_W + LANE_GAP) + (LANE_W - NOTE_SIZE) / 2;
            g.fill(x, top, x + NOTE_SIZE, top + NOTE_SIZE, ZenkaiPalette.VALUE);
            g.fill(x, top, x + NOTE_SIZE, top + 1, NOTE_OUTLINE); // borde arriba
            g.fill(x, top + NOTE_SIZE - 1, x + NOTE_SIZE, top + NOTE_SIZE, NOTE_OUTLINE); // abajo
            g.fill(x, top, x + 1, top + NOTE_SIZE, NOTE_OUTLINE); // izquierda
            g.fill(x + NOTE_SIZE - 1, top, x + NOTE_SIZE, top + NOTE_SIZE, NOTE_OUTLINE); // derecha
        }

        // Judgments flotantes (SICK!/GOOD/OK/MISS) — suben y se desvanecen sobre su carril.
        for (Judgment j : judgments) {
            long age = now - j.shownAtMs();
            float t = Math.min(1f, age / (float) JUDGMENT_LIFETIME_MS);
            int alpha = Math.max(0, (int) (255 * (1f - t)));
            int color = (j.color() & 0x00FFFFFF) | (alpha << 24);
            int x = left + j.lane() * (LANE_W + LANE_GAP) + LANE_W / 2;
            int y = hitY - 20 - (int) (t * 12);
            PanelText.centeredOnDark(g, this.font, Component.literal(j.text()), x, y, color);
        }

        long elapsed = now - sessionStartMs;
        if (elapsed < 0) {
            // Colchón de LEAD_IN_MS (ver su javadoc): las notas ya caen normal debajo, pero
            // Combo/tiempo restante saldrían con números sin sentido (elapsed negativo) — un
            // aviso de cuenta atrás en su lugar, sin tapar la vista de los carriles.
            int secondsToGo = (int) Math.ceil(-elapsed / 1000.0);
            PanelText.centeredOnDark(g, this.font,
                    Component.translatable("screen.zenkai.meditation.get_ready", secondsToGo)
                            .copy().withStyle(ChatFormatting.BOLD),
                    this.width / 2, 8, ZenkaiPalette.TEXT);
            return;
        }
        int secondsLeft = (int) Math.max(0, (sessionDurationMs() - elapsed) / 1000);
        PanelText.onDark(g, this.font,
                Component.translatable("screen.zenkai.meditation.combo", combo),
                left, 8, ZenkaiPalette.TEXT);
        PanelText.onDark(g, this.font,
                Component.translatable("screen.zenkai.meditation.accuracy", accuracyPercent()),
                left, 18, ZenkaiPalette.TEXT);
        PanelText.rightOnDark(g, this.font,
                Component.translatable("screen.zenkai.meditation.time_left", secondsLeft),
                left + LANES * (LANE_W + LANE_GAP) - LANE_GAP, 8, ZenkaiPalette.TEXT);
    }

    private void renderResults(GuiGraphics g) {
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + ZenkaiMenuScreen.BG_W / 2, panelTop);
        int cx = panelLeft + ZenkaiMenuScreen.BG_W / 2;
        int ty = panelTop + 50;

        // LABEL_ON_PANEL + negrita, NO GOLD: GOLD es dorado pensado para fondo OSCURO (ver
        // ZenkaiPalette), y sobre el beige del panel se leía casi invisible (feedback de imagen).
        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.meditation.result.title")
                        .copy().withStyle(ChatFormatting.BOLD),
                cx, ty, ZenkaiPalette.LABEL_ON_PANEL);
        ty += 16;
        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.meditation.result.notes_hit", notesHit, maxCombo),
                cx, ty, ZenkaiPalette.LABEL_ON_PANEL);
        ty += 16;
        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.meditation.accuracy", accuracyPercent()),
                cx, ty, ZenkaiPalette.LABEL_ON_PANEL);
        ty += 16;
        Component reward = resultReward == null
                ? Component.translatable("screen.zenkai.meditation.result.calculating")
                : Component.translatable("screen.zenkai.meditation.result.tp_earned", resultReward);
        PanelText.centeredOnPanel(g, this.font, reward, cx, ty, ZenkaiPalette.OK_ON_PANEL);

        if (resultRecord != null) {
            ty += 16;
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.record", resultRecord),
                    cx, ty, ZenkaiPalette.MUTED_ON_PANEL);
            if (resultReward != null && resultReward > 0 && resultReward.equals(resultRecord)) {
                ty += 12;
                PanelText.centeredOnPanel(g, this.font,
                        Component.translatable("screen.zenkai.training_hub.new_record")
                                .copy().withStyle(ChatFormatting.BOLD),
                        cx, ty, ZenkaiPalette.VALUE_ON_PANEL);
            }
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
