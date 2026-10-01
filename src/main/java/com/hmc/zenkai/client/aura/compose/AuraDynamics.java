package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key;
import com.hmc.zenkai.feature.aura.AuraManager;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * ESTADOS dinámicos del aura de un jugador, derivados SOLO de datos que el cliente ya tiene
 * sincronizados (flags de carga/transformación, ki, % liberado, Kaioken, turbo, vuelo, daño). El
 * servidor sigue decidiendo cuándo hay aura; esto decide cómo REACCIONA mientras la hay.
 * <p>
 * El TAMAÑO nunca se toca aquí: lo da el poder real (AuraFormula ya crece con turbo, Kaioken y el
 * % liberado, y encoge con la supresión a través del PL aparente). Los estados cambian RITMO,
 * turbulencia, opacidad y la cantidad de componentes secundarios — "más espectacular" no es "más
 * poderoso".
 * <p>
 * {@code /zenkaiaura state} puede forzar cualquiera para el aura propia ({@link AuraDebug}).
 */
public final class AuraDynamics {

    /** Estados con nombre, los que acepta {@code /zenkaiaura state}. */
    public enum State {
        CHARGING, HIGH_CHARGE, TRANSFORMING, TURBO, KAIOKEN, LOW_KI, SUPPRESSED, FLYING, PERTURBED;

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    public boolean charging;
    /** Cargando con el % liberado a ≥ 90% de lo que permite el Ki Control. */
    public boolean highCharge;
    public boolean transforming;
    public boolean turbo;
    /** Intensidad de Kaioken 0..1 (0 = sin Kaioken). */
    public float kaioken;
    public float kiFrac = 1f;
    /** Ki bajo (&lt; 25%): el aura parpadea y pierde cuerpo. */
    public boolean lowKi;
    /** Poder contenido: % liberado por debajo de la mitad de lo que permite el Ki Control. */
    public boolean suppressed;
    public boolean flying;
    /** Recibió daño hace nada (hurtTime): el aura se sacude. */
    public boolean perturbed;

    /** Relee el estado del jugador. Sin asignaciones. */
    public void read(AbstractClientPlayer p, boolean self) {
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(p);
        transforming = form.isTransforming();
        charging = AuraClientState.isChargingOrTransforming(p);
        turbo = AuraClientState.isTurbo(p);
        kaioken = AuraManager.kaiokenIntensity(form.getKaioken());
        kiFrac = Mth.clamp(stats.getEnergy() / (float) Math.max(1, stats.getEnergyMax()), 0f, 1f);
        lowKi = kiFrac < 0.25f;
        int max = Math.max(1, SkillEffects.maxPowerPercent(stats));
        float rel = stats.getPowerPercent() / (float) max;
        highCharge = charging && rel >= 0.9f;
        suppressed = rel < 0.5f;
        flying = stats.isFlyEnabled() && !p.onGround();
        perturbed = p.hurtTime > 0;
        if (self) applyForced();
    }

    /** Estados forzados por {@code /zenkaiaura state} (solo el aura propia). */
    private void applyForced() {
        for (State s : State.values()) {
            Boolean v = AuraDebug.FORCED.get(s);
            if (v == null) continue;
            boolean on = v;
            switch (s) {
                case CHARGING -> charging = on;
                case HIGH_CHARGE -> { highCharge = on; if (on) charging = true; }
                case TRANSFORMING -> transforming = on;
                case TURBO -> turbo = on;
                case KAIOKEN -> kaioken = on ? Math.max(kaioken, 0.6f) : 0f;
                case LOW_KI -> { lowKi = on; kiFrac = on ? 0.08f : Math.max(kiFrac, 0.5f); }
                case SUPPRESSED -> suppressed = on;
                case FLYING -> flying = on;
                case PERTURBED -> perturbed = on;
            }
        }
    }

    public boolean is(State s) {
        return switch (s) {
            case CHARGING -> charging;
            case HIGH_CHARGE -> highCharge;
            case TRANSFORMING -> transforming;
            case TURBO -> turbo;
            case KAIOKEN -> kaioken > 0f;
            case LOW_KI -> lowKi;
            case SUPPRESSED -> suppressed;
            case FLYING -> flying;
            case PERTURBED -> perturbed;
        };
    }

    /** Latido de Kaioken: doble golpe (lub-dub) a 1.2-2.4 Hz según la intensidad. 0..1. */
    public float kaiokenBeat(float seconds) {
        if (kaioken <= 0f) return 0f;
        float ph = (seconds * (1.2f + 1.2f * kaioken)) % 1f;
        return Math.max(Mth.clamp(1f - Math.abs(ph - 0.08f) / 0.07f, 0f, 1f),
                0.6f * Mth.clamp(1f - Math.abs(ph - 0.27f) / 0.07f, 0f, 1f));
    }

