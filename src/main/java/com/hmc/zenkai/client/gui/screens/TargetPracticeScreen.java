package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.BackIconButton;
import com.hmc.zenkai.client.gui.buttons.MinusIconButton;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.client.gui.buttons.PlayIconButton;
import com.hmc.zenkai.client.gui.buttons.PlusIconButton;
import com.hmc.zenkai.feature.training.TargetPracticeSessionPacket;
import com.hmc.zenkai.feature.training.TrainingInfoRequestPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Ki Target Practice" (TrainingHubScreen): orbes flotantes aparecen en la pantalla y hay que
 * reventarlos con click rápido antes de que se desvanezcan; entre ellos aparecen bombas/
 * calaveras que hay que EVITAR — tocar una pierde la ronda (mismo pedido explícito del usuario:
 * "debe tener cuidado si hay unas bombas o calaveras... y con eso pierde"). 100% minijuego de
 * GUI, sin entidades/proyectiles de verdad — el hit-test es inline en mouseClicked(), mismo
 * idioma que PartyScreen usa para el retrato de un miembro (sin widget nuevo).
 *
 * Mismo estado INTRO -> PLAYING -> RESULTS que MeditationScreen (ver su javadoc para el porqué
 * y para el convenio de fondo/orden de render compartido) — INTRO explica la regla de las
 * bombas, RESULTS enseña el TP REAL que respondió el servidor (TrainingSessionRewardPacket) más
 * Retry/Back. PLAYING lleva un botón "Finish" (esquina superior izquierda) — Escape hace LO
 * MISMO (ver onClose()/finishSessionEarly()): terminar YA y pasar a RESULTS, nunca el silencio
 * de antes que salía directo al hub sin enseñar el reward (pedido explícito del usuario, mismo
 * cambio que MeditationScreen).
 *
 * DIFICULTAD PROGRESIVA (pedido explícito del usuario, Pista C del plan de pulido de Training):
 * stepper 50%-200% como el de ShadowTrainingScreen, elegido en INTRO. Más difícil = orbes que
 * aparecen más seguido, duran menos en pantalla, y más probabilidad de bomba — ver
 * applyDifficulty().
 */
public class TargetPracticeScreen extends Screen implements TrainingMinigameScreen {

    private enum State { INTRO, PLAYING, RESULTS }

    private static final long BASE_SPAWN_INTERVAL_MS = 600;
    private static final long BASE_ORB_LIFESPAN_MS = 1500;
    private static final double BASE_BOMB_CHANCE = 0.2;
    private static final int ORB_RADIUS = 12;

    private static final int[] STEPS_PCT = {50, 75, 100, 125, 150, 175, 200};

    /** Duración de sesión elegible (pedido explícito del usuario: "a mayor tiempo jugando se
     *  pueda conseguir mayor TP") — mismos pasos que MeditationScreen.DURATION_STEPS_SEC para
     *  que el idioma visual del stepper sea idéntico entre los dos minijuegos con duración
     *  configurable. 30s (durationIndex por defecto) es la duración fija que tenía antes. */
    private static final int[] DURATION_STEPS_SEC = {15, 30, 45, 60, 90, 120};
    private int durationIndex = 1; // arranca en 30s
    private long sessionDurationMs() { return DURATION_STEPS_SEC[durationIndex] * 1000L; }

    private static final ResourceLocation ICONS_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/icons.png");
    private static final int ICON_ORB_U = 0, ICON_ORB_V = 120;
    private static final int ICON_BOMB_U = 20, ICON_BOMB_V = 120;
    private static final int ICON_CELL = 20;
    private static final int ICONS_ATLAS = 256;

    // El beige real de common_screen.png va de x=12 a x=244 (muestreado píxel a píxel, ver
    // TrainingHubScreen) — 10/245 se metían 2px dentro del marco por cada lado.
    private static final int IN_X1 = 12;
    private static final int IN_X2 = 243;

    private record Orb(int x, int y, long spawnMs, boolean bomb) {}

    private final Random random = new Random();
    private final List<Orb> orbs = new ArrayList<>();

    private State state = State.INTRO;
    private int stepIndex = 2; // arranca en 100%
    private long spawnIntervalMs = BASE_SPAWN_INTERVAL_MS;
    private long orbLifespanMs = BASE_ORB_LIFESPAN_MS;
    private double bombChance = BASE_BOMB_CHANCE;

    private long sessionStartMs = -1;
    private long lastSpawnMs = -1;
    private int orbsPopped = 0;
    private boolean reported = false;
    private boolean lost = false;
    private Integer resultReward = null;
    private Integer resultRecord = null;

    private Integer introRecord = null;
    private Integer introPotential = null;

