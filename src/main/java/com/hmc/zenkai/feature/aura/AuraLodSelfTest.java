package com.hmc.zenkai.feature.aura;

import java.util.List;

/**
 * Verificación estructural del LOD. Como AuraSelfTest: sin Minecraft, sin renderer.
 *
 * No comprueba que "se vea bien" — eso se midió antes con renders. Comprueba las
 * invariantes que un cambio descuidado rompería sin que nadie se diera cuenta hasta
 * verlo en un servidor con doce jugadores transformados.
 */
public final class AuraLodSelfTest {
    private AuraLodSelfTest() {}

    private static final long PL_FLOOR = 231L;
    private static final long PL_CEIL  = 4_534_321L;

    private static int failures = 0;

    private static void fail(String what, Object expected, Object actual) {
        failures++;
        System.out.printf("  FALLO  %-46s esperado %s  obtenido %s%n",
                what, expected, actual);
    }

    private static void eq(String what, Object expected, Object actual) {
        if (!expected.equals(actual)) fail(what, expected, actual);
    }

    private static void eqf(String what, float expected, float actual) {
        if (Math.abs(expected - actual) > 1e-4f) fail(what, expected, actual);
    }

    private static void isTrue(String what, boolean cond) {
        if (!cond) fail(what, true, false);
    }

    private static AuraProfile sample() {
        AuraState st = AuraFormula.state(3_721_319L, PL_FLOOR, PL_CEIL,
                100, 100, 10, 1f, 0f, 0f);
        return AuraFormula.profile(st, AuraModifier.NONE);
    }

    /** Fronteras de banda. Se comprueban justo por dentro y justo por fuera. */
    private static void checkBoundaries() {
        eq("banda a 0 bloques", AuraLod.NEAR, AuraLod.byDistanceSq(0));
        eq("banda a 7.9 bloques", AuraLod.NEAR, AuraLod.byDistanceSq(7.9 * 7.9));
        eq("banda a 8.1 bloques", AuraLod.MID, AuraLod.byDistanceSq(8.1 * 8.1));
        eq("banda a 17.9 bloques", AuraLod.MID, AuraLod.byDistanceSq(17.9 * 17.9));
        eq("banda a 18.1 bloques", AuraLod.FAR, AuraLod.byDistanceSq(18.1 * 18.1));
        eq("banda a 31.9 bloques", AuraLod.FAR, AuraLod.byDistanceSq(31.9 * 31.9));
        eq("banda a 32.1 bloques", AuraLod.SIGNATURE, AuraLod.byDistanceSq(32.1 * 32.1));
        eq("banda a 500 bloques", AuraLod.SIGNATURE, AuraLod.byDistanceSq(500 * 500));
    }

    /** La calidad puede degradar, nunca mejorar. */
    private static void checkQualityFloor() {
        eq("cerca + calidad baja -> degrada",
                AuraLod.FAR, AuraLod.effective(1.0, AuraLod.FAR));
        eq("lejos + calidad alta NO mejora",
                AuraLod.SIGNATURE, AuraLod.effective(100 * 100, AuraLod.NEAR));
        eq("sin limite de calidad", AuraLod.MID, AuraLod.effective(10 * 10, null));
    }

    /**
     * LO IMPORTANTE: el LOD solo puede mover alpha y core. Si toca la forma o el tamaño
     * está alterando la lectura de poder en vez de conservarla, que es justo lo que la
     * medición de deriva descartó.
     */
    private static void checkOnlyAlphaAndCore() {
        AuraProfile base = sample();
        for (AuraLod lod : AuraLod.values()) {
            AuraProfile c = lod.compensate(base);
            String tag = "compensate(" + lod + ")";
            eqf(tag + " no toca mass", base.mass(), c.mass());
            eqf(tag + " no toca spike", base.spike(), c.spike());
            eqf(tag + " no toca turbulence", base.turbulence(), c.turbulence());
            eqf(tag + " no toca spread", base.spread(), c.spread());
            eqf(tag + " no toca height", base.height(), c.height());
            eqf(tag + " no toca density", base.density(), c.density());
            eqf(tag + " no toca size", base.size(), c.size());
            eqf(tag + " no toca pulseHz", base.pulseHz(), c.pulseHz());
            eqf(tag + " no toca pulseAmp", base.pulseAmp(), c.pulseAmp());
            eqf(tag + " no toca frameTicks", base.frameTicks(), c.frameTicks());
            eqf(tag + " no toca groundSpin", base.groundSpin(), c.groundSpin());
            eqf(tag + " compensa alpha", base.alpha() * lod.alphaMul(), c.alpha());
            eqf(tag + " compensa core", base.core() * lod.coreMul(), c.core());
        }
    }

    /** Las capas solo se apagan al alejarse: nunca reaparecen en una banda más lejana. */
    private static void checkLayersMonotonic() {
        AuraLod prev = null;
        for (AuraLod lod : AuraLod.values()) {
            if (prev != null) {
                isTrue(lod + " no reactiva wisps", !(lod.wisps() && !prev.wisps()));
                isTrue(lod + " no reactiva sparks", !(lod.sparks() && !prev.sparks()));
                isTrue(lod + " no reactiva ground", !(lod.ground() && !prev.ground()));
                isTrue(lod + " no reactiva distorsion",
                        !(lod.distortion() && !prev.distortion()));
                isTrue(lod + " no cuesta mas quads que " + prev,
                        lod.quadCost(AuraSkirts.C_V2) <= prev.quadCost(AuraSkirts.C_V2));
                isTrue(lod + " compensa mas alpha que " + prev,
                        lod.alphaMul() >= prev.alphaMul());
            }
            prev = lod;
        }
    }

