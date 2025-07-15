package com.hmc.db_renewed.client.input;

import com.hmc.db_renewed.client.gui.RaceSelectionScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;

public class KeyInputHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (KeyBindings.openRaceScreen.isDown() && Minecraft.getInstance().screen == null) {
            Minecraft.getInstance().setScreen(new RaceSelectionScreen());
        }
    }
}