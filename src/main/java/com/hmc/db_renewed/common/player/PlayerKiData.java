package com.hmc.db_renewed.common.player;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;


import java.util.WeakHashMap;

public class PlayerKiData {

    private static final WeakHashMap<Player, Integer> kiValues = new WeakHashMap<>();

    public static int getKi(Player player) {
        return kiValues.getOrDefault(player, 100);
    }

    public static void setKi(Player player, int value) {
        kiValues.put(player, value);
    }

    public static void copyData(Player from, Player to) {
        if (kiValues.containsKey(from)) {
            kiValues.put(to, kiValues.get(from));
        }
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        copyData(event.getOriginal(), event.getEntity());
    }

    public static void onPlayerTick(PlayerTickEvent event) {
        // Aquí puedes implementar lógica si deseas regenerar ki o mantenerlo actualizado
        // Player player = event.player;
    }
}