package com.hmc.zenkai.feature.aura;

/**
 * TODA la aritmetica del aura. CERO imports: ni Minecraft, ni renderer, ni GeckoLib, ni
 * texturas, ni siquiera el resto del mod.
 * Esta separacion no es estetica. La curva de presencia, el mapeo a parametros y las
 * firmas raciales se calibraron en una simulacion fuera de Java; AuraSelfTest le mete a
 * esta clase los mismos valores de entrada y comprueba que salen los mismos de salida.
 * Si la aritmetica viviera mezclada con la extraccion de datos del Player, esa
 * comprobacion necesitaria arrancar el juego y en la practica no se haria nunca.
 * AuraManager es el adaptador: saca los numeros del jugador y llama aqui.
 */
public final class AuraFormula {
    private AuraFormula() {}

    /**
     * @param plApparent      PL APARENTE (ya suprimido). Un jugador escondiendo su ki
     *                        debe verse débil aunque tenga millones.
     * @param plFloor         PL de un personaje recién creado (AuraCeiling.floor()).
     * @param plCeil          PL de referencia de endgame (AuraCeiling.ceiling()).
     * @param powerPercent    slider de liberación, 0..100.
     * @param maxPowerPercent techo por habilidad: 50 + 5·nivel(ki_control).
     * @param kiControlLevel  nivel de la habilidad ki_control, 0..10.
     * @param kiFraction      ki actual / ki máximo, 0..1.
     * @param kaiokenIntensity 0..1, normalizado sobre el statPercent del enum.
     * @param turbo           0..1, desplazamiento temporal hacia el extremo energético.
     */
    public static AuraState state(long plApparent, long plFloor, long plCeil,
                                  int powerPercent, int maxPowerPercent,
                                  int kiControlLevel, float kiFraction,
                                  float kaiokenIntensity, float turbo) {

        float release = AuraTuning.clamp01(powerPercent / 100f);
        float tension = AuraTuning.clamp01(powerPercent / (float) Math.max(1, maxPowerPercent));
        float refine = AuraTuning.clamp01(kiControlLevel / 10f);

        float ki = AuraTuning.clamp01(kiFraction);
        float energy = AuraTuning.ENERGY_FLOOR + AuraTuning.ENERGY_PER_KI * ki;
        float instability = AuraTuning.smoothstep(
                (AuraTuning.INSTABILITY_KNEE - ki) / AuraTuning.INSTABILITY_KNEE);

        float presence = presence(plApparent, plFloor, plCeil);

        float chaos = tension * (1f - refine);
        // Atenuado por presencia: sin esto, un personaje recién creado (powerPercent 50,
        // que es exactamente su techo con ki_control 0) sale con chaos 1.00 — máxima
        // inestabilidad visual el primer minuto de partida. La atenuación lo baja a
        // ~0.41 sin tocar balance, y deja intacto al maestro descontrolado, que es el
        // caso que sí queremos ver.
        float chaosVisual = chaos * (AuraTuning.CHAOS_VIS_BASE
                + AuraTuning.CHAOS_VIS_PER_PRESENCE * presence);

        return new AuraState(release, tension, refine, energy, presence,
                chaos, chaosVisual, instability,
                AuraTuning.clamp01(kaiokenIntensity), AuraTuning.clamp01(turbo));
    }

    /** log10 normalizado entre suelo y techo. Cuatro décadas y media de rango real. */
    public static float presence(long plApparent, long plFloor, long plCeil) {
        double lo = Math.log10(Math.max(1L, plFloor));
        double hi = Math.log10(Math.max(10L, plCeil));
        if (hi - lo < 1e-6d) return 0f;
        double v = Math.log10(Math.max(1L, plApparent));
        return AuraTuning.clamp01((float) ((v - lo) / (hi - lo)));
    }

