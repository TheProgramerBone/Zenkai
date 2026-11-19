package com.hmc.zenkai.client.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.combat.GrabEscapePressPacket;
import com.hmc.zenkai.feature.combat.GrabSkillCheck;
import com.hmc.zenkai.feature.combat.GrabStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lado cliente del AGARRE para quien está agarrado: guarda el estado que manda GrabStatePacket, le anula el
 * movimiento, se queda con sus clics y su espacio (que ya no golpean ni saltan: son el botón del skill check)
 * y dibuja la barra con la aguja y la zona de acierto.
 *
 * La posición de la aguja y la zona salen de GrabSkillCheck, la MISMA matemática con la que el servidor juzga la
 * pulsación; aquí solo se pinta y se avisa de qué check era y cuántos ticks llevaba la aguja. El acierto lo decide
 * el servidor (PhysicalCombatServer.onEscapePress).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class GrabClientState {
    private GrabClientState() {}

    private static boolean held;
    private static int checkIndex = -1;
    private static long seed;
    private static float zoneSize = 0.2f;
    private static int needleTicks = 18;
    private static int checksDone, checksNeeded;
    private static int holdLeft;
    /** Instante (ticks de juego + parcial) en que llegó el último paquete: origen de la aguja y del tiempo restante. */
    private static double stateAt;
    private static boolean pressed;
    private static int lastResult;
    private static double resultAt = -100;

    /** Posición (0..1) donde quedó la aguja al pulsar o al caducar: se queda fija durante el destello del resultado. */
    private static double frozenPos = -1;

    // ── HUD propio (atlas grab_hud.png; fuente única: tools/gen_grab_hud.py, sus coordenadas son estas constantes). Un
    //    solo panel opaco que contiene cada elemento: título, barra, progreso y pista. ──
    private static final net.minecraft.resources.ResourceLocation TEX =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/hud/grab_hud.png");
    private static final int TEX_W = 256, TEX_H = 64;
    private static final int PANEL_W = 184, PANEL_H = 60;
    private static final int TRACK_X = 13, TRACK_Y = 25, TRACK_W = 158, TRACK_H = 12;
    private static final int U_KEY = 186, KEY_W = 46, KEY_H = 13;
    private static final int U_WARN = 186, V_WARN = 14, WARN = 11;
    private static final int V_ROW = 14;
    private static final int U_NEEDLE = 200, U_NEEDLE_GOLD = 208, NEEDLE_W = 5, NEEDLE_H = 20;
    private static final int U_DIAMOND_OFF = 216, U_DIAMOND_ON = 224, DIAMOND = 7;
    private static final int U_ZONE = 232, ZONE_TILE = 4;
    private static final int C_OFFWHITE = 0xFFD9D9DE, C_MUTED = 0xFF74747F, C_GOLD = 0xFFFFD84A;
    private static final double HUD_SCALE = 2.0;
    /** Separación entre el centro de la pantalla y el borde superior del panel (en píxeles de GUI ya escalados). */
    private static final int PANEL_BELOW_CROSSHAIR = 30;
    private static final double FLASH_TICKS = 10;

    public static void accept(GrabStatePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!p.held()) { reset(); return; }
        double now = mc.level.getGameTime();
        boolean newCheck = p.checkIndex() != checkIndex;
        held = true;
        checkIndex = p.checkIndex();
        seed = p.seed();
        zoneSize = p.zoneSize();
        needleTicks = p.needleTicks();
        checksDone = p.checksDone();
        checksNeeded = p.checksNeeded();
        holdLeft = p.holdTicks();
        stateAt = now;
        if (newCheck) pressed = false;
        if (p.lastResult() != 0) {
            lastResult = p.lastResult();
            resultAt = now;
            if (!pressed) frozenPos = 0.5;   // caducó sin pulsar: la aguja no tiene posición que enseñar
        }
    }

    private static void reset() {
        held = false;
        checkIndex = -1;
        pressed = false;
    }

    /** ¿Está el jugador local sujeto por un agarre? Caduca solo si el aviso de soltar se pierde. */
    public static boolean isHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (!held || mc.level == null) return false;
        // holdLeft > 1 millón de ticks = agarre sin límite: no caduca por tiempo, solo por el aviso de soltar.
        if (holdLeft < 1_000_000 && mc.level.getGameTime() - stateAt > holdLeft + 60) { reset(); return false; }
        return true;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { reset(); }

    /** Agarrado = ni andar, ni saltar, ni agacharse, ni volar: sujeto delante del agarrador. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent e) {
        if (!isHeld()) return;
        Input in = e.getInput();
        in.forwardImpulse = 0f;
        in.leftImpulse = 0f;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** ESPACIO = pulsar el check. Los clics se CONSUMEN siempre mientras se está agarrado (no hay golpe) pero no cuentan. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !isHeld() || mc.screen != null) return;
        boolean press = false;
        while (mc.options.keyAttack.consumeClick()) { /* el clic NO pulsa el check: solo se descarta */ }
        while (mc.options.keyJump.consumeClick()) press = true;
        mc.options.keyAttack.setDown(false);
        if (!press || pressed || checkIndex < 0) return;
        pressed = true;
        float elapsed = (float) (mc.level.getGameTime() - stateAt);
        frozenPos = GrabSkillCheck.needlePos(elapsed, needleTicks);
        PacketDistributor.sendToServer(new GrabEscapePressPacket(checkIndex, elapsed));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !isHeld()) return;
        GuiGraphics g = e.getGuiGraphics();
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;

        // Escala fija x2 con una transformación del PoseStack (como el HUD del choque): es un overlay sin clics.
        double realScale = mc.getWindow().getGuiScale();
        float k = (float) (Math.min(HUD_SCALE, realScale) / realScale);
        g.pose().pushPose();
        g.pose().scale(k, k, 1f);
        int w = Math.round(g.guiWidth() / k), h = Math.round(g.guiHeight() / k);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        int px = w / 2 - PANEL_W / 2;
        int py = h / 2 + PANEL_BELOW_CROSSHAIR;
        int tx = px + TRACK_X, ty = py + TRACK_Y;
        boolean active = checkIndex >= 0;
        double since = now - resultAt;
        boolean flashing = since >= 0 && since < FLASH_TICKS;

        g.blit(TEX, px, py, 0, 0, PANEL_W, PANEL_H, TEX_W, TEX_H);

        if (active) {
            // Zona de acierto: la trama dorada se repite a lo ancho, con filos blancos y un pulso suave que la
            // hace latir (alfa que sube y baja sobre la propia zona) para que se lea de un vistazo dónde pulsar.
            double zs = GrabSkillCheck.zoneStart(seed, checkIndex, zoneSize);
            int zx0 = tx + (int) Math.round(zs * (TRACK_W - 1));
            int zx1 = tx + (int) Math.round((zs + zoneSize) * (TRACK_W - 1)) + 1;
            for (int x = zx0; x < zx1; x += ZONE_TILE) {
                g.blit(TEX, x, ty, U_ZONE, V_ROW, Math.min(ZONE_TILE, zx1 - x), TRACK_H, TEX_W, TEX_H);
            }
            int pulse = (int) (60 * (0.5 + 0.5 * Math.sin(now * 0.45)));
            g.fill(zx0, ty, zx1, ty + TRACK_H, (pulse << 24) | 0xFFFFFF);
            g.fill(zx0, ty, zx0 + 1, ty + TRACK_H, 0xFFFFFFFF);
            g.fill(zx1 - 1, ty, zx1, ty + TRACK_H, 0xFFFFFFFF);
        }

        // Aguja: viva mientras hay check sin pulsar; fija (dorada si acertó) durante el destello del resultado.
        double pos = -1;
        boolean gold = false;
        if (active && !pressed) {
            pos = GrabSkillCheck.needlePos(Math.max(0.0, now - stateAt), needleTicks);
        } else if (flashing && frozenPos >= 0) {
            pos = frozenPos;
            gold = lastResult == 1;
        } else if (active) {
            pos = frozenPos;
            gold = true;   // pulsado y a la espera del veredicto del servidor
        }
        if (pos >= 0) {
            int x = tx + (int) Math.round(pos * (TRACK_W - 1));
            // Halo tenue detrás de la aguja (dorado si está fijada).
            g.fill(x - 3, ty, x + 4, ty + TRACK_H, (40 << 24) | (gold ? 0xFFD84A : 0xFFFFFF));
            g.blit(TEX, x - NEEDLE_W / 2, ty + (TRACK_H - NEEDLE_H) / 2, gold ? U_NEEDLE_GOLD : U_NEEDLE, V_ROW,
                    NEEDLE_W, NEEDLE_H, TEX_W, TEX_H);
        }

        // Resultado: el borde del hueco de la barra destella en verde (acierto) o rojo (fallo).
        if (flashing) {
            int a = (int) (230 * (1.0 - since / FLASH_TICKS));
            int col = (a << 24) | (lastResult == 1 ? 0x62D670 : 0xE05A5A);
            g.fill(tx - 1, ty - 1, tx + TRACK_W + 1, ty, col);
            g.fill(tx - 1, ty + TRACK_H, tx + TRACK_W + 1, ty + TRACK_H + 1, col);
            g.fill(tx - 1, ty, tx, ty + TRACK_H, col);
            g.fill(tx + TRACK_W, ty, tx + TRACK_W + 1, ty + TRACK_H, col);
        }

        // Cabecera: icono de aviso + título a la izquierda, rombos de progreso a la derecha.
        var font = mc.font;
        g.blit(TEX, px + 7, py + 3, U_WARN, V_WARN, WARN, WARN, TEX_W, TEX_H);
        g.drawString(font, Component.translatable("zenkai.grab.title"), px + 22, py + 5, C_GOLD, true);
        int total = Math.max(1, checksNeeded);
        int dx = px + PANEL_W - 9 - (total * (DIAMOND + 3) - 3);
        for (int i = 0; i < total; i++) {
            g.blit(TEX, dx + i * (DIAMOND + 3), py + 5, i < checksDone ? U_DIAMOND_ON : U_DIAMOND_OFF, V_ROW,
                    DIAMOND, DIAMOND, TEX_W, TEX_H);
        }

        // Pie: la tecla (espacio) y qué hacer con ella. La tecla se hunde un píxel mientras la aguja está pulsada.
        int ky = py + 45 + (active && pressed ? 1 : 0);
        g.blit(TEX, px + 8, ky, U_KEY, 0, KEY_W, KEY_H, TEX_W, TEX_H);
        g.drawCenteredString(font, Component.translatable("zenkai.grab.key"), px + 8 + KEY_W / 2, ky + 2, 0xFFFFFFFF);
        g.drawString(font, Component.translatable(active ? "zenkai.grab.press" : "zenkai.grab.wait"),
                px + 8 + KEY_W + 8, py + 47, active ? C_OFFWHITE : C_MUTED, false);

        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        g.pose().popPose();
    }
}
