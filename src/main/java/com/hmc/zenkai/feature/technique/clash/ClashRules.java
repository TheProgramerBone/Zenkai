package com.hmc.zenkai.feature.technique.clash;

import java.util.Random;

/**
 * Reglas PURAS del minijuego del choque de haces: sin Minecraft, sin estado, sin red. Las
 * comparten cliente y servidor, de modo que la barra que el jugador ve moverse es exactamente la
 * función con la que el servidor evalúa su pulsación. El cliente nunca manda un valor, solo
 * "pulsé"; el servidor lo evalúa con SU tick.
 *
 * La barra va de 0 a 1000 y el cursor sube y baja en onda triangular. La VELOCIDAD (lenta, media o
 * rápida) se sortea UNA vez al empezar el choque y vale para los dos participantes hasta el final:
 * no cambia a mitad del minijuego (como en Dragon Ball Legends), así que la barra es una función
 * lineal del tiempo y no hace falta ninguna fase acumulada.
 */
public final class ClashRules {
    private ClashRules() {}

    /** Valor máximo de la barra. */
    public static final int MAX = 1000;

    /** Rango «PERFECT»: a partir de aquí el feedback es dorado. */
    public static final int PERFECT_FROM = 950;

    /** Velocidades posibles del cursor; el índice es también la clave de lang `zenkai.clash.stage.N`. */
    public static final int SPEED_SLOW = 0, SPEED_MEDIUM = 1, SPEED_FAST = 2;

    /**
     * Parámetros del minijuego. Viajan enteros en ClashStartPacket para que el cliente no dependa
     * de que su copia de la config coincida con la del servidor.
     * @param duration ticks totales del plazo
     * @param period   ticks por barrido completo (sube y baja) a la velocidad sorteada
     * @param speed    cuál de las tres velocidades salió ({@link #SPEED_SLOW}...{@link #SPEED_FAST})
     */
    public record Params(int duration, int period, int speed) {
        public Params {
            duration = Math.max(1, duration);
            period = Math.max(2, period);
            speed = Math.max(SPEED_SLOW, Math.min(SPEED_FAST, speed));
        }
    }

    /** Velocidad del choque (0 lenta, 1 media, 2 rápida). No depende del instante: es la misma
     *  durante todo el minijuego. Conserva el parámetro `t` para no tocar a los llamantes. */
    public static int stage(double t, Params p) {
        return p.speed();
    }

    /** Fase en vueltas completas (1.0 = un barrido de subida y bajada). */
    public static double phase(double t, Params p) {
        return t <= 0 ? 0 : t / p.period();
    }

    /**
     * Posición del cursor, 0..{@link #MAX}. `seed` en [0,1) desfasa la onda para que los dos
     * jugadores no vean (ni pulsen) el mismo ritmo.
     */
    public static double cursor(double t, double seed, Params p) {
        double x = phase(t, p) + seed;
        x -= Math.floor(x);
        double tri = x < 0.5 ? 2.0 * x : 2.0 - 2.0 * x;
        return tri * MAX;
    }

    /**
     * Valor de quien NO llegó a pulsar: lo que marcaba el cursor al agotarse el plazo, atenuado.
     * No es un 0 automático para que un lag o una distracción no sea una derrota segura, pero
     * tampoco compensa dejar de jugar.
     */
    public static double noPressValue(double seed, Params p, double penalty) {
        return cursor(p.duration(), seed, p) * penalty;
    }

    /**
     * Bonus por la potencia relativa del haz, en puntos de barra. Acotado a ±cap: la potencia
     * inclina el resultado pero el minijuego sigue siendo el protagonista.
     * @param relative daño propio / daño del rival
     */
    public static double statBonus(double relative, double cap) {
        if (cap <= 0.0 || Double.isNaN(relative)) return 0.0;
        double d = Math.max(-1.0, Math.min(1.0, relative - 1.0));
        return d * cap;
    }

    /** Valor final de un jugador: cursor + bonus. Puede pasar de {@link #MAX}. */
    public static double score(double cursorValue, double relativePower, double bonusCap) {
        return cursorValue + statBonus(relativePower, bonusCap);
    }

    /**
     * Multiplicador de cada miembro del equipo PEQUEÑO en un choque por equipos:
     * {@code 1 + (grande − pequeño) × (bonus − 1) / pequeño}. Con bonus 1,6: un 2 contra 1 deja al
     * que va solo en ×1,6 (máximo 1600 frente a 2000), un 3 contra 2 en ×1,3 por miembro. El equipo
     * igual o mayor siempre ×1, y un 1 contra 1 también: ahí el choque es exactamente el de siempre.
     * @param own   miembros de este equipo
     * @param other miembros del contrario
     * @param bonus {@code clash.outnumbered_bonus} (1 = sin compensación)
     */
    public static double outnumberedMultiplier(int own, int other, double bonus) {
        if (own <= 0 || own >= other || !(bonus > 1.0)) return 1.0;
        return 1.0 + (other - own) * (bonus - 1.0) / own;
    }

    /**
     * Puntuación de un equipo: suma de los valores de barra de sus miembros por el multiplicador
     * del equipo pequeño, más el bonus de potencia (que es del EQUIPO, con el daño total de cada
     * lado, y no se multiplica). Con un solo miembro y multiplicador 1 es {@link #score}.
     */
    public static double teamScore(double[] cursorValues, double multiplier,
                                   double relativePower, double bonusCap) {
        double sum = 0.0;
        for (double v : cursorValues) sum += v;
        return sum * multiplier + statBonus(relativePower, bonusCap);
    }

    /** Resultado de comparar a los dos participantes. */
    public enum Outcome { A_WINS, B_WINS, TIE }

    public static Outcome compare(double scoreA, double scoreB, double tieMargin) {
        if (Math.abs(scoreA - scoreB) < tieMargin) return Outcome.TIE;
        return scoreA > scoreB ? Outcome.A_WINS : Outcome.B_WINS;
    }

    /**
     * Tick en que un mob «pulsa». Toma varias muestras aleatorias del plazo y se queda con la de
     * cursor más alto: con skill 0 es una sola muestra (al azar), con skill 1 son nueve (casi
     * siempre cerca del pico). Nunca antes del 20 % del plazo, para que no resuelva el choque
     * antes de que el jugador tenga tiempo de reaccionar.
     */
    public static int mobPressTick(Random rng, double seed, double skill, Params p) {
        int samples = 1 + (int) Math.round(Math.max(0.0, Math.min(1.0, skill)) * 8.0);
        int lo = Math.max(1, (int) (p.duration() * 0.2));
        int hi = Math.max(lo + 1, p.duration());
        int best = lo;
        double bestVal = -1.0;
        for (int i = 0; i < samples; i++) {
            int t = lo + rng.nextInt(hi - lo + 1);
            double v = cursor(t, seed, p);
            if (v > bestVal) { bestVal = v; best = t; }
        }
        return Math.min(best, p.duration());
    }
}
