package com.hmc.zenkai.client.aura.compose.component;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Tramos de CINTA orientados a cámara para rayos y wisps (RenderType
 * {@code AuraComposeShaders.ribbons()}). Coordenadas relativas a la cámara: la cámara está en el
 * origen, así que la visual de cada tramo es menos su propio punto medio.
 */
final class Ribbons {

    private Ribbons() {}

    /**
     * Un tramo de p0 a p1 con ancho w0 → w1 y alfa a0 → a1. El ancho se abre en perpendicular al
     * tramo y a la visual, así la cinta siempre se ve de cara.
     */
    static void segment(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                        float w0, float w1, float r, float g, float b, float a0, float a1) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float mx = -(x0 + x1) * 0.5f, my = -(y0 + y1) * 0.5f, mz = -(z0 + z1) * 0.5f;
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0e-6f) return;
        sx /= sl;
        sy /= sl;
        sz /= sl;
        float h0 = w0 * 0.5f, h1 = w1 * 0.5f;
        vc.addVertex(m, x0 - sx * h0, y0 - sy * h0, z0 - sz * h0).setColor(r, g, b, a0);
        vc.addVertex(m, x0 + sx * h0, y0 + sy * h0, z0 + sz * h0).setColor(r, g, b, a0);
        vc.addVertex(m, x1 + sx * h1, y1 + sy * h1, z1 + sz * h1).setColor(r, g, b, a1);
        vc.addVertex(m, x1 - sx * h1, y1 - sy * h1, z1 - sz * h1).setColor(r, g, b, a1);
    }

}
