package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.MinusIconButton;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.client.training.CuratedSong;
import com.hmc.zenkai.client.training.MeditationChart;
import com.hmc.zenkai.client.training.MeditationChartLoader;
import com.hmc.zenkai.client.training.MeditationDiscSound;
import com.hmc.zenkai.feature.training.MeditationSessionPacket;
import com.hmc.zenkai.feature.training.TrainingInfoRequestPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * "Meditation" (TrainingHubScreen): ritmo estilo FNF — 4 carriles (A/S/D/F), las notas caen
 * hacia una zona de impacto fija; acertar en ventana sube la racha, fallar la corta. Dos modos:
 *
 *  - PRÁCTICA LIBRE (por defecto): generador procedural (spawnea una nota en un carril al azar a
 *    intervalo fijo) — evita tener que autorar charts, sesión corta de {@link #SESSION_DURATION_MS}.
 *  - MODO CANCIÓN (nuevo, pedido explícito del usuario): elige un disco vanilla curado
 *    ({@link CuratedSong}) y las notas siguen un chart real generado por
 *    tools/gen_meditation_chart.py (detección de onsets sobre el .ogg del disco) — la sesión dura
 *    lo que dura la canción, que suena de verdad ({@link SoundEvents} MUSIC_DISC_*) mientras se
 *    juega. La dificultad ES la canción elegida (Pigstep = difícil).
 *
 * Cuatro estados (INTRO -> SONG_SELECT -> PLAYING -> RESULTS desde "Choose a Song", o
 * INTRO -> PLAYING -> RESULTS desde "Free Practice"): INTRO explica qué hacer y tiene los dos
 * botones de modo + Back; SONG_SELECT lista los discos curados cuyo chart ya existe; PLAYING es
 * el minijuego a pantalla completa, sin botones (Escape reporta lo hecho y sale, ver onClose());
 * RESULTS enseña el reward REAL (no una estimación del cliente) + récord + Retry/Back.
 *
 * ANTI-TRAMPA: la sesión entera se reporta como desempeño CRUDO (notas acertadas + racha máxima
 * + duración), nunca como un TP ya calculado — el servidor decide cuánto vale eso
 * (MeditationSessionPacket) y responde con el TP REAL vía TrainingSessionRewardPacket
 * (ver TrainingMinigameScreen/ClientPayloadHandlers.onTrainingReward), que es lo que RESULTS
 * enseña — nunca una cifra adivinada en el cliente.
 *
 * `extends Screen` directamente (canvas propio) pero reusa el fondo/tamaño de panel de
 * ZenkaiMenuScreen (BG_TEX/BG_W/BG_H, protected + mismo paquete = accesible) para INTRO/
 * SONG_SELECT/RESULTS, sin heredar de ella — la fase PLAYING necesita la pantalla ENTERA para los
 * carriles, cosa que ZenkaiMenuScreen (con su barra de pestañas fija) no puede dar. Sigue el
 * convenio de orden de render de CLAUDE.md: renderBackground() pinta DEBAJO de super.render(), el
 * contenido va DESPUÉS.
 */
public class MeditationScreen extends Screen implements TrainingMinigameScreen {

    private enum State { INTRO, SONG_SELECT, PLAYING, RESULTS }

    private static final int LANES = 4;
    private static final int[] KEYS = {
            GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F
    };
    private static final String[] KEY_LABELS = {"A", "S", "D", "F"};

    private static final long SESSION_DURATION_MS = 30_000;
    private static final long BASE_SPAWN_INTERVAL_MS = 450;
    private static final long TRAVEL_MS = 1400;
    /** Ventana de acierto: |ratio-1.0| <= esto se cuenta como golpe válido. */
    private static final double BASE_HIT_WINDOW = 0.12;

    /** Dificultad progresiva de PRÁCTICA LIBRE (pedido explícito del usuario, Pista C del plan
     *  de pulido de Training) — modo Canción NO usa esto, ahí la dificultad ES la canción
     *  elegida (ver startSongSession(), que resetea estos dos a su base). */
    private static final int[] STEPS_PCT = {50, 75, 100, 125, 150, 175, 200};
    private int stepIndex = 2; // arranca en 100%
    private long spawnIntervalMs = BASE_SPAWN_INTERVAL_MS;
    private double hitWindow = BASE_HIT_WINDOW;

    private static final int LANE_W = 40;
    private static final int LANE_GAP = 8;
    private static final int NOTE_H = 14;
    private static final int HIT_ZONE_Y_OFFSET = 60; // desde el borde inferior

    private static final int IN_X1 = 10;
    private static final int IN_X2 = 245;
    private static final int SONG_ROW_H = 24;
    private static final int SONG_ROW_GAP = 6;

    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICON_NOTE_U = 40, ICON_NOTE_V = 120;
    private static final int ICON_CELL = 20;
    private static final int ICONS_ATLAS = 256;

    private record Note(int lane, long spawnMs) {}

    private final Random random = new Random();
    private final List<Note> notes = new ArrayList<>();

    private State state = State.INTRO;
    private long sessionStartMs = -1;
    private long lastSpawnMs = -1;
    private int notesHit = 0;
    private int combo = 0;
    private int maxCombo = 0;
    private boolean reported = false;
    private Integer resultReward = null;
    private Integer resultRecord = null;

    /** Récord/potencial de Práctica libre, mostrados en INTRO (TrainingInfoPacket) — null hasta
     *  que responde el servidor. El potencial de Práctica libre se sigue mostrando aunque el
     *  jugador acabe eligiendo modo Canción: una canción larga no tiene un techo de sesión fijo
     *  con el que calcular "hasta X" (ver F8/G5 del plan), así que esto es deliberadamente solo
     *  la referencia de Práctica libre. */
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
    private List<FormattedCharSequence> introLines;
    /** Y (relativo al panel) del stepper de dificultad de Práctica libre — calculado UNA VEZ a
     *  partir de las líneas de intro reales, con una franja fija reservada para potencial/récord
     *  aunque el packet todavía no haya respondido, así los botones nunca "saltan" cuando llega
     *  el dato (mismo problema que ya documentó ShadowTrainingScreen para su propio stepper). */
    private int stepperY;

    /** true = la sesión ya arrancó (beginPlaying) pero espera a que el jugador pulse una tecla
     *  para que empiecen a caer notas/sonar la canción — pedido explícito del usuario ("hasta
     *  que el jugador no pulse... no comience la canción"). Aplica a los dos modos por igual
     *  (más simple que gatear solo Canción, y Práctica libre tampoco pierde nada por dar un
     *  respiro antes de la primera nota). */
    private boolean waitingForStart;

    public MeditationScreen() {
        super(Component.translatable("screen.zenkai.training_hub.row.meditation"));
    }

    @Override
    protected void init() {
        panelLeft = (this.width - ZenkaiMenuScreen.BG_W) / 2;
        panelTop = (this.height - ZenkaiMenuScreen.BG_H) / 2;
        introLines = this.font.split(
                Component.translatable("screen.zenkai.meditation.intro"), ZenkaiMenuScreen.BG_W - 24);
        stepperY = 30 + introLines.size() * 10 + 4 + 20 + 4;
        buildIntroWidgets();

        introRecord = null;
        introPotential = null;
        PacketDistributor.sendToServer(new TrainingInfoRequestPacket(TrainingInfoRequestPacket.MEDITATION));
    }

    private void buildIntroWidgets() {
        state = State.INTRO;
        this.clearWidgets();
        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;
        addRenderableWidget(PanelButton.secondary(panelLeft + IN_X1, y,
                Component.translatable("screen.zenkai.back"), this::onClose));
        addRenderableWidget(PanelButton.primary(panelLeft + IN_X2 - PanelButton.W, y,
                Component.translatable("screen.zenkai.meditation.free_practice"), this::startFreePractice));

        int cx = panelLeft + ZenkaiMenuScreen.BG_W / 2;
        addRenderableWidget(new MinusIconButton(cx - 60, panelTop + stepperY, this::decreaseDifficulty));
        addRenderableWidget(new PlusIconButton(cx + 48, panelTop + stepperY, this::increaseDifficulty));

        addRenderableWidget(PanelButton.secondary(
                panelLeft + (ZenkaiMenuScreen.BG_W - PanelButton.W) / 2, panelTop + stepperY + 20,
                Component.translatable("screen.zenkai.meditation.choose_song"), this::openSongSelect));
    }

    private void decreaseDifficulty() { stepIndex = Math.max(0, stepIndex - 1); }
    private void increaseDifficulty() { stepIndex = Math.min(STEPS_PCT.length - 1, stepIndex + 1); }

    /** Más difícil = notas más seguidas y ventana de acierto más estrecha — los dos números que
     *  ya hacían de "perilla" implícita de Práctica libre, ahora escalados por el stepper en vez
     *  de fijos. TRAVEL_MS se queda constante a propósito (la velocidad de caída no cambia, solo
     *  cuánto margen de error y cuántas notas hay que leer). */
    private void applyDifficulty() {
        double fraction = STEPS_PCT[stepIndex] / 100.0;
        spawnIntervalMs = clampLong(Math.round(BASE_SPAWN_INTERVAL_MS / fraction), 220, 900);
        hitWindow = clampDouble(BASE_HIT_WINDOW / fraction, 0.06, 0.22);
    }

    private static long clampLong(long v, long min, long max) { return Math.max(min, Math.min(max, v)); }
    private static double clampDouble(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    private void openSongSelect() {
        state = State.SONG_SELECT;
        songCharts = MeditationChartLoader.loadAll();
        songRows = Arrays.stream(CuratedSong.values())
                .filter(s -> songCharts.containsKey(s.discId))
                .toList();

        this.clearWidgets();
        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;
        addRenderableWidget(PanelButton.secondary(panelLeft + IN_X1, y,
                Component.translatable("screen.zenkai.back"), this::buildIntroWidgets));
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
        spawnIntervalMs = BASE_SPAWN_INTERVAL_MS;
        hitWindow = BASE_HIT_WINDOW;
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
     *  se ve el prompt "PRESS ANY KEY TO START" (renderPlaying()). Aquí, y no en
     *  startSongSession(), es donde arranca de verdad el disco: así el jugador nunca escucha
     *  audio antes de estar listo. */
    private void actuallyStart() {
        waitingForStart = false;
        sessionStartMs = System.currentTimeMillis();
        lastSpawnMs = sessionStartMs;
        if (activeChart != null) {
            muteVanillaMusic();
            discSound = new MeditationDiscSound(activeSong.sound.value(), 1.0f);
            Minecraft.getInstance().getSoundManager().play(discSound);
        }
    }

    private void stopSongIfAny() {
        if (discSound != null) {
            Minecraft.getInstance().getSoundManager().stop(discSound);
            discSound = null;
        }
        restoreVanillaMusic();
    }

    private long sessionDurationMs() {
        return activeChart != null ? activeChart.durationMs() : SESSION_DURATION_MS;
    }

    @Override
    public void tick() {
        super.tick();
        if (state != State.PLAYING || waitingForStart) return;
        long now = System.currentTimeMillis();
        long elapsed = now - sessionStartMs;

        if (elapsed >= sessionDurationMs()) {
            stopSongIfAny();
            sendSessionReport();
            state = State.RESULTS;
            buildResultsWidgets();
            return;
        }

        if (activeChart != null) {
            List<MeditationChart.MeditationNote> chartNotes = activeChart.notes();
            // La nota debe APARECER TRAVEL_MS antes de su instante de golpe real, para que
            // llegue a la zona de impacto exactamente cuando suena — mismo mecanismo de
            // carriles/tiempo de viaje que Práctica libre, solo cambia CUÁNDO se decide spawnear.
            while (chartCursor < chartNotes.size()
                    && chartNotes.get(chartCursor).timeMs() - TRAVEL_MS <= elapsed) {
                var n = chartNotes.get(chartCursor);
                notes.add(new Note(n.lane(), sessionStartMs + n.timeMs() - TRAVEL_MS));
                chartCursor++;
            }
        } else if (now - lastSpawnMs >= spawnIntervalMs) {
            lastSpawnMs = now;
            notes.add(new Note(random.nextInt(LANES), now));
        }

        // Notas que ya pasaron la ventana de acierto sin pulsarse: fallo, corta la racha.
        notes.removeIf(n -> {
            double ratio = (now - n.spawnMs()) / (double) TRAVEL_MS;
            if (ratio > 1.0 + hitWindow) {
                if (activeChart != null && discSound != null) {
                    discSound.duck(220); // el disco "se apaga un poco" — ver MeditationDiscSound
                } else if (combo > 0) {
                    playMiss(); // Práctica libre: sin música real que clashee, sí lleva pitido
                }
                judgments.add(new Judgment(n.lane(), "MISS", ZenkaiPalette.ERROR, now));
                laneBadUntil[n.lane()] = now + 150;
                combo = 0;
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
        PacketDistributor.sendToServer(
                new MeditationSessionPacket(notesHit, maxCombo, (int) durationTicks));
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
        // Escape a mitad de partida reporta lo ya hecho (mismo espíritu que "sesión
        // interrumpida cuenta") y sale directo al hub sin pasar por RESULTS — ver un resultado
        // parcial no aporta nada que el jugador no supiera ya si él mismo decidió salir.
        if (state == State.PLAYING && !waitingForStart) sendSessionReport();
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
        if (state == State.PLAYING) {
            if (waitingForStart) {
                actuallyStart();
                return true;
            }
            for (int lane = 0; lane < LANES; lane++) {
                if (keyCode == KEYS[lane]) {
                    tryHit(lane);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void tryHit(int lane) {
        long now = System.currentTimeMillis();
        Note best = null;
        double bestDelta = Double.MAX_VALUE;
        for (Note n : notes) {
            if (n.lane() != lane) continue;
            double ratio = (now - n.spawnMs()) / (double) TRAVEL_MS;
            double delta = Math.abs(ratio - 1.0);
            if (delta <= hitWindow && delta < bestDelta) {
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
        if (delta <= hitWindow * 0.35) {
            text = "SICK!";
            color = ZenkaiPalette.VALUE;
        } else if (delta <= hitWindow * 0.7) {
            text = "GOOD";
            color = ZenkaiPalette.OK;
        } else {
            text = "OK";
            color = ZenkaiPalette.TEXT;
        }
        judgments.add(new Judgment(lane, text, color, System.currentTimeMillis()));
        laneGoodUntil[lane] = System.currentTimeMillis() + 150;
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
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        switch (state) {
            case INTRO -> renderIntro(g, mouseX, mouseY);
            case SONG_SELECT -> renderSongSelect(g, mouseX, mouseY);
            case PLAYING -> renderPlaying(g);
            case RESULTS -> renderResults(g);
        }
    }

    private void renderIntro(GuiGraphics g, int mouseX, int mouseY) {
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + ZenkaiMenuScreen.BG_W / 2, panelTop);
        int cx = panelLeft + ZenkaiMenuScreen.BG_W / 2;
        int ty = panelTop + 30;
        for (var line : introLines) {
            PanelText.onPanel(g, this.font, line, cx - this.font.width(line) / 2, ty, ZenkaiPalette.MUTED_ON_PANEL);
            ty += 10;
        }
        ty += 4;
        if (introPotential != null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.potential", introPotential),
                    cx, ty, ZenkaiPalette.VALUE_ON_PANEL);
            ty += 10;
        }
        if (introRecord != null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.record", introRecord),
                    cx, ty, ZenkaiPalette.MUTED_ON_PANEL);
        }

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.training_hub.shadow.difficulty", STEPS_PCT[stepIndex]),
                cx, panelTop + stepperY + 2, ZenkaiPalette.LABEL_ON_PANEL);
    }

    private void renderSongSelect(GuiGraphics g, int mouseX, int mouseY) {
        ScreenTitle.drawAbovePanel(g, this.font,
                Component.translatable("screen.zenkai.meditation.song_select.title"),
                panelLeft + ZenkaiMenuScreen.BG_W / 2, panelTop);
        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;
        int y = panelTop + 30;

        if (songRows.isEmpty()) {
            PanelText.onPanel(g, this.font,
                    Component.translatable("screen.zenkai.meditation.song_select.empty"),
                    x, y, ZenkaiPalette.MUTED_ON_PANEL);
            return;
        }

        for (CuratedSong song : songRows) {
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SONG_ROW_H;
            g.fill(x, y, x + w, y + SONG_ROW_H, hovered ? ZenkaiPalette.ROW_HOVER : ZenkaiPalette.INSET_BG);
            g.fill(x, y, x + w, y + 1, ZenkaiPalette.BORDER_IN);
            g.fill(x, y + SONG_ROW_H - 1, x + w, y + SONG_ROW_H, ZenkaiPalette.BORDER_IN);
            g.fill(x, y, x + 1, y + SONG_ROW_H, ZenkaiPalette.BORDER_IN);
            g.fill(x + w - 1, y, x + w, y + SONG_ROW_H, ZenkaiPalette.BORDER_IN);

            g.renderItem(new ItemStack(song.item), x + 4, y + (SONG_ROW_H - 16) / 2);

            Component name = new ItemStack(song.item).getHoverName();
            PanelText.onPanel(g, this.font, name, x + 26, y + 5, ZenkaiPalette.LABEL_ON_PANEL);
            PanelText.rightOnPanel(g, this.font,
                    Component.translatable(song.difficulty.translationKey),
                    x + w - 6, y + 5, ZenkaiPalette.MUTED_ON_PANEL);

            y += SONG_ROW_H + SONG_ROW_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state == State.SONG_SELECT && button == 0 && clickSongRow(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickSongRow(double mouseX, double mouseY) {
        int x = panelLeft + IN_X1;
        int w = IN_X2 - IN_X1;
        int y = panelTop + 30;
        if (mouseX < x || mouseX >= x + w) return false;

        for (CuratedSong song : songRows) {
            if (mouseY >= y && mouseY < y + SONG_ROW_H) {
                startSongSession(song);
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
            int laneBg = ZenkaiPalette.POPUP_BG;
            // Flash breve de carril al acertar/fallar (pedido explícito del usuario: "no hay
            // marcadores... tampoco hay un efecto para cuando te equivocas") — mismo color que
            // el judgment de texto, pero MUY translúcido para no tapar las notas que caen.
            if (now < laneGoodUntil[lane]) laneBg = (ZenkaiPalette.OK & 0x00FFFFFF) | 0x50000000;
            else if (now < laneBadUntil[lane]) laneBg = (ZenkaiPalette.ERROR & 0x00FFFFFF) | 0x50000000;
            g.fill(x, 20, x + LANE_W, this.height - 20, laneBg);
            g.fill(x, hitY, x + LANE_W, hitY + 4, ZenkaiPalette.OK);
            PanelText.onDark(g, this.font, Component.literal(KEY_LABELS[lane]),
                    x + LANE_W / 2 - 3, hitY + 8, ZenkaiPalette.TEXT);
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
            int y = (int) (20 + ratio * (hitY - 20));
            int x = left + n.lane() * (LANE_W + LANE_GAP);
            g.blit(ICONS_TEX, x + 2, y, LANE_W - 4, NOTE_H,
                    ICON_NOTE_U, ICON_NOTE_V, ICON_CELL, ICON_CELL, ICONS_ATLAS, ICONS_ATLAS);
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
        int secondsLeft = (int) Math.max(0, (sessionDurationMs() - elapsed) / 1000);
        PanelText.onDark(g, this.font,
                Component.translatable("screen.zenkai.meditation.combo", combo),
                left, 8, ZenkaiPalette.TEXT);
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
