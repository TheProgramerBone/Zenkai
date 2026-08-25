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
 * Lengüetas que se desprenden de la corona del aura y suben ancladas al MUNDO. Volando
 * quedan detrás como rastro; simuladas por gameTime, así que se pausan solas.
 *
 * Traslado del código que ya funcionaba, con tres cambios:
 *  1. La CANTIDAD la manda el Plan (profile.wisps(), 6–24 según presencia) en vez de un
 *     tope fijo de 24 igual para cualquiera. Un novato suelta pocas; un endgame, muchas.
 *  2. RELOJ UNIFICADO. Antes el cono usaba (gameTime/frameTicks) y las lenguas
 *     (gameTime/2): dos relojes distintos para el mismo efecto. Ahora ambos usan
 *     frameTicks del perfil, así que un aura turbulenta suelta lenguas más seguido sin
 *     que nadie lo configure.
 *  3. El LOD las apaga poniendo wisps a 0; aquí no hay que consultar la banda.
 *
 * Siguen vivas aunque el aura se apague: por eso el mapa es global y no se limpia al
 * dejar de renderizar, solo al dejar de trackear al jugador.
 */
public final class AuraWispRenderer {
    private AuraWispRenderer() {}

    private static final float ALPHA_MUL = 1.10f;

    private static final class Wisp {
        double x, y, z, vx, vy, vz;
        float age, life, size, u0, v0, r, g, b;
        boolean mirror;
    }

    private static final Random RNG = new Random();
    private static final Map<Integer, List<Wisp>> WISPS = new HashMap<>();
    /** Último step visto por jugador: se suelta UNA lengua por step. */
    private static final Map<Integer, Integer> LAST_STEP = new HashMap<>();
    private static long lastSimTick = Long.MIN_VALUE;

    public static void clear(int playerId) {
        WISPS.remove(playerId);
        LAST_STEP.remove(playerId);
    }

    /** Suelta una lengua si el step del aura ha cambiado. */
    public static void spawn(AbstractClientPlayer p, Vec3 at, AuraSkirts.Plan plan, long t) {
        int max = plan.profile().wisps();
        if (max <= 0) return;

        int step = (int) (t / plan.profile().frameTicks());
        Integer prev = LAST_STEP.put(p.getId(), step);
        if (prev != null && prev == step) return;

        List<Wisp> list = WISPS.computeIfAbsent(p.getId(), k -> new ArrayList<>());
        if (list.size() >= max) return;

        float eff = AuraSkirts.AURA_SCALE * plan.profile().size();
        double ang = RNG.nextDouble() * Math.PI * 2.0;
        double rad = (0.22 + 0.28 * RNG.nextDouble()) * eff;
        double y0 = (1.45 + 0.95 * RNG.nextDouble()) * eff;

        // Con capa envolvente, la mitad de las lenguas salen del color exterior: es lo
        // que hace que un kaioken sobre forma suelte chispas de los dos colores.
        int rgb = (plan.hasOuter() && RNG.nextBoolean())
                ? plan.outerColor() : plan.innerColor();

        Wisp w = new Wisp();
        w.x = at.x + Math.sin(ang) * rad;
        w.y = at.y + y0;
        w.z = at.z + Math.cos(ang) * rad;
        w.vx = Math.sin(ang) * 0.012;
        w.vz = Math.cos(ang) * 0.012;
        w.vy = 0.055 + 0.050 * RNG.nextDouble();
        w.life = 8f + RNG.nextInt(7);
        w.size = (0.45f + 0.30f * RNG.nextFloat()) * eff;
        // Cuadrantes de llama alta y penacho: los dos que se leen bien sueltos.
        int q = RNG.nextBoolean() ? 1 : 2;
        w.u0 = AuraQuads.cellU(q);
        w.v0 = AuraQuads.cellV(q);
        w.mirror = RNG.nextBoolean();
        w.r = AuraQuads.red(rgb);
        w.g = AuraQuads.green(rgb);
        w.b = AuraQuads.blue(rgb);
        list.add(w);
    }

    /**
     * Avanza la simulación y dibuja cada lengua viva del conjunto de jugadores.
     * Se llama UNA vez por frame, fuera del bucle de jugadores: las lenguas están en
     * coordenadas de mundo y no dependen del pose de nadie.
     */
    public static void renderAll(PoseStack pose, MultiBufferSource buffers, Camera cam,
                                 Vec3 camPos, long t, float pt) {
        long dt = (lastSimTick == Long.MIN_VALUE) ? 0
                : Math.min(10, Math.max(0, t - lastSimTick));
        lastSimTick = t;
        if (WISPS.isEmpty()) return;

        if (dt > 0) {
            for (Iterator<Map.Entry<Integer, List<Wisp>>> it = WISPS.entrySet().iterator();
                 it.hasNext(); ) {
                List<Wisp> list = it.next().getValue();
                for (Iterator<Wisp> wi = list.iterator(); wi.hasNext(); ) {
                    Wisp w = wi.next();
                    w.age += dt;
                    if (w.age >= w.life) { wi.remove(); continue; }
                    w.x += w.vx * dt;
                    w.y += w.vy * dt;
                    w.z += w.vz * dt;
                }
                if (list.isEmpty()) it.remove();
            }
            if (WISPS.isEmpty()) return;
        }

        // Reloj de hoja propio: las lenguas del conjunto de jugadores comparten frame, que
        // es más barato que un buffer por jugador y visualmente indistinguible.
        int frame = (int) ((t / 2) % AuraTuning.SHEET_FRAMES);
        VertexConsumer vc = buffers.getBuffer(
                ModAuraRenderType.energy(AuraSkirtRenderer.sheet(frame)));

        // ⚠ Signo del billboard cilíndrico en 1.21.1: heredado del código anterior,
        // sigue sin verificarse en juego.
        float yaw = -cam.getYRot();

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);
        for (List<Wisp> list : WISPS.values()) {
            for (Wisp w : list) {
                float lf = 1f - Math.min(1f, (w.age + pt) / w.life);
                float fade = lf * lf * (3f - 2f * lf);
                float ww = w.size * (0.35f + 0.65f * lf);
                float hh = w.size * (0.55f + 0.85f * lf);
                float a = AuraSkirts.BASE_ALPHA * ALPHA_MUL * fade;

                pose.pushPose();
                pose.translate(w.x + w.vx * pt, w.y + w.vy * pt, w.z + w.vz * pt);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                AuraQuads.plane(vc, pose.last(), ww, hh, w.u0, w.v0, w.mirror, 0f,
                        w.r, w.g, w.b, a);
                pose.popPose();
            }
        }
        pose.popPose();
    }
}