package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estela de vuelo: cinta de puntos detrás del jugador, misma técnica que la de los
 * proyectiles de ki. TRASLADO LITERAL, sin rediseño.
 *
 * Es independiente del rework: usa su propia textura (ki_trail) y el RenderType vanilla
 * entityTranslucentEmissive, no la hoja de llamas ni zenkai_energy. Lo único que toma
 * del sistema de aura es el color.
 *
 * En hover o al dejar de volar la cola se retrae punto a punto hasta desaparecer, en vez
 * de quedarse como un blob quieto detrás del jugador.
 */
public final class AuraTrailRenderer {
    private AuraTrailRenderer() {}

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_trail.png");

    private static final int MAX_POINTS = 16;
    private static final double MIN_STEP = 0.15;
    private static final float WIDTH = 0.75f;
    private static final double MIN_SPEED = 0.18;

    private static final Map<Integer, ArrayDeque<Vec3>> TRAILS = new HashMap<>();

    public static void clear(int playerId) { TRAILS.remove(playerId); }

    /**
     * @param pose sin trasladar al jugador: la estela vive en coordenadas de mundo
     * @param rgb  color ya resuelto (AuraColors)
     */
    public static void render(PoseStack pose, MultiBufferSource buffers,
                              AbstractClientPlayer p, Vec3 at, Vec3 camPos, int rgb,
                              AuraTiltController.Motion mo) {
        var trail = TRAILS.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
        Vec3 head = at.add(0, p.getBbHeight() * 0.5, 0);

        boolean moving = mo.flying() && mo.vel().length() > MIN_SPEED;
        if (moving) {
            if (trail.isEmpty() || trail.peekFirst().distanceTo(head) >= MIN_STEP) {
                trail.addFirst(head);
                while (trail.size() > MAX_POINTS) trail.pollLast();
            }
        } else if (!trail.isEmpty()) {
            trail.pollLast();
        }
        if (trail.size() < 2) return;

        List<Vec3> pts = new ArrayList<>(trail);
        pts.addFirst(head);

        float r = AuraQuads.red(rgb), g = AuraQuads.green(rgb), b = AuraQuads.blue(rgb);

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        PoseStack.Pose mat = pose.last();

        int n = pts.size();
        Vec3 prevL = null, prevR = null;
        float prevA = 0f, prevV = 0f;
        for (int i = 0; i < n; i++) {
            Vec3 pt = pts.get(i);
            Vec3 dir = (i < n - 1) ? pts.get(i + 1).subtract(pt) : pt.subtract(pts.get(i - 1));
            Vec3 side = dir.cross(camPos.subtract(pt));
            side = side.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : side.normalize();

            float t = 1f - (float) i / (n - 1);
            float half = WIDTH * 0.5f * (0.25f + 0.75f * t);
            float alpha = 0.85f * t;
            float v = (float) i / (n - 1);

            Vec3 vL = pt.add(side.scale(half));
            Vec3 vR = pt.subtract(side.scale(half));

            if (i > 0) {
                quad(vc, mat, prevL, prevR, vR, vL, r, g, b, prevA, alpha, prevV, v);
                quad(vc, mat, vL, vR, prevR, prevL, r, g, b, alpha, prevA, v, prevV);
            }
            prevL = vL; prevR = vR; prevA = alpha; prevV = v;
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose mat,
                             Vec3 aL, Vec3 aR, Vec3 bR, Vec3 bL,
                             float r, float g, float b,
                             float aAlpha, float bAlpha, float aV, float bV) {
        vert(vc, mat, aL, r, g, b, aAlpha, 0f, aV);
        vert(vc, mat, aR, r, g, b, aAlpha, 1f, aV);
        vert(vc, mat, bR, r, g, b, bAlpha, 1f, bV);
        vert(vc, mat, bL, r, g, b, bAlpha, 0f, bV);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose mat, Vec3 v,
                             float r, float g, float b, float a, float u, float tv) {
        vc.addVertex(mat, (float) v.x, (float) v.y, (float) v.z)
                .setColor(r, g, b, a).setUv(u, tv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(AuraQuads.FULL_BRIGHT)
                .setNormal(mat, 0f, 1f, 0f);
    }
}