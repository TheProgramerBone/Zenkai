package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.aura.AuraSkirts;
import com.hmc.zenkai.feature.aura.AuraTuning;
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
 * Chispas: destellos cortos y violentos que saltan alrededor del aura.
 * FRECUENCIA = sparksPerSecond del perfil = 14/s × presence × tension × (1 + 2.5·kI).
 * O sea: solo chispea quien tiene poder Y está cerca de su límite. Un maestro al 100%
 * con control 10 chispea poco porque su tensión es baja; el mismo poder con control 2
 * chispea mucho. Y el Kaioken lo multiplica por 3.5 sin tocar el tamaño del aura — que
 * es exactamente su firma: no crece, se descontrola.
 * Las chispas sobreviven hasta la banda FAR a propósito: cuando el aura ya no se
 * distingue, siguen diciendo "ese está forzando algo".
 * Las chispas NORMALES usan el cuadrante de penacho de la hoja de llamas compartida —
 * muy estirado y corto de vida, da un destello afilado aceptable.
 *
 * RAYOS QUEBRADOS (AuraModifier.electricSparks, hoy solo rose.json): TEXTURA PROPIA
 * (aura_rayo.png, ver tools/gen_aura_particles.py — un segmento recto que se afina en
 * las dos puntas), no el cuadrante de penacho de la hoja de llamas — ese fue un
 * placeholder aceptable mientras no existía nada mejor, pero seguía siendo geometría de
 * llama, no de rayo. En vez de un quad recto se dibujan 3 segmentos encadenados con
 * quiebro lateral alternado (zigzag, la "geometría segmentada" que el diseño original ya
 * apuntaba), en ADITIVO (`ModAuraRenderType.energyAdditive`) en vez de translúcido, y
 * tintados siempre con el color INTERIOR del plan (para Rose, violeta — garantizado, no
 * el sorteo inner/outer/core normal). El resto de la vida (spawn, física, expiración) es
 * EXACTAMENTE el mismo sistema que las chispas normales; solo cambia cómo se dibuja una
 * chispa concreta. Aditivo aquí es más seguro que en AuraSkirtRenderer.additiveGlow
 * (viabilidad descartada, ver AuraTuning.GLOW_*): son quads pequeños, dispersos en un
 * radio alrededor del jugador (nunca centrados en su cuerpo) y de vida cortísima (2-5
 * ticks), no un cono grande solapando la silueta entera.
 */
public final class AuraSparkRenderer {
    private AuraSparkRenderer() {}

    private static final ResourceLocation RAYO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/aura_rayo.png");

    private static final float ALPHA_MUL = 1.35f;
    private static final int MAX_PER_PLAYER = 32;

    /** Alfa de cada segmento del rayo quebrado. Más bajo que ALPHA_MUL porque es
     *  aditivo (suma, no mezcla) y son 3 segmentos que se solapan en las esquinas. */
    private static final float JAGGED_ALPHA_MUL = 0.85f;
    /** Desplazamiento lateral del quiebro, como fracción del ancho de la chispa. */
    private static final float JAGGED_KICK = 2.4f;

    private static final class Spark {
        double x, y, z, vx, vy, vz;
        float age, life, w, h, roll, r, g, b;
        boolean mirror;
        boolean jagged;
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

            boolean jagged = plan.profile().electricSparks();
            // El rayo quebrado siempre usa el color INTERIOR (para Rose, violeta
            // garantizado) — nada de sortear outer/core, que diluiría el "morado" que
            // pidió el brief de arte en un carmesí o un blanco al azar.
            int rgb = jagged ? plan.innerColor()
                    : (plan.hasOuter() && RNG.nextBoolean())
                            ? plan.outerColor()
                            : (plan.hasCore() ? plan.coreColor() : plan.innerColor());

            Spark s = new Spark();
            s.jagged = jagged;
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
        ResourceLocation sheet = AuraSkirtRenderer.sheet(frame);
        float yaw = -cam.getYRot();
        float u0 = AuraQuads.cellU(2), v0 = AuraQuads.cellV(2);

