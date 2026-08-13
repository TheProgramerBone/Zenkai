package com.hmc.zenkai.feature.aura;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.RaceStatTable;
import com.hmc.zenkai.feature.StatSynergy;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.combat.PowerLevel;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.util.BalanceUtil;

/**
 * Techo y suelo de la escala de presencia. Se DERIVAN; no se escriben a mano.
 * EL PROBLEMA QUE RESUELVE: normalizar el PL con log10 necesita un techo, y un techo
 * escrito a mano se queda obsoleto en cuanto alguien añade una transformación. Aquí el
 * techo recorre el registro ENTERO de formas y todos los escalones de Kaioken, así que
 * una forma nueva lo sube sola. El único número de referencia es AuraTuning.REFERENCE_TP,
 * y ese no cambia al añadir formas: dice cuánto TP invierte un jugador de endgame, no
 * cuánto poder existe.
 * OJO CON UNA TRAMPA DE LA CADENA: statMultiplier se aplica a melee, defensa y ki_power,
 * pero NO a computeConFinal ni a computeKiPoolFinal (PlayerStatsAttachment). Como W_CON
 * pesa 0.6 y W_SPI 0.25 en el PL, el factor EFECTIVO de una forma sobre el PL es siempre
 * menor que (1 + statPercent): SSJ4 es nominal ×13 pero efectivo ×7.93, y Black nominal
 * ×15 pero efectivo ×9.85. Por eso el techo se calcula con la fórmula completa en vez de
 * multiplicar un PL base por el porcentaje mayor — esa cuenta daría un techo ~35% alto y
 * aplastaría toda la escala de presencia.
 * Se cachea porque recorre razas × estilos × formas × escalones. Hay que invalidarlo al
 * recargar datapacks (RaceStatManager / FormRegistry.rebuild) y al recibir el sync en
 * cliente.
 */
public final class AuraCeiling {
    private AuraCeiling() {}

    /** Reparto de puntos de referencia por estilo (STR, CON, DEX, WIL, SPI). Son las
     *  builds arquetípicas con las que se calibró la curva. */
    private static final double[] W_WARRIOR = {0.42, 0.24, 0.20, 0.09, 0.05};
    private static final double[] W_MARTIAL = {0.26, 0.20, 0.22, 0.18, 0.14};
    private static final double[] W_SPIRIT  = {0.06, 0.14, 0.16, 0.38, 0.26};

    private static volatile long cachedCeil = 0L;
    private static volatile long cachedFloor = 0L;

    /** Llamar al recargar datapacks y al recibir RaceStatSyncPacket / FormSyncPacket. */
    public static void invalidate() {
        cachedCeil = 0L;
        cachedFloor = 0L;
    }

    public static long ceiling() {
        long c = cachedCeil;
        if (c > 0L) return c;
        c = computeCeiling();
        cachedCeil = c;
        return c;
    }

    public static long floor() {
        long f = cachedFloor;
        if (f > 0L) return f;
        f = computeFloor();
        cachedFloor = f;
        return f;
    }

    // ── cálculo ──────────────────────────────────────────────────────────────

    private static double[] weights(Style s) {
        return switch (s) {
            case WARRIOR -> W_WARRIOR;
            case MARTIAL_ARTIST -> W_MARTIAL;
            case SPIRITUALIST -> W_SPIRIT;
        };
    }

    /** Inversa de la curva de coste de TP: cuántos puntos compra ese TP.
     *  coste(n) = n·(base + coeff·(n−1)/2)  ->  resolver n. */
    public static double pointsForTp(double tp) {
        double coeff = CommonConfig.tpCoefficient();
        double base = CommonConfig.attributeBaseCost();
        if (coeff <= 0d) return base <= 0d ? 0d : tp / base;
        double a = coeff / 2d;
        double b = base - coeff / 2d;
        return Math.max(0d, (-b + Math.sqrt(b * b + 4d * a * tp)) / (2d * a));
    }

    /**
     * PL de un jugador de referencia. Réplica exacta de la cadena real:
     * atributos -> StatSynergy -> RaceStatTable -> statMultiplier -> PowerLevel.compute.
     */
    private static long referencePl(Race race, Style style, double points,
                                    double statPercent) {
        int[] base = RaceStatTable.baseAttributes(race);
        double[] w = weights(style);
        int cap = CommonConfig.globalAttributeCap();

        int str = (int) Math.min(cap, base[0] + points * w[0]);
        int con = (int) Math.min(cap, base[1] + points * w[1]);
        int dex = (int) Math.min(cap, base[2] + points * w[2]);
        int wil = (int) Math.min(cap, base[3] + points * w[3]);
        int spi = (int) Math.min(cap, base[4] + points * w[4]);

        double melee = StatSynergy.melee(str, wil, RaceStatTable.melee(race, style));
        double defense = StatSynergy.defense(dex, con, RaceStatTable.defense(race, style));
        double kiPower = StatSynergy.kiPower(wil, spi, RaceStatTable.kiDamage(race, style));
        double kiPool = StatSynergy.kiPool(spi, RaceStatTable.kiReserves(race, style));
        double conFinal = BalanceUtil.computeStat(con, race, style,
                ZenkaiAttributes.CONSTITUTION);

        double m = 1.0 + statPercent;
        // melee, defensa y ki_power llevan el multiplicador; con y pool NO. Es la
        // asimetría que hace que las formas rindan menos de lo que dice su número.
        return PowerLevel.compute(melee * m, conFinal, defense * m, kiPower * m, kiPool);
    }

    private static long computeCeiling() {
        if (!RaceStatTable.isLoaded()) return AuraTuning.PL_CEIL_FALLBACK;

        double points = pointsForTp(AuraTuning.REFERENCE_TP);
        double maxKaioken = maxKaiokenPercent();

        // El mayor statPercent que el datapack ofrece hoy. Recorrer el registro entero
        // es lo que hace que una forma nueva suba el techo sin tocar código.
        double maxForm = 0d;
        for (FormDef d : FormDef.all()) {
            maxForm = Math.max(maxForm, d.statPercentMastered());
        }

        long best = 0L;
        for (Race r : Race.values()) {
            for (Style s : Style.values()) {
                best = Math.max(best, referencePl(r, s, points, maxForm + maxKaioken));
            }
        }
        return Math.max(1L, best);
    }

    private static long computeFloor() {
        if (!RaceStatTable.isLoaded()) return AuraTuning.PL_FLOOR_FALLBACK;
        long worst = Long.MAX_VALUE;
        for (Race r : Race.values()) {
            for (Style s : Style.values()) {
                worst = Math.min(worst, referencePl(r, s, 0d, 0d));
            }
        }
        return worst == Long.MAX_VALUE ? AuraTuning.PL_FLOOR_FALLBACK : Math.max(1L, worst);
    }

    /** statPercent del escalón de Kaioken más alto. Se recorre el enum para que añadir
     *  un escalón no obligue a tocar esto. */
    public static double maxKaiokenPercent() {
        double max = 0d;
        for (KaiokenTier t : KaiokenTier.values()) {
            max = Math.max(max, t.statPercent());
        }
        return max;
    }
}