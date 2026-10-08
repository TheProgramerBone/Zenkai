package com.hmc.zenkai.client.clash;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.action.ActionStateClient;
import com.hmc.zenkai.feature.technique.clash.ClashPressPacket;
import com.hmc.zenkai.feature.technique.clash.ClashPressedPacket;
import com.hmc.zenkai.feature.technique.clash.ClashResultPacket;
import com.hmc.zenkai.feature.technique.clash.ClashRules;
import com.hmc.zenkai.feature.technique.clash.ClashStartPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estado CLIENTE del choque de haces del jugador local: parámetros de la barra, su fase, si ya
 * pulsó y el resultado. Lo llenan ClashStartPacket / ClashResultPacket; nada de aquí decide el
 * resultado (lo hace el servidor), solo lo pinta y manda "pulsé".
 *
 * INMOVILIDAD: el bloqueo del input NO depende de este estado sino del ActionState sincronizado
 * (ActionType.CLASH): es la fuente de verdad del servidor y se apaga solo en cualquier salida,
 * incluso si se pierde un packet de resultado. Este estado solo gobierna la barra.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ClashClientState {
    private ClashClientState() {}

    /** Ticks que se sigue enseñando el resultado tras recibirlo. */
    private static final int RESULT_SHOW_TICKS = 40;

    private static boolean active;
    private static int clashId;
    private static ClashRules.Params params;
    private static float seed;
    private static long startTick;

    private static String rivalName = "";
    private static int rivalEntityId = -1;
    private static boolean rivalIsPlayer;
    private static java.util.UUID rivalUuid = new java.util.UUID(0L, 0L);
    private static boolean rivalPressed;

    private static boolean pressed;
    private static double pressedValue;

    /** Ticks de juego de cada hito, solo para las animaciones del HUD (ClashBarOverlay). */
    private static long receivedAt, pressedAt, rivalPressedAt;

    private static int result = -1;
    private static int mine, rival, mineStage, rivalStage;
    private static long resultAt;

    public static boolean active() { return active; }
    public static boolean pressed() { return pressed; }
    public static double pressedValue() { return pressedValue; }
    public static int result() { return result; }
    public static int mineValue() { return mine; }
    public static int rivalValue() { return rival; }
    public static ClashRules.Params params() { return params; }
    public static int mineStage() { return mineStage; }
    public static int rivalStage() { return rivalStage; }

    public static String rivalName() { return rivalName; }
    /** Id de red de la entidad rival; con él la ficha saca la cara de un mob (ClashBarOverlay). */
    public static int rivalEntityId() { return rivalEntityId; }
    public static boolean rivalIsPlayer() { return rivalIsPlayer; }
    public static java.util.UUID rivalUuid() { return rivalUuid; }
    public static boolean rivalPressed() { return rivalPressed; }

    public static long receivedAt() { return receivedAt; }
    public static long pressedAt() { return pressedAt; }
    public static long rivalPressedAt() { return rivalPressedAt; }
    public static long resultAt() { return resultAt; }

    public static void rivalPressed(ClashPressedPacket p) {
        if (active && p.clashId() == clashId && !rivalPressed) {
            rivalPressed = true;
            rivalPressedAt = now();
        }
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    public static void start(ClashStartPacket p) {
        active = true;
        clashId = p.clashId();
        rivalName = p.rivalName();
        rivalEntityId = p.rivalEntityId();
        rivalIsPlayer = p.rivalIsPlayer();
        rivalUuid = p.rivalUuid();
        rivalPressed = false;
        params = p.params();
        seed = p.seed();
        startTick = p.minigameStartTick();
        pressed = false;
        pressedValue = 0.0;
        result = -1;
        receivedAt = now();
    }

    public static void result(ClashResultPacket p) {
        if (!active || p.clashId() != clashId) return;
        if (p.result() == ClashResultPacket.ABORT) { clear(); return; }
        result = p.result();
        mine = p.mine();
        rival = p.rival();
        mineStage = p.mineStage();
        rivalStage = p.rivalStage();
        resultAt = now();
    }

    public static void clear() {
        active = false;
        result = -1;
        pressed = false;
    }

    /** Ticks (con parcial) desde que arrancó la barra; negativo mientras dura el contacto. */
    public static double elapsed(long gameTime, float partial) {
        return gameTime + partial - startTick;
    }

    /** ¿Está la barra en marcha (ya empezó, no acabó el plazo y no hay resultado)? */
    public static boolean running(long gameTime) {
        return active && result < 0 && params != null
                && gameTime >= startTick && gameTime < startTick + params.duration();
    }

    /** Posición del cursor ahora mismo (o la fijada al pulsar). */
    public static double cursorNow(long gameTime, float partial) {
        if (pressed) return pressedValue;
        double t = Math.max(0.0, Math.min(params.duration(), elapsed(gameTime, partial)));
        return ClashRules.cursor(t, seed, params);
    }

    /** Sentido en que se mueve ahora el cursor: +1 subiendo, -1 bajando, 0 si está fijado. Solo
     *  orienta la estela del HUD. */
    public static int cursorDirection(long gameTime, float partial) {
        if (pressed || params == null) return 0;
        double t = Math.max(0.0, Math.min(params.duration(), elapsed(gameTime, partial)));
        double before = ClashRules.cursor(Math.max(0.0, t - 0.25), seed, params);
        return ClashRules.cursor(t, seed, params) >= before ? 1 : -1;
    }

    public static boolean beforeStart(long gameTime) { return active && gameTime < startTick; }

    public static int stageAt(long gameTime, float partial) {
        if (params == null) return 0;
        return ClashRules.stage(Math.max(0.0, elapsed(gameTime, partial)), params);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { if (active) clear(); return; }
        long now = mc.level.getGameTime();

        if (active) {
            if (result >= 0 && now - resultAt > RESULT_SHOW_TICKS) clear();
            // Red de seguridad: si el servidor ya no nos tiene en CLASH y el plazo pasó hace rato,
            // la barra no puede quedarse colgada en pantalla aunque se perdiera el packet final.
            else if (params != null && !ActionStateClient.local().inClash()
                    && now > startTick + params.duration() + RESULT_SHOW_TICKS) clear();
        }
        if (!active) return;

        // Pulsación: clic izquierdo o derecho. Se consumen SIEMPRE mientras dura el choque para
        // que ni el golpe vanilla ni la guardia se disparen por debajo.
        boolean click = false;
        while (mc.options.keyAttack.consumeClick()) click = true;
        while (mc.options.keyUse.consumeClick()) click = true;
        if (click && !pressed && running(now) && mc.screen == null) {
            pressed = true;
            pressedValue = cursorNow(now, 0f);
            pressedAt = now;
            PacketDistributor.sendToServer(new ClashPressPacket(clashId, now));
            // «Clac» seco y local: tiene que sonar a fijar una posición, no a botón de menú.
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON, 0.6f, 0.9f));
        }
    }

    /** Mientras el servidor nos tiene en CLASH no hay movimiento: ni andar, ni saltar, ni
     *  agacharse, ni subir o bajar volando. Anula el input, no la velocidad: el servidor ya la
     *  mantiene a cero y esto evita que el cliente la vuelva a predecir. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent e) {
        if (!ActionStateClient.local().inClash()) return;
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
}
