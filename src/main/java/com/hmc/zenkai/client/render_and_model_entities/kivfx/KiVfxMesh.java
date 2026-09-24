package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Malla de VFX ya calculada: solo posiciones, UV, normal y un factor de BLANCURA por vértice, en
 * espacio LOCAL de la técnica (eje de vuelo = +Z local, ver {@link KiVfxGeometry}).
 * POR QUÉ GUARDA DATOS Y NO UN VertexBuffer: la parte cara de una hélice es calcular su geometría,
 * no empujar sus vértices. Guardando el array se ahorra exactamente eso — {@link KiVfxGeometry}
 * cachea por combinación de parámetros — y a cambio los vértices siguen pasando por
 * {@code MultiBufferSource}, así que diez proyectiles del mismo tipo entran en un solo lote y el
 * color puede ir POR VÉRTICE (el tinte de la técnica).
 * LA BLANCURA solo la usa la ruta de RESPALDO sin shader (ver {@code KiVfxCompositeRenderer}):
 * 1 = blanco puro, 0 = tinte de la técnica. Con el shader de energía sobra (whiteMul 0), porque
 * las bandas se deciden por píxel.
 * Formato por vértice: x y z u v nx ny nz w → 9 floats. Cuatro vértices por quad.
 */
public record KiVfxMesh(float[] data, int quadCount) {

    public static final int STRIDE = 9;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float FLAT_U = 0.5f, FLAT_V = 0.5f;

    /**
     * @param whiteMul escala la blancura horneada. 0 deja el tinte puro (ruta del shader);
     *                 valores altos lavan la malla a blanco (ruta de respaldo, núcleo).
     * @param flatUv   true = cada vértice muestrea el centro de la textura — para una textura de
     *                 glow de billboard estirada sobre geometría 3D. false = UV real, que el
     *                 shader de energía usa como coordenada de banda (RADIAL) o de detalle.
     */
    public void emit(VertexConsumer vc, PoseStack.Pose pose,
                     float tr, float tg, float tb, float alpha, float whiteMul, boolean flatUv) {
        for (int i = 0; i < quadCount * 4; i++) {
            int o = i * STRIDE;
            float w = Math.min(1f, data[o + 8] * whiteMul);
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