    private int panelLeft, panelTop;
    private List<FormattedCharSequence> introLines;
    /** Y (relativo al panel) del stepper de dificultad — calculado UNA VEZ a partir de las
     *  líneas de intro reales, con una franja fija reservada para potencial/récord aunque el
     *  packet todavía no haya respondido, así los botones nunca "saltan" cuando llega el dato
     *  (mismo problema que ya documentó ShadowTrainingScreen para su propio stepper). */
    private int stepperY;

    public TargetPracticeScreen() {
        super(Component.translatable("screen.zenkai.training_hub.row.target_practice"));
    }

    @Override
    protected void init() {
        panelLeft = (this.width - ZenkaiMenuScreen.BG_W) / 2;
        panelTop = (this.height - ZenkaiMenuScreen.BG_H) / 2;
        introLines = this.font.split(
                Component.translatable("screen.zenkai.target_practice.intro"), ZenkaiMenuScreen.BG_W - 24);
        stepperY = 30 + introLines.size() * 10 + 4 + 20 + 4;
        buildIntroWidgets();
        requestInfo();
    }

    /** (Re)pide récord + TP potencial — disparado al abrir la pantalla y cada vez que cambia
     *  CUALQUIERA de los dos steppers. La dificultad SÍ dispara esto ahora (antes no, pero
     *  TrainingHooks.targetPracticeAchievableRawTp() depende de ella: más difícil = orbes más
     *  seguidos = más caben en la misma duración = techo más alto — bug real reportado por el
     *  usuario, "siento que hay una discrepancia" entre el TP obtenido y el "up to" mostrado). */
    private void requestInfo() {
        introRecord = null;
        introPotential = null;
        PacketDistributor.sendToServer(new TrainingInfoRequestPacket(
                TrainingInfoRequestPacket.TARGET_PRACTICE, (int) (sessionDurationMs() / 50),
                STEPS_PCT[stepIndex] / 100f));
    }

    private void buildIntroWidgets() {
        this.clearWidgets();
        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;
        // Back en icono atlas / Start en ▶ — mismos íconos ya existentes que
        // ShadowTrainingScreen/MeditationScreen, con tooltip (pedido explícito del usuario) para
        // que el icono solo no tenga que explicarse por sí mismo.
        BackIconButton backBtn = new BackIconButton(
                panelLeft + IN_X1, y + (PanelButton.H - 20) / 2, 20, this::onClose);
        backBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.back")));
        addRenderableWidget(backBtn);

        PlayIconButton startBtn = new PlayIconButton(
                panelLeft + IN_X2 - 24, y + (PanelButton.H - 24) / 2, 24, this::startSession);
        startBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.training_hub.shadow.start")));
        addRenderableWidget(startBtn);

        int cx = panelLeft + ZenkaiMenuScreen.BG_W / 2;
        addRenderableWidget(new MinusIconButton(cx - 60, panelTop + stepperY, this::decrease));
        addRenderableWidget(new PlusIconButton(cx + 48, panelTop + stepperY, this::increase));
        // Duración de sesión (pedido explícito del usuario, ver DURATION_STEPS_SEC) — SU PROPIA
        // línea de etiqueta arriba (+20, sin botones) y los botones en la línea de abajo (+32,
        // no +20): "Session length: 30s" combinado en una sola línea se montaba con el botón +
        // (mismo bug real que MeditationScreen, "Difficulty: 100%" es más corto y sí cabía) — ver
        // el javadoc de clase de ShadowTrainingScreen para el mismo arreglo.
        addRenderableWidget(new MinusIconButton(cx - 60, panelTop + stepperY + 32, this::decreaseDuration));
        addRenderableWidget(new PlusIconButton(cx + 48, panelTop + stepperY + 32, this::increaseDuration));
    }

    private void buildResultsWidgets() {
        this.clearWidgets();
        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;
        BackIconButton backBtn = new BackIconButton(
                panelLeft + IN_X1, y + (PanelButton.H - 20) / 2, 20, this::onClose);
        backBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.back")));
        addRenderableWidget(backBtn);

        PlayIconButton retryBtn = new PlayIconButton(
                panelLeft + IN_X2 - 24, y + (PanelButton.H - 24) / 2, 24, this::startSession);
        retryBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.meditation.retry")));
        addRenderableWidget(retryBtn);
    }

    private void decrease() { stepIndex = Math.max(0, stepIndex - 1); requestInfo(); }
    private void increase() { stepIndex = Math.min(STEPS_PCT.length - 1, stepIndex + 1); requestInfo(); }

