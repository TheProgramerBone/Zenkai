package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Componente ÚNICO para toda geometría en cinta de este pipeline: estela recta, estela en doble
 * hélice y rayos radiales eran, en el sistema anterior, tres implementaciones casi idénticas
 * copiadas una de otra ({@code KiProjectileRenderer.renderTrail/renderHelixTrail/renderRays}).
 * Aquí es una sola cosa: una lista de puntos en espacio de mundo + un par de laterales FIJOS.
 *
 * POR QUÉ EL LATERAL ES FIJO, NO UN BILLBOARD. La versión anterior de la estela recalculaba su
 * lateral cada frame como {@code dir.cross(cam - punto)} — exactamente el patrón que causaba el
 * "volteo" al mirar casi en paralelo a la cinta: ese producto cruzado tiende a longitud cero
 * cuando la vista queda paralela a la dirección, y al cruzar ese ángulo el vector normalizado
 * invierte de signo DE GOLPE (confirmado con capturas del usuario, tanto en la estela como en los
 * rayos). Con un lateral fijo en espacio de mundo (ver {@link KiAxis#basis}, calculado UNA VEZ a
 * partir de la dirección de vuelo/rayo, nunca de la cámara) no hay ningún ángulo crítico que
 * cruzar. Dos planos cruzados (dos laterales perpendiculares) en vez de uno solo es lo que impide
 * que la cinta desaparezca vista exactamente de canto — el mismo principio que ya usa el haz de
 * un faro o los rayos de fin de portal en vainilla, ninguno de los dos billboardea.
 * {@link KiAxis#orderByFacing} decide solo el ORDEN de dibujo entre los dos planos (cuál "gana"
 * la mezcla), nunca una posición de vértice — eso sí puede depender de la cámara sin reintroducir
 * el bug, porque un cambio de orden se ve como un blend suave, no como geometría que salta.
 */
public final class KiRibbon {
    private KiRibbon() {}

    /**
     * Cinta completa: dos planos cruzados con lateral fijo, ordenados por qué tan de frente está
     * cada uno a la cámara EN ESTE FRAME.
     * @param pts    puntos en espacio de MUNDO, cabeza primero (alfa 1 en pts[0], decayendo hacia
     *               el final de la lista).
     * @param origin punto de referencia del PoseStack actual (cada vértice se emite como
     *               {@code punto - origin}).
     */
    public static void draw(VertexConsumer vc, PoseStack.Pose mat, List<Vec3> pts, Vec3 origin,
                            Vec3 sideA, Vec3 sideB, Vec3 cam, float fullWidth, float scroll,
                            float r, float g, float b, float headAlpha) {
        draw(vc, mat, pts, origin, sideA, sideB, cam, fullWidth, scroll, r, g, b, headAlpha, 0f);
    }

    /** @param headClear ver {@link #strand(VertexConsumer, PoseStack.Pose, List, Vec3, Vec3, float,
     *                   float, float, float, float, float, float)}. */
    public static void draw(VertexConsumer vc, PoseStack.Pose mat, List<Vec3> pts, Vec3 origin,
                            Vec3 sideA, Vec3 sideB, Vec3 cam, float fullWidth, float scroll,
                            float r, float g, float b, float headAlpha, float headClear) {
        Vec3[] order = KiAxis.orderByFacing(pts.get(0), cam, sideA, sideB);
        strand(vc, mat, pts, origin, order[0], fullWidth, scroll, r, g, b, headAlpha, headClear);
        strand(vc, mat, pts, origin, order[1], fullWidth, scroll, r, g, b, headAlpha, headClear);
    }

    /** Una sola cinta plana, lateral fijo dado. Expuesto aparte para quien ya tenga el orden
     *  decidido (p. ej. dos hebras de hélice que se dibujan con el MISMO orden que la cinta recta
     *  para no generar otro cálculo de facing por hebra). */
    public static void strand(VertexConsumer vc, PoseStack.Pose mat, List<Vec3> pts, Vec3 origin, Vec3 side,
                              float fullWidth, float scroll, float r, float g, float b, float headAlpha) {
        strand(vc, mat, pts, origin, side, fullWidth, scroll, r, g, b, headAlpha, 0f);
    }

    /**
     * @param headClear distancia en bloques, medida A LO LARGO de la cinta desde {@code pts[0]},
     *                  en la que el alfa sube de 0 a su valor normal. 0 = sin rampa (rayos: nacen
     *                  en el centro de una esfera que ya los tapa).
     *                  POR QUÉ EXISTE (vídeo 2026-09-24 09-18-20): la cinta arranca en el CENTRO
     *                  de la cabeza del proyectil con su alfa MÁXIMO. Mientras era aditiva eso solo
     *                  sumaba brillo; con mezcla normal (ver "SIN ADITIVO" en KiVfxRenderTypes) el
     *                  plano pintaba color liso encima de media cabeza y su arista de arranque se
     *                  veía como un CORTE recto atravesando la esfera ("flechas de papel"). Con la
     *                  rampa, el arranque queda escondido dentro de la cabeza.
     */
    public static void strand(VertexConsumer vc, PoseStack.Pose mat, List<Vec3> pts, Vec3 origin, Vec3 side,
                              float fullWidth, float scroll, float r, float g, float b, float headAlpha,
                              float headClear) {
        int n = pts.size();
        if (n < 2) return;
        Vec3 prevL = null, prevR = null;
        float prevA = 0, prevV = 0;
        double along = 0;
        for (int i = 0; i < n; i++) {
            Vec3 pt = pts.get(i);
            if (i > 0) along += pts.get(i - 1).distanceTo(pt);
            float t = 1f - (float) i / (n - 1);           // 1 cabeza -> 0 cola
            float half = fullWidth * 0.5f * (0.22f + 0.78f * t);
            float alpha = headAlpha * t * t;               // cuadrático: la cola se apaga antes,
                                                            // sin dejar un rabo largo y sucio
            if (headClear > 0f) {
                float h = (float) Math.min(1.0, along / headClear);
                alpha *= h * h * (3f - 2f * h);
            }
            float v = (float) i / (n - 1) + scroll;

            Vec3 vL = pt.add(side.scale(half)).subtract(origin);
            Vec3 vR = pt.subtract(side.scale(half)).subtract(origin);

            if (i > 0) {
                quad(vc, mat, prevL, prevR, vR, vL, r, g, b, prevA, alpha, prevV, v);
                quad(vc, mat, vL, vR, prevR, prevL, r, g, b, alpha, prevA, v, prevV);
            }
            prevL = vL; prevR = vR; prevA = alpha; prevV = v;
        }
    }

    /**
     * Desplaza cada punto de {@code centerPts} en espiral alrededor de sí mismo, usando la MISMA
     * fórmula de torsión que hornea la malla HELIX ({@link KiVfxGeometry#helixAngleFromTip}) —
     * es lo que hace que la estela en hélice continúe la geometría horneada sin costura visible
     * donde la malla termina, algebraicamente (ver el javadoc de esa función), no por ajuste
     * manual.
     * @param meshLength longitud horneada de la malla (ver KiVfxProfile.Shell.meshLength) —
     *                   fija dónde cae d=0 en la fórmula de torsión.
     * @param phase      0 o π: las dos hebras de la hélice.
     */
    public static List<Vec3> helixStrand(List<Vec3> centerPts, Vec3 right, Vec3 up,
                                         float radius, float meshLength, double phase) {
        List<Vec3> out = new ArrayList<>(centerPts.size());
        double d = 0;
        out.add(helixOffset(centerPts.get(0), right, up, radius,
                KiVfxGeometry.helixAngleFromTip(meshLength, 0, phase)));
        for (int i = 1; i < centerPts.size(); i++) {
            d += centerPts.get(i - 1).distanceTo(centerPts.get(i));
            out.add(helixOffset(centerPts.get(i), right, up, radius,
                    KiVfxGeometry.helixAngleFromTip(meshLength, d, phase)));
        }
        return out;
    }

    private static Vec3 helixOffset(Vec3 center, Vec3 right, Vec3 up, float radius, double angle) {
        return center.add(right.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle)));
    }

    private static final int FULL_BRIGHT = 0xF000F0;

    private static void quad(VertexConsumer vc, PoseStack.Pose mat,
                             Vec3 aL, Vec3 aR, Vec3 bR, Vec3 bL,
                             float r, float g, float b,
                             float aAlpha, float bAlpha, float aV, float bV) {
        vert(vc, mat, aL, r, g, b, aAlpha, 0, aV);
        vert(vc, mat, aR, r, g, b, aAlpha, 1, aV);
        vert(vc, mat, bR, r, g, b, bAlpha, 1, bV);
        vert(vc, mat, bL, r, g, b, bAlpha, 0, bV);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose mat, Vec3 p,
                             float r, float g, float b, float a, float u, float v) {
        vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(mat, 0, 1, 0);
    }
}
