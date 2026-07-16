package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Aura estilo DBC/DMZ: CONO RADIAL de planos-llama.
 * Colores claros → pase ADITIVO (brilla). Colores oscuros → pase ALPHA (oscurece/ocluye).
 * En una banda alrededor del umbral se dibujan AMBOS pases con pesos que se cruzan
 * (crossfade), para que el cambio claro↔oscuro sea suave y no un salto brusco.
 * Se renderiza en AFTER_PARTICLES (el jugador ya escribió profundidad → depth-test lo ocluye).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraRenderer {
    private AuraRenderer() {}

    private static final int FRAMES = 3;
    private static final ResourceLocation[] FLAME = new ResourceLocation[FRAMES];
    static {
        for (int i = 0; i < FRAMES; i++) {
            FLAME[i] = ResourceLocation.fromNamespaceAndPath(
                    Zenkai.MOD_ID, "textures/entity/aura_flame_" + i + ".png");
        }
    }

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float BASE_ALPHA = 0.13f;   // bajo a propósito: el aditivo acumula
    private static final float AURA_SCALE = 1.30f;   // tamaño global del aura en el MUNDO

    // Transición claro↔oscuro por luminancia: crossfade en [THRESHOLD - BAND, THRESHOLD + BAND].
    private static final float DARK_THRESHOLD = 0.22f;
    private static final float DARK_BAND      = 0.12f; // ancho de la transición (súbelo = más suave)

    /** Un faldón del cono: anillo de {@code count} planos-llama. */
    private record Skirt(int count, float offsetDeg, float tiltDeg, float baseR,
                         float width, float height, float yStart, float jitter, float alpha) {}

    private static final Skirt[] SKIRTS = {
            //        count offset  tilt   baseR  width height yStart jitter alpha
            new Skirt(12,   0f,     6f,    0.22f, 1.4f, 3.2f,  0.0f,  0.30f, 0.55f), // COLUMNA
            new Skirt(12,   15f,    20f,   0.44f, 1.5f, 2.8f,  0.0f,  0.35f, 1.00f), // MEDIA
            new Skirt(3,    0f,     0f,    0.06f, 1.0f, 3.3f,  0.0f,  0.15f, 0.35f), // PLUME
            new Skirt(8,    0f,     48f,   0.55f, 1.4f, 1.6f,  0.0f,  0.30f, 1.00f), // FLARE
    };

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        long t = mc.level.getGameTime();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer p)) continue;
            if (!AuraClientState.isAuraActive(p)) continue;

            int rgb = AuraClientState.resolveColor(p);
            Vec3 at = p.getPosition(pt);
            pose.pushPose();
            pose.translate(at.x - camPos.x, at.y - camPos.y, at.z - camPos.z);
            drawAura(pose, buffers, rgb, AURA_SCALE, t, pt, p.getId());
            pose.popPose();
        }

        buffers.endBatch();
    }

    /**
     * Dibuja el aura completa para un jugador, eligiendo aditivo/oscuro por luminancia y
     * haciendo crossfade en la banda de transición. No hace flush.
     */
    public static void drawAura(PoseStack pose, MultiBufferSource buffers, int rgb, float scale,
                                long gameTime, float partialTick, int seed) {
        int frame = (int) (((float) gameTime / 1.5f + seed) % FRAMES);
        ResourceLocation tex = FLAME[frame];
        double time = gameTime + partialTick;

        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float lum = 0.299f * r + 0.587f * g + 0.114f * b;

        // wLight: 1 = claro puro (aditivo), 0 = oscuro puro (alpha). Crossfade suave (smoothstep).
        float lo = DARK_THRESHOLD - DARK_BAND;
        float hi = DARK_THRESHOLD + DARK_BAND;
        float u = clamp01((lum - lo) / (hi - lo));
        float wLight = u * u * (3f - 2f * u);
        float wDark = 1f - wLight;

        if (wLight > 0.001f)
            drawCone(pose, buffers.getBuffer(ModAuraRenderType.energy(tex)), rgb, scale, time, seed, wLight);
        if (wDark > 0.001f)
            drawCone(pose, buffers.getBuffer(ModAuraRenderType.energyDark(tex)), rgb, scale, time, seed, wDark);
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    /**
     * Dibuja el cono en el espacio actual del PoseStack (origen = pies, +Y arriba, bloques).
     * alphaMul escala el alpha de todos los planos (para el crossfade). No hace flush.
     */
    public static void drawCone(PoseStack pose, VertexConsumer vc, int rgb, float scale,
                                double timeTicks, int seed, float alphaMul) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float pulse = 1f + 0.05f * (float) Math.sin(timeTicks * 0.3 + seed);

        int si = 0;
        for (Skirt s : SKIRTS) {
            float w = s.width() * scale * pulse;
            float step = 360f / s.count();
            float a = BASE_ALPHA * s.alpha() * alphaMul;
            for (int i = 0; i < s.count(); i++) {
                float wobble = 0.5f + 0.5f * (float) Math.sin(i * 2.399f + si * 1.3f);
                float h = s.height() * scale * pulse * (1f - s.jitter() * wobble);
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(s.offsetDeg() + i * step));
                pose.translate(0f, s.yStart() * scale, s.baseR() * scale);
                pose.mulPose(Axis.XP.rotationDegrees(s.tiltDeg()));
                plane(vc, pose.last(), w, h, r, g, b, a);
                pose.popPose();
            }
            si++;
        }
    }

    /** Plano-llama vertical: nace en y=0 (piso), sube a y=h, ancho w centrado, en z=0. */
    private static void plane(VertexConsumer vc, PoseStack.Pose m, float w, float h,
                              float r, float g, float b, float a) {
        vert(vc, m, -w / 2, 0f, 0f, 1f, r, g, b, a);
        vert(vc, m, w / 2, 0f, 1f, 1f, r, g, b, a);
        vert(vc, m, w / 2, h, 1f, 0f, r, g, b, a);
        vert(vc, m, -w / 2, h, 0f, 0f, r, g, b, a);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose m, float x, float y,
                             float u, float v, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, 0f)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(m, 0, 0, 1);
    }
}