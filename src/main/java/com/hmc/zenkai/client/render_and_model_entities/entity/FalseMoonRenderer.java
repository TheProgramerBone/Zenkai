package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxCompositeRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxGeometry;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.compat.IrisCompat;
import com.hmc.zenkai.content.entity.technique.FalseMoonEntity;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Luna falsa ({@link FalseMoonEntity}): una esfera grande, pálida y cálida, quieta en el aire, con un latido muy lento. Se
 * dibuja con el mismo cuerpo que una bola de ki (perfil BLAST) y por la cola diferida, como la masa del Majin, pero a lo
 * grande: tiene que leerse como una luna, no como un proyectil.
 */
public class FalseMoonRenderer extends EntityRenderer<FalseMoonEntity> {

    /** Diámetro de la luna en bloques. */
    private static final float SIZE = 7.0f;
    /** Tono luna llena, algo cálido. */
    private static final int RGB = 0xFFF1C9;

    public FalseMoonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(@NotNull FalseMoonEntity e, float yaw, float pt, @NotNull PoseStack pose,
                       @NotNull MultiBufferSource buffers, int light) {
        if (!KiVfxRenderTypes.available() || IrisCompat.renderingShadowPass()) return;
        float t = e.tickCount + pt;
        float r = ((RGB >> 16) & 0xFF) / 255f, g = ((RGB >> 8) & 0xFF) / 255f, b = (RGB & 0xFF) / 255f;
        float pulse = 1f + 0.03f * Mth.sin(t * 0.07f);
        // Nace creciendo (primeros ticks); el apagado final lo da el propio descarte.
        float grow = Mth.clamp(t / 12f, 0.15f, 1f);
        float diameter = SIZE * pulse * grow;

        Vec3 world = e.getPosition(pt);
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        Vec3 fromCamera = world.subtract(cam);
        pose.pushPose();
        pose.translate(0, e.getBbHeight() * 0.5, 0);
        KiVfxFrameQueue.submit(pose, fromCamera, (p, buf) -> {
            KiVfxProfile v = KiVfxProfile.of(KiTechniqueType.BLAST);
            KiVfxColors.begin(0xFFFFFF);
            float proximity = KiVfxRenderTypes.proximity(cam.distanceTo(world), v.worldRadius(diameter));
            KiVfxCompositeRenderer.render(buf, v, KiVfxGeometry.shell(v), KiVfxGeometry.core(v, true),
                    p, diameter, r, g, b, 1f, proximity);
        });
        pose.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull FalseMoonEntity e) {
        return KiVfxRenderTypes.BALL_TEXTURE;
    }

    /** Siempre se dibuja (es enorme y flota lejos: el culling por caja la perdería al girar la cámara). */
    @Override
    public boolean shouldRender(@NotNull FalseMoonEntity e, @NotNull net.minecraft.client.renderer.culling.Frustum f,
                                double x, double y, double z) {
        return true;
    }
}
