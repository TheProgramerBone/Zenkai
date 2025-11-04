package com.hmc.db_renewed.client.input;

import com.hmc.db_renewed.gui.stats.DbrStatsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;


public final class KeyBindings {
    private KeyBindings() {}

    public static KeyMapping OPEN_RACE_SCREEN;
    public static KeyMapping OPEN_STATS;

    private static boolean REGISTERED = false;

    // Llamado SOLO desde ClientModEvents.onKeyMappingRegister
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        if (REGISTERED) return; // evita dobles registros
        REGISTERED = true;

        OPEN_RACE_SCREEN = new KeyMapping(
                "key.db_renewed.open_race_screen",
                GLFW.GLFW_KEY_V,
                "key.categories.db_renewed"
        );
        event.register(OPEN_RACE_SCREEN);

        OPEN_STATS = new KeyMapping(
                "key.db_renewed.open_stats",
                GLFW.GLFW_KEY_O,
                "key.categories.db_renewed"
        );
        event.register(OPEN_STATS);
    }

    public static void handleKeyInput(InputEvent.Key e) {
        if (OPEN_RACE_SCREEN != null) {
            OPEN_RACE_SCREEN.consumeClick();
        }

        if (OPEN_STATS != null && OPEN_STATS.consumeClick()) {
            Minecraft.getInstance().setScreen(new DbrStatsScreen());
        }
    }
}