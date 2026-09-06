package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.feature.training.MeditationSessionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Meditation" (TrainingHubScreen): ritmo estilo FNF — 4 carriles (A/S/D/F), las notas caen
 * hacia una zona de impacto fija; acertar en ventana sube la racha, fallar la corta. Ninguna
 * "canción" real: un generador procedural (spawnea una nota en un carril al azar a intervalo
 * fijo) evita tener que autorar charts.
 *
 * Tres estados (INTRO -> PLAYING -> RESULTS), pedido explícito del usuario tras ver la v1 (que
 * arrancaba la sesión sola al abrir la pantalla): INTRO explica qué hacer y tiene Start/Back;
 * PLAYING es el minijuego a pantalla completa, sin botones (Escape reporta lo hecho y sale, ver
 * onClose()); RESULTS enseña el reward REAL (no una estimación del cliente) + Retry/Back.
 *
 * ANTI-TRAMPA: la sesión entera se reporta como desempeño CRUDO (notas acertadas + racha máxima
 * + duración), nunca como un TP ya calculado — el servidor decide cuánto vale eso
 * (MeditationSessionPacket) y responde con el TP REAL vía TrainingSessionRewardPacket
 * (ver TrainingMinigameScreen/ClientPayloadHandlers.onTrainingReward), que es lo que RESULTS
 * enseña — nunca una cifra adivinada en el cliente.
 *
 * `extends Screen` directamente (canvas propio) pero reusa el fondo/tamaño de panel de
 * ZenkaiMenuScreen (BG_TEX/BG_W/BG_H, protected + mismo paquete = accesible) para INTRO/RESULTS,
 * sin heredar de ella — la fase PLAYING necesita la pantalla ENTERA para los carriles, cosa que
 * ZenkaiMenuScreen (con su barra de pestañas fija) no puede dar. Sigue el convenio de orden de
 * render de CLAUDE.md: renderBackground() pinta DEBAJO de super.render(), el contenido va DESPUÉS.
 */
public class MeditationScreen extends Screen implements TrainingMinigameScreen {

    private enum State { INTRO, PLAYING, RESULTS }

    private static final int LANES = 4;
    private static final int[] KEYS = {
            GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_F
    };
    private static final String[] KEY_LABELS = {"A", "S", "D", "F"};

    private static final long SESSION_DURATION_MS = 30_000;
    private static final long SPAWN_INTERVAL_MS = 450;
    private static final long TRAVEL_MS = 1400;
    /** Ventana de acierto: |ratio-1.0| <= esto se cuenta como golpe válido. */
    private static final double HIT_WINDOW = 0.12;

    private static final int LANE_W = 40;
    private static final int LANE_GAP = 8;
    private static final int NOTE_H = 14;
    private static final int HIT_ZONE_Y_OFFSET = 60; // desde el borde inferior

    private static final int IN_X1 = 10;
    private static final int IN_X2 = 245;

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

    private int panelLeft, panelTop;
    private List<FormattedCharSequence> introLines;

    public MeditationScreen() {
        super(Component.translatable("screen.zenkai.training_hub.row.meditation"));
    }

    @Override
    protected void init() {
        panelLeft = (this.width - ZenkaiMenuScreen.BG_W) / 2;
        panelTop = (this.height - ZenkaiMenuScreen.BG_H) / 2;
        introLines = this.font.split(
                Component.translatable("screen.zenkai.meditation.intro"), ZenkaiMenuScreen.BG_W - 24);
        buildIntroWidgets();
    }

    private void buildIntroWidgets() {
        this.clearWidgets();
        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;
        addRenderableWidget(PanelButton.secondary(panelLeft + IN_X1, y,
                Component.translatable("screen.zenkai.back"), this::onClose));
        addRenderableWidget(PanelButton.primary(panelLeft + IN_X2 - PanelButton.W, y,
                Component.translatable("screen.zenkai.training_hub.shadow.start"), this::startSession));
    }

