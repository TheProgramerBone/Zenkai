package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.render_and_model_entities.entity.KiVfxProjectileRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxCompositeRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxDebugMode;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxGeometry;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxMesh;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxShape;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxTuning;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.TechniquePosition;
import com.hmc.zenkai.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Bola de ki mientras se carga una técnica, en el punto que diga su TechniquePosition (mano,
 * boca, frente...). La ve el resto, no solo quien carga: los datos llegan por
 * {@code KiChargeStatePacket} y el crecimiento se deriva del tick de inicio.
 *
 * EL CUERPO SALE DE {@link KiVfxCompositeRenderer}, el mismo que dibuja el proyectil ya disparado
 * — con la misma malla+shader, cargar y disparar son el MISMO cuerpo a distinto tamaño, y una
 * esfera real (no un billboard) se lee bien desde cualquier ángulo, incluido "un palmo de la
 * cámara".
 *
 * LA MALLA ES CASI SIEMPRE UNA ESFERA DESNUDA ({@link KiVfxGeometry#chargeSphere()}), aunque la
 * técnica dispare un haz: cargando, la energía todavía no tiene forma. Color, bandas y alfas SÍ
 * salen del {@link KiVfxProfile} real de la técnica.
 *
 * DISK ES LA ÚNICA EXCEPCIÓN (ver {@link #drawBall}): un disco ya se reconoce como disco desde
 * que se condensa, así que usa su malla real desde el primer frame de carga.
 *
 * Toda la lógica de anclaje (huesos, respaldo de primera persona sin dato de hueso, tamaños
 * anticipados de técnicas grandes, aire ante la cámara) es GAMEPLAY/UX ya calibrada a ojo en
 * juego y AJENA al rework de VFX — se porta sin tocarla, solo se cambia CÓMO se pinta el cuerpo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiVfxChargeRenderer {
    private KiVfxChargeRenderer() {}

    private static final int FULL_BRIGHT = 0xF000F0;

    private static final float BASE_RADIUS = 0.16f;
    private static final float SIZE_RADIUS = 0.05f;
    private static final float START_SCALE = 0.35f;
    private static final float START_RADIUS_CAP = 0.10f;
    private static final float PLAYER_RADIUS = 0.90f;
    private static final float BARRIER_CHARGE_RADIUS = 1.0f;

    private static float targetRadius(KiChargeClientState.Charge c) {
        if (c.type() == KiTechniqueType.BARRIER) {
            return BARRIER_CHARGE_RADIUS;
        }
        if (c.type().chargeShowsRealSize()) {
            return (float) (c.type().projectileSize(c.size()) * 1.5 * 0.5);
        }
        return BASE_RADIUS + SIZE_RADIUS * c.size();
    }

    private static float chargeRadius(KiChargeClientState.Charge c, float progress) {
        float target = targetRadius(c);
        if (c.type() == KiTechniqueType.EXPLOSION) {
            return Mth.lerp(progress, PLAYER_RADIUS, Math.max(PLAYER_RADIUS, target));
        }
        float start = Math.min(target * START_SCALE, Math.min(START_RADIUS_CAP, target));
        return Mth.lerp(progress, start, target);
    }

    private static final float FP_FORWARD = 0.35f;
    private static final float FP_SIDE    = 0.10f;
    private static final float FP_DOWN    = 0.25f;
    private static final double FP_CLEARANCE = 0.35;
    private static final float REST_ON_ANCHOR_RADIUS = 0.45f;

    /**
     * ¿Esta técnica se OCULTA POR COMPLETO en primera persona propia, en TODAS sus fases (carga,
     * activa/disparada — ver KiVfxProjectileRenderer —, y el desvanecido al soltar)? Antes solo
     * EXPLOSION estaba aquí; BARRIER se sumó 2026-09-24 a petición explícita del usuario tras
     * confirmar en vídeo que, lejos de leerse como "una burbuja pequeña de energía" (la intención
     * original del 35% de {@link KiVfxProfile#firstPersonOpacity}), la cáscara/halo/bloom de una
     * esfera de hasta {@link #BARRIER_CHARGE_RADIUS} bloques CENTRADA EN LA CABEZA del jugador
     * terminaba tapando la pantalla entera — con la cámara pegada o dentro de su propia
     * geometría, ni una opacidad reducida ni el culling de cara trasera bastan para que se lea
     * como "pequeña". Simplifica el trato: BARRIER, igual que EXPLOSION, no se dibuja NUNCA en tu
     * propia vista en primera persona, sea cual sea la fase — el resto de jugadores la siguen
     * viendo con normalidad (esto solo afecta a `selfFirstPerson`).
     */
    private static boolean hidesFullyInFirstPerson(KiTechniqueType type) {
        return type == KiTechniqueType.EXPLOSION || type == KiTechniqueType.BARRIER;
    }

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
        // DIBUJADO DIFERIDO (ver KiVfxFrameQueue): aquí se calcula el anclaje y se encola;
        // KiVfxBloomPipeline (prioridad LOWEST en este mismo stage) lo reproduce para el mundo y
        // para el bloom. rememberDrawn y las partículas se quedan fuera: pasan una vez por frame.
        for (Player p : mc.level.players()) {
            KiChargeClientState.Charge c = KiChargeClientState.of(p);
            if (c == null) continue;

            KiVfxProfile v = KiVfxProfile.of(c.type());
            boolean selfFirstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            float fpOpacity = selfFirstPerson
                    ? (hidesFullyInFirstPerson(c.type())
                            ? 0f : v.firstPersonOpacity(ClientConfig.kiFirstPersonOpacityFrac()))
                    : 1f;

            float progress = KiChargeClientState.progress(c, now);

            var anchors = PlayerHandTracker.get(p.getId());
            Vec3 origin;
            if (c.type() == KiTechniqueType.EXPLOSION) {
                origin = p.getPosition(pt).add(0, p.getBbHeight() * 0.5, 0);
            } else if (anchors != null) {
                origin = camPos.add(anchors.resolve(c.position()));
            } else if (selfFirstPerson) {
                float bodyYaw = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot);
                double rad = Math.toRadians(bodyYaw);
                Vec3 look = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
                origin = c.position().origin(p, p.getPosition(pt), look);

                if (isHandAnchored(c.position())) {
                    Vec3 right = new Vec3(-look.z, 0.0, look.x);
                    origin = origin.add(look.scale(FP_FORWARD))
                            .add(right.scale(FP_SIDE))
                            .subtract(0.0, FP_DOWN, 0.0);
                }
            } else {
                origin = c.position().origin(p, p.getPosition(pt));
            }

            float radius = chargeRadius(c, progress)
                    * (1f + 0.05f * (float) Math.sin((now + pt) * 0.4));

            boolean fpFallback = selfFirstPerson && anchors == null;
            if (c.type() != KiTechniqueType.EXPLOSION && !fpFallback) {
                origin = origin.add(0, Math.max(0f, radius - REST_ON_ANCHOR_RADIUS), 0);
            }
            if (selfFirstPerson && c.type().chargeShowsRealSize()) {
                origin = pushOutOfCamera(camPos, origin, radius);
            }

            float r = ((c.rgb() >> 16) & 0xFF) / 255f;
            float g = ((c.rgb() >> 8) & 0xFF) / 255f;
            float b = (c.rgb() & 0xFF) / 255f;

            KiChargeClientState.rememberDrawn(p.getId(), origin, radius);

            if (KiVfxDebugMode.current().showsParticles()) {
                spawnChargeArcs(p.getId(), origin, radius, c.rgb(), v.particles().chargeSparkRate(), now);
            }

            if (fpOpacity > 0f) {
                Vec3 look = (v.shape() == KiVfxShape.DISK) ? p.getViewVector(pt) : null;
                final Vec3 o = origin;
                final float rad = radius;
                KiVfxFrameQueue.submit(pose, o.subtract(camPos), (ps, buf) -> drawBall(ps, buf, c.rgb2(), cam, camPos, o, v, rad,
                        fpOpacity, r, g, b, now, pt, look, selfFirstPerson));
            }
        }
        // Esferas apagándose: sitio y tamaño congelados, alfa bajando.
        for (Player p : mc.level.players()) {
            var f = KiChargeClientState.fadeOf(p);
            if (f == null) continue;

            float a = KiChargeClientState.fadeAlpha(f, now, pt);
            if (a <= 0f) { KiChargeClientState.dropFade(p.getId()); continue; }

            KiVfxProfile v = KiVfxProfile.of(f.type());
            boolean selfFirstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            float alpha = (selfFirstPerson && hidesFullyInFirstPerson(f.type())) ? 0f
                    : a * (selfFirstPerson
                            ? v.firstPersonOpacity(ClientConfig.kiFirstPersonOpacityFrac()) : 1f);
            float r = ((f.rgb() >> 16) & 0xFF) / 255f;
            float g = ((f.rgb() >> 8) & 0xFF) / 255f;
            float b = (f.rgb() & 0xFF) / 255f;
            float radius = f.radius() * (0.6f + 0.4f * a);

            if (alpha > 0f) {
                KiVfxFrameQueue.submit(pose, f.origin().subtract(camPos), (ps, buf) -> drawBall(ps, buf, f.rgb2(), cam, camPos, f.origin(), v,
                        radius, alpha, r, g, b, now, pt, null, selfFirstPerson));
            }
        }
    }

    private static final Map<Integer, Long> lastArcTick = new HashMap<>();

    private static void spawnChargeArcs(int playerId, Vec3 origin, float radius, int rgb,
                                        float rate, long now) {
        if (rate <= 0f) return;
        Long last = lastArcTick.get(playerId);
        if (last != null && last == now) return;
        lastArcTick.put(playerId, now);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var rnd = mc.level.random;

        int count = (int) rate;
        if (rnd.nextFloat() < rate - count) count++;
        for (int i = 0; i < count; i++) {
            double theta = rnd.nextDouble() * Math.PI * 2.0;
            double z = rnd.nextDouble() * 2.0 - 1.0;
            double planar = Math.sqrt(Math.max(0.0, 1.0 - z * z));
            mc.level.addParticle(ModParticles.arc(rgb, Math.min(0.65f, 0.20f + 0.10f * radius)),
                    origin.x + planar * Math.cos(theta) * radius,
                    origin.y + z * radius,
                    origin.z + planar * Math.sin(theta) * radius,
                    0, 0, 0);
        }
    }

    private static Vec3 pushOutOfCamera(Vec3 camPos, Vec3 origin, float radius) {
        Vec3 fromCam = origin.subtract(camPos);
        double dist = fromCam.length();
        double minDist = radius + FP_CLEARANCE;
        if (dist >= minDist || dist < 1.0E-4) return origin;
        return camPos.add(fromCam.scale(minDist / dist));
    }

    private static boolean isHandAnchored(TechniquePosition pos) {
        return switch (pos) {
            case RIGHT_HAND, LEFT_HAND, BOTH_HANDS -> true;
            case MOUTH, FOREHEAD, EYES -> false;
        };
    }

    /** Cuerpo (esfera real, no billboard, salvo DISK con `look`) + halo. */
    private static void drawBall(PoseStack pose, MultiBufferSource.BufferSource buffers, int rgb2,
                                 Camera cam, Vec3 camPos, Vec3 origin, KiVfxProfile v, float radius,
                                 float alphaMul, float r, float g, float b, long now, float pt,
                                 @Nullable Vec3 look, boolean selfFirstPerson) {
        float size = radius * 2f;
        com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors.begin(rgb2);

        boolean asDisk = v.shape() == KiVfxShape.DISK && look != null;
        KiVfxMesh mesh = asDisk ? KiVfxGeometry.shell(v) : KiVfxGeometry.chargeSphere();

        pose.pushPose();
        pose.translate(origin.x - camPos.x, origin.y - camPos.y, origin.z - camPos.z);
        if (asDisk) {
            pose.mulPose(Axis.YP.rotationDegrees(
                    (float) (Math.atan2(look.x, look.z) * 180.0 / Math.PI)));
            pose.mulPose(Axis.XP.rotationDegrees(
                    (float) (-Math.asin(look.y) * 180.0 / Math.PI)));
            // KiVfxProjectileRenderer.DISK_CANT_DEGREES: UNA sola constante para las dos vistas
            // (carga y vuelo) — antes de esta reconstrucción cada renderer llevaba su propia
            // copia del mismo número, con el riesgo explícito de que se desincronizaran.
            pose.mulPose(Axis.XP.rotationDegrees(KiVfxProjectileRenderer.DISK_CANT_DEGREES));
            pose.mulPose(Axis.ZP.rotationDegrees((now + pt) * 22f));
        }
        float proximity = KiVfxRenderTypes.proximity(camPos.distanceTo(origin), radius);
        KiVfxMesh coreMesh = KiVfxGeometry.core(v, asDisk);
        if (KiVfxDebugMode.current().showsShell() || KiVfxDebugMode.current().showsCore()) {
            KiVfxCompositeRenderer.render(buffers, v, mesh, coreMesh, pose, size, r, g, b, alphaMul, proximity);
        }
        pose.popPose();

        if (!KiVfxDebugMode.current().showsHalo() || !v.halo().enabled()) return;
        float haloOpacity = selfFirstPerson ? ClientConfig.kiFirstPersonOpacityFrac() : 1f;
        if (haloOpacity <= 0f) return;
        float pulse = 1f + 0.07f * Mth.sin((now + pt) * 0.22f);
        float half = radius * v.halo().scale() * pulse;
        float camFade = KiVfxRenderTypes.billboardCameraFade(camPos.distanceTo(origin), half);
        if (camFade <= 0f) return;

        pose.pushPose();
        pose.translate(origin.x - camPos.x, origin.y - camPos.y, origin.z - camPos.z);
        pose.mulPose(cam.rotation());
        PoseStack.Pose mat = pose.last();
        VertexConsumer vc = buffers.getBuffer(KiVfxRenderTypes.glow(KiVfxRenderTypes.HALO_TEXTURE));
        float a = v.halo().alpha() * haloOpacity * camFade
                * KiVfxTuning.get(KiVfxTuning.Param.HALO_ALPHA_MUL, v);
        vert(vc, mat, -half, -half, 0f, 1f, r, g, b, a);
        vert(vc, mat,  half, -half, 1f, 1f, r, g, b, a);
        vert(vc, mat,  half,  half, 1f, 0f, r, g, b, a);
        vert(vc, mat, -half,  half, 0f, 0f, r, g, b, a);
        pose.popPose();
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose m, float x, float y,
                             float u, float v, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, 0f)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(m, 0, 0, 1);
    }
}