    private void decreaseDuration() { durationIndex = Math.max(0, durationIndex - 1); requestInfo(); }
    private void increaseDuration() { durationIndex = Math.min(DURATION_STEPS_SEC.length - 1, durationIndex + 1); requestInfo(); }

    /** Más difícil = orbes más seguidos, viven menos tiempo, y más probabilidad de que salga una
     *  bomba en vez de un orbe bueno — los 3 números que ya hacían de "perilla" implícita del
     *  minijuego (ver BASE_*), ahora escalados por el stepper en vez de fijos. Clamps generosos
     *  para que ni el extremo fácil se sienta AFK ni el difícil se vuelva literalmente imposible
     *  de leer en pantalla. */
    private void applyDifficulty() {
        double fraction = STEPS_PCT[stepIndex] / 100.0;
        spawnIntervalMs = clampLong(Math.round(BASE_SPAWN_INTERVAL_MS / fraction), 300, 1100);
        orbLifespanMs = clampLong(Math.round(BASE_ORB_LIFESPAN_MS / fraction), 700, 2200);
        bombChance = Math.min(0.5, BASE_BOMB_CHANCE * fraction);
    }

    private static long clampLong(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }

    private void startSession() {
        state = State.PLAYING;
        applyDifficulty();
        this.clearWidgets();
        orbs.clear();
        orbsPopped = 0;
        reported = false;
        lost = false;
        resultReward = null;
        sessionStartMs = System.currentTimeMillis();
        lastSpawnMs = sessionStartMs;

        // Botón "Finish" (pedido explícito del usuario tras la queja de que ESC salía sin
        // enseñar el TP conseguido) — mismo BackIconButton que Back/Meditation, esquina superior
        // izquierda: el HUD de juego usa (20,8) para "Popped: N" (desplazado, ver renderPlaying())
        // y el lado derecho para el tiempo restante, así que la esquina queda libre de sobra.
        BackIconButton finishBtn = new BackIconButton(8, 8, 16, this::finishSessionEarly);
        finishBtn.setTooltip(Tooltip.create(Component.translatable("screen.zenkai.training_hub.finish")));
        addRenderableWidget(finishBtn);
    }

    /** Termina la sesión YA (botón Finish o Escape, ver onClose()) y pasa a RESULTS con el reward
     *  REAL — mismo camino que una sesión completada del todo (tick() al agotar la duración),
     *  nunca el "silencio" que antes solo devolvía al hub sin enseñar nada. Único sitio que sabe
     *  terminar una sesión a medias, para que los dos disparadores no diverjan. */
    private void finishSessionEarly() {
        if (state != State.PLAYING) return;
        sendSessionReport(false);
        state = State.RESULTS;
        buildResultsWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (state != State.PLAYING) return;
        long now = System.currentTimeMillis();
        long elapsed = now - sessionStartMs;

        if (elapsed >= sessionDurationMs()) {
            finishSessionEarly();
            return;
        }

        if (now - lastSpawnMs >= spawnIntervalMs) {
            lastSpawnMs = now;
            int margin = 30;
            int x = margin + random.nextInt(Math.max(1, this.width - margin * 2));
            int y = 40 + random.nextInt(Math.max(1, this.height - 80));
            orbs.add(new Orb(x, y, now, random.nextDouble() < bombChance));
        }

        orbs.removeIf(o -> now - o.spawnMs() > orbLifespanMs);
    }

