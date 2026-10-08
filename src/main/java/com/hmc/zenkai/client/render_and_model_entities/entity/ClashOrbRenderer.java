package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxCompositeRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxDebugMode;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxGeometry;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.compat.IrisCompat;
import com.hmc.zenkai.content.entity.technique.ClashOrbEntity;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Esfera de choque en el mundo ({@link ClashOrbEntity}): DOS lóbulos, uno por haz y cada uno de
 * su color, empujándose, más un núcleo blanco en el punto de contacto. Se dibuja con el MISMO
 * cuerpo que una bola de ki (perfil BLAST de {@link KiVfxProfile}, {@link KiVfxCompositeRenderer})
 * y por la cola diferida ({@link KiVfxFrameQueue}), así que entra en el bloom y en el orden de
 * atrás hacia delante como cualquier técnica.
 *
 * Animación, sacada solo de datos sincronizados (tickCount y {@link ClashOrbEntity#resolvedAt()}):
 * - Contacto: crece de 0 a su tamaño en {@link #GROW_TICKS}.
 * - Minijuego: late y tiembla EN EL SITIO, igual para los dos lóbulos. No se inclina hacia nadie
 *   — ni los participantes saben aún quién gana (regla del choque, ver combate-y-muerte.md).
 * - Resolución: el lóbulo ganador crece y empuja la esfera hacia el perdedor, cuyo lóbulo se
 *   encoge; en el empate los dos se comprimen y estallan (la detonación real la pone el servidor).
 *   Al soltarse el golpe, el servidor retira la esfera: el haz ganador la «absorbe».
 */
public class ClashOrbRenderer extends EntityRenderer<ClashOrbEntity> {

    private static final float GROW_TICKS = 8f;
    private static final float RESOLVE_TICKS = 14f;
    /** Centro de cada lóbulo desde el punto de contacto, en RADIOS de ese lóbulo: por debajo de
     *  1 se solapan en el centro, donde se empujan. Antes los dos lóbulos se solapaban casi
     *  enteros y, con el núcleo blanco y el bloom, se leían como una sola mancha blanca. */
    private static final float LOBE_PUSH = 0.62f;
    /** Núcleo blanco del contacto, respecto al lóbulo más pequeño, y su alfa: un destello en la
     *  junta, no una tercera bola que tape los colores. */
    private static final float CORE_SIZE = 0.28f;
    private static final float CORE_ALPHA = 0.7f;
    /** Cada lóbulo es MÁS ancho que la cabeza de su haz: con el mismo diámetro que el chorro se
     *  fundía con el cuerpo del haz anclado y la junta se leía como un tubo continuo, no como una
     *  esfera (capturas 2026-10-08). El bulto en el punto de choque es lo que la hace legible. */
    private static final float LOBE_BULGE = 1.3f;
    /** Temblor en el sitio, en fracciones del diámetro medio. */
    private static final float SHAKE = 0.035f;

    public ClashOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ClashOrbEntity orb, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffer, int packedLight) {
        if (IrisCompat.renderingShadowPass()) return;

        float t = orb.tickCount + partialTick;
        float sizeA = orb.sizeA() * LOBE_BULGE, sizeB = orb.sizeB() * LOBE_BULGE;
        float size = 0.5f * (sizeA + sizeB);
        Vector3f ax = orb.axis();
        Vec3 axis = new Vec3(ax.x(), ax.y(), ax.z());
        // Dos perpendiculares al eje para el temblor (que no empuja a lo largo del eje).
        Vec3 ref = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 side = axis.cross(ref).normalize();
        Vec3 up = side.cross(axis).normalize();

        float grow = smooth(Mth.clamp(t / GROW_TICKS, 0f, 1f));
        float pulse = 1f + 0.06f * Mth.sin(t * 1.7f) + 0.03f * Mth.sin(t * 4.3f + 0.8f);
        // Escala y desplazamiento de cada lóbulo (A en +eje, B en −eje) y del conjunto.
        float scaleA = 1f, scaleB = 1f, coreScale = 1f, alpha = 1f;
        double shift = 0.0;

        int resolvedAt = orb.resolvedAt();
        byte outcome = orb.outcome();
        if (resolvedAt >= 0 && outcome != ClashOrbEntity.OUTCOME_NONE) {
            float k = Mth.clamp((t - resolvedAt) / RESOLVE_TICKS, 0f, 1f);
            if (outcome == ClashOrbEntity.OUTCOME_TIE) {
                // Se comprime y luego revienta hacia fuera desvaneciéndose.
                float s = k < 0.7f ? 1f - 0.4f * (k / 0.7f) : 0.6f + 1.4f * ((k - 0.7f) / 0.3f);
                scaleA = scaleB = s;
                coreScale = s * (1f + k);
                alpha = k < 0.7f ? 1f : 1f - (k - 0.7f) / 0.3f;
            } else {
                boolean aWins = outcome == ClashOrbEntity.OUTCOME_A;
                float win = 1f + 0.35f * smooth(k), lose = 1f - 0.75f * smooth(k);
                scaleA = aWins ? win : lose;
                scaleB = aWins ? lose : win;
                // Hacia el perdedor: si gana A, hacia −eje (lado de B).
                shift = (aWins ? -1.0 : 1.0) * 0.6 * (aWins ? sizeB : sizeA) * k * k;
            }
        }

        // Temblor: dos senos por perpendicular, de frecuencias no múltiplos; se apaga al resolver.
        float shakeAmp = SHAKE * size * (resolvedAt >= 0 ? 0.3f : 1f) * grow;
        Vec3 jitter = side.scale(shakeAmp * (Mth.sin(t * 2.9f) + 0.5f * Mth.sin(t * 7.3f)))
                .add(up.scale(shakeAmp * (Mth.sin(t * 3.7f + 1.1f) + 0.5f * Mth.sin(t * 6.1f))));
        Vec3 center = axis.scale(shift).add(jitter);

        float k0 = grow * pulse;
        float lobeA = sizeA * k0 * scaleA;
        float lobeB = sizeB * k0 * scaleB;
        Vec3 posA = center.add(axis.scale(0.5f * lobeA * LOBE_PUSH));
        Vec3 posB = center.subtract(axis.scale(0.5f * lobeB * LOBE_PUSH));
        float coreD = Math.min(sizeA, sizeB) * k0 * CORE_SIZE * coreScale;
        float base = size * k0;

        final float fAlpha = alpha;
        Vec3 world = orb.getPosition(partialTick);
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        Vec3 fromCamera = world.subtract(cam);
        KiVfxFrameQueue.submit(pose, fromCamera, (p, buf) -> {
            KiVfxProfile v = KiVfxProfile.of(KiTechniqueType.BLAST);
            // El lóbulo más lejano primero: los dos son translúcidos y el de delante debe tapar.
            boolean aFirst = world.add(posA).distanceToSqr(cam) > world.add(posB).distanceToSqr(cam);
            if (aFirst) {
                drawLobe(p, buf, v, posA, lobeA, orb.rgbA(), orb.rgb2A(), fAlpha, world, cam);
                drawLobe(p, buf, v, posB, lobeB, orb.rgbB(), orb.rgb2B(), fAlpha, world, cam);
            } else {
                drawLobe(p, buf, v, posB, lobeB, orb.rgbB(), orb.rgb2B(), fAlpha, world, cam);
                drawLobe(p, buf, v, posA, lobeA, orb.rgbA(), orb.rgb2A(), fAlpha, world, cam);
            }
            // Núcleo blanco donde se empujan: blanco puro con su capa caliente también blanca.
            drawLobe(p, buf, v, center, coreD, 0xFFFFFF, 0xFFFFFF, fAlpha * CORE_ALPHA, world, cam);
        });

        if (KiVfxDebugMode.current().showsParticles() && orb.tickCount != orb.lastFxTick) {
            orb.lastFxTick = orb.tickCount;
            spawnFx(orb, world.add(center), axis, base * 0.5f, resolvedAt >= 0);
        }
    }

    private static void drawLobe(PoseStack pose, MultiBufferSource.BufferSource buf, KiVfxProfile v,
                                 Vec3 offset, float diameter, int rgb, int rgb2, float alpha,
                                 Vec3 world, Vec3 cam) {
        if (diameter <= 0.01f || alpha <= 0.01f) return;
        KiVfxColors.begin(rgb2);
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float proximity = KiVfxRenderTypes.proximity(cam.distanceTo(world.add(offset)),
                v.worldRadius(diameter));
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        KiVfxCompositeRenderer.render(buf, v, KiVfxGeometry.shell(v), KiVfxGeometry.core(v, true),
                pose, diameter, r, g, b, alpha, proximity);
        pose.popPose();
    }

    /** Arcos y chispas en el plano de contacto, alternando el color de cada haz. Una vez por tick. */
    private static void spawnFx(ClashOrbEntity orb, Vec3 at, Vec3 axis, float radius, boolean resolved) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var rnd = level.random;
        int arcs = resolved ? 1 : (orb.tickCount % 2 == 0 ? 2 : 1);
        for (int i = 0; i < arcs; i++) {
            // Dirección aleatoria PERPENDICULAR al eje: los arcos salen del plano donde chocan.
            Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian());
            d = d.subtract(axis.scale(d.dot(axis)));
            if (d.lengthSqr() < 1.0e-6) continue;
            d = d.normalize();
            int rgb = rnd.nextBoolean() ? orb.rgbA() : orb.rgbB();
            Vec3 p = at.add(d.scale(radius * 0.6));
            level.addParticle(ModParticles.arc(rgb, 0.18f + radius * 0.08f), p.x, p.y, p.z,
                    d.x * 0.3, d.y * 0.3, d.z * 0.3);
        }
        if (orb.tickCount % 3 == 0) {
            Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
            int rgb = rnd.nextBoolean() ? orb.rgbA() : orb.rgbB();
            level.addParticle(ModParticles.spark(rgb, 0.8f), at.x, at.y, at.z,
                    d.x * 0.25, d.y * 0.25, d.z * 0.25);
        }
    }

    private static float smooth(float k) { return k * k * (3f - 2f * k); }

    @Override
    public ResourceLocation getTextureLocation(ClashOrbEntity entity) {
        return KiVfxRenderTypes.BALL_TEXTURE;
    }
}
