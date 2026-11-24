package com.hmc.zenkai.client.render_and_model_entities;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.content.block.CapsuleBlock;
import com.hmc.zenkai.content.blockentity.CapsuleBlockEntity;
import com.hmc.zenkai.feature.capsule.CapsuleZone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Contorno y holograma de las cápsulas colocadas (Fase 6):
 *   - VACÍA con el contorno encendido: las aristas del cubo de captura en el color del metal; armada (esperando a que se
 *     vacíe la zona), parpadeando.
 *   - LLENA: cada bloque guardado como un cubo translúcido en el color del metal, y en ROJO donde algo del mundo estorba
 *     (no es aire ni reemplazable): lo que haría fallar el pegado. Lo calcula el cliente con lo que ve; el servidor vuelve a
 *     comprobarlo al soltar.
 *
 * RENDER (arreglado 2026-11-24 sobre una captura del usuario): cada cubo libre se encoge {@link #INSET} por cada lado (el
 * bloqueado, en rojo, crece lo mismo para no quedar dentro del bloque que estorba), porque la
 * capa de abajo de lo guardado se pega ENCIMA del terreno y su cara inferior caía justo en la cara superior del césped
 * (z-fighting: franjas del color del metal). Y se dibuja con {@link Holo#QUADS}, que NO escribe profundidad: con
 * debugQuads (sí la escribe) los cubos translúcidos sin ordenar se tapaban unos a otros a trozos.
 *
 * RENDIMIENTO: solo cápsulas a menos de 48 bloques, y del holograma solo se emiten las caras que dan a una celda vacía de
 * lo guardado (una casa maciza son sus paredes, no su volumen). El conjunto de posiciones se cachea por cápsula.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class CapsuleHologramRenderer {
    private CapsuleHologramRenderer() {}

    private static final double MAX_DIST_SQ = 48.0 * 48.0;
    /** Lo que se encoge cada cubo del holograma por cada lado (ver la cabecera). */
    private static final float INSET = 0.02f;

    /**
     * Quads de color translúcidos SIN escribir profundidad (prueba LEQUAL, sin recorte de caras). Se hereda de RenderType
     * solo para alcanzar los shards protegidos, como KiChamberRenderTypes; nunca se instancia.
     */
    private static final class Holo extends RenderType {
        private Holo() { super("", null, null, 0, false, false, null, null); }

        static final RenderType QUADS = RenderType.create("zenkai_capsule_hologram",
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }
    private static final Map<int[], IntOpenHashSet> SETS = new WeakHashMap<>();

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (CapsuleBlockEntity.CLIENT_LOADED.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = mc.level.getGameTime();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * 0.3);

        for (CapsuleBlockEntity be : CapsuleBlockEntity.CLIENT_LOADED) {
            if (be.isRemoved() || be.getLevel() != mc.level) continue;
            if (be.getBlockPos().distToCenterSqr(mc.player.position()) > MAX_DIST_SQ) continue;
            BlockState st = be.getBlockState();
            if (!(st.getBlock() instanceof CapsuleBlock)) continue;
            int rgb = st.getValue(CapsuleBlock.TIER).rgb;
            float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;

            if (st.getValue(CapsuleBlock.FULL)) {
                if (be.preview().length == 0) continue;
                BlockPos min = CapsuleZone.pasteMin(be.getBlockPos(), be.previewRadius());
                drawHologram(buffers, ps, cam, be, min, r, g, b, 0.22f + 0.06f * pulse);
                drawBox(buffers, ps, cam, CapsuleZone.box(min, be.previewRadius()), r, g, b, 0.6f);
            } else if (be.mode() != CapsuleBlockEntity.Mode.IDLE) {
                int radius = st.getValue(CapsuleBlock.TIER).radius();
                AABB box = CapsuleZone.box(CapsuleZone.captureMin(be.getBlockPos(), radius), radius);
                float a = be.mode() == CapsuleBlockEntity.Mode.ARMED ? 0.35f + 0.65f * pulse : 0.9f;
                drawBox(buffers, ps, cam, box, r, g, b, a);
            }
        }
    }

    private static void drawBox(MultiBufferSource.BufferSource buffers, PoseStack ps, Vec3 cam, AABB box,
                                float r, float g, float b, float a) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(ps, vc, box.inflate(0.002), r, g, b, a);
        ps.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void drawHologram(MultiBufferSource.BufferSource buffers, PoseStack ps, Vec3 cam,
                                     CapsuleBlockEntity be, BlockPos min, float r, float g, float b, float alpha) {
        int[] preview = be.preview();
        IntOpenHashSet set = SETS.computeIfAbsent(preview, IntOpenHashSet::new);
        var level = Minecraft.getInstance().level;
        VertexConsumer vc = buffers.getBuffer(Holo.QUADS);
        Matrix4f m = ps.last().pose();
        int bad = ZenkaiTechPalette.LED_RED;
        float br = ((bad >> 16) & 0xFF) / 255f, bg = ((bad >> 8) & 0xFF) / 255f, bb = (bad & 0xFF) / 255f;
        BlockPos.MutableBlockPos t = new BlockPos.MutableBlockPos();
        for (int p : preview) {
            int x = CapsuleZone.unpackX(p), y = CapsuleZone.unpackY(p), z = CapsuleZone.unpackZ(p);
            t.set(min.getX() + x, min.getY() + y, min.getZ() + z);
            BlockState there = level.getBlockState(t);
            boolean blocked = !t.equals(be.getBlockPos()) && !there.isAir() && !there.canBeReplaced();
            float cr = blocked ? br : r, cg = blocked ? bg : g, cb = blocked ? bb : b;
            float a = blocked ? 0.5f : alpha;
            // Libre: se encoge (no pelea con el césped). Bloqueada: CRECE, porque encogida quedaría dentro del bloque que
            // estorba y la prueba de profundidad la escondería entera (el rojo no se veía; capturas K5/K6 del 2026-11-24).
            float in = blocked ? -INSET : INSET;
            float x0 = (float) (t.getX() - cam.x) + in, y0 = (float) (t.getY() - cam.y) + in, z0 = (float) (t.getZ() - cam.z) + in;
            float x1 = x0 + 1f - 2 * in, y1 = y0 + 1f - 2 * in, z1 = z0 + 1f - 2 * in;
            // Una cara solo si la celda vecina NO es parte de lo guardado (o si esta celda está bloqueada: se ve entera).
            if (blocked || !set.contains(packOrNeg(x, y - 1, z))) quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, cr, cg, cb, a);
            if (blocked || !set.contains(packOrNeg(x, y + 1, z))) quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, cr, cg, cb, a);
            if (blocked || !set.contains(packOrNeg(x, y, z - 1))) quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, cr, cg, cb, a);
            if (blocked || !set.contains(packOrNeg(x, y, z + 1))) quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, cr, cg, cb, a);
            if (blocked || !set.contains(packOrNeg(x - 1, y, z))) quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, cr, cg, cb, a);
            if (blocked || !set.contains(packOrNeg(x + 1, y, z))) quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, cr, cg, cb, a);
        }
        buffers.endBatch(Holo.QUADS);
    }

    /** Empaqueta, o −1 si se sale de la rejilla (−1 nunca está en el conjunto: es «vecino vacío»). */
    private static int packOrNeg(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x > 63 || y > 63 || z > 63) return -1;
        return CapsuleZone.pack(x, y, z);
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

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        CapsuleBlockEntity.CLIENT_LOADED.clear();
        SETS.clear();
    }
}
