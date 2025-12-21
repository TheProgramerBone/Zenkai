package com.hmc.db_renewed.core.network.feature.ki;

import com.hmc.db_renewed.core.network.feature.stats.PlayerStatsAttachment;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KiCombat {
    private KiCombat() {}

    // cooldown por jugador (ticks del juego)
    private static final Map<UUID, Integer> COOLDOWNS = new ConcurrentHashMap<>();

    /** Llamar cada tick de servidor para decrementar. */
    public static void tickCooldowns() {
        COOLDOWNS.replaceAll((id, left) -> Math.max(0, left - 1));
    }

    public static boolean isOnCooldown(ServerPlayer sp) {
        return COOLDOWNS.getOrDefault(sp.getUUID(), 0) > 0;
    }

    public static void putCooldown(ServerPlayer sp, int ticks) {
        COOLDOWNS.put(sp.getUUID(), Math.max(ticks, 0));
    }

    /** Normaliza 0..200 -> factor 0..2 (100 = 1.0; 200 = 2.0). */
    public static double overchargeFactor(int chargePercent) {
        int clamped = Math.max(0, Math.min(chargePercent, 200));
        return clamped / 100.0;
    }

    /** Coste de Ki = (baseCost por poder) * overcharge * (ajuste por WILLPOWER). */
    public static int computeKiCost(PlayerStatsAttachment att, KiAttackDefinition def, int chargePercent) {
        double over = overchargeFactor(chargePercent); // 0..2
        double kiPower = Math.max(1.0, att.computeKiPowerFinal());
        // Base sencillo: cada punto de basePower cuesta 1 de Ki; mayor willpower reduce ligeramente el costo
        double base = def.basePower(); // p.ej. 4.0
        double cost = base * over * (1.0 - Math.min(0.5, (kiPower / 100.0))); // hasta -50% de descuento
        return (int)Math.ceil(Math.max(1.0, cost));
    }

    /** Daño final = basePower * overcharge * (1 + kiPower/100) */
    public static double computeDamage(PlayerStatsAttachment att, KiAttackDefinition def, int chargePercent) {
        double over = overchargeFactor(chargePercent);
        double kiPower = Math.max(0.0, att.computeKiPowerFinal());
        return def.basePower() * over * (1.0 + (kiPower / 100.0));
    }

    /** Velocidad final = speed * (0.75 + 0.25*overcharge) para dar un plus en overcharge. */
    public static double computeSpeed(KiAttackDefinition def, int chargePercent) {
        double over = overchargeFactor(chargePercent);
        return def.speed() * (0.75 + 0.25 * over);
    }
}