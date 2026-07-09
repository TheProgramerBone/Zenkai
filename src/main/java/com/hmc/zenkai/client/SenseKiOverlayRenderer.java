package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.combat.SenseKiMode;
import com.hmc.zenkai.core.network.feature.sense.SenseKiDataPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Barras de vida flotantes del SENTIR EL KI (nivel 1: solo barras).
 * Billboards sobre la cabeza, visibles A TRAVÉS de paredes.
 *
 * Render: RenderType.textBackgroundSeeThrough() + bufferSource del juego (el camino de los
 * fondos de nametag). Reglas aprendidas EN JUEGO de este RenderType:
 *  - Cullea: el winding de quad(...) es el frontal correcto para nuestra transformación.
 *  - Las capas NO deben superponerse: quads coplanares en el mismo plano hacían z-fighting
 *    (titileo). Por eso el layout es SIN solapamiento: relleno hasta pct, fondo desde pct,
 *    y el borde en 4 tiras alrededor.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SenseKiOverlayRenderer {
    private SenseKiOverlayRenderer() {}

    // Diales (unidades tras el scale de nametag 0.025: 36 px ≈ 0.9 bloques de ancho).
    private static final float BAR_W    = 36f;
    private static final float BAR_H    = 4f;
    private static final float Y_OFFSET = 0.75f;
    private static final int   C_BG     = 0xB0101010;
    private static final int   C_EDGE   = 0xFF303030;
    private static final int   FULL_BRIGHT = 0xF000F0; // LightTexture.FULL_BRIGHT

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (SenseKiClientState.mode() == SenseKiMode.OFF) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (SenseKiClientState.sensed().isEmpty()) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackgroundSeeThrough());

        for (SenseKiDataPacket.Entry entry : SenseKiClientState.sensed().values()) {
            if (!SenseKiClientState.passesFilter(entry, mc)) continue;

            Entity ent = mc.level.getEntity(entry.entityId());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) continue;
            if (le == mc.player) continue;

            Vec3 p = le.getPosition(pt).add(0, le.getBbHeight() + Y_OFFSET, 0);

            pose.pushPose();
            pose.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            pose.mulPose(cam.rotation());          // billboard: mira a la cámara
            pose.scale(-0.025f, -0.025f, 0.025f);  // convención nametag

            drawBar(pose.last().pose(), vc, entry.body(), entry.bodyMax());
            pose.popPose();
        }

        buffers.endBatch(RenderType.textBackgroundSeeThrough());
    }

    /**
     * Layout SIN solapamiento (anti z-fighting):
     *   - relleno: desde la izquierda hasta pct (opaco, verde->rojo)
     *   - fondo:   desde pct hasta la derecha (translúcido)
     *   - borde:   4 tiras alrededor (arriba/abajo/izq/der), fuera del interior
     */
    private static void drawBar(Matrix4f m, VertexConsumer vc, int cur, int max) {
        float half = BAR_W / 2f;
        float pct  = (max > 0) ? Math.max(0f, Math.min(1f, cur / (float) max)) : 0f;

        float left = -half, right = half;
        float fillEnd = left + BAR_W * pct;

        // Verde (llena) -> rojo (vacía).
        int r = Math.min(255, (int) (255 * (1f - pct)));
        int g = Math.min(255, (int) (220 * pct) + 35);
        int fill = 0xFF000000 | (r << 16) | (g << 8) | 0x20;

        // Interior: dos tramos que NO se tocan entre sí.
        if (pct > 0f)  quad(vc, m, left,    0, fillEnd, BAR_H, fill);
        if (pct < 1f)  quad(vc, m, fillEnd, 0, right,   BAR_H, C_BG);

        // Borde: 4 tiras alrededor del interior (sin pisarlo).
        quad(vc, m, left - 1, -1,        right + 1, 0,          C_EDGE); // arriba
        quad(vc, m, left - 1, BAR_H,     right + 1, BAR_H + 1,  C_EDGE); // abajo
        quad(vc, m, left - 1, 0,         left,      BAR_H,      C_EDGE); // izquierda
        quad(vc, m, right,    0,         right + 1, BAR_H,      C_EDGE); // derecha
    }

    /**
     * Quad de UNA cara, con el winding que el RenderType considera frontal con nuestra
     * transformación de billboard (verificado en juego: el orden inverso lo culleaba).
     */
    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float x1, float y1, int argb) {
        vc.addVertex(m, x1, y0, 0).setColor(argb).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y1, 0).setColor(argb).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y1, 0).setColor(argb).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y0, 0).setColor(argb).setLight(FULL_BRIGHT);
    }
}