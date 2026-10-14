package com.hmc.zenkai.feature.kiweapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qué arma de ki tiene materializada cada jugador, legible desde CUALQUIER lado.
 * El arma ya no es un ítem (no hay nada en la mano que mirar), así que el cliente no puede
 * deducirla del inventario de otro jugador: el servidor manda la variante con
 * {@link KiWeaponSyncPacket} y aquí se guarda por id de entidad.
 *  - Servidor: se calcula en el momento ({@link KiWeaponServer#activeVariant}).
 *  - Cliente: lo último que dijo el servidor. Incluye al jugador local a propósito: así el
 *    cliente y el servidor usan la MISMA regla (mano libre + interruptor + modo combate) y la
 *    guardia/técnicas que se predicen en cliente no discrepan del servidor.
 * Código común: lo usa GuardHands, que corre en los dos lados.
 */
public final class KiWeaponState {
    private KiWeaponState() {}

    /** entityId -> variante activa. Solo se toca en el hilo del cliente; el mapa es concurrente
     *  por si el render lo lee mientras el packet encola el cambio. */
    private static final Map<Integer, String> CLIENT = new ConcurrentHashMap<>();
    /** entityId -> escudo de ki de la mano SECUNDARIA (cliente). */
    private static final Map<Integer, String> CLIENT_OFF = new ConcurrentHashMap<>();
    /** entityId -> gameTime (cliente) en que se materializó, para la animación de aparición. */
    private static final Map<Integer, Long> CLIENT_SINCE = new ConcurrentHashMap<>();

    /** Variante activa de esta entidad o null. */
    @Nullable
    public static String variantOf(LivingEntity e) {
        if (!(e instanceof Player p)) return null;
        if (p.level().isClientSide()) return CLIENT.get(p.getId());
        return KiWeaponServer.activeVariant(p);
    }

    public static boolean isActive(LivingEntity e) {
        return variantOf(e) != null;
    }

    /** Arma de ki de la mano SECUNDARIA (el escudo) de esta entidad, o null. */
    @Nullable
    public static String offhandOf(LivingEntity e) {
        if (!(e instanceof Player p)) return null;
        if (p.level().isClientSide()) return CLIENT_OFF.get(p.getId());
        return KiWeaponServer.activeOffhand(p);
    }

    /** La variante que se dibuja en ESTE brazo: la principal en el brazo principal y el escudo en el otro. */
    @Nullable
    public static String variantForArm(Player p, net.minecraft.world.entity.HumanoidArm arm) {
        return arm == p.getMainArm() ? variantOf(p) : offhandOf(p);
    }

    // ── Lado cliente ─────────────────────────────────────────────────────────

    /** Lo llama el handler del packet. Variante vacía = sin arma; {@code offhand} vacío = sin escudo. */
    public static void clientSet(int entityId, String variant, String offhand, long gameTime) {
        if (offhand.isEmpty()) CLIENT_OFF.remove(entityId);
        else CLIENT_OFF.put(entityId, offhand);
        if (variant.isEmpty()) {
            CLIENT.remove(entityId);
            CLIENT_SINCE.remove(entityId);
            return;
        }
        String old = CLIENT.put(entityId, variant);
        if (!variant.equals(old)) CLIENT_SINCE.put(entityId, gameTime);
    }

    /** Ticks (con parcial) desde que se materializó; un valor grande si no hay dato. */
    public static float clientAge(int entityId, long gameTime, float partialTick) {
        Long since = CLIENT_SINCE.get(entityId);
        return since == null ? 1000.0F : (gameTime - since) + partialTick;
    }

    public static void clientClear() {
        CLIENT.clear();
        CLIENT_OFF.clear();
        CLIENT_SINCE.clear();
    }
}
