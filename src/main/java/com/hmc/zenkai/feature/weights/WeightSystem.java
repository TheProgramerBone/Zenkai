package com.hmc.zenkai.feature.weights;

import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.content.item.WeightArmorItem;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * ÚNICO dueño de las matemáticas de las pesas. Nadie más calcula capacidad, carga,
 * penalización ni bono: cada uno de los sitios (tick, movimiento, vuelo, TP, GUI, pantalla de
 * ajuste) leen de aquí. Si algún día cambia la curva, cambia en un solo archivo.
 *
 * Modelo:
 *   carga r  = toneladas equipadas / capacidad
 *   capacidad = (PL_LIMPIO / divisor) ^ exponente          [toneladas]
 *
 * PL_LIMPIO = con forma y kaioken, SIN el factor de pesas. Es obligatorio: la capacidad
 * depende del PL y el PL depende de la penalización, así que usar el PL penalizado crearía
 * un bucle que converge a cualquier cosa.
 *
 * Penalizaciones (lineales en r, con r CLAMPADO al umbral de sobrecarga para que llevar
 * 500x tu capacidad no te deje en stats negativos):
 *   stats     x (1 - stat_penalty * r)   -> entra en PlayerStatsAttachment.weightFactor,
 *                                           afecta melee/defensa/ki power y por tanto el PL
 *   movimiento x (1 - move_penalty * r)  -> suelo, vuelo y turbo
 *   salto      x (1 - jump_penalty * r)
 *   TP         x (1 + tp_bonus * r)
 *
 * Sobrecarga (r > umbral): movimiento clavado al factor de arrastre, salto y vuelo
 * anulados, y el bono de TP cae a 1.0 — la carga que no puedes mover no entrena.
 */
public final class WeightSystem {
    private WeightSystem() {}

    /** Id del slot de Curios declarado en data/zenkai/curios/slots/weight.json. */
    public static final String CURIOS_SLOT = "weight";

    // ── Carga equipada ───────────────────────────────────────────────────────

    /** Toneladas totales: pecho + slot de Curios. Se SUMAN (las dos pesas son acumulables). */
    public static double equippedTons(Player p) {
        if (p == null) return 0.0;
        double t = 0.0;
        t += tonsOf(p.getItemBySlot(EquipmentSlot.CHEST));
        t += tonsOf(CuriosCompat.findEquipped(p, CURIOS_SLOT));
        return t;
    }

    private static double tonsOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;
        if (!(stack.getItem() instanceof WeightArmorItem w)) return 0.0;
        return w.getTons(stack);
    }

    // ── Capacidad y carga ────────────────────────────────────────────────────

    /** Capacidad en toneladas para un PL limpio dado. Nunca 0 (evita división por cero). */
    public static double capacityTons(long cleanPl) {
        double div = Math.max(0.0001, CommonConfig.weightCapacityDivisor());
        double exp = CommonConfig.weightCapacityExponent();
        double base = Math.max(1.0, cleanPl) / div;
        return Math.max(0.01, Math.pow(base, exp));
    }

    /** r = toneladas / capacidad. 0 si no lleva pesas. */
    public static double computeLoad(Player p) {
        double tons = equippedTons(p);
        if (tons <= 0.0) return 0.0;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (!att.isRaceChosen()) return 0.0;
        return tons / capacityTons(att.getPowerLevelRaw());
    }

    /** PL mínimo para que un peso dado quede en r = 1 (lo muestra la pantalla de ajuste). */
    public static long plForTons(double tons) {
        if (tons <= 0.0) return 0L;
        double div = Math.max(0.0001, CommonConfig.weightCapacityDivisor());
        double exp = CommonConfig.weightCapacityExponent();
        if (exp <= 0.0) return 0L;
        return Math.max(1L, Math.round(Math.pow(tons, 1.0 / exp) * div));
    }

    // ── Factores derivados ───────────────────────────────────────────────────

    public static boolean isOverloaded(double load) {
        return load > CommonConfig.weightOverloadThreshold();
    }

    /** r acotado al umbral: pasado el umbral la penalización ya no crece, la sobrecarga manda. */
    private static double effective(double load) {
        return Math.min(Math.max(0.0, load), CommonConfig.weightOverloadThreshold());
    }

    /** Multiplicador de melee / defensa / ki power (y por tanto del PL mostrado). */
    public static double statFactor(double load) {
        return Math.max(0.05, 1.0 - CommonConfig.weightStatPenalty() * effective(load));
    }

    /** Multiplicador de velocidad de suelo, vuelo y turbo. */
    public static double moveFactor(double load) {
        if (isOverloaded(load)) return CommonConfig.weightOverloadMoveFactor();
        return Math.max(0.0, 1.0 - CommonConfig.weightMovePenalty() * effective(load));
    }

    /** Multiplicador de altura de salto. 0 en sobrecarga: no despegas del suelo. */
    public static double jumpFactor(double load) {
        if (isOverloaded(load)) return 0.0;
        return Math.max(0.0, 1.0 - CommonConfig.weightJumpPenalty() * effective(load));
    }

    /** Multiplicador de cualquier ganancia de TP. 1.0 en sobrecarga: la carga muerta no entrena. */
    public static double tpFactor(double load) {
        if (isOverloaded(load)) return 1.0;
        return 1.0 + CommonConfig.weightTpBonus() * effective(load);
    }
}