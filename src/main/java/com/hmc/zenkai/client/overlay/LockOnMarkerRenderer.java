package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.LockOnClientState;
import com.hmc.zenkai.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Feedback visual del lock-on. Minimalista a propósito (pedido del usuario: moderno, nada
 * intrusivo): un anillo fino roto en cuatro arcos que gira despacio alrededor del objetivo, con
 * cuatro muescas internas que giran al revés. Un solo color, centro vacío (no tapa a quien
 * miras) y SIN números: coherente con el Ki Sense, que es percepción cualitativa.
 *
 * Estados, todos con el mismo dibujo y variando tinte/alfa/escala:
 *  - FIJANDO: el anillo se cierra desde más grande en ~6 ticks.
 *  - FIJADO: giro lento y alfa constante.
 *  - PERDIENDO (sin línea de vista): se apaga y parpadea suave; es lo que ves antes de que la
 *    cámara suelte el objetivo o este se suelte solo.
 *  - FUERA DE VISTA: una flecha pequeña en el borde de la pantalla hacia el objetivo.
 * Además, una flecha roja por cada jugador que te tiene fijado a ti (LockOnAlertPacket) cuando
 * está fuera de pantalla.
 *
 * Se dibuja con RenderType.textSeeThrough, el mismo que las llamas del Ki Sense: sin test de
 * profundidad (se ve tras una pared, que es justo el caso "perdiendo") y sin inventar un
 * RenderType propio, que dejaría de pintarse con Iris/Oculus.
 *
 * La posición en pantalla de la flecha del borde se calcula con los vectores de la cámara
 * (mira/izquierda/arriba) y el FOV de las opciones, no con las matrices de proyección: así no
 * depende del GUI Scale ni de trucos de matrices, que es donde fallan estos indicadores.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class LockOnMarkerRenderer {
    private LockOnMarkerRenderer() {}

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/sense/lock_on_marker.png");

    /** Hoja de 128x128 (ver tools/gen_lockon_marker.py): anillo, marcas, flecha. */
    private static final int TEX_SIZE = 128;
    private static final int ARROW_U = 0, ARROW_V = 64, ARROW_CELL = 32;

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Aguamarina suave: legible sobre pasto, piedra y cielo sin gritar. */
    private static final int COLOR_LOCKED = 0x7FE8E0;
    private static final int COLOR_ALERT = 0xFF6464;

    /** Duración de la animación de cierre al fijar (ticks), y vuelta completa del anillo (ticks). */
    private static final float CLOSE_TICKS = 6f;
    private static final float RING_PERIOD_TICKS = 80f;

    /** Radio mínimo en pantalla (fracción de la distancia) y tope, para que ni desaparezca de
     *  lejos ni se coma la pantalla de cerca. */
    private static final float MIN_RADIUS_PER_DIST = 0.045f;
    private static final float MAX_RADIUS_PER_DIST = 0.35f;

    /** Flecha del borde: tamaño en píxeles de GUI y distancia al borde. */
    private static final int ARROW_SIZE = 18;
    private static final int EDGE_MARGIN = 26;

    // ── Anillo sobre el objetivo (HUD proyectado) ─────────────────────────────

    /**
     * El anillo se dibuja en PANTALLA, proyectando el punto con las matrices del último frame del
     * mundo (SenseKiOverlayRenderer.projectWorld), igual que los ecos del Ki Sense. Antes era un
     * quad en el mundo (textSeeThrough en AFTER_PARTICLES) y con shaderpack las nubes y el agua,
     * que se pintan DESPUÉS, lo tapaban en parte; el HUD va siempre encima y no depende del pipeline.
     */
    private static void drawRing(GuiGraphics g, Minecraft mc, float pt, float opacity) {
        LivingEntity t = LockOnClientState.target(mc);
        if (t == null || !t.isAlive()) return;
        float time = mc.level.getGameTime() + pt;

        Vec3 p = LockOnClientState.aimPoint(t, pt);
        float[] s = SenseKiOverlayRenderer.projectWorld(p, g.guiWidth(), g.guiHeight());
        if (s == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float dist = (float) p.distanceTo(cam);
        if (dist < 0.5f) return;

        float world = Math.max(t.getBbHeight(), t.getBbWidth()) * 0.65f + 0.25f;
        float radius = Mth.clamp(Math.max(world, dist * MIN_RADIUS_PER_DIST),
                0f, dist * MAX_RADIUS_PER_DIST);

        // FIJANDO: sale más grande y se cierra con salida suave.
        float age = Mth.clamp((time - LockOnClientState.lockedAtTick()) / CLOSE_TICKS, 0f, 1f);
        float ease = 1f - (1f - age) * (1f - age) * (1f - age);
        radius *= 1f + 0.8f * (1f - ease);

        // PERDIENDO: se atenúa y parpadea despacio. Fijado: alfa estable.
        float alpha = opacity * (0.35f + 0.65f * ease);
        if (LockOnClientState.blockedTicks() > 0) {
            alpha *= 0.55f + 0.2f * Mth.sin(time * 0.5f);
        }
        int argb = argb(COLOR_LOCKED, alpha);
        float spin = time * (360f / RING_PERIOD_TICKS) + (1f - ease) * 120f;

        float px = radius * s[2];                 // radio en píxeles de GUI
        if (px < 4f) return;
        ringHalf(g, s[0], s[1], px, spin, 0, 0, argb);        // anillo
        ringHalf(g, s[0], s[1], px, -spin * 1.5f, 64, 0, argb); // marcas internas, al revés y más rápido
    }

    private static void ringHalf(GuiGraphics g, float cx, float cy, float r, float deg, int u, int v, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float red = ((argb >> 16) & 0xFF) / 255f, gr = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(deg));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(red, gr, b, a);
        int size = Math.round(r * 2f);
        g.blit(TEX, -size / 2, -size / 2, size, size, u, v, 64, 64, TEX_SIZE, TEX_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    // ── HUD: flechas en el borde ──────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        float opacity = ClientConfig.lockOnMarkerOpacity();
        Minecraft mc = Minecraft.getInstance();
        if (opacity <= 0f || mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (!LockOnClientState.hasTarget() && LockOnClientState.alerts().isEmpty()) return;

        GuiGraphics g = e.getGuiGraphics();
        Camera cam = mc.gameRenderer.getMainCamera();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        float time = mc.level.getGameTime() + pt;
        if (LockOnClientState.hasTarget() && mc.screen == null) drawRing(g, mc, pt, opacity);

        // Fijado fuera de vista: flecha aguamarina que respira.
        LivingEntity t = LockOnClientState.target(mc);
        if (t != null && t.isAlive()) {
            drawEdgeArrow(g, mc, cam, LockOnClientState.aimPoint(t, pt),
                    argb(COLOR_LOCKED, opacity * (0.6f + 0.25f * Mth.sin(time * 0.2f))));
        }

        // Quien te tiene fijado, fuera de vista: flecha roja, algo más viva (es un aviso).
        for (int id : LockOnClientState.alerts()) {
            Entity a = mc.level.getEntity(id);
            if (a == null || !a.isAlive()) continue;
            drawEdgeArrow(g, mc, cam, a.getBoundingBox().getCenter(),
                    argb(COLOR_ALERT, opacity * (0.7f + 0.3f * Mth.sin(time * 0.45f))));
        }
    }

    /**
     * Dibuja una flecha en el borde de la pantalla hacia {@code world} si ese punto queda fuera
     * de ella. Si está a la vista no dibuja nada (ya lo cubre el anillo o la propia entidad).
     */
    private static void drawEdgeArrow(GuiGraphics g, Minecraft mc, Camera cam, Vec3 world, int argb) {
        drawEdgeArrow(g, mc, cam, world, argb, ARROW_SIZE);
    }

    /**
     * Misma flecha con tamaño propio (la reutilizan los indicadores de borde del Ki Sense).
     * @return true si dibujó (el punto estaba fuera de pantalla), para que quien la reparte no
     *         gaste su tope de flechas en lo que ya se ve.
     */
    static boolean drawEdgeArrow(GuiGraphics g, Minecraft mc, Camera cam, Vec3 world, int argb, int size) {
        Vec3 d = world.subtract(cam.getPosition());
        Vector3f look = cam.getLookVector(), left = cam.getLeftVector(), up = cam.getUpVector();
        double fz = d.x * look.x() + d.y * look.y() + d.z * look.z();
        double right = -(d.x * left.x() + d.y * left.y() + d.z * left.z());
        double upAmount = d.x * up.x() + d.y * up.y() + d.z * up.z();

        int w = g.guiWidth(), h = g.guiHeight();

        if (fz > 0.1) {
            // FOV vertical de las opciones (no cuenta los efectos de zoom de sprint/vuelo: el
            // margen del 0.92 absorbe esa diferencia).
            double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
            double nx = right / (fz * tanHalf * ((double) w / h));
            double ny = upAmount / (fz * tanHalf);
            if (Math.abs(nx) < 0.92 && Math.abs(ny) < 0.92) return false;
        }

        // Dirección en pantalla (y hacia abajo). Justo detrás, sin componente lateral, se elige
        // la derecha: es indiferente hacia qué lado girar.
        double sx = right, sy = -upAmount;
        double len = Math.hypot(sx, sy);
        if (len < 1.0e-4) { sx = 1; sy = 0; len = 1; }
        sx /= len;
        sy /= len;

        double hx = w / 2.0 - EDGE_MARGIN, hy = h / 2.0 - EDGE_MARGIN;
        double tx = Math.abs(sx) < 1.0e-6 ? Double.MAX_VALUE : hx / Math.abs(sx);
        double ty = Math.abs(sy) < 1.0e-6 ? Double.MAX_VALUE : hy / Math.abs(sy);
        double k = Math.min(tx, ty);
        float px = (float) (w / 2.0 + sx * k);
        float py = (float) (h / 2.0 + sy * k);
        float angle = (float) Math.atan2(sx, -sy);   // la flecha del PNG apunta ARRIBA

        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f, gr = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;

        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().mulPose(Axis.ZP.rotation(angle));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(r, gr, b, a);
        g.blit(TEX, -size / 2, -size / 2, size, size,
                ARROW_U, ARROW_V, ARROW_CELL, ARROW_CELL, TEX_SIZE, TEX_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        g.pose().popPose();
        return true;
    }

    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
