package com.hmc.zenkai.client.input;

import com.hmc.zenkai.client.CombatModeClientState;
import com.hmc.zenkai.client.LockOnClientState;
import com.hmc.zenkai.client.ScouterClientState;
import com.hmc.zenkai.client.SenseKiClientState;
import com.hmc.zenkai.client.gui.screens.RaceSelectionScreen;
import com.hmc.zenkai.client.gui.screens.StatsScreen;
import com.hmc.zenkai.client.gui.screens.StyleSelectionScreen;
import com.hmc.zenkai.core.network.feature.ki.KiChargePacket;
import com.hmc.zenkai.core.network.feature.ki.PowerPercentPacket;
import com.hmc.zenkai.core.network.feature.ki.ToggleFlyPacket;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.stats.TransformHoldPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

    private KeyBindings() {}

    public static KeyMapping OPEN_STATS;
    public static KeyMapping TOGGLE_FLY;
    public static KeyMapping CHARGE_KI;
    public static KeyMapping SENSE_KI;
    public static KeyMapping TURBO;
    public static KeyMapping COMBAT_MODE;
    public static KeyMapping LOCK_ON;

    /** Z: baja el % de poder en escalones (Ki Control). */
    public static KeyMapping POWER_DOWN;

    /** B: toque = destransformar, sostenido = transformar (la máquina de hold es del servidor). */
    public static KeyMapping FORM;

    private static boolean REGISTERED = false;

    private static boolean lastChargeSent = false;
    private static boolean lastTransformSent = false;

    /** Soltar FORM antes de esto = toque (destransformar). Más largo = intento de transformación. */
    private static final int FORM_TAP_MAX_TICKS = 6;
    private static int formHeldTicks = 0;

    // Llamado SOLO desde tu evento RegisterKeyMappingsEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        if (REGISTERED) return;
        REGISTERED = true;

        OPEN_STATS = new KeyMapping(
                "key.zenkai.open_stats",
                GLFW.GLFW_KEY_V,
                "key.categories.zenkai"
        );
        event.register(OPEN_STATS);

        TOGGLE_FLY = new KeyMapping(
                "key.zenkai.toggle_fly",
                GLFW.GLFW_KEY_G,
                "key.categories.zenkai"
        );
        event.register(TOGGLE_FLY);

        // C = cargar KI (se mantiene)
        CHARGE_KI = new KeyMapping(
                "key.zenkai.charge_ki",
                GLFW.GLFW_KEY_C,
                "key.categories.zenkai"
        );
        event.register(CHARGE_KI);

        // Z = bajar el % de poder
        POWER_DOWN = new KeyMapping(
                "key.zenkai.power_down",
                GLFW.GLFW_KEY_Z,
                "key.categories.zenkai"
        );
        event.register(POWER_DOWN);

        // B = transformar (hold) / destransformar (tap)
        FORM = new KeyMapping(
                "key.zenkai.form",
                GLFW.GLFW_KEY_H,
                "key.categories.zenkai"
        );
        event.register(FORM);

        SENSE_KI = new KeyMapping("key.zenkai.sense_ki", GLFW.GLFW_KEY_F4, "key.categories.zenkai");
        event.register(SENSE_KI);

        TURBO = new KeyMapping(
                "key.zenkai.turbo",
                GLFW.GLFW_KEY_R,
                "key.categories.zenkai"
        );
        event.register(TURBO);

        COMBAT_MODE = new KeyMapping(
                "key.zenkai.combat_mode",
                GLFW.GLFW_KEY_X,
                "key.categories.zenkai"
        );
        event.register(COMBAT_MODE);

        LOCK_ON = new KeyMapping(
                "key.zenkai.lock_on",
                GLFW.GLFW_KEY_LEFT_ALT,
                "key.categories.zenkai"
        );
        event.register(LOCK_ON);
    }

    /**
     * Esto lo sigues llamando desde tu InputEvent.Key (como lo tenías antes).
     * Aquí solo manejamos acciones tipo "consumeClick".
     */
    public static void handleKeyInput(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerStatsAttachment stats = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        boolean hasRace = stats.isRaceChosen();

        // OPEN_STATS (V)
        if (OPEN_STATS != null && OPEN_STATS.consumeClick()) {
            if (!stats.isRaceChosen()) {
                mc.setScreen(new RaceSelectionScreen()); // nuevo “RaceScreen real”
            } else if (!stats.isStyleChosen()) {
                mc.setScreen(new StyleSelectionScreen(null,null,null)); // ya tiene raza, falta estilo
            } else {
                mc.setScreen(new StatsScreen());
            }
            return;
        }

        // TOGGLE_FLY (G)
        if (hasRace && TOGGLE_FLY != null && TOGGLE_FLY.consumeClick()) {
            PacketDistributor.sendToServer(new ToggleFlyPacket());
        }

        if (SENSE_KI != null && SENSE_KI.consumeClick()) {
            SenseKiClientState.onKeyPress(mc);
        }
    }

    /**
     * IMPORTANTÍSIMO:
     * Llama esto UNA VEZ por tick desde tu ClientTickEvent.Post (donde ya haces PAL o client logic).
     * Ejemplo en tu handler:
     * public static void onClientTick(ClientTickEvent.Post e) { KeyBindings.handleClientTick(); }
     */
    public static void handleClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var player = mc.player;
        SenseKiClientState.tick(mc);
        ScouterClientState.tick(mc);
        CombatModeClientState.tick(mc);
        PlayerStatsAttachment stats = player.getData(DataAttachments.PLAYER_STATS.get());
        boolean hasRace = stats.isRaceChosen();

        if (LOCK_ON != null) {
            while (LOCK_ON.consumeClick()) LockOnClientState.toggle(mc);
        }
        LockOnClientState.tick(mc);

        // Gate: sin raza, cortar y vaciar colas. Sin el drenaje, los clicks se acumulan
        // y se disparan todos de golpe la próxima vez que alguien los consuma.
        if (!hasRace) {
            stopChargeIfNeeded();
            stopTransformHoldIfNeeded();
            drainClicks(CHARGE_KI);
            drainClicks(POWER_DOWN);
            drainClicks(FORM);
            drainClicks(COMBAT_MODE);
            formHeldTicks = 0;
            return;
        }

        // ── B: sostenido = transformar, toque = volver a base ──────────────
        boolean formDown = false;
        if (FORM != null) {
            drainClicks(FORM); // el estado lo lleva formHeldTicks, no la cola
            formDown = FORM.isDown();

            if (formDown) {
                formHeldTicks++;
            } else if (formHeldTicks > 0) {
                // Toque corto = destransformar. Un hold largo abortado no hace nada:
                // el servidor ya descartó el progreso al dejar de recibir transformHeld.
                if (formHeldTicks <= FORM_TAP_MAX_TICKS) {
                    PacketDistributor.sendToServer(new TransformHoldPacket(
                            TransformHoldPacket.Action.DETRANSFORM, true));
                }
                formHeldTicks = 0;
            }

            if (formDown != lastTransformSent) {
                lastTransformSent = formDown;

                // feedback local
                var form = player.getData(DataAttachments.PLAYER_FORM.get());
                form.setTransformHeld(formDown);

                PacketDistributor.sendToServer(new TransformHoldPacket(
                        TransformHoldPacket.Action.TRANSFORM_HOLD, formDown));
            }
        }

        // ── Z: bajar el % de poder, escalón a escalón ──────────────────────
        if (POWER_DOWN != null) {
            while (POWER_DOWN.consumeClick()) {
                PacketDistributor.sendToServer(new PowerPercentPacket());
            }
        }

        // ── C: cargar ki (el % de poder sube solo). Sin modificadores. ─────
        if (formDown) {
            // Transformando no se carga ki: respeta la animación de transformación.
            stopChargeIfNeeded();
            drainClicks(CHARGE_KI);
        } else if (CHARGE_KI != null) {
            drainClicks(CHARGE_KI); // se lee por isDown(): hay que vaciar la cola igual
            boolean now = CHARGE_KI.isDown();
            if (now != lastChargeSent) {
                lastChargeSent = now;
                PacketDistributor.sendToServer(new KiChargePacket(now));
            }
        }

        while (COMBAT_MODE.consumeClick()) {
            CombatModeClientState.toggle(mc);
        }
    }

    /** Vacía la cola de clicks de una tecla que se lee por isDown(). Sin esto se acumulan
     *  y se disparan de golpe cuando alguien las consuma (era el bug del % de poder). */
    private static void drainClicks(KeyMapping key) {
        if (key == null) return;
        while (key.consumeClick()) { /* descartar */ }
    }

    private static void stopChargeIfNeeded() {
        if (lastChargeSent) {
            lastChargeSent = false;
            PacketDistributor.sendToServer(new KiChargePacket(false));
        }
    }

    private static void stopTransformHoldIfNeeded() {
        if (lastTransformSent) {
            lastTransformSent = false;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var form = mc.player.getData(DataAttachments.PLAYER_FORM.get());
                form.setTransformHeld(false);
            }

            PacketDistributor.sendToServer(new TransformHoldPacket(
                    TransformHoldPacket.Action.TRANSFORM_HOLD, false
            ));
        }
    }
}
