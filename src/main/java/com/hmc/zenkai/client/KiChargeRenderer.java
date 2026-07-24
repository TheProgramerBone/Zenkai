package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Bola de ki mientras se carga una técnica, en el punto que diga su TechniquePosition
 * (mano, boca, frente...). La ven todos, no solo quien carga: los datos llegan por
 * KiChargeStatePacket y el crecimiento se deriva del tick de inicio.
 *
 * Reutiliza ModAuraRenderType.energy y AFTER_PARTICLES igual que el aura: mismo tratamiento
 * de luz y oclusión, y una sola forma de dibujar energía en el mundo. Billboard con la
 * rotación de la cámara, más un núcleo blanco interior como el del aura.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiChargeRenderer {
    private KiChargeRenderer() {}

    private static final ResourceLocation BALL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_ball.png");

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Radio en bloques: base + aporte del tamaño de la técnica. */
    private static final float BASE_RADIUS = 0.16f;
    private static final float SIZE_RADIUS = 0.05f;
    /** Al 0% ya se ve algo: una bola que nace de la nada se lee como un parpadeo. */
    private static final float START_SCALE = 0.35f;
    private static final float CORE_MUL = 0.55f;
    private static final float ALPHA = 0.85f;
    private static final float CORE_ALPHA = 0.75f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        long now = mc.level.getGameTime();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        boolean drew = false;
        for (Player p : mc.level.players()) {
            KiChargeClientState.Charge c = KiChargeClientState.of(p);
            if (c == null) continue;

            float progress = KiChargeClientState.progress(c, now);
            // Posición interpolada: con p.position() cruda la bola vibra al andar.
            Vec3 origin = c.position().origin(p, p.getPosition(pt));

            float radius = (BASE_RADIUS + SIZE_RADIUS * c.size())
                    * (START_SCALE + (1f - START_SCALE) * progress)
                    * (1f + 0.05f * (float) Math.sin((now + pt) * 0.4));

            float r = ((c.rgb() >> 16) & 0xFF) / 255f;
            float g = ((c.rgb() >> 8) & 0xFF) / 255f;
            float b = (c.rgb() & 0xFF) / 255f;

            pose.pushPose();
            pose.translate(origin.x - camPos.x, origin.y - camPos.y, origin.z - camPos.z);
            pose.mulPose(cam.rotation()); // billboard: siempre de cara

            VertexConsumer vc = buffers.getBuffer(ModAuraRenderType.energy(BALL));
            quad(vc, pose.last(), radius, r, g, b, ALPHA);
            // Núcleo blanco: el "más claro que el tinte" del canon, y hace legible la bola
            // aunque el jugador haya elegido un color oscuro.
            quad(vc, pose.last(), radius * CORE_MUL, 1f, 1f, 1f, CORE_ALPHA);

            pose.popPose();
            drew = true;
        }

        if (drew) buffers.endBatch();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose m, float half,
                             float r, float g, float b, float a) {
        vert(vc, m, -half, -half, 0f, 1f, r, g, b, a);
        vert(vc, m,  half, -half, 1f, 1f, r, g, b, a);
        vert(vc, m,  half,  half, 1f, 0f, r, g, b, a);
        vert(vc, m, -half,  half, 0f, 0f, r, g, b, a);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose m, float x, float y,
                             float u, float v, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, 0f)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(m, 0, 0, 1);
    }
}