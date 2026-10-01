package com.hmc.zenkai.client.aura.compose.component;

/**
 * Generador PURO de la polilínea de un rayo (sin nada de Minecraft: se verifica con
 * {@link LightningPathSelfTest} sin arrancar el juego). Desplazamiento del punto medio en 3D: se
 * parte el segmento en dos, el punto medio se aparta en perpendicular una fracción de la longitud,
 * y se repite {@code depth} veces con la mitad de amplitud cada vez — el zigzag fractal del rayo
 * de anime (aura_9/aura_10), no los tres quads rectos del rayo legacy.
 * <p>
 * DETERMINISTA por semilla: la misma semilla da el mismo rayo. El componente la cambia cada pocos
 * centisegundos manteniendo los extremos, y el rayo "crepita" en el sitio en vez de saltar.
 * <p>
 * Escribe en un {@code float[]} que da quien llama (x, y, z intercalados): sin asignaciones.
 */
public final class LightningPath {

    private LightningPath() {}

    /** Nº de puntos para una profundidad dada (2^depth + 1). */
    public static int points(int depth) { return (1 << depth) + 1; }

    /**
     * @param out       destino, al menos {@code points(depth) * 3} floats.
     * @param roughness apartamiento del primer punto medio, en fracción de la longitud (0.2-0.4).
     */
    public static void generate(float[] out, int depth, float sx, float sy, float sz,
                                float ex, float ey, float ez, float roughness, long seed) {
        int n = points(depth);
        if (out.length < n * 3) throw new IllegalArgumentException("out demasiado pequeño: " + out.length + " < " + n * 3);
        out[0] = sx; out[1] = sy; out[2] = sz;
        int last = (n - 1) * 3;
        out[last] = ex; out[last + 1] = ey; out[last + 2] = ez;
        float dx = ex - sx, dy = ey - sy, dz = ez - sz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-5f) {
            for (int i = 1; i < n - 1; i++) { out[i * 3] = sx; out[i * 3 + 1] = sy; out[i * 3 + 2] = sz; }
            return;
        }
        float amp = len * roughness;
        long s = seed;
        for (int step = n - 1; step > 1; step >>= 1) {
            int half = step >> 1;
            for (int i = 0; i + step < n; i += step) {
                int a = i * 3, b = (i + step) * 3, m = (i + half) * 3;
                float mx = (out[a] + out[b]) * 0.5f, my = (out[a + 1] + out[b + 1]) * 0.5f, mz = (out[a + 2] + out[b + 2]) * 0.5f;
                // Vector aleatorio sin la componente a lo largo del tramo: el zigzag es perpendicular.
                s = next(s); float rx = unit(s);
                s = next(s); float ry = unit(s);
                s = next(s); float rz = unit(s);
                float tx = out[b] - out[a], ty = out[b + 1] - out[a + 1], tz = out[b + 2] - out[a + 2];
                float tl2 = tx * tx + ty * ty + tz * tz;
                if (tl2 > 1.0e-10f) {
                    float proj = (rx * tx + ry * ty + rz * tz) / tl2;
                    rx -= proj * tx; ry -= proj * ty; rz -= proj * tz;
                }
                out[m] = mx + rx * amp;
                out[m + 1] = my + ry * amp;
                out[m + 2] = mz + rz * amp;
            }
            amp *= 0.5f;
        }
    }

    /** LCG de 64 bits (constantes de Knuth/MMIX). */
    public static long next(long s) { return s * 6364136223846793005L + 1442695040888963407L; }

    /** -1..1 a partir de los bits altos. */
    public static float unit(long s) { return ((s >>> 40) & 0xFFFFFF) / (float) 0x800000 - 1f; }

    /** 0..1 a partir de los bits altos. */
    public static float frac(long s) { return ((s >>> 40) & 0xFFFFFF) / (float) 0x1000000; }
}