        // DOS PASADAS, a propósito — CRASH REAL en la primera prueba en juego con chispas
        // activas (sesión 2026-09-04): "IllegalStateException: Not building!" en
        // AuraQuads.vert, sin relación con ningún cambio de esa sesión (confirmado leyendo
        // el .java real de MultiBufferSource.BufferSource, decompilado de las fuentes de
        // NeoForge: TODO RenderType custom no-fixed comparte UN solo sharedBuffer, y
        // getBuffer(tipoDistinto) cierra automáticamente (endBatch) el tipo anterior antes
        // de devolver el nuevo). La versión de una sola pasada pedía `vc` (energy(sheet)) y
        // luego, ANTES de escribir nada en él, pedía `vcGlow` (energyAdditive) — eso cerraba
        // `vc` en el acto, así que la primera chispa NO jagged que el bucle intentaba
        // dibujar en `vc` reventaba, aunque ese frame no hubiera ninguna chispa jagged. Con
        // dos pasadas, cada VertexConsumer se pide y se agota por completo antes de pedir
        // el otro — nunca hay dos "tipos compartidos" abiertos a la vez.
        VertexConsumer vc = buffers.getBuffer(ModAuraRenderType.energy(sheet));
        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);
        for (List<Spark> list : SPARKS.values()) {
            for (Spark s : list) {
                if (s.jagged) continue;
                float lf = 1f - Math.min(1f, (s.age + pt) / s.life);
                // Cuadrática: la chispa muere de golpe en vez de desvanecerse.
                float fade = lf * lf;
                float h = s.h * (0.5f + 0.5f * lf);

                pose.pushPose();
                pose.translate(s.x + s.vx * pt, s.y + s.vy * pt, s.z + s.vz * pt);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.mulPose(Axis.ZP.rotationDegrees(s.roll));
                AuraQuads.plane(vc, pose.last(), s.w, h, u0, v0,
                        s.mirror, 0f, s.r, s.g, s.b,
                        AuraSkirts.BASE_ALPHA * ALPHA_MUL * fade);
                pose.popPose();
            }
        }
        pose.popPose();

        // Textura PROPIA para el rayo (aura_rayo.png), no la hoja compartida — pedida DESPUÉS
        // de que la pasada de arriba ya terminó de escribir en `vc` por completo.
        VertexConsumer vcGlow = buffers.getBuffer(ModAuraRenderType.energyAdditive(RAYO_TEXTURE));
        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);
        for (List<Spark> list : SPARKS.values()) {
            for (Spark s : list) {
                if (!s.jagged) continue;
                float lf = 1f - Math.min(1f, (s.age + pt) / s.life);
                float fade = lf * lf;
                float h = s.h * (0.5f + 0.5f * lf);

                pose.pushPose();
                pose.translate(s.x + s.vx * pt, s.y + s.vy * pt, s.z + s.vz * pt);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.mulPose(Axis.ZP.rotationDegrees(s.roll));
                drawJagged(vcGlow, pose, s, h, fade);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /**
     * Rayo quebrado: 3 segmentos encadenados subiendo desde el origen de la chispa, con
     * quiebro lateral alternado (zigzag) — la "geometría segmentada" del TODO de clase.
     * El pose ya está trasladado/rotado al billboard de la chispa (ver renderAll); cada
     * segmento solo añade su propio quiebro lateral encima.
     */
    private static void drawJagged(VertexConsumer vc, PoseStack pose, Spark s, float totalH,
                                   float fade) {
        int segs = 3;
        float segH = totalH / segs;
        float kick = s.w * JAGGED_KICK;
        float a = AuraSkirts.BASE_ALPHA * JAGGED_ALPHA_MUL * fade;

        for (int seg = 0; seg < segs; seg++) {
            float lateral = (seg % 2 == 0 ? 1f : -1f) * kick;
            pose.pushPose();
            pose.translate(lateral, seg * segH, 0f);
            // Quiebro angular además del lateral: sin esto los 3 segmentos siguen leyéndose
            // como un solo quad recto desplazado, no como un rayo roto.
            pose.mulPose(Axis.ZP.rotationDegrees(seg % 2 == 0 ? 16f : -16f));
            // Segmentos ligeramente más largos que totalH/segs para que se solapen en las
            // uniones — si no, el quiebro lateral deja huecos entre segmento y segmento.
            // planeFull: textura propia (aura_rayo.png), sin offset de celda de la hoja
            // compartida.
            AuraQuads.planeFull(vc, pose.last(), s.w, segH * 1.3f,
                    s.mirror, 0f, s.r, s.g, s.b, a);
            pose.popPose();
        }
    }
}