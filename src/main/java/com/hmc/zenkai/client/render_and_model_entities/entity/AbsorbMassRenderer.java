package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxCompositeRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxGeometry;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.compat.IrisCompat;
import com.hmc.zenkai.content.entity.technique.AbsorbMassEntity;
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
 * Masa de la absorción del Majin ({@link AbsorbMassEntity}): una esfera blanda del color de su piel, que se estira en el
 * sentido del vuelo y palpita al avanzar (se siente como algo elástico, no como una bola de ki). Se dibuja con el MISMO
 * cuerpo que una bola de ki (perfil BLAST, {@link KiVfxCompositeRenderer}) y por la cola diferida ({@link KiVfxFrameQueue}),
 * pero con el núcleo del MISMO tono (algo más oscuro) en vez del blanco caliente de ki: es materia, no energía.
 */
public class AbsorbMassRenderer extends EntityRenderer<AbsorbMassEntity> {

    /** Diámetro base de la masa en bloques. */
    private static final float SIZE = 0.75f;

    public AbsorbMassRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(@NotNull AbsorbMassEntity e, float yaw, float pt, @NotNull PoseStack pose,
                       @NotNull MultiBufferSource buffers, int light) {
        if (!KiVfxRenderTypes.available() || IrisCompat.renderingShadowPass()) return;
        float t = e.tickCount + pt;
        int rgb = e.rgb();
        int core = darken(rgb, 0.78f);
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        // Palpita y se alarga un poco en el sentido del vuelo.
        float pulse = 1f + 0.10f * Mth.sin(t * 1.9f) + 0.04f * Mth.sin(t * 5.1f + 0.7f);
        float diameter = SIZE * pulse;

        Vec3 world = e.getPosition(pt);
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        Vec3 fromCamera = world.subtract(cam);
        pose.pushPose();
        pose.translate(0, e.getBbHeight() * 0.5, 0);
        KiVfxFrameQueue.submit(pose, fromCamera, (p, buf) -> {
            KiVfxProfile v = KiVfxProfile.of(KiTechniqueType.BLAST);
            KiVfxColors.begin(core);
            float proximity = KiVfxRenderTypes.proximity(cam.distanceTo(world), v.worldRadius(diameter));
            KiVfxCompositeRenderer.render(buf, v, KiVfxGeometry.shell(v), KiVfxGeometry.core(v, true),
                    p, diameter, r, g, b, 1f, proximity);
        });
        pose.popPose();
    }

    private static int darken(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f), g = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull AbsorbMassEntity e) {
        return KiVfxRenderTypes.BALL_TEXTURE;
    }

    @Override
    public boolean shouldRender(@NotNull AbsorbMassEntity e, @NotNull net.minecraft.client.renderer.culling.Frustum f,
                                double x, double y, double z) {
        return true;
    }
}
