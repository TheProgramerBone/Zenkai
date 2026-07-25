package com.hmc.zenkai.feature.wishes;

import com.hmc.zenkai.config.ServerConfig;

import java.util.EnumMap;

/**
 * Cache cliente de los toggles de deseos, alimentado por SyncWishTogglesPayload.
 * Por defecto está habilitado (visible) hasta que llega el sync del servidor.
 */
public final class ClientWishToggles {

    private static final EnumMap<ServerConfig.WishType, Boolean> MAP =
            new EnumMap<>(ServerConfig.WishType.class);

    static {
        for (ServerConfig.WishType t : ServerConfig.WishType.values()) MAP.put(t, true);
    }

    private ClientWishToggles() {}

    public static void apply(SyncWishTogglesPayload p) {
        for (ServerConfig.WishType t : ServerConfig.WishType.values()) {
            MAP.put(t, p.isEnabled(t));
        }
    }

    public static boolean isEnabled(ServerConfig.WishType t) {
        return MAP.getOrDefault(t, true);
    }
}