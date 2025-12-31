package com.hmc.db_renewed.client.input;

import com.hmc.db_renewed.client.gui.screens.RaceAppearanceScreen;
import com.hmc.db_renewed.client.gui.screens.StatsScreen;
import com.hmc.db_renewed.client.gui.screens.StyleSelectionScreen;
import com.hmc.db_renewed.core.network.feature.ki.KiChargePacket;
import com.hmc.db_renewed.core.network.feature.ki.ToggleFlyPacket;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.stats.TransformHoldPacket;
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

    /** Modificador configurable para transformar + C (default ALT) */
    public static KeyMapping TRANSFORM_MOD;

    /** Modificador configurable para destransformar + C (default SHIFT) */
    public static KeyMapping DETRANSFORM_MOD;

    private static boolean REGISTERED = false;

    private static boolean lastChargeSent = false;
    private static boolean lastTransformSent = false;

    // Detransform hold 3s = 60 ticks
    private static final int DETRANSFORM_HOLD_REQUIRED_TICKS = 60;
    private static int detransformHoldTicks = 0;
    private static boolean detransformTriggered = false;

    // Llamado SOLO desde tu evento RegisterKeyMappingsEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        if (REGISTERED) return;
        REGISTERED = true;

        OPEN_STATS = new KeyMapping(
                "key.db_renewed.open_stats",
                GLFW.GLFW_KEY_V,
                "key.categories.db_renewed"
        );
        event.register(OPEN_STATS);

        TOGGLE_FLY = new KeyMapping(
                "key.db_renewed.toggle_fly",
                GLFW.GLFW_KEY_G,
                "key.categories.db_renewed"
        );
        event.register(TOGGLE_FLY);

        // C = cargar KI (se mantiene)
        CHARGE_KI = new KeyMapping(
                "key.db_renewed.charge_ki",
                GLFW.GLFW_KEY_C,
                "key.categories.db_renewed"
        );
        event.register(CHARGE_KI);

        // Modificador para transformar (default ALT)
        TRANSFORM_MOD = new KeyMapping(
                "key.db_renewed.transform_modifier",
                GLFW.GLFW_KEY_LEFT_ALT,
                "key.categories.db_renewed"
        );
        event.register(TRANSFORM_MOD);

        // Modificador para destransformar (default SHIFT)
        DETRANSFORM_MOD = new KeyMapping(
                "key.db_renewed.detransform_modifier",
                GLFW.GLFW_KEY_LEFT_SHIFT,
                "key.categories.db_renewed"
        );
        event.register(DETRANSFORM_MOD);
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
                mc.setScreen(new RaceAppearanceScreen()); // nuevo “RaceScreen real”
            } else if (!stats.isStyleChosen()) {
                mc.setScreen(new StyleSelectionScreen(null)); // ya tiene raza, falta estilo
            } else {
                mc.setScreen(new StatsScreen());
            }
            return;
        }

        // TOGGLE_FLY (G)
        if (hasRace && TOGGLE_FLY != null && TOGGLE_FLY.consumeClick()) {
            PacketDistributor.sendToServer(new ToggleFlyPacket());
        }
    }

    /**
     * IMPORTANTÍSIMO:
     * Llama esto UNA VEZ por tick desde tu ClientTickEvent.Post (donde ya haces PAL o client logic).
     *
     * Ejemplo en tu handler:
     * @SubscribeEvent
     * public static void onClientTick(ClientTickEvent.Post e) { KeyBindings.handleClientTick(); }
     */
    public static void handleClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var player = mc.player;

        PlayerStatsAttachment stats = player.getData(DataAttachments.PLAYER_STATS.get());
        boolean hasRace = stats.isRaceChosen();

        // Gate: sin raza, cortar todo
        if (!hasRace) {
            stopChargeIfNeeded();
            stopTransformHoldIfNeeded();

            detransformHoldTicks = 0;
            detransformTriggered = false;
            return;
        }

        boolean cDown = (CHARGE_KI != null && CHARGE_KI.isDown());
        boolean transformModDown = (TRANSFORM_MOD != null && TRANSFORM_MOD.isDown());
        boolean detransformModDown = (DETRANSFORM_MOD != null && DETRANSFORM_MOD.isDown());

        // ======================================================
        // PRIORIDAD 1: DETRANSFORM (hold 3s) = DETRANSFORM_MOD + C
        // ======================================================
        boolean detransformNow = detransformModDown && cDown;

        if (detransformNow) {
            // Si está intentando destransformar, no puede correr ni transform ni ki
            stopChargeIfNeeded();
            stopTransformHoldIfNeeded();

            if (!detransformTriggered) {
                detransformHoldTicks++;
                if (detransformHoldTicks >= DETRANSFORM_HOLD_REQUIRED_TICKS) {
                    detransformTriggered = true;

                    // feedback local: cortar hold (si tu PAL lo usa)
                    var form = player.getData(DataAttachments.PLAYER_FORM.get());
                    form.setTransformHeld(false);

                    PacketDistributor.sendToServer(new TransformHoldPacket(
                            TransformHoldPacket.Action.DETRANSFORM, true
                    ));
                }
            }
            return;
        } else {
            detransformHoldTicks = 0;
            detransformTriggered = false;
        }

        // ======================================================
        // PRIORIDAD 2: TRANSFORM HOLD = TRANSFORM_MOD + C
        // ======================================================
        boolean transformNow = transformModDown && cDown;

        if (transformNow != lastTransformSent) {
            lastTransformSent = transformNow;

            // feedback local
            var form = player.getData(DataAttachments.PLAYER_FORM.get());
            form.setTransformHeld(transformNow);

            PacketDistributor.sendToServer(new TransformHoldPacket(
                    TransformHoldPacket.Action.TRANSFORM_HOLD, transformNow
            ));
        }

        // Si el modificador de transform está abajo, NO cargar KI
        if (transformModDown) {
            stopChargeIfNeeded();
            return;
        }

        // ======================================================
        // PRIORIDAD 3: C normal = CHARGE KI
        // ======================================================
        if (CHARGE_KI != null) {
            boolean now = CHARGE_KI.isDown();
            if (now != lastChargeSent) {
                lastChargeSent = now;
                PacketDistributor.sendToServer(new KiChargePacket(now));
            }
        }
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
