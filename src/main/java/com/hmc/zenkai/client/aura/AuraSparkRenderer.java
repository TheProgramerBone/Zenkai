package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.feature.aura.AuraSkirts;
import com.hmc.zenkai.feature.aura.AuraTuning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Chispas: destellos cortos y violentos que saltan alrededor del aura.
 * FRECUENCIA = sparksPerSecond del perfil = 14/s × presence × tension × (1 + 2.5·kI).
 * O sea: solo chispea quien tiene poder Y está cerca de su límite. Un maestro al 100%
 * con control 10 chispea poco porque su tensión es baja; el mismo poder con control 2
 * chispea mucho. Y el Kaioken lo multiplica por 3.5 sin tocar el tamaño del aura — que
 * es exactamente su firma: no crece, se descontrola.
 * Las chispas sobreviven hasta la banda FAR a propósito: cuando el aura ya no se
 * distingue, siguen diciendo "ese está forzando algo".
 * TODO(assets): hoy usan el cuadrante de penacho de la hoja de llamas, muy estirado y
 * corto de vida, que da un destello afilado aceptable. Cuando existan hojas propias de
 * rayo (rayo_0..2), sustituir la celda y subir la vida: para los rayos GRANDES el plan
 * es geometría segmentada, no billboards.
 */
public final class AuraSparkRenderer {
    private AuraSparkRenderer() {}

    private static final float ALPHA_MUL = 1.35f;
    private static final int MAX_PER_PLAYER = 32;

    private static final class Spark {
        double x, y, z, vx, vy, vz;
        float age, life, w, h, roll, r, g, b;
        boolean mirror;
    }

    private static final Random RNG = new Random();
    private static final Map<Integer, List<Spark>> SPARKS = new HashMap<>();
    /** Fracción de chispa acumulada por jugador: permite frecuencias por debajo de 1/tick. */
    private static final Map<Integer, Float> CARRY = new HashMap<>();
    private static long lastSimTick = Long.MIN_VALUE;

    public static void clear(int playerId) {
        SPARKS.remove(playerId);
        CARRY.remove(playerId);
    }

    /** Acumula el ritmo del perfil y suelta las chispas que toquen este tick. */
    public static void spawn(AbstractClientPlayer p, Vec3 at, AuraSkirts.Plan plan,
                             long t, long dtTicks) {
        float perSecond = plan.profile().sparksPerSecond();
        if (perSecond <= 0.01f || dtTicks <= 0) return;

        float carry = CARRY.getOrDefault(p.getId(), 0f) + perSecond * (dtTicks / 20f);
        int n = (int) carry;
        CARRY.put(p.getId(), carry - n);
        if (n <= 0) return;

        List<Spark> list = SPARKS.computeIfAbsent(p.getId(), k -> new ArrayList<>());
        float eff = AuraSkirts.AURA_SCALE * plan.profile().size();

        for (int i = 0; i < n && list.size() < MAX_PER_PLAYER; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2.0;
            double rad = (0.30 + 0.45 * RNG.nextDouble()) * eff;
            double y0 = (0.35 + 1.75 * RNG.nextDouble()) * eff;

            int rgb = (plan.hasOuter() && RNG.nextBoolean())
                    ? plan.outerColor()
                    : (plan.hasCore() ? plan.coreColor() : plan.innerColor());

            Spark s = new Spark();
            s.x = at.x + Math.sin(ang) * rad;
            s.y = at.y + y0;
            s.z = at.z + Math.cos(ang) * rad;
            // Salen hacia fuera y algo arriba: fuga de energía, no ascenso tranquilo.
            s.vx = Math.sin(ang) * (0.05 + 0.09 * RNG.nextDouble());
            s.vz = Math.cos(ang) * (0.05 + 0.09 * RNG.nextDouble());
            s.vy = 0.02 + 0.11 * RNG.nextDouble();
            s.life = 2f + RNG.nextInt(4);            // 2-5 ticks: destello, no llama
            s.w = (0.05f + 0.05f * RNG.nextFloat()) * eff;
            s.h = (0.55f + 0.65f * RNG.nextFloat()) * eff;
            s.roll = RNG.nextFloat() * 360f;
            s.mirror = RNG.nextBoolean();
            s.r = AuraQuads.red(rgb);
            s.g = AuraQuads.green(rgb);
            s.b = AuraQuads.blue(rgb);
            list.add(s);
        }
    }

    public static void renderAll(PoseStack pose, MultiBufferSource buffers, Camera cam,
                                 Vec3 camPos, long t, float pt) {
        long dt = (lastSimTick == Long.MIN_VALUE) ? 0
                : Math.min(10, Math.max(0, t - lastSimTick));
        lastSimTick = t;
        if (SPARKS.isEmpty()) return;

        if (dt > 0) {
            for (Iterator<Map.Entry<Integer, List<Spark>>> it = SPARKS.entrySet().iterator();
                 it.hasNext(); ) {
                List<Spark> list = it.next().getValue();
                for (Iterator<Spark> si = list.iterator(); si.hasNext(); ) {
                    Spark s = si.next();
                    s.age += dt;
                    if (s.age >= s.life) { si.remove(); continue; }
                    s.x += s.vx * dt;
                    s.y += s.vy * dt;
                    s.z += s.vz * dt;
                }
                if (list.isEmpty()) it.remove();
            }
            if (SPARKS.isEmpty()) return;
        }

        int frame = (int) ((t / 2) % AuraTuning.SHEET_FRAMES);
        VertexConsumer vc = buffers.getBuffer(
                ModAuraRenderType.energy(AuraSkirtRenderer.sheet(frame)));
        float yaw = -cam.getYRot();
        float u0 = AuraQuads.cellU(2), v0 = AuraQuads.cellV(2);

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);
        for (List<Spark> list : SPARKS.values()) {
            for (Spark s : list) {
                float lf = 1f - Math.min(1f, (s.age + pt) / s.life);
                // Cuadrática: la chispa muere de golpe en vez de desvanecerse.
                float fade = lf * lf;

                pose.pushPose();
                pose.translate(s.x + s.vx * pt, s.y + s.vy * pt, s.z + s.vz * pt);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.mulPose(Axis.ZP.rotationDegrees(s.roll));
                AuraQuads.plane(vc, pose.last(), s.w, s.h * (0.5f + 0.5f * lf), u0, v0,
                        s.mirror, 0f, s.r, s.g, s.b,
                        AuraSkirts.BASE_ALPHA * ALPHA_MUL * fade);
                pose.popPose();
            }
        }
        pose.popPose();
    }
}