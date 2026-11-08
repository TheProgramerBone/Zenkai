package com.hmc.zenkai.feature.rage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Avisos pendientes de «la próxima transformación de este jugador es su primera con esa forma: secuencia larga». Contenedor sin
 * imports de cliente (lo escribe el handler del packet, código común). Cada aviso CADUCA: si el cambio de forma nunca llega (otro
 * addon lo vetó, el jugador salió del rango de render…), no debe quedar armado para una transformación posterior que ya no
 * sería la primera.
 */
public final class LongTransformState {
    private LongTransformState() {}

    /** Margen entre el aviso y el cambio de forma visible: el hold termina, el servidor aplica y sincroniza la forma. */
    static final long TTL_MS = 6000L;

    private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();

    public static void mark(UUID player) {
        PENDING.put(player, System.currentTimeMillis() + TTL_MS);
    }

    /** ¿Hay un aviso vigente para este jugador? NO lo gasta (lo mira el compositor para tener el aura viva cuando llegue el cambio). */
    public static boolean pending(UUID player) {
        Long until = PENDING.get(player);
        return until != null && until >= System.currentTimeMillis();
    }

    /** @return true (y lo gasta) si había un aviso vigente para este jugador */
    public static boolean consume(UUID player) {
        Long until = PENDING.remove(player);
        return until != null && until >= System.currentTimeMillis();
    }

    /** Al salir del mundo. */
    public static void clear() { PENDING.clear(); }
}
