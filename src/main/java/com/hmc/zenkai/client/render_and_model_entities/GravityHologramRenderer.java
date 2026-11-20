package com.hmc.zenkai.client.render_and_model_entities;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.content.blockentity.GravityChamberBlockEntity;
import com.hmc.zenkai.feature.gravity.GravityFields;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Holograma de la cámara de gravedad (Fase 3 de la 0.7.0): con el botón de la consola encendido, cada
 * bloque de la corteza de la esfera del radio elegido que todavía no tiene pared se ve como un cubo cian
 * translúcido (dónde colocar los bloques que faltan), y cada FUGA que mandó el servidor como un cubo
 * rojo (por dónde se escapa el aire de la sala, también si la sala es más pequeña que la esfera).
 *
 * RENDIMIENTO: la corteza se calcula una vez por radio y lo que falta como mucho cada 10 ticks
 * ({@link GravityChamberBlockEntity#missingShell}); aquí solo se emiten vértices, en un único lote de
 * debugQuads y solo para consolas a menos de 64 bloques con el holograma encendido. Apagado no cuesta nada.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class GravityHologramRenderer {
    private GravityHologramRenderer() {}

    private static final double MAX_DIST_SQ = 64.0 * 64.0;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (GravityChamberBlockEntity.CLIENT_LOADED.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<GravityChamberBlockEntity> shown = new ArrayList<>(2);
        for (GravityChamberBlockEntity be : GravityChamberBlockEntity.CLIENT_LOADED) {
            if (be.isRemoved() || !be.hologramOn() || be.getLevel() != mc.level) continue;
            if (be.getBlockPos().distToCenterSqr(mc.player.position()) > MAX_DIST_SQ) continue;
            shown.add(be);
        }
        if (shown.isEmpty()) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = ps.last().pose();
        long now = mc.level.getGameTime();
        // Parpadeo suave para que se lea como holograma y no como bloque de cristal.
        float pulse = 0.20f + 0.08f * (float) Math.sin(now * 0.15);

        for (GravityChamberBlockEntity be : shown) {
            for (long k : be.missingShell(now)) {
                cube(vc, m, cam, k, 0.10f, ZenkaiTechPalette.CYAN_HI, pulse);
            }
            for (long k : be.leaks()) {
                cube(vc, m, cam, k, 0.25f, ZenkaiTechPalette.LED_RED, 0.45f);
            }
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    /** Cubo translúcido en la celda k con {@code inset} de margen por lado. Seis caras (debugQuads no recorta). */
    private static void cube(VertexConsumer vc, Matrix4f m, Vec3 cam, long k, float inset, int argb, float alpha) {
        float x0 = (float) (BlockPos.getX(k) - cam.x) + inset, x1 = x0 + 1f - 2f * inset;
        float y0 = (float) (BlockPos.getY(k) - cam.y) + inset, y1 = y0 + 1f - 2f * inset;
        float z0 = (float) (BlockPos.getZ(k) - cam.z) + inset, z1 = z0 + 1f - 2f * inset;
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, alpha);
        quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
        quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, alpha);
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
        quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, alpha);
        quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
    }

    /** Al salir del mundo: la g de cámara del jugador local vuelve a 1 y se olvidan las consolas. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        GravityFields.resetClient();
        GravityChamberBlockEntity.CLIENT_LOADED.clear();
    }
}
