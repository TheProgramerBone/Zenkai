package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * Cámara de la secuencia LARGA de la primera transformación (solo la del jugador local y solo con {@link LongTransformMode#FULL}):
 * una sacudida de posición en los tres pulsos y un acercamiento (FOV) que se aprieta con la acumulación y se suelta con la
 * intensificación final. Sin ROTAR la cámara (quita la puntería y marea; mismo criterio que la sacudida de Ki Sense) y con topes
 * bajos: en primera persona un destello o un zoom exagerado se comen la vista.
 * <p>
 * CANCELAR: abrir cualquier pantalla (menú, inventario, pausa) corta la capa de cámara al instante. Es solo la capa visual: la
 * forma, el desbloqueo y la crónica ya están escritos en el servidor. La sacudida se aplica desde CameraShakeMixin (final de
 * Camera.setup), igual que la de Ki Sense.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class TransformCameraFx {
    private TransformCameraFx() {}

    private static final long NEVER = Long.MIN_VALUE;
    /** Duración total en ticks (6,3 s, como TransformFx.LONG_DURATION). */
    private static final float TOTAL_TICKS = 126f;
    /** Los tres pulsos, en ticks desde el inicio: 0, 0,9 s y 1,9 s (== TransformFx.LONG_BEAT_AT). */
    private static final float[] BEATS = {0f, 26f, 54f, 80f};
    private static final float[] BEAT_GAIN = {1.0f, 0.7f, 0.9f, 1.2f};
    private static final float SHAKE_AMP = 0.045f;   // bloques de Camera.move: un temblor, no un terremoto
    private static final float FOV_PULL = 0.07f;     // máx. 7 % de acercamiento
    private static final float LIFT_FROM = 42f;      // el contrapicado empieza a los 20 ticks...
    private static final float LIFT_MAX = 0.28f;     // ...y llega a 0,28 bloques arriba hacia el estallido

    private static long start = NEVER;

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) start = mc.level.getGameTime();
    }

    /** Corta la capa de cámara (secuencia cancelada). */
    public static void cancel() { start = NEVER; }

    private static float elapsed(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (start == NEVER || mc.level == null) return -1f;
        // Cualquier pantalla abierta cancela la capa de cámara.
        if (mc.screen != null) { start = NEVER; return -1f; }
        float e = (mc.level.getGameTime() - start) + partialTick;
        if (e < 0f || e >= TOTAL_TICKS) { start = NEVER; return -1f; }
        return e;
    }

    /** Fuerza del pulso i en el instante e (0..~1): sube en 2 ticks y se apaga en unos 12. */
    private static float beat(float e, int i) {
        float x = e - BEATS[i];
        if (x < 0f) return 0f;
        if (x < 2f) return BEAT_GAIN[i] * (x / 2f);
        return BEAT_GAIN[i] * (float) Math.exp(-(x - 2f) * 0.22);
    }

    /** Desplazamiento de la sacudida {frente, arriba, lateral} para este fotograma, o null si no hay secuencia en curso. */
    public static float[] shakeOffset(float partialTick) {
        float e = elapsed(partialTick);
        if (e < 0f) return null;
        float amp = SHAKE_AMP * (beat(e, 0) + beat(e, 1) + beat(e, 2) + beat(e, 3));
        // Tres frecuencias inconmensurables entre sí, como en Ki Sense: que no se lea como una vibración mecánica.
        // Contrapicado: la cámara sube despacio hacia el estallido y vuelve a su sitio al acabar (sin rotar: no quita la puntería).
        float lift = e < LIFT_FROM ? 0f : LIFT_MAX * Mth.sin(Mth.PI * (e - LIFT_FROM) / (TOTAL_TICKS - LIFT_FROM));
        return new float[]{
                amp * Mth.sin(e * 1.71f + 2.3f) * 0.4f,
                amp * Mth.sin(e * 3.13f + 0.9f) * 0.8f + lift,
                amp * Mth.sin(e * 2.27f)
        };
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent e) {
        float el = elapsed(0f);
        if (el < 0f) return;
        // Se aprieta mientras se acumula (los primeros 18 ticks se mantiene), y se suelta con la intensificación final.
        float pull = FOV_PULL * (Math.max(Math.max(beat(el, 0), beat(el, 1)), Math.max(beat(el, 2), beat(el, 3))));
        e.setNewFovModifier(e.getNewFovModifier() * (1f - pull));
    }
}
