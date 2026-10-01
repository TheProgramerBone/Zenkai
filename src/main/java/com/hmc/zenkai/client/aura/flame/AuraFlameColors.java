package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import net.minecraft.util.Mth;

/**
 * Cómo se PINTA el color del aura (no cuál es: eso lo decide solo AuraColors). Venían de la llama
 * sola de antes del sistema compuesto (AuraFlameShellRenderer, borrada el 2026-09-30) y las usan el
 * compositor y sus componentes.
 * <p>
 * ═══ COLOR POR CAPA (2026-09-29) ═══ Antes una sola llama iba del color interior (en el centro, de
 * frente) al exterior (en el borde), y el centro usaba la capa caliente ENTERA del interior: en
 * Blue+Kaioken y en Beast el azul quedaba como un cian pálido en la columna central que, al
 * solaparse las lenguas, se lavaba a blanco. Las referencias (aura_2, aura_8, aura_10) dicen otra
 * cosa: con dos tonos son DOS VOLÚMENES, cada uno con su color SATURADO, y el blanco es solo un
 * acento. Por eso cada capa usa SU tinte: saturado en el borde ({@link #saturated}) y algo
 * calentado en el centro ({@link #heatedCenter}).
 */
public final class AuraFlameColors {

    private AuraFlameColors() {}

    /**
     * Tinte de una capa con la saturación realzada ({@code saturation}): se aleja cada canal de
     * la luminancia. Un 0x5FE8FF (Blue) pasa de cian pastel a cian vivo sin cambiar de tono. Se
     * aplica ANTES de calentar el centro, para que el centro también parta del color vivo.
     */
    public static float[] saturated(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float s = Math.max(0f, AuraFlameTuning.get(AuraFlameTuning.Param.SATURATION));
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        return new float[]{
                Mth.clamp(luma + (r - luma) * s, 0f, 1f),
                Mth.clamp(luma + (g - luma) * s, 0f, 1f),
                Mth.clamp(luma + (b - luma) * s, 0f, 1f)};
    }

    /**
     * Color del CENTRO de una capa: su tinte calentado un poco hacia su capa caliente
     * ({@code KiVfxColors.derivedHot}, la misma del ki: azul hacia cian, amarillo hacia blanco
     * cálido) y, con mucho poder, un poco más hacia blanco. NUNCA la capa caliente entera: eso
     * dejaba el centro casi blanco y, sumado al solape de lenguas, borraba el tinte.
     */
    public static float[] heatedCenter(float[] tint, float coreWhite) {
        float r = tint[0], g = tint[1], b = tint[2];
        float[] hot = KiVfxColors.derivedHot(r, g, b);
        float h = Mth.clamp(AuraFlameTuning.get(AuraFlameTuning.Param.HOT_MIX), 0f, 1f);
        float cr = Mth.lerp(h, r, hot[0]), cg = Mth.lerp(h, g, hot[1]), cb = Mth.lerp(h, b, hot[2]);
        return new float[]{Mth.lerp(coreWhite, cr, 1f), Mth.lerp(coreWhite, cg, 1f), Mth.lerp(coreWhite, cb, 1f)};
    }
}
