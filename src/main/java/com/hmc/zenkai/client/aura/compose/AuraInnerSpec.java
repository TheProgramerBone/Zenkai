package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key;
import org.jetbrains.annotations.Nullable;

/**
 * Capa INTERIOR del cuerpo: un segundo volumen, más pequeño, dibujado ENCIMA del exterior (la
 * convención de aura_2/aura_8: la nube de dentro se ve limpia y la llama de fuera asoma por su
 * silueta). Cuándo aparece lo decide la familia o el estilo:
 * <ul>
 *   <li>{@link Mode#TWO_TONE}: solo si la forma tiene dos colores (aura_outer_rgb, Kaioken...) —
 *       el comportamiento de la llama desde el 2026-09-29;</li>
 *   <li>{@link Mode#ALWAYS}: siempre, con el color caliente del mismo tinte si no hay dos tonos —
 *       el núcleo de nube de la familia divina, el núcleo denso de la oscura.</li>
 * </ul>
 * @param kind  programa del volumen interior; null = el mismo que el exterior.
 * @param scale tamaño relativo al exterior (dimensiones en bloques).
 * @param amp   factor de la amplitud de las lenguas/lóbulos.
 * @param alpha factor del alfa del centro (el interior se lee como cuerpo de color, no contorno).
 * @param tip   factor de la punta (el interior no debe asomar por encima del exterior).
 * @param emit  factor de la emisión (el interior cubre como pigmento, ver AuraFlameParams.innerLayer).
 */
public record AuraInnerSpec(Mode mode, @Nullable AuraBodyKind kind, float scale, float amp, float alpha,
                            float tip, float emit) {

    public enum Mode { NONE, TWO_TONE, ALWAYS }

    public static final AuraInnerSpec NONE = new AuraInnerSpec(Mode.NONE, null, 1f, 1f, 1f, 1f, 1f);

    public boolean draws(boolean twoTone) {
        return mode == Mode.ALWAYS || (mode == Mode.TWO_TONE && twoTone);
    }

    /** Parámetros del volumen interior a partir de los del exterior (ya moldeados por familia y estilo). */
    public AuraBodyParams innerOf(AuraBodyParams outer) {
        AuraBodyParams in = outer.copy();
        for (Key k : Key.values()) if (k.dimensional) in.mul(k, scale);
        // La mitad de arriba se encoge, pero el centro (la cadera) se queda donde está: la nube
        // interior rodea el cuerpo, no flota por debajo de él.
        in.put(Key.CENTER_Y, outer.get(Key.CENTER_Y));
        in.mul(Key.AMP, amp);
        in.mul(Key.TIP, tip);
        in.mul(Key.TOP_BOOST, 0.5f);
        in.mul(Key.SHARP, 0.4f);
        in.put(Key.ALPHA_CENTER, Math.min(0.5f, outer.get(Key.ALPHA_CENTER) * alpha));
        in.mul(Key.ALPHA_EDGE, 0.8f);
        in.mul(Key.EMIT_BODY, emit);
        in.mul(Key.EMIT_EDGE, emit);
        return in;
    }
}
