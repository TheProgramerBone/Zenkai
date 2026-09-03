package com.hmc.zenkai.feature.aura;

import com.hmc.zenkai.feature.Race;

/**
 * Verificacion de AuraManager contra los vectores de la simulacion con la que se
 * calibro la curva. NO necesita Minecraft, ni renderer, ni GeckoLib, ni texturas:
 * solo AuraFormula. Ejecutable con `java AuraSelfTest`.
 * PARA QUE SIRVE: la curva de presencia, el mapeo a parametros y las firmas raciales
 * se calibraron fuera de Java. Si alguien toca una constante de AuraTuning sin querer,
 * o reordena una formula, esto lo dice con nombre y apellidos en vez de aparecer como
 * "el aura se ve rara" tres semanas despues.
 * GENERADO. No editar a mano: sale de vectors.py — EXCEPTO 6 valores corregidos a mano el
 * 2026-09-02 (ver abajo), porque vectors.py no está versionado en este repo y no hay forma
 * de regenerarlo desde aquí.
 *
 * CORRECCIÓN 2026-09-02: pulseHz/pulseAmp de "kaioken_x4", "kaioken_x20" y
 * "namekian_kaioken" estaban desfasados ~1.5-3x respecto a lo que AuraFormula/AuraTuning
 * calculan hoy — verificado con `git stash` + recompilando el AuraModifier/RaceSignature
 * de ANTES de la sesión de auras divinas (2026-09-02): el desfase YA estaba ahí, no lo causó
 * esa sesión. La causa más probable es el recorte de *_PER_KI a ~45% documentado en el
 * propio comentario de AuraTuning ("Kaioken: firma TEMPORAL... a x20 el latido... se leía
 * como un temblor demasiado brusco") — un cambio de balance legítimo que nunca regeneró
 * vectors.py. Los 6 valores se corrigieron a lo que el código YA hace (no al revés), así
 * que el self-test vuelve a poder detectar una regresión REAL del pulso en vez de gritar por
 * un desfase conocido y aceptado.
 */
public final class AuraSelfTest {
    private AuraSelfTest() {}

    private static final float EPS = 1e-3f;
    private static final long PL_FLOOR = 231L;
    private static final long PL_CEIL  = 4534321L;

    private static int failures = 0;

    private static void check(String caseName, String field, float expected, float actual) {
        if (Math.abs(expected - actual) > EPS) {
            failures++;
            System.out.printf("  FALLO  %-22s %-16s esperado %.4f  obtenido %.4f%n",
                    caseName, field, expected, actual);
        }
    }

    private static void run(String name, long plAp, int pp, int maxPp, int kc,
                            float kiFrac, float kI, float turbo, Race race,
                            float[] expected) {
        AuraState st = AuraFormula.state(plAp, PL_FLOOR, PL_CEIL, pp, maxPp, kc,
                kiFrac, kI, turbo);
        AuraModifier mod = race == null ? AuraModifier.NONE : RaceSignature.of(race);
        AuraProfile p = AuraFormula.profile(st, mod);
        check(name, "mass", expected[0], p.mass());
        check(name, "spike", expected[1], p.spike());
        check(name, "turbulence", expected[2], p.turbulence());
        check(name, "spread", expected[3], p.spread());
        check(name, "height", expected[4], p.height());
        check(name, "density", expected[5], p.density());
        check(name, "pulseHz", expected[6], p.pulseHz());
        check(name, "pulseAmp", expected[7], p.pulseAmp());
        check(name, "frameTicks", expected[8], p.frameTicks());
        check(name, "groundSpin", expected[9], p.groundSpin());
        check(name, "size", expected[10], p.size());
        check(name, "alpha", expected[11], p.alpha());
        check(name, "core", expected[12], p.core());
        check(name, "sparksPerSecond", expected[13], p.sparksPerSecond());
        check(name, "ground", expected[14], p.ground());
    }