    /** @param bombHit true si la sesión terminó por tocar una bomba (solo informativo, ver
     *  RESULTS) — pedido explícito del usuario: tocar una bomba YA NO borra lo reventado hasta
     *  ese punto, `orbsPopped` viaja tal cual gane o pierda la ronda (ver el javadoc de
     *  TargetPracticeSessionPacket; antes esto ponía reportedOrbs a 0 cuando bombHit=true). */
    private void sendSessionReport(boolean bombHit) {
        if (reported) return;
        reported = true;
        long elapsedTicks = Math.max(1, Math.round((System.currentTimeMillis() - sessionStartMs) / 50.0));
        PacketDistributor.sendToServer(new TargetPracticeSessionPacket(
                orbsPopped, bombHit ? 1 : 0, (int) elapsedTicks));
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
        // Escape ahora pasa por RESULTS igual que el botón Finish (pedido explícito del usuario,
        // ver el mismo cambio en MeditationScreen.onClose()) — antes salía directo al hub sin
        // enseñar el reward, indistinguible de "no gané nada" aunque sí se hubiera concedido TP.
        if (state == State.PLAYING) {
            finishSessionEarly();
            return;
        }
        Minecraft.getInstance().setScreen(new TrainingHubScreen());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state == State.PLAYING && button == 0) {
            for (int i = orbs.size() - 1; i >= 0; i--) {
                Orb o = orbs.get(i);
                double dx = mouseX - o.x();
                double dy = mouseY - o.y();
                if (dx * dx + dy * dy <= ORB_RADIUS * ORB_RADIUS) {
                    orbs.remove(i);
                    if (o.bomb()) {
                        playBomb();
                        lost = true;
                        sendSessionReport(true);
                        state = State.RESULTS;
                        buildResultsWidgets();
                    } else {
                        orbsPopped++;
                        playPop();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Notas musicales de Minecraft como feedback (pedido explícito del usuario, mismo idioma
     *  que MeditationScreen) — pling agudo y subiendo con lo reventado para un acierto, bajo
     *  grave para la bomba. Cero asset nuevo. */
    private void playPop() {
        float pitch = 1.0f + Math.min(orbsPopped, 20) * 0.02f;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, 0.7f));
    }

    private void playBomb() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.8f));
    }

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
            case INTRO -> renderIntro(g);
            case PLAYING -> renderPlaying(g);
            case RESULTS -> renderResults(g);
        }
    }

    private void renderIntro(GuiGraphics g) {
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
        }
        ty += 10;
        if (introRecord != null) {
            PanelText.centeredOnPanel(g, this.font,
                    Component.translatable("screen.zenkai.training_hub.record", introRecord),
                    cx, ty, ZenkaiPalette.MUTED_ON_PANEL);
        }

        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.training_hub.shadow.difficulty", STEPS_PCT[stepIndex]),
                cx, panelTop + stepperY + 2, ZenkaiPalette.LABEL_ON_PANEL);
        // Etiqueta + valor separados — ver el javadoc de buildIntroWidgets() para el bug real que
        // esto arregla (mismo que MeditationScreen).
        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.meditation.session_length_label"),
                cx, panelTop + stepperY + 20, ZenkaiPalette.LABEL_ON_PANEL);
        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.meditation.session_length_value", DURATION_STEPS_SEC[durationIndex]),
                cx, panelTop + stepperY + 34, ZenkaiPalette.LABEL_ON_PANEL);
    }

    private void renderPlaying(GuiGraphics g) {
        long now = System.currentTimeMillis();
        for (Orb o : orbs) {
            double lifeRatio = (now - o.spawnMs()) / (double) orbLifespanMs;
            float alpha = (float) Math.max(0.15, 1.0 - lifeRatio);
            int u = o.bomb() ? ICON_BOMB_U : ICON_ORB_U;
            int v = o.bomb() ? ICON_BOMB_V : ICON_ORB_V;
            int size = ORB_RADIUS * 2;
            g.setColor(1f, 1f, 1f, alpha);
            g.blit(ICONS_TEX, o.x() - ORB_RADIUS, o.y() - ORB_RADIUS, size, size,
                    u, v, ICON_CELL, ICON_CELL, ICONS_ATLAS, ICONS_ATLAS);
            g.setColor(1f, 1f, 1f, 1f);
        }

        // x=44, no 20: deja hueco a la izquierda para el botón Finish (8,8,16px), ver startSession().
        PanelText.onDark(g, this.font,
                Component.translatable("screen.zenkai.target_practice.orbs_popped", orbsPopped),
                44, 8, ZenkaiPalette.TEXT);

        long elapsed = now - sessionStartMs;
        int secondsLeft = (int) Math.max(0, (sessionDurationMs() - elapsed) / 1000);
        PanelText.rightOnDark(g, this.font,
                Component.translatable("screen.zenkai.meditation.time_left", secondsLeft),
                this.width - 20, 8, ZenkaiPalette.TEXT);
    }

    private void renderResults(GuiGraphics g) {
        ScreenTitle.drawAbovePanel(g, this.font, this.title, panelLeft + ZenkaiMenuScreen.BG_W / 2, panelTop);
        int cx = panelLeft + ZenkaiMenuScreen.BG_W / 2;
        int ty = panelTop + 50;

        // LABEL_ON_PANEL + negrita, NO GOLD: ver el comentario equivalente en MeditationScreen.
        PanelText.centeredOnPanel(g, this.font,
                Component.translatable("screen.zenkai.meditation.result.title")
                        .copy().withStyle(net.minecraft.ChatFormatting.BOLD),
                cx, ty, ZenkaiPalette.LABEL_ON_PANEL);
        ty += 16;
        Component result = lost
                ? Component.translatable("screen.zenkai.target_practice.result.lost")
                : Component.translatable("screen.zenkai.target_practice.result.orbs_popped", orbsPopped);
        PanelText.centeredOnPanel(g, this.font, result, cx, ty, ZenkaiPalette.LABEL_ON_PANEL);
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
