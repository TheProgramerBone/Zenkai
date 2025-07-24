package com.hmc.db_renewed.client.input;

import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    public static KeyMapping openRaceSelectionScreen;

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openRaceSelectionScreen = new KeyMapping(
                "key.db_renewed.open_race_screen",
                GLFW.GLFW_KEY_V,
                "key.categories.db_renewed"
        );
        event.register(openRaceSelectionScreen);
    }
}