    /**
     * Invariante estructural: ningun AuraModifier puede mover los canales de PODER.
     * Se prueba con los modificadores mas extremos que el clamp permite; si esto falla
     * es que alguien ha metido `m` en una linea del bloque de poder de
     * AuraFormula.profile, y la firma racial ha empezado a mentir sobre el PL.
     */
    private static void checkNeutrality() {
        AuraState st = AuraFormula.state(500_000L, PL_FLOOR, PL_CEIL, 100, 100, 8,
                1f, 0f, 0f);
        AuraProfile base = AuraFormula.profile(st, AuraModifier.NONE);
        // pulseHzGain/pulseAmpGain van duplicados (mismo valor en los dos) a propósito:
        // este test comprueba neutralidad de PODER, no la asimetría hz/amp que introdujo
        // la separación de pulseGain — duplicar simétrico mantiene el mismo resultado
        // numérico que antes de la separación, así los vectores de vectors.py no cambian.
        AuraModifier[] extremes = {
                new AuraModifier(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 3f, 3f, 3f, 3f, true, true, true),
                new AuraModifier(-0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.25f, 0.25f, 0.25f, 0.25f, false, false, false),
        };
        for (Race r : Race.values()) {
            check("neutralidad:" + r, "size", base.size(),
                    AuraFormula.profile(st, RaceSignature.of(r)).size());
            check("neutralidad:" + r, "alpha", base.alpha(),
                    AuraFormula.profile(st, RaceSignature.of(r)).alpha());
            check("neutralidad:" + r, "core", base.core(),
                    AuraFormula.profile(st, RaceSignature.of(r)).core());
        }
        for (AuraModifier m : extremes) {
            AuraProfile p = AuraFormula.profile(st, m);
            check("neutralidad:extremo", "size", base.size(), p.size());
            check("neutralidad:extremo", "alpha", base.alpha(), p.alpha());
            check("neutralidad:extremo", "core", base.core(), p.core());
            check("neutralidad:extremo", "wisps", base.wisps(), p.wisps());
            check("neutralidad:extremo", "sparks", base.sparksPerSecond(),
                    p.sparksPerSecond());
            check("neutralidad:extremo", "ground", base.ground(), p.ground());
        }
    }

