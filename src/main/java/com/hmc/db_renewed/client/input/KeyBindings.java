package com.hmc.db_renewed.client.input;

import com.hmc.db_renewed.client.gui.screens.RaceSelectionScreen;
import com.hmc.db_renewed.client.gui.screens.StatsScreen;
import com.hmc.db_renewed.core.network.feature.ki.KiChargePacket;
import com.hmc.db_renewed.core.network.feature.ki.ToggleFlyPacket;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.db_renewed.core.network.feature.stats.TransformHoldPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;


public final class KeyBindings {

    private KeyBindings() {
    }

    public static KeyMapping OPEN_STATS;
    public static KeyMapping TOGGLE_FLY;
    public static KeyMapping CHARGE_KI;
    public static KeyMapping SPECIAL;

    private static boolean REGISTERED = false;
    private static boolean lastChargeSent = false;
    private static boolean lastTransformSent = false;


    // Capa de animación reutilizable solo para la transformación

    // Llamado SOLO desde ClientModEvents.onKeyMappingRegister
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        if (REGISTERED) return; // evita dobles registros
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
                "key.categories.db_renewed");
        event.register(TOGGLE_FLY);

        CHARGE_KI = new KeyMapping(
                "key.db_renewed.charge_ki",
                GLFW.GLFW_KEY_C,
                "key.categories.db_renewed");
        event.register(CHARGE_KI);

        SPECIAL = new KeyMapping(
                "key.db_renewed.special_alt",
                GLFW.GLFW_KEY_LEFT_ALT,
                "key.categories.db_renewed"
        );
        event.register(SPECIAL);
    }

    public static void handleKeyInput(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerStatsAttachment att = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        boolean hasRace = att.isRaceChosen();

        // OPEN_STATS (V) siempre permitido
        if (OPEN_STATS != null && OPEN_STATS.consumeClick()) {
            if (!hasRace) mc.setScreen(new RaceSelectionScreen());
            else mc.setScreen(new StatsScreen());
            return;
        }

        // Si NO tiene raza, invalida TODO lo demás (ki/fly/transform)
        if (!hasRace) {
            if (lastChargeSent) {
                lastChargeSent = false;
                PacketDistributor.sendToServer(new KiChargePacket(false));
            }
            if (lastTransformSent) {
                lastTransformSent = false;
                att.setTransforming(false);
                PacketDistributor.sendToServer(new TransformHoldPacket(false));
            }
            return;
        }

        boolean specialDown = (SPECIAL != null && SPECIAL.isDown());
        boolean cDown = (CHARGE_KI != null && CHARGE_KI.isDown());

        // TRANSFORMACIÓN = ALT + C (mantenido)
        boolean transformNow = specialDown && cDown;

        if (transformNow != lastTransformSent) {
            lastTransformSent = transformNow;

            // estado local para PAL
            att.setTransforming(transformNow);

            // server authoritative
            PacketDistributor.sendToServer(new TransformHoldPacket(transformNow));
        }

        // Si ALT está abajo, no procesar C como carga de ki
        if (specialDown) {
            return;
        }

        // TOGGLE_FLY (G)
        if (TOGGLE_FLY != null && TOGGLE_FLY.consumeClick()) {
            PacketDistributor.sendToServer(new ToggleFlyPacket());
        }

        // CHARGE_KI (C) normal (sin ALT)
        if (CHARGE_KI != null) {
            boolean now = CHARGE_KI.isDown();
            if (now != lastChargeSent) {
                lastChargeSent = now;
                PacketDistributor.sendToServer(new KiChargePacket(now));
            }
        }
    }

}