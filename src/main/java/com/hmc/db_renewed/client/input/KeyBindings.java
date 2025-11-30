package com.hmc.db_renewed.client.input;

import com.hmc.db_renewed.gui.RaceSelectionScreen;
import com.hmc.db_renewed.gui.StatsScreen;
import com.hmc.db_renewed.network.ki.KiChargePacket;
import com.hmc.db_renewed.network.ki.ToggleFlyPacket;
import com.hmc.db_renewed.network.stats.DataAttachments;
import com.hmc.db_renewed.network.stats.PlayerStatsAttachment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;


public final class KeyBindings {
    private KeyBindings() {}

    public static KeyMapping OPEN_RACE_SCREEN;
    public static KeyMapping OPEN_STATS;
    public static KeyMapping TOGGLE_FLY;
    public static KeyMapping CHARGE_KI;
    public static KeyMapping SPECIAL;

    private static boolean REGISTERED = false;
    private static boolean lastChargeSent = false;

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

        if (OPEN_STATS != null && OPEN_STATS.consumeClick()) {

            PlayerStatsAttachment att = mc.player.getData(DataAttachments.PLAYER_STATS.get());

            if (!att.isRaceChosen()) {
                // Primera vez / aún no eligió raza → abrir selección
                mc.setScreen(new RaceSelectionScreen());
            } else {
                // Raza ya definida → abrir menú de stats
                mc.setScreen(new StatsScreen());
            }
        }

        if (TOGGLE_FLY != null && TOGGLE_FLY.consumeClick()) {
            PacketDistributor.sendToServer(new ToggleFlyPacket());
        }

        if (CHARGE_KI != null) {
            boolean now = CHARGE_KI.isDown();
            if (now != lastChargeSent) {
                lastChargeSent = now;
                PacketDistributor.sendToServer(new KiChargePacket(now));
            }
        }
    }
}