package com.hmc.zenkai.client.render_and_model_entities.ki;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Malla de ki ya calculada: solo posiciones, UV, normal y un factor de BLANCURA por vértice.
 * POR QUÉ GUARDA DATOS Y NO UN VertexBuffer
 * -----------------------------------------
 * La parte cara de una hélice es calcular su geometría, no empujar sus vértices. Guardando el
 * array se ahorra exactamente eso, y a cambio los vértices siguen pasando por MultiBufferSource:
 * diez proyectiles del mismo tipo se dibujan en UNA llamada en vez de diez, y el color puede ir
 * POR VÉRTICE.
 * LA BLANCURA es lo que da el núcleo sin geometría extra en la RUTA DE RESPALDO: 1 = blanco
 * puro, 0 = tinte de la técnica. Con el shader de fresnel sobra (whiteMul 0), porque las
 * bandas se deciden por píxel.
 * Formato por vértice: x y z u v nx ny nz w  → 9 floats. Cuatro vértices por quad.
 */
public record KiMesh(float[] data, int quadCount) {

    public static final int STRIDE = 9;
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Punto fijo de muestreo cuando la textura no está pensada para envolver la malla. */
    private static final float FLAT_U = 0.5f, FLAT_V = 0.5f;

    /**
     * @param whiteMul escala la blancura horneada. 1 = cáscara teñida como se generó;
     *                 valores altos lavan la malla a blanco y la convierten en NÚCLEO;
     *                 0 deja el tinte puro (ruta del shader).
     * @param flatUv   true = cada vértice muestrea el centro de la textura.
     *                 La decisión vive AQUÍ y no en el generador porque depende de con qué se
     *                 va a dibujar la malla, no de cómo se construyó: {@code ki_ball.png} es un
     *                 glow de billboard y estirado sobre una esfera pinta un disco de otro
     *                 color en cada polo, mientras que el shader de fresnel no muestrea nada y
     *                 usa la U real como coordenada de banda. Una misma malla sirve para las
     *                 dos rutas.
     */
    public void emit(VertexConsumer vc, PoseStack.Pose pose,
                     float tr, float tg, float tb, float alpha, float whiteMul, boolean flatUv) {
        for (int i = 0; i < quadCount * 4; i++) {
            int o = i * STRIDE;
            float w = Math.min(1f, data[o + 8] * whiteMul);
            // lerp(tinte, blanco, w): el núcleo se lava a blanco y el borde conserva el color.
            float r = tr + (1f - tr) * w;
            float g = tg + (1f - tg) * w;
            float b = tb + (1f - tb) * w;

            vc.addVertex(pose, data[o], data[o + 1], data[o + 2])
                    .setColor(r, g, b, alpha)
                    .setUv(flatUv ? FLAT_U : data[o + 3], flatUv ? FLAT_V : data[o + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
        }
    }
}