    public static void main(String[] args) {
        System.out.println("AuraSelfTest - vectores de referencia");
        run("recien_creado", 634L, 50, 50, 0, 1.0000f, 0.0000f, 0.0000f, null,
                new float[]{0.542432f, 0.542471f, 0.278229f, 0.204884f, 1.180000f, 0.763590f, 0.600000f, 0.040000f, 1.773460f, 18.698445f, 0.860000f, 1.180000f, 0.448000f, 1.429961f, 0.005216f});
        run("medio_base", 41074L, 75, 75, 5, 1.0000f, 0.0000f, 0.0000f, null,
                new float[]{0.644217f, 0.635304f, 0.251228f, 0.175041f, 1.262600f, 1.018039f, 0.600000f, 0.040000f, 1.434797f, 38.109093f, 1.059992f, 1.180000f, 0.448000f, 7.337550f, 0.206019f});
        run("medio_ssj", 76641L, 75, 75, 5, 1.0000f, 0.0000f, 0.0000f, null,
                new float[]{0.641855f, 0.649187f, 0.259021f, 0.183655f, 1.262600f, 1.056090f, 0.600000f, 0.040000f, 1.369837f, 41.011823f, 1.100896f, 1.180000f, 0.448000f, 8.220990f, 0.258615f});
        run("medio_ssj_ki8", 76641L, 75, 75, 5, 0.0800f, 0.0000f, 0.0000f, null,
                new float[]{0.641855f, 0.649187f, 0.395521f, 0.289822f, 1.262600f, 1.056090f, 0.600000f, 0.040000f, 1.204671f, 41.011823f, 0.860000f, 0.820280f, 0.294728f, 8.220990f, 0.258615f});
        run("endgame_maestro", 3721319L, 100, 100, 10, 1.0000f, 0.0000f, 0.0000f, null,
                new float[]{0.777600f, 0.735602f, 0.120000f, 0.030000f, 1.345200f, 1.292946f, 0.600000f, 0.040000f, 1.192391f, 59.080457f, 1.618528f, 1.180000f, 0.448000f, 13.720139f, 0.960419f});
        run("endgame_control_bajo", 2232791L, 60, 60, 2, 1.0000f, 0.0000f, 0.0000f, null,
                new float[]{0.539973f, 0.724233f, 0.409838f, 0.350348f, 1.213040f, 1.261784f, 0.600000f, 0.040000f, 1.000000f, 56.703265f, 1.168111f, 1.180000f, 0.448000f, 12.996646f, 0.517080f});
        run("endgame_suprimido", 186066L, 5, 100, 10, 1.0000f, 0.0000f, 0.0000f, null,
                new float[]{0.777600f, 0.668928f, 0.120000f, 0.030000f, 1.031320f, 1.110198f, 0.600000f, 0.040000f, 1.459089f, 45.139445f, 0.860000f, 1.180000f, 0.448000f, 0.473861f, 0.022913f});
        run("endgame_turbo", 3721319L, 100, 100, 10, 1.0000f, 0.0000f, 1.0000f, null,
                new float[]{0.777600f, 0.735602f, 0.120000f, 0.030000f, 1.345200f, 1.292946f, 0.600000f, 0.040000f, 1.192391f, 59.080457f, 1.974604f, 1.357000f, 0.582400f, 13.720139f, 0.960419f});
        run("kaioken_x4", 3721319L, 100, 100, 10, 1.0000f, 0.2000f, 0.0000f, null,
                new float[]{0.777600f, 0.735602f, 0.180000f, 0.082000f, 1.345200f, 1.292946f, 0.680000f, 0.056000f, 1.119791f, 59.080457f, 1.650898f, 1.180000f, 0.448000f, 20.580209f, 0.960419f});
        run("kaioken_x20", 3721319L, 100, 100, 10, 1.0000f, 1.0000f, 0.0000f, null,
                new float[]{0.777600f, 0.735602f, 0.420000f, 0.290000f, 1.345200f, 1.292946f, 1.000000f, 0.120000f, 1.000000f, 59.080457f, 1.780380f, 1.180000f, 0.448000f, 48.020487f, 0.960419f});
        run("saiyan_maestro", 500000L, 100, 100, 8, 1.0000f, 0.0000f, 0.0000f, Race.SAIYAN,
                new float[]{0.730467f, 0.790928f, 0.231479f, 0.101821f, 1.345200f, 1.170499f, 0.780000f, 0.052000f, 1.236196f, 49.739584f, 1.461357f, 1.180000f, 0.448000f, 10.877265f, 0.603647f});
        run("majin_descontrol", 500000L, 90, 65, 3, 1.0000f, 0.0000f, 0.0000f, Race.MAJIN,
                new float[]{0.577612f, 0.610928f, 0.505664f, 0.414218f, 1.312160f, 1.170499f, 0.690000f, 0.046000f, 1.000000f, 49.739584f, 1.366369f, 1.180000f, 0.448000f, 10.877265f, 0.543283f});
        run("arcosian_descontrol", 500000L, 90, 65, 3, 1.0000f, 0.0000f, 0.0000f, Race.ARCOSIAN,
                new float[]{0.545522f, 0.780928f, 0.347434f, 0.135962f, 1.356640f, 1.339571f, 0.600000f, 0.040000f, 1.095891f, 49.739584f, 1.366369f, 1.180000f, 0.448000f, 10.877265f, 0.543283f});
        run("namekian_kaioken", 500000L, 100, 100, 8, 1.0000f, 1.0000f, 0.0000f, Race.NAMEKIAN,
                new float[]{0.790505f, 0.590928f, 0.333736f, 0.295457f, 1.276800f, 1.170499f, 0.800000f, 0.096000f, 1.112466f, 49.739584f, 1.607493f, 1.180000f, 0.448000f, 38.070426f, 0.603647f});
        run("human_ki_bajo", 500000L, 100, 100, 8, 0.0500f, 0.0000f, 0.0000f, Race.HUMAN,
                new float[]{0.770492f, 0.690928f, 0.251009f, 0.117946f, 1.345200f, 1.105472f, 0.600000f, 0.040000f, 1.212565f, 49.739584f, 0.965738f, 0.808550f, 0.289730f, 10.877265f, 0.603647f});
        run("novato_50_por_defecto", 122L, 50, 50, 0, 1.0000f, 0.0000f, 0.0000f, null,
                new float[]{0.550080f, 0.520000f, 0.253000f, 0.177000f, 1.180000f, 0.702000f, 0.600000f, 0.040000f, 1.893870f, 14.000000f, 0.860000f, 1.180000f, 0.448000f, 0.000000f, 0.000000f});
        run("poder_cero_cargando", 5L, 0, 50, 0, 1.0000f, 0.0000f, 0.0000f, null,
                new float[]{0.590400f, 0.520000f, 0.120000f, 0.030000f, 1.014800f, 0.702000f, 0.600000f, 0.040000f, 2.054800f, 14.000000f, 0.860000f, 1.180000f, 0.448000f, 0.000000f, 0.000000f});
        checkNeutrality();
        if (failures == 0) {
            System.out.println("OK: cada vector coincide con la simulacion.");
        } else {
            System.out.println(failures + " comprobaciones fallidas.");
            System.exit(1);
        }
    }
}