    /**
     * Efecto de los estados sobre el cuerpo, después de familia y estilo.
     * @param seed desfase por jugador (parpadeos nunca sincronizados entre jugadores).
     */
    public void applyToBody(AuraBodyParams b, float seconds, int seed) {
        if (charging) {
            // Cargar = la energía se agita: sube más rápido y tiembla más.
            b.mul(Key.SPEED, 1.25f);
            b.mul(Key.DETAIL, 1.2f);
        }
        if (highCharge) {
            // Carga extrema: turbulencia y actividad de la corona al máximo.
            b.mul(Key.SPEED, 1.2f);
            b.mul(Key.DETAIL, 1.3f);
            b.mul(Key.AMP, 1.12f);
            b.mul(Key.EMIT_EDGE, 1.1f);
        }
        if (transforming) {
            // Acumulación antes del estallido: la energía se concentra (algo más prieta, mucho
            // más agitada). El estallido lo pone TransformFx al terminar.
            b.mul(Key.SPEED, 1.5f);
            b.mul(Key.DETAIL, 1.4f);
            b.mul(Key.RADIUS, 0.93f + 0.04f * Mth.sin(seconds * 18f));
        }
        if (turbo) {
            // Turbo = núcleo más lleno y algo más de brillo del borde.
            b.mul(Key.ALPHA_CENTER, 1.25f);
            b.mul(Key.EMIT_EDGE, 1.15f);
        }
        if (kaioken > 0f) {
            // Kaioken = presión inestable: ritmo alto, temblor, y el volumen entero LATE con un
            // doble golpe de corazón. El rojo lo pone AuraColors.
            float beat = kaiokenBeat(seconds);
            b.mul(Key.SPEED, 1f + 0.6f * kaioken);
            b.mul(Key.DETAIL, 1f + 0.7f * kaioken);
            float swell = 1f + 0.07f * kaioken * beat;
            b.mul(Key.RADIUS, swell);
            b.mul(Key.AMP, 1f + 0.25f * kaioken * beat);
            b.mul(Key.EMIT_EDGE, 1f + 0.2f * beat);
        }
        if (suppressed) {
            // Poder contenido: calma. (Más pequeña ya lo es por el PL aparente.)
            b.mul(Key.SPEED, 0.7f);
            b.mul(Key.AMP, 0.75f);
            b.mul(Key.DETAIL, 0.7f);
        }
        if (perturbed) {
            // Golpe recibido: sacudida breve del contorno.
            b.mul(Key.DETAIL, 1.8f);
            b.mul(Key.OPACITY, 0.85f + 0.15f * Mth.sin(seconds * 40f + seed));
        }
        if (lowKi) {
            // Poco ki = el aura pierde cuerpo y parpadea (dos senos desfasados por jugador).
            float depth = 1f - kiFrac / 0.25f; // 0 en el 25%, 1 sin ki
            float flicker = 0.5f + 0.5f * Mth.sin(seconds * 13f + seed) * Mth.sin(seconds * 7.3f + seed * 0.7f);
            b.mul(Key.OPACITY, 1f - depth * (0.35f + 0.3f * flicker));
            b.mul(Key.ALPHA_CENTER, 1f - 0.4f * depth);
            b.mul(Key.AMP, 1f - 0.3f * depth);
        }
    }

    /** Factor de emisión de partículas y wisps por estado. */
    public float emissionFactor() {
        float f = 1f;
        if (charging) f *= 1.6f;
        if (highCharge) f *= 1.4f;
        if (transforming) f *= 1.5f;
        if (turbo) f *= 1.3f;
        if (kaioken > 0f) f *= 1f + 0.5f * kaioken;
        if (suppressed) f *= 0.5f;
        if (lowKi) f *= 0.5f;
        return f;
    }

    /** Factor de rayos por estado: la carga extrema y la transformación los disparan. */
    public float electricityFactor() {
        float f = emissionFactor();
        if (highCharge) f *= 1.5f;
        if (transforming) f *= 1.5f;
        if (lowKi) f *= 0.5f;
        return f;
    }

    /** Factor de frecuencia de los pulsos (contorno, cáscaras). */
    public float pulseRateFactor() {
        float f = 1f;
        if (highCharge) f *= 1.4f;
        if (kaioken > 0f) f *= 1f + kaioken;
        if (suppressed) f *= 0.7f;
        return f;
    }
}
