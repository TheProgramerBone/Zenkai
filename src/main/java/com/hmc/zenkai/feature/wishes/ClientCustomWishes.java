package com.hmc.zenkai.feature.wishes;

import java.util.List;

/** Cache cliente de los deseos de addons de la conversación actual (los pinta ShenlongWishScreen). */
public final class ClientCustomWishes {
    private ClientCustomWishes() {}

    private static volatile List<CustomWishListPayload.Entry> CURRENT = List.of();

    public static void apply(CustomWishListPayload p) { CURRENT = List.copyOf(p.wishes()); }

    public static List<CustomWishListPayload.Entry> current() { return CURRENT; }
}
