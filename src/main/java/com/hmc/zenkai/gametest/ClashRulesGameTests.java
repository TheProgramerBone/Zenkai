package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.clash.ClashRules;
import com.hmc.zenkai.feature.technique.clash.ClashRules.Outcome;
import com.hmc.zenkai.feature.technique.clash.ClashRules.Params;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Random;

/**
 * Lógica pura del minijuego del choque de haces (cursor, puntuación, comparación y pulsación de los
 * mobs). {@link ClashRules} no toca Minecraft, así que no necesita jugador ni mundo: es el mismo
 * patrón que {@code AuraCeilingGameTests}. Importa porque cliente y servidor comparten estas
 * funciones: si el cursor deja de ser continuo o el bonus se sale de su tope, la barra que el
 * jugador ve y la que evalúa el servidor se desfasan sin ningún error visible.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClashRulesGameTests {
    private ClashRulesGameTests() {}

    /** Los valores por defecto de ServerConfig: duración 100 ticks y velocidad media (periodo 18). */
    private static final Params P = new Params(100, 18, ClashRules.SPEED_MEDIUM);
    /** Las tres velocidades con sus periodos por defecto (30 / 18 / 10 ticks por barrido). */
    private static final Params[] SPEEDS = {
            new Params(100, 30, ClashRules.SPEED_SLOW),
            new Params(100, 18, ClashRules.SPEED_MEDIUM),
            new Params(100, 10, ClashRules.SPEED_FAST)};

    private static void near(GameTestHelper h, double expected, double actual, double eps, String what) {
        h.assertTrue(Math.abs(expected - actual) <= eps,
                what + ": se esperaba " + expected + " y salió " + actual);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void paramsAreClamped(GameTestHelper helper) {
        Params bad = new Params(-5, 0, 9);
        helper.assertTrue(bad.duration() == 1, "duration mínima 1, salió " + bad.duration());
        helper.assertTrue(bad.period() == 2, "el periodo no puede bajar de 2 ticks, salió " + bad.period());
        helper.assertTrue(bad.speed() == ClashRules.SPEED_FAST,
                "una velocidad por encima de 2 se recorta a la rápida, salió " + bad.speed());
        helper.assertTrue(new Params(10, 5, -3).speed() == ClashRules.SPEED_SLOW,
                "una velocidad negativa se recorta a la lenta");
        helper.succeed();
    }

    /** El corazón del cambio: la velocidad se sortea una vez y NO cambia durante el choque. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void speedIsTheSameForTheWholeClash(GameTestHelper helper) {
        for (Params p : SPEEDS) {
            for (int t = 0; t <= p.duration(); t += 5) {
                helper.assertTrue(ClashRules.stage(t, p) == p.speed(),
                        "la velocidad " + p.speed() + " cambió a " + ClashRules.stage(t, p) + " en t=" + t);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void cursorStaysInRangeAndIsContinuous(GameTestHelper helper) {
        for (Params p : SPEEDS) {
            // La onda recorre 2*MAX por periodo: a esa velocidad máxima, en 0.1 tick, un salto mayor sería
            // una discontinuidad.
            double maxStep = 2.0 * ClashRules.MAX / p.period() * 0.1 + 1.0e-6;
            for (double seed : new double[]{0.0, 0.25, 0.5, 0.93}) {
                double prev = ClashRules.cursor(0, seed, p);
                for (int i = 1; i <= 1000; i++) {
                    double t = i * 0.1;
                    double c = ClashRules.cursor(t, seed, p);
                    helper.assertTrue(c >= 0.0 && c <= ClashRules.MAX,
                            "cursor fuera de 0.." + ClashRules.MAX + ": " + c + " (t=" + t + ", seed=" + seed + ")");
                    helper.assertTrue(Math.abs(c - prev) <= maxStep,
                            "salto de " + Math.abs(c - prev) + " puntos entre t=" + (t - 0.1) + " y t=" + t
                                    + " (periodo " + p.period() + ", seed=" + seed + ")");
                    prev = c;
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void cursorShapeAndSeed(GameTestHelper helper) {
        for (Params p : SPEEDS) {
            near(helper, 0.0, ClashRules.cursor(0, 0.0, p), 1e-9, "arranca en 0 con seed 0");
            near(helper, ClashRules.MAX, ClashRules.cursor(p.period() / 2.0, 0.0, p), 1e-6,
                    "a medio barrido llega al máximo (periodo " + p.period() + ")");
            near(helper, 0.0, ClashRules.cursor(p.period(), 0.0, p), 1e-6,
                    "tras un barrido completo vuelve a 0 (periodo " + p.period() + ")");
            near(helper, ClashRules.MAX, ClashRules.cursor(0, 0.5, p), 1e-9,
                    "la seed 0.5 desfasa media vuelta: arranca en el máximo");
        }
        // Más rápida = más barridos en el mismo plazo.
        helper.assertTrue(ClashRules.phase(100, SPEEDS[2]) > ClashRules.phase(100, SPEEDS[0]),
                "la velocidad rápida debe dar más vueltas que la lenta en el mismo plazo");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void noPressIsAttenuatedCursorAtDeadline(GameTestHelper helper) {
        double seed = 0.3;
        double atEnd = ClashRules.cursor(P.duration(), seed, P);
        near(helper, atEnd * 0.5, ClashRules.noPressValue(seed, P, 0.5), 1e-9, "penalización 0.5");
        near(helper, 0.0, ClashRules.noPressValue(seed, P, 0.0), 1e-9, "penalización 0 no puntúa nada");
        near(helper, atEnd, ClashRules.noPressValue(seed, P, 1.0), 1e-9, "penalización 1 deja el cursor tal cual");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void statBonusIsCapped(GameTestHelper helper) {
        near(helper, 0.0, ClashRules.statBonus(1.0, 100.0), 1e-9, "potencias iguales: sin bonus");
        near(helper, 50.0, ClashRules.statBonus(1.5, 100.0), 1e-9, "50 % más de potencia: media escala");
        near(helper, 100.0, ClashRules.statBonus(2.0, 100.0), 1e-9, "el doble de potencia llega al tope");
        near(helper, 100.0, ClashRules.statBonus(50.0, 100.0), 1e-9, "una potencia enorme no pasa del tope");
        near(helper, -100.0, ClashRules.statBonus(0.0, 100.0), 1e-9, "sin potencia, el tope en negativo");
        near(helper, -50.0, ClashRules.statBonus(0.5, 100.0), 1e-9, "la mitad de potencia, media escala negativa");
        near(helper, 0.0, ClashRules.statBonus(Double.NaN, 100.0), 1e-9, "NaN no puede colar un bonus");
        near(helper, 0.0, ClashRules.statBonus(3.0, 0.0), 1e-9, "tope 0 desactiva el bonus");
        near(helper, 0.0, ClashRules.statBonus(3.0, -5.0), 1e-9, "un tope negativo también lo desactiva");
        near(helper, 640.0, ClashRules.score(600.0, 1.4, 100.0), 1e-9, "score = cursor + bonus");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void compareUsesTieMargin(GameTestHelper helper) {
        helper.assertTrue(ClashRules.compare(500, 505, 10) == Outcome.TIE, "5 puntos de diferencia es empate");
        helper.assertTrue(ClashRules.compare(500, 490.1, 10) == Outcome.TIE, "9.9 puntos sigue siendo empate");
        helper.assertTrue(ClashRules.compare(500, 490, 10) == Outcome.A_WINS, "10 puntos ya no es empate");
        helper.assertTrue(ClashRules.compare(400, 800, 10) == Outcome.B_WINS, "gana B");
        helper.assertTrue(ClashRules.compare(800, 400, 10) == Outcome.A_WINS, "gana A");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void mobPressTickRespectsBoundsAndSkill(GameTestHelper helper) {
        int lo = (int) (P.duration() * 0.2);
        double seed = 0.37;
        Random rng = new Random(12345L);
        double sumLow = 0, sumHigh = 0;
        int n = 400;
        for (int i = 0; i < n; i++) {
            int tLow = ClashRules.mobPressTick(rng, seed, 0.0, P);
            int tHigh = ClashRules.mobPressTick(rng, seed, 1.0, P);
            helper.assertTrue(tLow >= lo && tLow <= P.duration(),
                    "pulsación de skill 0 fuera de [" + lo + ", " + P.duration() + "]: " + tLow);
            helper.assertTrue(tHigh >= lo && tHigh <= P.duration(),
                    "pulsación de skill 1 fuera de [" + lo + ", " + P.duration() + "]: " + tHigh);
            sumLow += ClashRules.cursor(tLow, seed, P);
            sumHigh += ClashRules.cursor(tHigh, seed, P);
        }
        double avgLow = sumLow / n, avgHigh = sumHigh / n;
        helper.assertTrue(avgHigh > avgLow + 100.0,
                "un mob hábil (skill 1) debería puntuar bastante más que uno torpe: media "
                        + avgHigh + " contra " + avgLow);
        helper.assertTrue(avgHigh > 800.0,
                "con nueve muestras la media del cursor debería rondar el pico, salió " + avgHigh);
        helper.succeed();
    }
}
