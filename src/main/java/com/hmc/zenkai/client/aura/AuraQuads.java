package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.feature.aura.AuraTuning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Primitivas de emisión compartidas por el conjunto de capas del aura. Sin lógica: solo
 * convierte parámetros en vértices.
 * Antes vivían dentro del AuraRenderer monolítico, y cada capa que se añadía tenía que
 * copiarlas o llamar a una manera privada. Aquí las comparten skirts, wisps, sparks y
 * ground sin que ninguna dependa de otra.
 */
public final class AuraQuads {
    private AuraQuads() {}

    public static final int FULL_BRIGHT = 0xF000F0;

    /** Celda de la hoja para la MASA del cuadrante dado (0..3). */
    public static float cellU(int tex) { return (tex & 1) * AuraTuning.CELL_U; }

    public static float cellV(int tex) { return (tex >> 1) * AuraTuning.CELL_V; }

    /** Celda del NÚCLEO del mismo cuadrante: dos filas más abajo en la hoja. */
    public static float coreCellV(int tex) {
        return ((tex >> 1) + AuraTuning.CORE_ROW_OFFSET) * AuraTuning.CELL_V;
    }

    /**
     * Plano-silueta vertical: nace en y=0, sube a y=h, centrado en x.
     *
     * @param zOffset desplazamiento hacia la cámara. Con sortOnUpload=true los quads
     *                del mismo RenderType se ordenan por profundidad, así que masa y
     *                núcleo del MISMO faldón (mismo z) quedarían en orden
     *                indeterminado y el núcleo podría dibujarse detrás de su propia
     *                llama. AuraTuning.CORE_Z_OFFSET los separa lo justo.
     */
    public static void plane(VertexConsumer vc, PoseStack.Pose m, float w, float h,
                             float u0, float v0, boolean mirror, float zOffset,
                             float r, float g, float b, float a) {
        float in = AuraTuning.UV_INSET;
        float uLo = u0 + in, uHi = u0 + AuraTuning.CELL_U - in;
        float uA = mirror ? uHi : uLo;
        float uB = mirror ? uLo : uHi;
        float vLo = v0 + in;
        float vHi = v0 + AuraTuning.CELL_V - in;

        vert(vc, m, -w / 2f, 0f, zOffset, uA, vHi, r, g, b, a);
        vert(vc, m,  w / 2f, 0f, zOffset, uB, vHi, r, g, b, a);
        vert(vc, m,  w / 2f, h,  zOffset, uB, vLo, r, g, b, a);
        vert(vc, m, -w / 2f, h,  zOffset, uA, vLo, r, g, b, a);
    }

    /** Quad horizontal centrado en el origen, en el plano XZ. Para la energía de suelo. */
    public static void ground(VertexConsumer vc, PoseStack.Pose m, float half, float y,
                              float u0, float v0, float r, float g, float b, float a) {
        float in = AuraTuning.UV_INSET;
        float uLo = u0 + in, uHi = u0 + AuraTuning.CELL_U - in;
        float vLo = v0 + in, vHi = v0 + AuraTuning.CELL_V - in;
        vertXZ(vc, m, -half, y, -half, uLo, vLo, r, g, b, a);
        vertXZ(vc, m, -half, y,  half, uLo, vHi, r, g, b, a);
        vertXZ(vc, m,  half, y,  half, uHi, vHi, r, g, b, a);
        vertXZ(vc, m,  half, y, -half, uHi, vLo, r, g, b, a);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z,
                             float u, float v, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(m, 0f, 0f, 1f);
    }

    private static void vertXZ(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z,
                               float u, float v, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(m, 0f, 1f, 0f);
    }

    /** Hash entero → [0,1). Determinista y estable dentro de un step. */
    public static float hash01(int a, int b, int c, int d) {
        int h = a * 0x8DA6B343 + b * 0xD8163841 + c * 0xCB1AB31F + d * 0x165667B1;
        h ^= (h >>> 13);
        h *= 0x5BD1E995;
        h ^= (h >>> 15);
        return (h & 0xFFFF) / 65536f;
    }

    public static float red(int rgb)   { return ((rgb >> 16) & 0xFF) / 255f; }
    public static float green(int rgb) { return ((rgb >> 8) & 0xFF) / 255f; }
    public static float blue(int rgb)  { return (rgb & 0xFF) / 255f; }
}