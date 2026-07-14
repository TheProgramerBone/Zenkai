package com.hmc.zenkai.client.render_and_model;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.core.technique.KiTechniqueType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Render v1.1: quad billboard (ki_ball.png tintada, fullbright) + ESTELA 3D para los tipos
 * viajeros (LAZER/WAVE/SPIRAL): cinta de quads encadenados por las posiciones históricas del
 * proyectil (KiProjectileEntity#trailHistory), orientada a cámara por segmento, teñida con el
 * RGB de la técnica, con grosor y alpha desvaneciéndose hacia la cola. Doble cara.
 * BARRIER se dibuja semi-transparente y más grande (envuelve al jugador).
 *
 * Texturas: ki_ball.png (bola/glow radial blanco) y ki_trail.png (franja vertical con núcleo
 * blanco difuminado a los bordes — el blanco toma el tinte). Ambas en textures/entity/.
 */
public class KiProjectileRenderer extends EntityRenderer<KiProjectileEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_ball.png");
    private static final ResourceLocation TRAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_trail.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public KiProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(KiProjectileEntity entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        boolean barrier = entity.techniqueType() == KiTechniqueType.BARRIER;
        int rgb = entity.rgb();
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float a = barrier ? 0.35f : 1.0f;
        float scale = entity.getBbWidth() * 1.5f; // el glow sobresale un poco del hitbox

        // ── Estela (antes que la bola: la cabeza queda debajo del glow) ──
        if (entity.techniqueType().hasTrail() && entity.trailHistory().size() >= 2) {
            renderTrail(entity, partialTick, pose, buffer, r, g, b);
        }

        pose.pushPose();
        pose.translate(0, entity.getBbHeight() * 0.5, 0);       // centro de la entidad
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation()); // billboard
        pose.scale(scale, scale, scale);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        PoseStack.Pose p = pose.last();
        // Quad 1x1 centrado (winding hacia la cámara).
        vc.addVertex(p, -0.5f, -0.5f, 0).setColor(r, g, b, a).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
        vc.addVertex(p, 0.5f, -0.5f, 0).setColor(r, g, b, a).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
        vc.addVertex(p, 0.5f, 0.5f, 0).setColor(r, g, b, a).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
        vc.addVertex(p, -0.5f, 0.5f, 0).setColor(r, g, b, a).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    /**
     * Cinta de la estela en espacio mundo (el PoseStack llega centrado en la posición de render
     * de la entidad: cada vértice = puntoMundo - posiciónRender). Cada punto genera un par de
     * vértices desplazados por el vector lateral (dirección×haciaCámara) para encarar la cámara.
     */
    private void renderTrail(KiProjectileEntity e, float partialTick, PoseStack pose,
                             MultiBufferSource buffer, float r, float g, float b) {
        List<Vec3> pts = new ArrayList<>(e.trailHistory()); // [0] = más reciente
        Vec3 feet = e.getPosition(partialTick);             // origen del PoseStack (pies)
        Vec3 head = feet.add(0, e.getBbHeight() * 0.5, 0);
        pts.add(0, head);                                   // cabeza interpolada: pegada a la bola

        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        float fullWidth = e.getBbWidth() * e.techniqueType().trailWidth();

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(TRAIL_TEXTURE));
        PoseStack.Pose mat = pose.last();

        int n = pts.size();
        Vec3 prevL = null, prevR = null;
        float prevA = 0, prevV = 0;
        for (int i = 0; i < n; i++) {
            Vec3 pt = pts.get(i);
            Vec3 dir = (i < n - 1)
                    ? pts.get(i + 1).subtract(pt)
                    : pt.subtract(pts.get(i - 1)); // último punto: prolonga el segmento anterior
            Vec3 side = dir.cross(cam.subtract(pt));
            side = side.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : side.normalize();

            float t = 1f - (float) i / (n - 1);              // 1 cabeza -> 0 cola
            float half = fullWidth * 0.5f * (0.25f + 0.75f * t);
            float alpha = 0.85f * t;
            float v = (float) i / (n - 1);

            Vec3 vL = pt.add(side.scale(half)).subtract(feet);
            Vec3 vR = pt.subtract(side.scale(half)).subtract(feet);

            if (i > 0) {
                // Doble cara: visible desde cualquier lado (los RenderType de entidad hacen cull).
                quad(vc, mat, prevL, prevR, vR, vL, r, g, b, prevA, alpha, prevV, v);
                quad(vc, mat, vL, vR, prevR, prevL, r, g, b, alpha, prevA, v, prevV);
            }
            prevL = vL; prevR = vR; prevA = alpha; prevV = v;
        }
    }

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

    @Override
    public ResourceLocation getTextureLocation(KiProjectileEntity entity) {
        return TEXTURE;
    }
}