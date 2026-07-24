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
 * DOS PASADAS, relleno + borde, cada una con su textura. El contorno nítido sale de tener
 * el borde dibujado en su sitio, no de meter una quad blanca encima: así el color que eligió
 * el jugador se respeta entero y una técnica oscura sigue teniendo silueta.
 *
 * Va en AFTER_PARTICLES como el aura (misma oclusión y misma luz), pero con energyCrisp y no
 * con energy: aquí la textura se magnifica muchísimo (media pantalla a un palmo de la cara)
 * y el filtrado bilineal del aura la convertía en un borrón. Sin filtro, se ve el píxel.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiChargeRenderer {
    private KiChargeRenderer() {}

    private static final ResourceLocation BALL =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_ball.png");
    /** Borde en su propia textura. Si aún no está dibujada, pon BORDER_ALPHA a 0 y se salta
     *  la pasada (si no, Minecraft pinta la textura de "falta" en morado y negro). */
    private static final ResourceLocation BALL_BORDER =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_ball_border.png");

    private static final float ALPHA = 0.85f;
    private static final float BORDER_ALPHA = 1.0f;

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Radio en bloques: base + aporte del tamaño de la técnica. */
    private static final float BASE_RADIUS = 0.16f;
    private static final float SIZE_RADIUS = 0.05f;
    /** Al 0% ya se ve algo: una bola que nace de la nada se lee como un parpadeo. */
    private static final float START_SCALE = 0.35f;

    /** Ajuste SOLO visual y SOLO en primera persona: el modelo que ves ahí no está donde
     *  está tu modelo real. NO toca el spawn del proyectil, que es autoritativo del
     *  servidor y debe ser igual para todos. Toca estos tres a ojo hasta que cuadre. */
    private static final float FP_FORWARD = 0.35f;
    private static final float FP_SIDE    = 0.10f;
    private static final float FP_DOWN    = 0.25f;

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
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                Vec3 look = p.getLookAngle().normalize();
                Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
                origin = origin.add(look.scale(FP_FORWARD))
                        .add(right.scale(FP_SIDE))
                        .subtract(0.0, FP_DOWN, 0.0);
            }

            float radius = (BASE_RADIUS + SIZE_RADIUS * c.size())
                    * (START_SCALE + (1f - START_SCALE) * progress)
                    * (1f + 0.05f * (float) Math.sin((now + pt) * 0.4));

            float r = ((c.rgb() >> 16) & 0xFF) / 255f;
            float g = ((c.rgb() >> 8) & 0xFF) / 255f;
            float b = (c.rgb() & 0xFF) / 255f;

            pose.pushPose();
            pose.translate(origin.x - camPos.x, origin.y - camPos.y, origin.z - camPos.z);
            pose.mulPose(cam.rotation()); // billboard: siempre de cara

            // Relleno primero, borde encima. Pedir un render type distinto vuelca el anterior,
            // así que el orden de estas dos llamadas ES el orden de dibujo.
            quad(buffers.getBuffer(ModAuraRenderType.energyCrisp(BALL)),
                    pose.last(), radius, r, g, b, ALPHA);
            if (BORDER_ALPHA > 0f) {
                quad(buffers.getBuffer(ModAuraRenderType.energyCrisp(BALL_BORDER)),
                        pose.last(), radius, r, g, b, BORDER_ALPHA);
            }

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