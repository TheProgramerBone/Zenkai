package com.hmc.zenkai.feature.rage;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Avisos pendientes de «la próxima transformación de este jugador es su primera con esa forma: secuencia larga». Contenedor sin
 * imports de cliente (lo escribe el handler del packet, código común). Cada aviso CADUCA: si el cambio de forma nunca llega (otro
 * addon lo vetó, el jugador salió del rango de render…), no debe quedar armado para una transformación posterior que ya no
 * sería la primera.
 * <p>
 * El aviso lleva también la forma y el trigger que la despertó: con ellos la escena elige su pose (de rodillas, rugido, agarrarse el
 * pecho, levantarse) y la nota que suena sobre el estallido.
 */
public final class LongTransformState {
    private LongTransformState() {}

    /** Margen entre el aviso y el cambio de forma visible: el hold termina, el servidor aplica y sincroniza la forma. */
    static final long TTL_MS = 6000L;

    /** @param trigger nombre de {@link RageTrigger}, o vacío si la vía no es Rage */
    public record Pending(long until, @Nullable ResourceLocation form, String trigger, boolean first) {
        public Pending(long until, @Nullable ResourceLocation form, String trigger) { this(until, form, trigger, true); }
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    public static void mark(UUID player) { mark(player, null, ""); }

    public static void mark(UUID player, @Nullable ResourceLocation form, String trigger) { mark(player, form, trigger, true); }

    public static void mark(UUID player, @Nullable ResourceLocation form, String trigger, boolean first) {
        PENDING.put(player, new Pending(System.currentTimeMillis() + TTL_MS, form, trigger == null ? "" : trigger, first));
    }

    /** ¿Hay un aviso vigente para este jugador? NO lo gasta (lo mira el compositor para tener el aura viva cuando llegue el cambio). */
    public static boolean pending(UUID player) {
        Pending p = PENDING.get(player);
        return p != null && p.until() >= System.currentTimeMillis();
    }

    /** @return true (y lo gasta) si había un aviso vigente para este jugador */
    public static boolean consume(UUID player) { return consumeInfo(player) != null; }

    /** Igual que {@link #consume} pero devuelve el aviso (forma y trigger) o null si no había uno vigente. */
    public static @Nullable Pending consumeInfo(UUID player) {
        Pending p = PENDING.remove(player);
        return p != null && p.until() >= System.currentTimeMillis() ? p : null;
    }

    /** Al salir del mundo. */
    public static void clear() { PENDING.clear(); }
}
