package com.hmc.zenkai.core.network.feature.wishes;

import com.hmc.zenkai.core.config.WishConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Difunde los mensajes públicos de Shenlong (invocación y deseos concedidos) a todos los
 * jugadores dentro del radio configurado, para que en multijugador se sepa qué pidió cada uno.
 * Radio 0 = solo el jugador que interactúa. Los errores y confirmaciones personales NO pasan
 * por aquí: siguen siendo privados.
 */
public final class WishBroadcast {
    private WishBroadcast() {}

    public static void nearby(Player source, Component msg, boolean actionBar) {
        if (!(source.level() instanceof ServerLevel level)) return;
        double r = WishConfig.broadcastRadius();
        double r2 = r * r;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(source) <= r2) p.displayClientMessage(msg, actionBar);
        }
    }
}