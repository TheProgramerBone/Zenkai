package com.hmc.zenkai.client.gui.screens.wishes;

import com.hmc.zenkai.core.config.WishConfig;
import com.hmc.zenkai.core.network.feature.wishes.SyncWishTogglesPayload;

import java.util.EnumMap;

/**
 * Cache cliente de los toggles de deseos, alimentado por SyncWishTogglesPayload.
 * Por defecto está habilitado (visible) hasta que llega el sync del servidor.
 */
public final class ClientWishToggles {

    private static final EnumMap<WishConfig.WishType, Boolean> MAP =
            new EnumMap<>(WishConfig.WishType.class);

    static {
        for (WishConfig.WishType t : WishConfig.WishType.values()) MAP.put(t, true);
    }

    private ClientWishToggles() {}

    public static void apply(SyncWishTogglesPayload p) {
        for (WishConfig.WishType t : WishConfig.WishType.values()) {
            MAP.put(t, p.isEnabled(t));
        }
    }

    public static boolean isEnabled(WishConfig.WishType t) {
        return MAP.getOrDefault(t, true);
    }
}