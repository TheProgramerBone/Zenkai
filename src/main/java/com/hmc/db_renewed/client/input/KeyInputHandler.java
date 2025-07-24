package com.hmc.db_renewed.client.input;

import com.hmc.db_renewed.api.PlayerStatData;
import com.hmc.db_renewed.client.gui.RaceSelectionScreen;
import com.hmc.db_renewed.client.gui.StatsScreen;
import com.hmc.db_renewed.common.capability.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;

public class KeyInputHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();

        if (KeyBindings.openRaceSelectionScreen.isDown() && mc.screen == null && mc.player != null) {
            PlayerStatData data = mc.player.getCapability(ModCapabilities.PLAYER_STATS);
            if (data != null) {
                if (data.isCharacterCreated()) {
                    mc.setScreen(new StatsScreen(mc.player)); // HUD de estadísticas
                } else {
                    mc.setScreen(new RaceSelectionScreen()); // Pantalla de creación
                }
            }
        }
    }
}