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

/**
 * Render simple v1.0: quad billboard (siempre de cara a la cámara) con la textura
 * ki_ball.png tintada con el color de la técnica, fullbright (brilla en la oscuridad).
 * BARRIER se dibuja semi-transparente y más grande (envuelve al jugador).
 *
 * Textura de Juan: assets/zenkai/textures/entity/ki_ball.png — bola/glow radial en BLANCO
 * sobre fondo transparente (el blanco toma el tinte; 32x32 o 64x64 va sobrado).
 */
public class KiProjectileRenderer extends EntityRenderer<KiProjectileEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_ball.png");
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

    @Override
    public ResourceLocation getTextureLocation(KiProjectileEntity entity) {
        return TEXTURE;
    }
}