    private void buildResultsWidgets() {
        this.clearWidgets();
        int y = panelTop + ZenkaiMenuScreen.BG_H - 12 - PanelButton.H;
        addRenderableWidget(PanelButton.secondary(panelLeft + IN_X1, y,
                Component.translatable("screen.zenkai.back"), this::onClose));
        addRenderableWidget(PanelButton.primary(panelLeft + IN_X2 - PanelButton.W, y,
                Component.translatable("screen.zenkai.meditation.retry"), this::startSession));
    }

    private void startSession() {
        state = State.PLAYING;
        this.clearWidgets();
        notes.clear();
        notesHit = 0;
        combo = 0;
        maxCombo = 0;
        reported = false;
        resultReward = null;
        sessionStartMs = System.currentTimeMillis();
        lastSpawnMs = sessionStartMs;
    }

    @Override
    public void tick() {
        super.tick();
        if (state != State.PLAYING) return;
        long now = System.currentTimeMillis();
        long elapsed = now - sessionStartMs;

        if (elapsed >= SESSION_DURATION_MS) {
            sendSessionReport();
            state = State.RESULTS;
            buildResultsWidgets();
            return;
        }

        if (now - lastSpawnMs >= SPAWN_INTERVAL_MS) {
            lastSpawnMs = now;
            notes.add(new Note(random.nextInt(LANES), now));
        }

        // Notas que ya pasaron la ventana de acierto sin pulsarse: fallo, corta la racha.
        notes.removeIf(n -> {
            double ratio = (now - n.spawnMs()) / (double) TRAVEL_MS;
            if (ratio > 1.0 + HIT_WINDOW) {
                combo = 0;
                return true;
            }
            return false;
        });
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
    public void onRewardReceived(int tpGranted) {
        resultReward = tpGranted;
    }

    @Override
    public void onClose() {
        // Escape a mitad de partida reporta lo ya hecho (mismo espíritu que "sesión
        // interrumpida cuenta") y sale directo al hub sin pasar por RESULTS — ver un resultado
        // parcial no aporta nada que el jugador no supiera ya si él mismo decidió salir.
        if (state == State.PLAYING) sendSessionReport();
        Minecraft.getInstance().setScreen(new TrainingHubScreen());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state == State.PLAYING) {
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
        }
        // Pulsar sin nota en ventana no rompe la racha a propósito: castigar solo dejar pasar
        // una nota (el "miss" real), no un roce de tecla de más en un ritmo de 4 carriles.
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
    }

    private void renderPlaying(GuiGraphics g) {
        int left = lanesLeft();
        int hitY = hitZoneY();
        long now = System.currentTimeMillis();

        for (int lane = 0; lane < LANES; lane++) {
            int x = left + lane * (LANE_W + LANE_GAP);
            g.fill(x, 20, x + LANE_W, this.height - 20, ZenkaiPalette.POPUP_BG);
            g.fill(x, hitY, x + LANE_W, hitY + 4, ZenkaiPalette.OK);
            PanelText.onDark(g, this.font, Component.literal(KEY_LABELS[lane]),
                    x + LANE_W / 2 - 3, hitY + 8, ZenkaiPalette.TEXT);
        }

        for (Note n : notes) {
            double ratio = (now - n.spawnMs()) / (double) TRAVEL_MS;
            int y = (int) (20 + ratio * (hitY - 20));
            int x = left + n.lane() * (LANE_W + LANE_GAP);
            g.fill(x + 2, y, x + LANE_W - 2, y + NOTE_H, ZenkaiPalette.VALUE);
        }

        long elapsed = now - sessionStartMs;
        int secondsLeft = (int) Math.max(0, (SESSION_DURATION_MS - elapsed) / 1000);
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
                        .copy().withStyle(net.minecraft.ChatFormatting.BOLD),
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
    }

    @Override public boolean isPauseScreen() { return false; }
}