    /**
     * Perfil final. {@code mods} es la suma de forma y raza; pasar
     * {@link AuraModifier#NONE} da el C_v2 puro modulado por el estado.
     * Los offsets desplazan la BASE (identidad estructural, siempre visible) y las
     * ganancias multiplican solo la parte REACTIVA — la que depende de chaos,
     * instability y kaioken. De ahí que un Majin no necesite parecer más poderoso en
     * reposo para desintegrarse de forma característica al perder el control.
     */
    public static AuraProfile profile(AuraState st, AuraModifier mods) {
        AuraModifier m = mods == null ? AuraModifier.NONE : mods;

        // ── FORMA ────────────────────────────────────────────────────────────
        float mass = (AuraTuning.C_MASS + m.dMass())
                * (AuraTuning.MASS_BASE
                + AuraTuning.MASS_PER_REFINE * st.refine()
                + AuraTuning.MASS_PER_CHAOS * st.chaosVisual());

        float spike = (AuraTuning.C_SPIKE + m.dSpike())
                + AuraTuning.SPIKE_PER_PRESENCE * st.presence();

        float turbReactive = AuraTuning.TURB_PER_CHAOS * st.chaosVisual()
                + AuraTuning.TURB_PER_INSTABILITY * st.instability()
                + AuraTuning.TURB_PER_KAIOKEN * st.kaiokenIntensity();
        float turbulence = (AuraTuning.C_TURB + m.dTurb()) + turbReactive * m.turbGain();

        float spreadReactive = AuraTuning.SPREAD_PER_CHAOS * st.chaosVisual()
                + AuraTuning.SPREAD_PER_INSTABILITY * st.instability()
                + AuraTuning.SPREAD_PER_KAIOKEN * st.kaiokenIntensity();
        float spread = (AuraTuning.C_SPREAD + m.dSpread()) + spreadReactive * m.spreadGain();

        float height = (AuraTuning.C_HEIGHT + m.dHeight())
                * (AuraTuning.HEIGHT_BASE + AuraTuning.HEIGHT_PER_RELEASE * st.release());

        float density = (AuraTuning.C_DENSITY + m.dDensity())
                * (AuraTuning.DENSITY_BASE + AuraTuning.DENSITY_PER_PRESENCE * st.presence());

        // ── COMPORTAMIENTO ───────────────────────────────────────────────────
        float pulseHz = (AuraTuning.PULSE_HZ_BASE
                + AuraTuning.PULSE_HZ_PER_KI * st.kaiokenIntensity()) * m.pulseGain();
        float pulseAmp = (AuraTuning.PULSE_AMP_BASE
                + AuraTuning.PULSE_AMP_PER_KI * st.kaiokenIntensity()) * m.pulseGain();

        // La cadencia mira turbulence Y presence: un maestro tiene chaos 0, y atarla
        // solo al descontrol dejaba al jugador más poderoso con el aura más plana.
        float frameTicks = AuraTuning.frameTicks(
                AuraTuning.clamp(turbulence, AuraTuning.MIN_TURB, AuraTuning.MAX_TURB),
                st.presence());
        float groundSpin = AuraTuning.groundSpin(st.presence());

        // ── PODER: solo AuraState. `m` NO aparece en ninguna de estas líneas. ─
        // El PL MULTIPLICA sobre el mínimo en vez de sumar sobre una base alta: así
        // el Power Level es lo que más se ve crecer, que es lo que debe pasar.
        float size = getSize(st);

        float alpha = (AuraTuning.ALPHA_BASE + AuraTuning.ALPHA_PER_ENERGY * st.energy())
                * (1f + AuraTuning.ALPHA_PER_TURBO * st.turbo());

        float core = AuraTuning.CORE_MUL
                * (AuraTuning.CORE_BASE + AuraTuning.CORE_PER_ENERGY * st.energy())
                * (1f + AuraTuning.CORE_PER_TURBO * st.turbo());

        int wisps = Math.round(AuraTuning.WISPS_BASE
                + AuraTuning.WISPS_PER_PRESENCE * st.presence());
        float sparks = AuraTuning.SPARKS_PER_SEC * st.presence() * st.tension()
                * (1f + AuraTuning.SPARKS_PER_KAIOKEN * st.kaiokenIntensity());
        float ground = st.presence() * st.presence() * st.release();

        return new AuraProfile(mass, spike, turbulence, spread, height, density,
                pulseHz, pulseAmp, frameTicks, groundSpin,
                size, alpha, core, wisps, sparks, ground);
    }

    private static float getSize(AuraState st) {
        float size = AuraTuning.SIZE_BASE
                * (1f + AuraTuning.SIZE_PER_PRESENCE * st.presence())
                * (AuraTuning.SIZE_RELEASE_BASE + AuraTuning.SIZE_PER_RELEASE * st.release())
                * (AuraTuning.SIZE_ENERGY_BASE + AuraTuning.SIZE_PER_ENERGY * st.energy())
                * (1f + AuraTuning.SIZE_PER_TURBO * st.turbo())
                * (1f + AuraTuning.SIZE_PER_KAIOKEN * st.kaiokenIntensity());
        // Nadie baja del aura de un recién creado liberando al 100%.
        size = Math.max(AuraTuning.SIZE_MIN, size);
        return size;
    }

}