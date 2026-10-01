package com.hmc.zenkai.client.aura.compose.component;

/**
 * Verificación de {@link LightningPath} sin Minecraft (mismo espíritu que AuraSelfTest): ejecutable
 * con {@code java} sobre las clases compiladas. Comprueba que el rayo respeta sus extremos, que es
 * determinista por semilla, que no se sale de un margen razonable del tramo y que el tamaño del
 * array se valida.
 */
public final class LightningPathSelfTest {

    private LightningPathSelfTest() {}

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        if (!ok) {
            failures++;
            System.out.println("  FALLO  " + name);
        }
    }

    public static void main(String[] args) {
        for (int depth = 1; depth <= 5; depth++) {
            int n = LightningPath.points(depth);
            float[] a = new float[n * 3];
            float[] b = new float[n * 3];
            LightningPath.generate(a, depth, 0f, 0f, 0f, 0f, 2f, 0f, 0.3f, 1234L);
            LightningPath.generate(b, depth, 0f, 0f, 0f, 0f, 2f, 0f, 0.3f, 1234L);
            check("extremo inicial depth " + depth, a[0] == 0f && a[1] == 0f && a[2] == 0f);
            check("extremo final depth " + depth, a[(n - 1) * 3 + 1] == 2f);
            boolean same = true;
            for (int i = 0; i < a.length; i++) same &= a[i] == b[i];
            check("determinista depth " + depth, same);
            // Cota: cada apartamiento es amp * |r| con |r| <= raíz de 2 (vector de componentes en
            // -1..1 sin su parte a lo largo del tramo), y las amplitudes se reducen a la mitad:
            // 0.3 * 2 * 1.42 * (1 + 1/2 + ...) < 1.75 bloques.
            boolean bounded = true;
            for (int i = 0; i < n; i++) {
                float x = a[i * 3], z = a[i * 3 + 2];
                bounded &= Math.sqrt(x * x + z * z) < 1.75;
                bounded &= !Float.isNaN(a[i * 3 + 1]);
            }
            check("acotado depth " + depth, bounded);
            float[] c = new float[n * 3];
            LightningPath.generate(c, depth, 0f, 0f, 0f, 0f, 2f, 0f, 0.3f, 99L);
            boolean differs = false;
            for (int i = 3; i < c.length - 3; i++) differs |= c[i] != a[i];
            check("semilla distinta cambia el rayo depth " + depth, depth == 0 || differs);
        }
        float[] zero = new float[LightningPath.points(3) * 3];
        LightningPath.generate(zero, 3, 1f, 1f, 1f, 1f, 1f, 1f, 0.3f, 7L);
        boolean allSame = true;
        for (int i = 0; i < zero.length; i += 3) allSame &= zero[i] == 1f && zero[i + 1] == 1f && zero[i + 2] == 1f;
        check("tramo de longitud cero", allSame);
        try {
            LightningPath.generate(new float[3], 3, 0f, 0f, 0f, 1f, 1f, 1f, 0.3f, 1L);
            check("array pequeño lanza excepción", false);
        } catch (IllegalArgumentException expected) {
            check("array pequeño lanza excepción", true);
        }
        System.out.println(failures == 0 ? "LightningPathSelfTest: OK" : "LightningPathSelfTest: " + failures + " fallos");
        if (failures > 0) System.exit(1);
    }
}