    /** Ningún anillo puede bajar del suelo de legibilidad. */
    private static void checkMinCount() {
        for (AuraLod lod : AuraLod.values()) {
            for (AuraSkirt s : lod.skirts(AuraSkirts.C_V2)) {
                isTrue(lod + " respeta MIN_COUNT", s.count() >= AuraSkirt.MIN_COUNT);
            }
        }
    }

    /** Un perfil apagado no produce trabajo para el renderer. */
    private static void checkEmptyPlan() {
        AuraSkirts.Plan p = AuraSkirts.plan(AuraProfile.OFF, 1.0, null, 0xFFE55C, -1);
        isTrue("perfil OFF -> plan vacio", p.isEmpty());
        eq("perfil OFF -> sin faldones", 0, p.skirts().size());
    }

    /** El núcleo se resuelve en el plan, no en el renderer. */
    private static void checkColors() {
        AuraProfile prof = sample();
        AuraSkirts.Plan claro = AuraSkirts.plan(prof, 1.0, null, 0xFFE55C, -1);
        eq("aura clara lleva nucleo", true, claro.hasCore());
        eq("sin kaioken no hay capa exterior", false, claro.hasOuter());
        eq("innerColor se propaga", 0xFFE55C, claro.innerColor());

        AuraSkirts.Plan oscura = AuraSkirts.plan(prof, 1.0, null, 0x2A0A3C, -1);
        eq("aura oscura NO lleva nucleo", false, oscura.hasCore());
        eq("aura oscura -> coreColor -1", -1, oscura.coreColor());

        AuraSkirts.Plan kaioken = AuraSkirts.plan(prof, 1.0, null, 0xFFE55C, 0xE0282C);
        eq("kaioken -> capa exterior", true, kaioken.hasOuter());
        eq("capas: exterior x nucleo", 4, kaioken.layerCount());
        isTrue("kaioken cuesta el doble de quads",
                kaioken.quadCost() == claro.quadCost() * 2);
    }

    /** La cadencia acelera con turbulence Y con presence, y respeta sus límites. */
    private static void checkFrameTicks() {
        isTrue("el descontrol acelera",
                AuraTuning.frameTicks(0.05f, 0f) > AuraTuning.frameTicks(0.9f, 0f));
        isTrue("la presencia acelera",
                AuraTuning.frameTicks(0.1f, 0f) > AuraTuning.frameTicks(0.1f, 1f));
        eqf("sin nada -> base", AuraTuning.FRAME_TICKS_BASE,
                AuraTuning.frameTicks(0f, 0f));
        isTrue("los extremos respetan el minimo",
                AuraTuning.frameTicks(1f, 1f) >= AuraTuning.FRAME_TICKS_MIN);

        // EL CASO QUE MOTIVO EL CAMBIO: un maestro tiene chaos 0, asi que su
        // turbulence se queda en el minimo. Antes eso lo dejaba con la animacion mas
        // lenta del juego siendo el jugador mas poderoso.
        AuraState maestro = AuraFormula.state(3_721_319L, PL_FLOOR, PL_CEIL,
                100, 100, 10, 1f, 0f, 0f);
        AuraState novato = AuraFormula.state(245L, PL_FLOOR, PL_CEIL,
                50, 50, 0, 1f, 0f, 0f);
        AuraProfile pm = AuraFormula.profile(maestro, AuraModifier.NONE);
        AuraProfile pn = AuraFormula.profile(novato, AuraModifier.NONE);
        isTrue("un endgame maestro NO se anima mas lento que un novato",
                pm.frameTicks() < pn.frameTicks());
        isTrue("y su anillo de suelo gira mas rapido",
                pm.groundSpin() > pn.groundSpin());
    }

    /** Un aura activa nunca desaparece. */
    private static void checkSizeFloor() {
        // poder al 0% cargando ki: presence 0, release 0. Antes no se dibujaba nada.
        AuraState apagado = AuraFormula.state(5L, PL_FLOOR, PL_CEIL,
                0, 50, 0, 1f, 0f, 0f);
        isTrue("con presence 0 el estado sigue siendo visible", apagado.isVisible());
        AuraProfile p = AuraFormula.profile(apagado, AuraModifier.NONE);
        isTrue("y el perfil tambien", p.isVisible());
        isTrue("respeta SIZE_MIN", p.size() >= AuraTuning.SIZE_MIN - 1e-4f);
    }

    private static void report() {
        System.out.println("\n  coste en quads por banda (cono, masa + nucleo):");
        for (AuraLod lod : AuraLod.values()) {
            List<AuraSkirt> sk = lod.skirts(AuraSkirts.C_V2);
            System.out.printf("    %-10s %d faldones, %3d quads, alpha x%.2f, core x%.2f%n",
                    lod, sk.size(), lod.quadCost(AuraSkirts.C_V2),
                    lod.alphaMul(), lod.coreMul());
        }
    }

    public static void main(String[] args) {
        System.out.println("AuraLodSelfTest - invariantes del LOD");
        checkBoundaries();
        checkQualityFloor();
        checkOnlyAlphaAndCore();
        checkLayersMonotonic();
        checkMinCount();
        checkEmptyPlan();
        checkColors();
        checkFrameTicks();
        checkSizeFloor();
        report();
        if (failures == 0) {
            System.out.println("\nOK: cada invariante del LOD se cumple.");
        } else {
            System.out.println("\n" + failures + " comprobaciones fallidas.");
            System.exit(1);
        }
    }
}