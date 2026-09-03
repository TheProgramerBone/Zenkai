package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.aura.AuraSkirts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Ascuas de fuego con TEXTURA PROPIA (aura_ember.png, ver tools/gen_aura_particles.py) —
 * activadas por AuraModifier.fireEmbers (hoy solo ascension.json, SSJG). Mismo patrón de
 * ciclo de vida que AuraWispRenderer (nace, sube, muere; sobrevive aunque el aura se
 * apague, se limpia al dejar de trackear), pero con su propia hoja e independiente del
 * conteo de wisps del perfil — es una capa de identidad de forma, no un canal de poder.
 *
 * TERCER INTENTO para "dar sensación de fuego" a SSJG: un pase aditivo sobre el propio
 * cono (revertido, lavaba al jugador a blanco) y partículas vanilla ParticleTypes.FLAME
 * (revertidas, estilo pixel-art vainilla fuera de lugar contra el aura translúcida del
 * mod) se probaron antes y no funcionaron. ADITIVO aquí es más seguro que el intento del
 * cono: son quads pequeños y dispersos en un radio alrededor del cuerpo, nunca
 * enveloping la silueta entera.
 */
public final class AuraEmberRenderer {
    private AuraEmberRenderer() {}

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/aura_ember.png");

    private static final float ALPHA_MUL = 0.95f;
    private static final int MAX_PER_PLAYER = 20;
    /** Una ascua nueva cada N steps del reloj de faldón del propio perfil — igual de
     *  reactivo a turbulencia/presencia que el resto del aura sin inventar un reloj más. */
    private static final int SPAWN_EVERY_STEPS = 2;

    private static final class Ember {
        double x, y, z, vx, vy, vz;
        float age, life, size, r, g, b;
        boolean mirror;
    }

    private static final Random RNG = new Random();
    private static final Map<Integer, List<Ember>> EMBERS = new HashMap<>();
    private static final Map<Integer, Integer> LAST_STEP = new HashMap<>();
    private static long lastSimTick = Long.MIN_VALUE;

    public static void clear(int playerId) {
        EMBERS.remove(playerId);
        LAST_STEP.remove(playerId);
    }

    public static void spawn(AbstractClientPlayer p, Vec3 at, AuraSkirts.Plan plan, long t) {
        if (!plan.profile().fireEmbers()) return;

        int step = (int) (t / plan.profile().frameTicks() / SPAWN_EVERY_STEPS);
        Integer prev = LAST_STEP.put(p.getId(), step);
        if (prev != null && prev == step) return;

        List<Ember> list = EMBERS.computeIfAbsent(p.getId(), k -> new ArrayList<>());
        if (list.size() >= MAX_PER_PLAYER) return;

        float eff = AuraSkirts.AURA_SCALE * plan.profile().size();
        double ang = RNG.nextDouble() * Math.PI * 2.0;
        double rad = (0.20 + 0.35 * RNG.nextDouble()) * eff;
        double y0 = (0.10 + 0.55 * RNG.nextDouble()) * eff;

        int rgb = (plan.hasOuter() && RNG.nextBoolean()) ? plan.outerColor() : plan.innerColor();

        Ember e = new Ember();
        e.x = at.x + Math.sin(ang) * rad;
        e.y = at.y + y0;
        e.z = at.z + Math.cos(ang) * rad;
        e.vx = Math.sin(ang) * 0.010;
        e.vz = Math.cos(ang) * 0.010;
        e.vy = 0.065 + 0.055 * RNG.nextDouble();
        e.life = 10f + RNG.nextInt(8);
        e.size = (0.30f + 0.22f * RNG.nextFloat()) * eff;
        e.mirror = RNG.nextBoolean();
        e.r = AuraQuads.red(rgb);
        e.g = AuraQuads.green(rgb);
        e.b = AuraQuads.blue(rgb);
        list.add(e);
    }

    public static void renderAll(PoseStack pose, MultiBufferSource buffers, Camera cam,
                                 Vec3 camPos, long t, float pt) {
        long dt = (lastSimTick == Long.MIN_VALUE) ? 0
                : Math.min(10, Math.max(0, t - lastSimTick));
        lastSimTick = t;
        if (EMBERS.isEmpty()) return;

        if (dt > 0) {
            for (Iterator<Map.Entry<Integer, List<Ember>>> it = EMBERS.entrySet().iterator();
                 it.hasNext(); ) {
                List<Ember> list = it.next().getValue();
                for (Iterator<Ember> ei = list.iterator(); ei.hasNext(); ) {
                    Ember e = ei.next();
                    e.age += dt;
                    if (e.age >= e.life) { ei.remove(); continue; }
                    e.x += e.vx * dt;
                    e.y += e.vy * dt;
                    e.z += e.vz * dt;
                }
                if (list.isEmpty()) it.remove();
            }
            if (EMBERS.isEmpty()) return;
        }

        VertexConsumer vc = buffers.getBuffer(ModAuraRenderType.energyAdditive(TEXTURE));
        float yaw = -cam.getYRot();

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);
        for (List<Ember> list : EMBERS.values()) {
            for (Ember e : list) {
                float lf = 1f - Math.min(1f, (e.age + pt) / e.life);
                float fade = lf * lf * (3f - 2f * lf);
                float sz = e.size * (0.5f + 0.5f * lf);
                float a = AuraSkirts.BASE_ALPHA * ALPHA_MUL * fade;

                pose.pushPose();
                pose.translate(e.x + e.vx * pt, e.y + e.vy * pt, e.z + e.vz * pt);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                AuraQuads.planeFull(vc, pose.last(), sz, sz, e.mirror, 0f, e.r, e.g, e.b, a);
                pose.popPose();
            }
        }
        pose.popPose();
    }
}
