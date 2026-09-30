package com.hmc.zenkai.client.aura.flame;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Malla de la LLAMA EXTERIOR del sistema de aura nuevo: una esfera unitaria de latitud/longitud,
 * construida una sola vez. Toda la forma (elipsoide, picos, lenguas, crecimiento) la pone
 * aura_flame_shell.vsh a partir de estas coordenadas UNITARIAS — igual que dbrebirth-0.3, cuyo
 * aura.vsh escala una malla propia por {@code sizes} y desplaza en dirección RADIAL
 * ({@code normalize(pos)}), en vez de inflar el modelo del jugador.
 * <p>
 * POR QUÉ NO EL PlayerModel (confirmado en juego 2026-09-29, primera prueba de la Fase 0): el
 * modelo del jugador son cubos de 4 vértices por cara; desplazar cada cara por su normal a escala
 * de 1-3 bloques separa las caras en triángulos sueltos ("cubo explotado") y no hay vértices
 * suficientes para dibujar picos. La esfera subdividida tiene la resolución que la onda de picos
 * necesita y es continua: el desplazamiento radial nunca abre grietas.
 * <p>
 * Posición = normal = el punto de la esfera unitaria (el shader recalcula la normal real de la
 * superficie desplazada; esta solo sirve de respaldo). Las coordenadas NO pasan por el PoseStack
 * en CPU: la transformación a mundo va en el uniform ZenkaiShellModelMat, para que el shader
 * trabaje siempre en espacio local del jugador (la trampa de la corrección de origen de
 * aura_flame.vsh no puede repetirse aquí por construcción).
 */
public final class AuraFlameShellMesh {

    private AuraFlameShellMesh() {}

    /** Anillos de latitud (polo a polo) y segmentos de longitud. Subidos de 24x32 a 32x96
     *  (2026-09-29): con 32 segmentos y 12-14 picos quedaban ~2.5 vértices por pico — la onda
     *  triangular se muestreaba tan poco que cada lengua era un par de caras planas enormes y el
     *  aura se leía como CRISTALES/pétalos (visto en juego), no como lenguas de fuego. 96 es
     *  múltiplo de 6/8/12/16 (vértice justo en la punta con esos nº de picos) y da ~7 por pico. */
    private static final int RINGS = 32;
    private static final int SEGMENTS = 96;

    /** Vértices ya expandidos a quads (4 por quad), x/y/z intercalados. */
    private static final float[] QUADS = build();

    private static float[] build() {
        float[] out = new float[RINGS * SEGMENTS * 4 * 3];
        int i = 0;
        for (int r = 0; r < RINGS; r++) {
            double lat0 = Math.PI * r / RINGS;          // 0 = polo superior
            double lat1 = Math.PI * (r + 1) / RINGS;
            for (int s = 0; s < SEGMENTS; s++) {
                double lon0 = 2.0 * Math.PI * s / SEGMENTS;
                double lon1 = 2.0 * Math.PI * (s + 1) / SEGMENTS;
                // Orden antihorario visto desde fuera; da igual con NO_CULL, pero se mantiene
                // coherente por si alguna fase futura activa el culling.
                i = put(out, i, lat0, lon0);
                i = put(out, i, lat1, lon0);
                i = put(out, i, lat1, lon1);
                i = put(out, i, lat0, lon1);
            }
        }
        return out;
    }

    private static int put(float[] out, int i, double lat, double lon) {
        double sinLat = Math.sin(lat);
        out[i]     = (float) (sinLat * Math.cos(lon));
        out[i + 1] = (float) Math.cos(lat);
        out[i + 2] = (float) (sinLat * Math.sin(lon));
        return i + 3;
    }

    /**
     * Emite la esfera con coordenadas crudas (sin PoseStack, ver el javadoc de la clase).
     * @param argb color de vértice; solo se usa su alfa (opacidad general del aura).
     */
    public static void emit(VertexConsumer vc, int argb) {
        float[] q = QUADS;
        for (int i = 0; i < q.length; i += 3) {
            vc.addVertex(q[i], q[i + 1], q[i + 2])
                    .setColor(argb)
                    .setNormal(q[i], q[i + 1], q[i + 2]);
        }
    }
}
