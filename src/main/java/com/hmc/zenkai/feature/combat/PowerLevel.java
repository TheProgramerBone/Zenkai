package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.combat.entity.EntityArchetype;

import java.util.EnumMap;

/**
 * Power Level = SUMA PONDERADA LINEAL de los stats de combate (predecible; el daño NO depende
 * del PL). MIND queda fuera (es para habilidades). Una sola fórmula para jugador y entidades.
 *
 * DOS JUEGOS DE PESOS, Y NO SON EL MISMO. Antes había uno solo haciendo dos trabajos
 * incompatibles:
 *  - MEDIDOR (W_*): "cuánto poder de pelea tiene esto". Es lo que lee el scouter.
 *  - REPARTIDOR (B_*): dado un PL objetivo, reparte atributos por la forma del arquetipo.
 * Compartiéndolos, tocar el medidor rebalanceaba las 82 fichas de entidades en silencio: al
 * quitar un peso, el denominador de solveAttributes encogía, k subía y cada uno de los atributos
 * crecían. Ahora el repartidor tiene los suyos y no se entera de lo que haga el medidor.
 *
 * Arrancan con los mismos valores a propósito: este cambio no debe mover un solo mob.
 */
public final class PowerLevel {
    private PowerLevel() {}

    // ── MEDIDOR ──────────────────────────────────────────────────────────────
    // En 1.0 => "1 punto de stat = 1 de PL": el PL es la SUMA de los stats, y como los shape
    // de arquetipo suman 100, el reparto queda literalmente stat = PL × (shape/100).
    // Así ningún stat supera el PL y el daño es proporcional al número de poder.
    public static final double W_STR = 1.0; // melee
    public static final double W_CON = 0.6; // body
    public static final double W_DEX = 1.0; // defensa
    public static final double W_WIL = 1.0; // ki power
    public static final double W_SPI = 0.25; // ki pool

    // ── REPARTIDOR ───────────────────────────────────────────────────────────
    // Solo los usa solveAttributes. Cambiarlos SÍ rebalancea el bestiario; cambiar los W_* no.
    public static final double B_STR = 1.0;
    public static final double B_CON = 1.0;
    public static final double B_DEX = 1.0;
    public static final double B_WIL = 1.0;
    public static final double B_SPI = 1.0;

    /**
     * Suelo del PL aparente de alguien que está suprimiendo su ki. NO es 0 a propósito: un 0
     * en el scouter se lee como "no hay nadie ahí", y eso es información falsa — hay alguien, y
     * está escondiéndose. Un 5 dice "hay algo, e insignificante", que es justo el engaño.
     */
    public static final long SUPPRESSED_FLOOR = 5L;

    /** PL a partir de cualquier portador de stats (jugador o entidad). */
    public static long compute(ZenkaiCombatStats s) {
        return compute(s.computeMeleeFinal(), s.computeConFinal(), s.computeDefenseFinal(),
                s.computeKiPowerFinal(), s.computeKiPoolFinal());
    }

    /** Núcleo de la fórmula. Existe aparte para que el PL LIMPIO de las pesas (sin el factor
     *  de carga) no tenga que duplicar los pesos ni dividir por el factor a posteriori. */
    public static long compute(double melee, double con, double defense,
                               double kiPower, double kiPool) {
        double pl = W_STR * melee
                + W_CON * con
                + W_DEX * defense
                + W_WIL * kiPower
                + W_SPI * kiPool;
        return Math.max(0L, Math.round(pl));
    }

    /**
     * PL APARENTE: lo que otros leen de ti con la supresión de Ki Control puesta.
     * La fracción se aplica al PL ENTERO, no a tres de sus cinco términos. Antes CON y SPI
     * quedaban fuera de la supresión, así que había un suelo del 24-48% del PL según la forma:
     * un tanque escondiéndose al 0% seguía leyendo casi la mitad de su poder, y bajar al 50%
     * solo tiraba el número un 26%. El control de poder no servía para nada.
     * Los POOLS no se tocan: esconder el ki no te quita corazones ni vacía tu barra, solo
     * cambia lo que el aparato del otro dice.
     */
    public static long suppress(long realPl, double fraction) {
        if (realPl <= 0L) return 0L;                 // sin raza / sin stats: no hay nada que esconder
        if (fraction >= 1.0) return realPl;
        long shown = Math.round(realPl * Math.max(0.0, fraction));
        return Math.max(SUPPRESSED_FLOOR, shown);
    }

    /**
     * Reparte atributos para alcanzar {@code targetPL} siguiendo la forma del arquetipo.
     * Como (para entidades) el stat efectivo = atributo × 1, se cumple PL = Σ B·attr, y con
     * attr = k·shape queda k = targetPL / Σ(B·shape). Cerrado y exacto. MIND se deja en 0.
     */
    public static EnumMap<ZenkaiAttributes, Integer> solveAttributes(long targetPL, EntityArchetype arch) {
        double denom =
                B_STR * arch.shape(ZenkaiAttributes.STRENGTH)
                        + B_CON * arch.shape(ZenkaiAttributes.CONSTITUTION)
                        + B_DEX * arch.shape(ZenkaiAttributes.DEXTERITY)
                        + B_WIL * arch.shape(ZenkaiAttributes.WILLPOWER)
                        + B_SPI * arch.shape(ZenkaiAttributes.SPIRIT);

        double k = (denom <= 0) ? 0 : targetPL / denom;

        EnumMap<ZenkaiAttributes, Integer> out = new EnumMap<>(ZenkaiAttributes.class);
        out.put(ZenkaiAttributes.STRENGTH,     round(k * arch.shape(ZenkaiAttributes.STRENGTH)));
        out.put(ZenkaiAttributes.CONSTITUTION, round(k * arch.shape(ZenkaiAttributes.CONSTITUTION)));
        out.put(ZenkaiAttributes.DEXTERITY,    round(k * arch.shape(ZenkaiAttributes.DEXTERITY)));
        out.put(ZenkaiAttributes.WILLPOWER,    round(k * arch.shape(ZenkaiAttributes.WILLPOWER)));
        out.put(ZenkaiAttributes.SPIRIT,       round(k * arch.shape(ZenkaiAttributes.SPIRIT)));
        out.put(ZenkaiAttributes.MIND,         0);
        return out;
    }

    private static int round(double v) {
        return (int) Math.max(0, Math.round(v));
    }
}