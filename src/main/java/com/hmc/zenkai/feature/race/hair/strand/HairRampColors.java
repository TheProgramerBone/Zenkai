package com.hmc.zenkai.feature.race.hair.strand;

/**
 * Rampa de tonos del pelo por mechones a partir de UN color (el de pelo del jugador o el hair_rgb
 * de la forma). Con un tinte multiplicado el pelo negro era una mancha plana (sombra × negro =
 * negro); aquí cada tono se calcula en Oklab: las sombras bajan de luz, ganan algo de croma y
 * giran hacia el frío; las luces suben, pierden croma y giran hacia el cálido. Los colores casi
 * neutros (negro, gris, blanco) reciben un toque azulado en sombra y luz para que tengan volumen.
 * Idea estudiada en la rampa de DragonMineZ 2.2 (HairColorRamp); implementación y constantes propias.
 *
 * Siete tonos fijos (de -1 a +1 en pasos de 1/3), sin degradado: estilo del proyecto (3-4 tonos
 * por material, sin antialiasing). Función pura, con gametest.
 */
public final class HairRampColors {
    private HairRampColors() {}

    public static final int STOPS = 7;

    /**
     * Luz (Oklab L) mínima del tono base: ~#0B0B0B en gris (0.30 y 0.22 se veían demasiado grises). El negro por defecto (0x000000) y los
     * casi negros se veían como una mancha sin detalle en los peinados DMZ/DBC (2026-10-12); por
     * debajo de este umbral el color se aclara hasta él conservando matiz y croma. Los colores más
     * claros no cambian.
     */
    public static final float MIN_BASE_LIGHTNESS = 0.15f;

    /** El color base con la luz subida a MIN_BASE_LIGHTNESS si es más oscuro; si no, tal cual. */
    public static int lift(int rgb) {
        float[] lab = toOklab(rgb);
        if (lab[0] >= MIN_BASE_LIGHTNESS) return rgb & 0xFFFFFF;
        return fromOklabInGamut(MIN_BASE_LIGHTNESS, lab[1], lab[2]);
    }

    /** Tono del color base para un sombreado en [-1, 1], cuantizado a STOPS pasos. */
    public static int shade(int rgb, float shade) {
        float s = Math.round(Math.max(-1f, Math.min(1f, shade)) * (STOPS / 2)) / (float) (STOPS / 2);
        if (s == 0f) return rgb & 0xFFFFFF;
        float[] lab = toOklab(rgb);
        float lightness = lab[0];
        float chroma = (float) Math.hypot(lab[1], lab[2]);
        float hue = (float) Math.atan2(lab[2], lab[1]);
        float amount = Math.abs(s);
        float targetL, targetC, targetH;
        if (s < 0) {
            targetL = lightness - amount * 0.26f * (0.35f + 0.65f * lightness);
            targetC = chroma * (1f + 0.3f * amount);
            targetH = hue + toward(hue, COOL, (float) Math.toRadians(20) * amount);
        } else {
            targetL = lightness + amount * 0.32f * (1f - lightness);
            targetC = chroma * (1f - 0.3f * amount * lightness);
            targetH = hue + toward(hue, WARM, (float) Math.toRadians(12) * amount);
        }
        // Casi neutro (negro/gris/blanco): el matiz no significa nada; se usa un tinte frío fijo.
        float chromatic = smoothstep(0.012f, 0.05f, chroma);
        float neutralC = 0.03f * amount;
        float a = (float) (targetC * Math.cos(targetH) * chromatic + neutralC * Math.cos(COOL) * (1 - chromatic));
        float b = (float) (targetC * Math.sin(targetH) * chromatic + neutralC * Math.sin(COOL) * (1 - chromatic));
        return fromOklabInGamut(Math.max(0f, Math.min(1f, targetL)), a, b);
    }

    /**
     * Mezcla de dos colores en Oklab (t = 0 -&gt; a, 1 -&gt; b). La usa el paso de una forma a otra
     * (pelo negro -&gt; amarillo SSJ): en RGB la mitad del camino sale verde oliva; en Oklab, un
     * castaño dorado.
     */
    public static int mix(int a, int b, float t) {
        if (t <= 0f) return a & 0xFFFFFF;
        if (t >= 1f) return b & 0xFFFFFF;
        float[] la = toOklab(a), lb = toOklab(b);
        return fromOklabInGamut(la[0] + (lb[0] - la[0]) * t, la[1] + (lb[1] - la[1]) * t,
                la[2] + (lb[2] - la[2]) * t);
    }

    // 290° y no 270°: con 270 el amarillo (SSJ) giraba hacia el verde en sombra; así gira por el naranja.
    private static final float COOL = (float) Math.toRadians(290);
    private static final float WARM = (float) Math.toRadians(90);

    private static float toward(float from, float to, float maxStep) {
        double d = (to - from) % (Math.PI * 2);
        if (d > Math.PI) d -= Math.PI * 2;
        if (d < -Math.PI) d += Math.PI * 2;
        return (float) Math.max(-maxStep, Math.min(maxStep, d));
    }

    private static float smoothstep(float lo, float hi, float x) {
        float t = Math.max(0f, Math.min(1f, (x - lo) / (hi - lo)));
        return t * t * (3 - 2 * t);
    }

    // ── Oklab (fórmulas públicas de Björn Ottosson) ──────────────────────────────

    private static float toLinear(int c8) {
        float c = c8 / 255f;
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    private static int toSrgb8(float c) {
        float v = c <= 0.0031308f ? c * 12.92f : 1.055f * (float) Math.pow(c, 1 / 2.4f) - 0.055f;
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    static float[] toOklab(int rgb) {
        float r = toLinear((rgb >> 16) & 0xFF), g = toLinear((rgb >> 8) & 0xFF), bl = toLinear(rgb & 0xFF);
        float l = (float) Math.cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * bl);
        float m = (float) Math.cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * bl);
        float s = (float) Math.cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * bl);
        return new float[]{
                0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
                1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
                0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s};
    }

    private static float[] toLinearRgb(float L, float a, float b) {
        float l = L + 0.3963377774f * a + 0.2158037573f * b;
        float m = L - 0.1055613458f * a - 0.0638541728f * b;
        float s = L - 0.0894841775f * a - 1.2914855480f * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return new float[]{
                4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
                -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
                -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s};
    }

    /** Si se sale del sRGB, se reduce el croma (no la luz) hasta que quepa. */
    private static int fromOklabInGamut(float L, float a, float b) {
        float k = 1f;
        float[] rgb = toLinearRgb(L, a, b);
        for (int i = 0; i < 16 && !inGamut(rgb); i++) {
            k *= 0.85f;
            rgb = toLinearRgb(L, a * k, b * k);
        }
        return (toSrgb8(rgb[0]) << 16) | (toSrgb8(rgb[1]) << 8) | toSrgb8(rgb[2]);
    }

    private static boolean inGamut(float[] rgb) {
        for (float c : rgb) if (c < -1e-4f || c > 1.0001f) return false;
        return true;
    }
}
