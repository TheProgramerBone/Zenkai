package com.hmc.zenkai.client.gui.screens;

import com.hmc.zenkai.client.gui.PanelText;
import com.hmc.zenkai.client.gui.ScreenTitle;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.gui.buttons.PanelButton;
import com.hmc.zenkai.feature.training.TargetPracticeSessionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
 * Retry/Back.
 */
public class TargetPracticeScreen extends Screen implements TrainingMinigameScreen {

    private enum State { INTRO, PLAYING, RESULTS }

    private static final long SESSION_DURATION_MS = 30_000;
    private static final long SPAWN_INTERVAL_MS = 600;
    private static final long ORB_LIFESPAN_MS = 1500;
    private static final int ORB_RADIUS = 12;
    private static final double BOMB_CHANCE = 0.2;

    private static final int IN_X1 = 10;
    private static final int IN_X2 = 245;

    private record Orb(int x, int y, long spawnMs, boolean bomb) {}

    private final Random random = new Random();
    private final List<Orb> orbs = new ArrayList<>();

    private State state = State.INTRO;
    private long sessionStartMs = -1;
    private long lastSpawnMs = -1;
    private int orbsPopped = 0;
    private boolean reported = false;
    private boolean lost = false;
    private Integer resultReward = null;

    private int panelLeft, panelTop;
    private List<FormattedCharSequence> introLines;

    public TargetPracticeScreen() {
        super(Component.translatable("screen.zenkai.training_hub.row.target_practice"));
    }

    @Override
    protected void init() {
        panelLeft = (this.width - ZenkaiMenuScreen.BG_W) / 2;
        panelTop = (this.height - ZenkaiMenuScreen.BG_H) / 2;
        introLines = this.font.split(
                Component.translatable("screen.zenkai.target_practice.intro"), ZenkaiMenuScreen.BG_W - 24);
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
        orbs.clear();
        orbsPopped = 0;
        reported = false;
        lost = false;
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
            sendSessionReport(false);
            state = State.RESULTS;
            buildResultsWidgets();
            return;
        }

        if (now - lastSpawnMs >= SPAWN_INTERVAL_MS) {
            lastSpawnMs = now;
            int margin = 30;
            int x = margin + random.nextInt(Math.max(1, this.width - margin * 2));
            int y = 40 + random.nextInt(Math.max(1, this.height - 80));
            orbs.add(new Orb(x, y, now, random.nextDouble() < BOMB_CHANCE));
        }

        orbs.removeIf(o -> now - o.spawnMs() > ORB_LIFESPAN_MS);
    }

    private void sendSessionReport(boolean forfeit) {
        if (reported) return;
        reported = true;
        long elapsedTicks = Math.max(1, Math.round((System.currentTimeMillis() - sessionStartMs) / 50.0));
        int reportedOrbs = forfeit ? 0 : orbsPopped;
        PacketDistributor.sendToServer(new TargetPracticeSessionPacket(
                reportedOrbs, forfeit ? 1 : 0, (int) elapsedTicks));
    }

    @Override
    public void onRewardReceived(int tpGranted) {
        resultReward = tpGranted;
    }

    @Override
    public void onClose() {
        if (state == State.PLAYING) sendSessionReport(false);
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
                        lost = true;
                        sendSessionReport(true);
                        state = State.RESULTS;
                        buildResultsWidgets();
                    } else {
                        orbsPopped++;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
    }

    private void renderPlaying(GuiGraphics g) {
        long now = System.currentTimeMillis();
        for (Orb o : orbs) {
            double lifeRatio = (now - o.spawnMs()) / (double) ORB_LIFESPAN_MS;
            float alpha = (float) Math.max(0.15, 1.0 - lifeRatio);
            int color = o.bomb() ? ZenkaiPalette.ERROR : ZenkaiPalette.VALUE;
            int a = (int) (alpha * 255) << 24;
            int rgb = color & 0x00FFFFFF;
            g.fill(o.x() - ORB_RADIUS, o.y() - ORB_RADIUS, o.x() + ORB_RADIUS, o.y() + ORB_RADIUS,
                    a | rgb);
        }

        PanelText.onDark(g, this.font,
                Component.translatable("screen.zenkai.target_practice.orbs_popped", orbsPopped),
                20, 8, ZenkaiPalette.TEXT);

        long elapsed = now - sessionStartMs;
        int secondsLeft = (int) Math.max(0, (SESSION_DURATION_MS - elapsed) / 1000);
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
    }

    @Override public boolean isPauseScreen() { return false; }
}
