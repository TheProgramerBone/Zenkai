package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.feature.forms.OverdriveTuning;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Estado compartido del "temblor" de romper el 100% (Shift+cargar ya al tope): condición común
 * para el jitter y el color del gauge (KiChargeGaugeOverlay), para no preguntar "¿estoy
 * temblando ahora mismo?" ni calcular el progreso de rotura en más de un sitio.
 *
 * Deliberadamente NO mueve la cámara (a petición explícita) — solo el gauge tiembla.
 *
 * El progreso de rotura (breakProgress) se calcula 100% en cliente, SIN un contador sincronizado
 * nuevo: el momento en que el cliente ve trembling() pasar a true es el MISMO tick (módulo
 * latencia de red) en que el servidor empieza a contar en KiChargeSystem/PlayerTickState.FORCE_
 * TICKS, así que basta con medir cuánto lleva el cliente viendo la condición activa
 * (transitionStart, ya trackeado para la rampa) contra el mismo umbral que usa el servidor
 * (OverdriveTuning.breakthroughTicksNeeded, con hasBrokenOverdriveOnce ya sincronizado). Es
 * aproximado (puramente cosmético) pero no necesita ida y vuelta al servidor.
 */
public final class OverdriveClientState {
    private OverdriveClientState() {}

    private static final int RAMP_TICKS = 5;

    /** Píxeles de pantalla, a intensidad máxima, para el jitter del gauge. */
    private static final float HUD_JITTER_PX = 1.6f;

    private static boolean lastState = false;
    private static long transitionStart = 0L;

    /** ¿Se cumple la condición de temblor ahora mismo (sin rampa)? Ya al tope de 100%, cargando,
     *  con Shift sostenido — exactamente la ventana entre llegar a 100 y romperlo de verdad. */
    public static boolean trembling() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        return att.isChargingKi() && att.isOverdriveCharging() && att.getPowerPercent() == 100;
    }

    /** Actualiza lastState/transitionStart si la condición cambió. Idempotente dentro del mismo
     *  tick — lo demás de esta clase llama esto primero. */
    private static void refresh(long now) {
        boolean active = trembling();
        if (active != lastState) {
            lastState = active;
            transitionStart = now;
        }
    }

    /** Factor 0..1 con rampa de entrada/salida, para el jitter. */
    private static float intensity(long now) {
        refresh(now);
        float ramp = Math.min(1f, (now - transitionStart) / (float) RAMP_TICKS);
        return lastState ? ramp : Math.max(0f, 1f - ramp);
    }

    /** 0..1: qué tan cerca está de romper el candado esta vez (0 = acaba de llegar a 100 y
     *  sostener Shift, 1 = ya debería estar rompiendo). Fuera de la ventana de temblor, 0. */
    public static float breakProgress(long now) {
        refresh(now);
        if (!lastState) return 0f;
        int need = OverdriveTuning.breakthroughTicksNeeded(hasBrokenBefore());
        return Math.min(1f, (now - transitionStart) / (float) need);
    }

    private static boolean hasBrokenBefore() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return PlayerStatsAttachment.get(mc.player).hasBrokenOverdriveOnce();
    }

    /** Offset en píxeles {x, y} para el jitter del gauge (número + anillo). {0,0} sin temblor.
     *  La amplitud crece con breakProgress: apenas se nota al empezar, más marcado justo antes
     *  de romper — la sensación de "está a punto de ceder". */
    public static float[] hudJitter(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return new float[]{0f, 0f};
        long now = mc.level.getGameTime();
        float amp = intensity(now);
        if (amp <= 0f) return new float[]{0f, 0f};

        float progressBoost = 0.4f + 0.6f * breakProgress(now);
        float t = now + partialTick;
        float px = HUD_JITTER_PX * amp * progressBoost;
        return new float[]{
                px * Mth.sin(t * 2.3f + 0.7f),
                px * Mth.sin(t * 3.1f + 2.9f)
        };
    }

    // ── Zoom de cámara (FOV) ─────────────────────────────────────────────────
    // Condición DISTINTA y más amplia que trembling(): esa exige powerPercent==100 exacto (la
    // ventana de temblor previa a romper el candado); el zoom debe seguir mientras se sostiene
    // el gesto YA forzando de verdad por encima de 100 también, no solo mientras se tiembla en
    // el tope — si usara trembling(), el zoom se apagaría de golpe justo al romper el límite,
    // que es el peor momento para que desaparezca el efecto.

    private static final int ZOOM_RAMP_TICKS = 10; // más suave que el jitter: es cámara, no HUD
    /** Multiplicador de FOV a intensidad máxima. 0.92 = zoom leve, nada agresivo (a petición). */
    private static final float ZOOM_MIN_MULT = 0.92f;

    private static boolean zoomLastState = false;
    private static long zoomTransitionStart = 0L;

    private static boolean forcingActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(mc.player);
        return att.isChargingKi() && att.isOverdriveCharging() && att.getPowerPercent() >= 100;
    }

    /** Multiplicador a aplicar sobre el FOV modifier (1 = sin efecto). Rampa propia de entrada/
     *  salida para que el zoom no sea un salto brusco de cámara. */
    public static float fovMultiplier(long now) {
        boolean active = forcingActive();
        if (active != zoomLastState) {
            zoomLastState = active;
            zoomTransitionStart = now;
        }
        float ramp = Math.min(1f, (now - zoomTransitionStart) / (float) ZOOM_RAMP_TICKS);
        float t = zoomLastState ? ramp : Math.max(0f, 1f - ramp);
        return 1f - (1f - ZOOM_MIN_MULT) * t;
    }
}
