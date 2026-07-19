package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

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
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;

            int rgb = AuraClientState.resolveColor(p);
            Vec3 at = p.getPosition(pt);
            Motion mo = sampleMotion(p, at); // velocidad + alpha de suavizado, UNA vez por frame

            // Estela de vuelo (coordenadas de mundo, relativa a cámara — antes del translate).
            updateAndRenderFlightTrail(pose, buffers, p, at, camPos, rgb, mo);

            pose.pushPose();
            pose.translate(at.x - camPos.x, at.y - camPos.y, at.z - camPos.z);
            applyFlightTilt(pose, p, mo);
            drawAura(pose, buffers, rgb, AURA_SCALE, t, pt, p.getId());
            pose.popPose();
        }

        buffers.endBatch();
    }

    // ── Inclinación del aura en VUELO ─────────────────────────────────────────
    // El eje del aura (vertical por defecto) se inclina como ESTELA: opuesto a la
    // dirección de movimiento, solo mientras vuela (flag fly + en el aire). Suavizado
    // exponencial ~0.25 s; al frenar o aterrizar vuelve gradualmente a la vertical.

    private static final float TILT_TAU_SECONDS = 0.10f; // constante de tiempo del lerp
    private static final double TILT_MIN_SPEED  = 0.08;  // por debajo: se considera quieto
    private static final double TILT_FULL_SPEED = 0.60;  // a esta velocidad la estela es total

    /** Eje suavizado del aura por jugador + timestamp del último frame (nanos). */
    private static final Map<Integer, Vector3f> TILT_AXIS = new HashMap<>();
    private static final Map<Integer, Long> TILT_LAST_NANOS = new HashMap<>();

    /** Última posición vista por jugador (para velocidad REAL por delta de posición). */
    private static final Map<Integer, Vec3> TILT_LAST_POS = new HashMap<>();

    // ── ÁNGULOS DEL AURA POR ESTADO DE ANIMACIÓN DE VUELO ────────────────────
    // El aura ya no adivina por velocidad: copia la POSE de tus animaciones fly.*.
    // pitchDeg: grados desde la vertical HACIA EL FRENTE del cuerpo (negativo = hacia atrás).
    // sideDeg:  grados hacia la DERECHA del cuerpo (negativo = izquierda).
    // AJUSTA AQUÍ para cuadrar con tus anims (valores iniciales tomados del torso de cada una):
    private static float[] anglesFor(ZenkaiPalAnimations.FlyDir dir, boolean boosting) {
        if (dir == null) return new float[]{0f, 0f};
        return switch (dir) {
            case IDLE    -> new float[]{0f, 0f};
            case FORWARD -> boosting ? new float[]{90f, 0f}   // fly.forward_boost: torso 90
                    : new float[]{15f, 0f};  // fly.forward: limbs 15
            case BACK    -> new float[]{-15f, 0f};            // fly.back: -15
            case LEFT    -> new float[]{0f, -8f};             // fly.left: inclinación lateral leve
            case RIGHT   -> new float[]{0f, 8f};              // fly.right
            case UP      -> boosting ? new float[]{45f, 0f}   // fly.up_boost: torso 45
                    : new float[]{0f, 0f};   // fly.up: vertical (brazos fuera)
            case DOWN    -> boosting ? new float[]{135f, 0f}  // fly.down_boost: torso 135 (picada)
                    : new float[]{8f, 0f};   // fly.down: casi vertical
            case FORWARD_LEFT, BACK_RIGHT, FORWARD_RIGHT, BACK_LEFT -> null;
        };
    }

    /** Muestra de movimiento por frame: velocidad (bloques/tick) + alpha de suavizado. */
    private record Motion(Vec3 vel, float alpha, boolean flying) {}

    private static Motion sampleMotion(AbstractClientPlayer p, Vec3 at) {
        long now = System.nanoTime();
        Long last = TILT_LAST_NANOS.put(p.getId(), now);
        float dt = (last == null) ? 1f : Math.min(0.25f, (now - last) / 1.0e9f);
        float alpha = 1f - (float) Math.exp(-dt / TILT_TAU_SECONDS);

        // Velocidad REAL por delta de posición (getDeltaMovement no refleja todos los
        // modos de vuelo, p.ej. descender con ctrl+shift+WASD). En bloques/tick.
        Vec3 prev = TILT_LAST_POS.put(p.getId(), at);
        Vec3 vel = (prev == null || dt <= 1.0e-4f) ? Vec3.ZERO
                : at.subtract(prev).scale(1.0 / (dt * 20.0));

        boolean flying = PlayerStatsAttachment
                .get(p).isFlyEnabled() && !p.onGround();
        return new Motion(vel, alpha, flying);
    }

    private static void applyFlightTilt(PoseStack pose, AbstractClientPlayer p, Motion mo) {
        // Objetivo desde la TABLA de estados de animación (no desde la velocidad).
        Vector3f target = new Vector3f(0f, 1f, 0f);
        var fp = ClientZenkaiPalTick.flyPoseOf(p.getUUID());
        if (mo.flying() && fp.dir() != null) {
            float[] ang = anglesFor(fp.dir(), fp.boosting());
            float pitch = (float) Math.toRadians(ang[0]);
            float side  = (float) Math.toRadians(ang[1]);
            if (Math.abs(pitch) > 1.0e-3f || Math.abs(side) > 1.0e-3f) {
                // Frente/derecha del CUERPO (yaw interpolado del torso).
                float yaw = (float) Math.toRadians(
                        Mth.lerp(1f, p.yBodyRotO, p.yBodyRot));
                float sinY = (float) Math.sin(yaw), cosY = (float) Math.cos(yaw);
                Vector3f fwd = new Vector3f(-sinY, 0f, cosY);
                Vector3f rgt = new Vector3f(cosY, 0f, sinY);
                target.set(
                        fwd.x * (float) Math.sin(pitch) + rgt.x * (float) Math.sin(side),
                        (float) (Math.cos(pitch) * Math.cos(side)),
                        fwd.z * (float) Math.sin(pitch) + rgt.z * (float) Math.sin(side));
                if (target.lengthSquared() < 1.0e-4f) target.set(0f, 1f, 0f);
                target.normalize();
            }
        }

        Vector3f axis = TILT_AXIS.computeIfAbsent(p.getId(),
                k -> new Vector3f(0f, 1f, 0f));
        axis.lerp(target, mo.alpha());
        if (axis.lengthSquared() < 1.0e-4f) axis.set(0f, 1f, 0f);
        axis.normalize();

        if (axis.y > 0.9999f) return; // prácticamente vertical: sin rotación
        pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, axis.x, axis.y, axis.z));
    }

    /** Limpieza de estado de inclinación/estela de jugadores que ya no están. */
    public static void clearTilt(int playerId) {
        TILT_AXIS.remove(playerId);
        TILT_LAST_NANOS.remove(playerId);
        TILT_LAST_POS.remove(playerId);
        TRAILS.remove(playerId);
    }

    // ── Estela de VUELO (misma técnica que la de los proyectiles ki) ─────────
    private static final ResourceLocation TRAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/ki_trail.png");
    private static final int   TRAIL_MAX_POINTS = 16;
    private static final double TRAIL_MIN_STEP  = 0.15; // dist mínima entre puntos guardados
    private static final float TRAIL_WIDTH      = 0.75f;

    /** Historial de puntos (mundo, altura del pecho) por jugador. [0] = más reciente. */
    private static final Map<Integer, ArrayDeque<Vec3>> TRAILS =
            new HashMap<>();

    /** Velocidad mínima (bloques/tick) para que la estela EXISTA: en hover/idle se retrae. */
    private static final double TRAIL_MIN_SPEED = 0.18;

    private static void updateAndRenderFlightTrail(PoseStack pose, MultiBufferSource buffers,
                                                   AbstractClientPlayer p, Vec3 at,
                                                   Vec3 camPos, int rgb, Motion mo) {
        var trail = TRAILS.computeIfAbsent(p.getId(), k -> new ArrayDeque<Vec3>());
        Vec3 head = at.add(0, p.getBbHeight() * 0.5, 0);

        boolean moving = mo.flying() && mo.vel().length() > TRAIL_MIN_SPEED;
        if (moving) {
            if (trail.isEmpty() || trail.peekFirst().distanceTo(head) >= TRAIL_MIN_STEP) {
                trail.addFirst(head);
                while (trail.size() > TRAIL_MAX_POINTS) trail.pollLast();
            }
        } else if (!trail.isEmpty()) {
            // Idle/hover o sin vuelo: la cola se retrae hasta desaparecer (nada de blob quieto).
            trail.pollLast();
        }
        if (trail.size() < 2) return;

        List<Vec3> pts = new ArrayList<>(trail);
        pts.add(0, head); // cabeza interpolada pegada al jugador

        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z); // verts en coords de mundo
        VertexConsumer vc = buffers.getBuffer(
                RenderType.entityTranslucentEmissive(TRAIL_TEXTURE));
        PoseStack.Pose mat = pose.last();

        int n = pts.size();
        Vec3 prevL = null, prevR = null;
        float prevA = 0, prevV = 0;
        for (int i = 0; i < n; i++) {
            Vec3 pt2 = pts.get(i);
            Vec3 dir = (i < n - 1) ? pts.get(i + 1).subtract(pt2) : pt2.subtract(pts.get(i - 1));
            Vec3 side = dir.cross(camPos.subtract(pt2));
            side = side.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : side.normalize();

            float t2 = 1f - (float) i / (n - 1);          // 1 cabeza -> 0 cola
            float half = TRAIL_WIDTH * 0.5f * (0.25f + 0.75f * t2);
            float alpha = 0.85f * t2;
            float v = (float) i / (n - 1);

            Vec3 vL = pt2.add(side.scale(half));
            Vec3 vR = pt2.subtract(side.scale(half));

            if (i > 0) {
                trailQuad(vc, mat, prevL, prevR, vR, vL, r, g, b, prevA, alpha, prevV, v);
                trailQuad(vc, mat, vL, vR, prevR, prevL, r, g, b, alpha, prevA, v, prevV);
            }
            prevL = vL; prevR = vR; prevA = alpha; prevV = v;
        }
        pose.popPose();
    }

    private static void trailQuad(VertexConsumer vc, PoseStack.Pose mat,
                                  Vec3 aL, Vec3 aR, Vec3 bR, Vec3 bL,
                                  float r, float g, float b,
                                  float aAlpha, float bAlpha, float aV, float bV) {
        trailVert(vc, mat, aL, r, g, b, aAlpha, 0, aV);
        trailVert(vc, mat, aR, r, g, b, aAlpha, 1, aV);
        trailVert(vc, mat, bR, r, g, b, bAlpha, 1, bV);
        trailVert(vc, mat, bL, r, g, b, bAlpha, 0, bV);
    }

    private static void trailVert(VertexConsumer vc, PoseStack.Pose mat, Vec3 v,
                                  float r, float g, float b, float a, float u, float tv) {
        vc.addVertex(mat, (float) v.x, (float) v.y, (float) v.z)
                .setColor(r, g, b, a).setUv(u, tv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(mat, 0, 1, 0);
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