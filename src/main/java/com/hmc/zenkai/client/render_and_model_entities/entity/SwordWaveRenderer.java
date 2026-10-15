package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxCompositeRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxGeometry;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.content.entity.technique.SwordWaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Hoja de luz (LIGHT_BLADE): media luna de ki DIBUJADA con tubos barridos, el mismo pipeline que las armas de ki
 * ({@link KiVfxCompositeRenderer#renderColumn}: cuatro capas de color, hervor del borde y bloom). La media luna vive
 * en el plano YZ local (el de la trayectoria): vista de LADO es una C invertida (convexa hacia delante, los cuernos
 * quedan atrás) y vista de FRENTE es una línea recta, porque la lámina es fina en X (2026-10-14: la primera versión
 * la dibujaba de cara a la cámara y salía una C de frente). Son dos mitades que parten del centro (gruesas) hacia cada
 * cuerno (finos). El cuerpo se ladea {@link SwordWaveEntity#tilt()} grados, como el tajo que la lanzó.
 */
public class SwordWaveRenderer extends EntityRenderer<SwordWaveEntity> {

    /** Radio del arco y medio ángulo de cada cuerno. */
    private static final float R = 1.0F;
    private static final double HALF_ANGLE = Math.toRadians(80);
    /** Radio del tubo en el centro (grueso) y en los cuernos (afilado). */
    private static final float TIP = 0.012F, CENTER = 0.17F;
    /** Aplastado en X (grosor de la lámina). */
    private static final float FLAT_X = 0.16F;

    private static KiVfxGeometry.TubePath left, right;

    public SwordWaveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    private static void ensurePaths() {
        if (left != null) return;
        left = half(-1);
        right = half(1);
    }

    /** Del centro de la luna hacia un cuerno; {@code side} = -1 abajo, +1 arriba. */
    private static KiVfxGeometry.TubePath half(int side) {
        List<Vec3> pts = new ArrayList<>();
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a = HALF_ANGLE * i / (n - 1);
            // Centro del arco DELANTE (z = 0): los cuernos (arriba y abajo) quedan detrás, en -Z.
            pts.add(new Vec3(0, side * R * Math.sin(a), -R * (1 - Math.cos(a))));
        }
        return KiVfxGeometry.tubePath(pts, 0F);
    }

    @Override
    public void render(@NotNull SwordWaveEntity e, float yaw, float pt, @NotNull PoseStack pose,
                       @NotNull MultiBufferSource buffers, int light) {
        if (!KiVfxRenderTypes.available()) return;
        ensurePaths();
        Vec3 vel = e.getDeltaMovement();
        if (vel.lengthSqr() < 1.0e-6) return;
        Vec3 d = vel.normalize();
        int rgb = e.rgb();
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        float yawRad = (float) Math.atan2(d.x, d.z), pitchRad = (float) Math.asin(Mth.clamp(d.y, -1, 1));
        int tilt = e.tilt();
        // Un pequeño latido para que no se vea rígida.
        float pulse = 1F + 0.04F * Mth.sin((e.tickCount + pt) * 1.2F);

        pose.pushPose();
        pose.translate(0, e.getBbHeight() * 0.5, 0);
        Vec3 fromCam = e.position().subtract(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        KiVfxFrameQueue.submit(pose, fromCam, (ps, buf) -> {
            KiVfxColors.begin(-1);
            KiVfxRenderTypes.resetAxis();
            ps.pushPose();
            ps.mulPose(Axis.YP.rotation(yawRad));
            ps.mulPose(Axis.XP.rotation(-pitchRad));
            ps.mulPose(Axis.ZP.rotationDegrees(tilt));
            ps.scale(FLAT_X, 1.6F * pulse, 1.6F * pulse);
            for (KiVfxGeometry.TubePath path : new KiVfxGeometry.TubePath[]{left, right}) {
                KiVfxCompositeRenderer.renderColumn(buf, KiVfxProfile.KI_WEAPON, ps, path, TIP, CENTER / TIP,
                        r, g, b, 1F, false, 1F);
            }
            ps.popPose();
        });
        pose.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull SwordWaveEntity e) {
        return KiVfxRenderTypes.BALL_TEXTURE;
    }

    @Override
    public boolean shouldRender(@NotNull SwordWaveEntity e, @NotNull net.minecraft.client.renderer.culling.Frustum f,
                                double x, double y, double z) {
        return true;
    }
}
