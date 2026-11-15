package com.hmc.zenkai.client.fx;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.KiChargeClientState;
import com.hmc.zenkai.client.aura.AuraChargeGroundFx;
import com.hmc.zenkai.client.aura.compose.AuraComposeShaders;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * VFX propio de la técnica EXPLOSION (la explosión final de Vegeta contra Majin Buu), en sus tres tiempos. Pedido del usuario el
 * 2026-11-14: la carga, la detonación y lo que queda después no se parecían a la escena. Color: el de la técnica con el núcleo
 * blanco. Extra elegido: onda de choque y polvo en el suelo (sin columna de luz ni destello de pantalla).
 * <ul>
 * <li><b>Carga</b> (mientras {@link KiChargeClientState} tenga una carga EXPLOSION, y unos ticks más para cubrir la mecha): motas
 *     de energía que salen del cuerpo hacia fuera y arriba, rayos, grietas luminosas que avanzan por el suelo con la carga, pulsos
 *     de onda cortos y polvo y rocas que levitan. La esfera de carga de siempre (KiVfxChargeRenderer) no se toca.</li>
 * <li><b>Detonación</b> ({@link #start}, avisada por {@code KiExplosionFxPacket}): una cúpula de luz MACIZA que se abre hasta el
 *     radio REAL del daño —tres capas: color, color claro y núcleo blanco—, tres ondas de choque por el suelo y una ráfaga de
 *     polvo y rocas hacia fuera.</li>
 * <li><b>Después</b>: la luz se apaga despacio de fuera hacia dentro, y durante unos segundos sube polvo y quedan motas de
 *     energía flotando sobre el círculo arrasado.</li>
 * </ul>
 * Quien tiene la cámara DENTRO de la cúpula la ve muy atenuada: sin eso la luz tapaba la pantalla entera (misma lección que
 * «Técnicas pegadas al dueño» en docs/tecnicas-y-vfx.md). En primera persona propia tampoco salen las motas ni los rayos de la
 * carga (taparían la vista); el suelo sí.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ExplosionBlastFx {
    private ExplosionBlastFx() {}

    /** Ticks: la cúpula se abre, aguanta y se apaga; el polvo sigue subiendo hasta LINGER. */
    private static final float EXPAND = 7f, HOLD = 18f, FADE_END = 46f;
    private static final int LINGER = 70;
    /** La mecha (la entidad pegada al dueño) dura los anim_ticks de la técnica: las grietas siguen ese rato tras soltar. */
    private static final int CHARGE_GRACE = 12;
    private static final int LAT = 10, LON = 20, RING_SEGMENTS = 48, CRACKS = 8, CRACK_STEPS = 5;
    private static final float INSIDE_DAMPEN = 0.15f;
    private static final float DOME_START_RADIUS = 2.5f;

    private record Blast(Vec3 center, float radius, int rgb, long start, boolean hasGround, double groundY,
                         AuraChargeGroundFx.Ground ground) {}

    private static final class Buildup {
        int rgb, size;
        float progress;
        long seen;
    }

    private static final List<Blast> BLASTS = new ArrayList<>();
    private static final Map<Integer, Buildup> BUILDUPS = new HashMap<>();

    // ── Detonación ──────────────────────────────────────────────────────────

    public static void start(double x, double y, double z, float radius, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || radius <= 0f) return;
        // El centro es el del cuerpo del dueño: el suelo se busca desde sus pies.
        AuraChargeGroundFx.Ground g = AuraChargeGroundFx.findGround(level, mc.player, x, y - 0.9, z);
        BLASTS.add(new Blast(new Vec3(x, y, z), radius, rgb, level.getGameTime(), g != null,
                g == null ? y - 0.9 : g.surfaceY(), g));
        if (g == null) return;

        RandomSource rng = level.random;
        int dustRgb = AuraChargeGroundFx.dustColor(level, g);
        // Una nube recorre ~14 veces su velocidad inicial (rozamiento de AuraDustParticle): se ajusta para que llegue al borde.
        double reach = radius / 14.0;
        int dust = Math.min(64, 20 + Math.round(radius * 5f));
        for (int i = 0; i < dust; i++) {
            double ang = (i + rng.nextDouble() * 0.5) / dust * Math.PI * 2.0;
            double cx = Math.cos(ang), cz = Math.sin(ang);
            double out = reach * (0.7 + rng.nextDouble() * 0.4);
            level.addParticle(ModParticles.auraDust(dustRgb, 1.8f + rng.nextFloat() * 1.2f),
                    x + cx, g.surfaceY() + 0.1, z + cz, cx * out, 0.03 + rng.nextDouble() * 0.06, cz * out);
        }
        BlockParticleOption rock = new BlockParticleOption(ModParticles.AURA_ROCK.get(), g.state());
        int rocks = Math.min(40, 8 + Math.round(radius * 3f));
        for (int i = 0; i < rocks; i++) {
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double r = 0.5 + rng.nextDouble() * radius * 0.6;
            level.addParticle(rock, x + Math.cos(ang) * r, g.surfaceY() + 0.05, z + Math.sin(ang) * r,
                    Math.cos(ang) * (0.1 + rng.nextDouble() * 0.12), 0.3 + rng.nextDouble() * 0.4,
                    Math.sin(ang) * (0.1 + rng.nextDouble() * 0.12));
        }
    }

    // ── Tick: lo que queda después, y la escalada de la carga ───────────────

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) { clear(); return; }
        if (mc.isPaused()) return;
        long now = level.getGameTime();
        RandomSource rng = level.random;

        for (Iterator<Blast> it = BLASTS.iterator(); it.hasNext(); ) {
            Blast b = it.next();
            long age = now - b.start();
            if (age > LINGER || age < 0) { it.remove(); continue; }
            if (age < 6) continue;
            // Motas del color de la técnica flotando mientras la luz se apaga.
            if (age < FADE_END) {
                for (int i = 0; i < 2; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0, r = Math.sqrt(rng.nextDouble()) * b.radius() * 0.85;
                    level.addParticle(ModParticles.energyMote(lighten(b.rgb(), 0.5f), 0.9f + rng.nextFloat() * 0.5f),
                            b.center().x + Math.cos(ang) * r, b.groundY() + 0.2 + rng.nextDouble() * 1.2,
                            b.center().z + Math.sin(ang) * r, 0.0, 0.05 + rng.nextDouble() * 0.07, 0.0);
                }
            }
            // Polvo que sigue subiendo del círculo arrasado.
            if (b.ground() != null && age % 2 == 0) {
                int dustRgb = AuraChargeGroundFx.dustColor(level, b.ground());
                int n = 2 + rng.nextInt(2);
                for (int i = 0; i < n; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0, r = Math.sqrt(rng.nextDouble()) * b.radius() * 0.9;
                    level.addParticle(ModParticles.auraDust(dustRgb, 1.5f + rng.nextFloat() * 0.9f),
                            b.center().x + Math.cos(ang) * r, b.groundY() + 0.1, b.center().z + Math.sin(ang) * r,
                            Math.cos(ang) * 0.01, 0.03 + rng.nextDouble() * 0.03, Math.sin(ang) * 0.01);
                }
            }
        }

        BUILDUPS.values().removeIf(bu -> now - bu.seen > CHARGE_GRACE);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Player p : level.players()) {
            KiChargeClientState.Charge c = KiChargeClientState.of(p);
            if (c == null || c.type() != KiTechniqueType.EXPLOSION) continue;
            Buildup bu = BUILDUPS.computeIfAbsent(p.getId(), k -> new Buildup());
            bu.rgb = c.rgb();
            bu.size = c.size();
            bu.progress = KiChargeClientState.progress(c, now);
            bu.seen = now;
            float k = bu.progress;
            double cy = p.getY() + p.getBbHeight() * 0.5;

            if (!(p == mc.player && firstPerson)) {
                // Energía que estalla hacia fuera: cuanto más cargada, más y más lejos.
                int motes = 1 + Math.round(3f * k);
                for (int i = 0; i < motes; i++) {
                    double ang = rng.nextDouble() * Math.PI * 2.0;
                    double cx = Math.cos(ang), cz = Math.sin(ang), out = 0.05 + 0.12 * k;
                    level.addParticle(ModParticles.energyMote(i % 2 == 0 ? c.rgb() : lighten(c.rgb(), 0.6f), 0.9f + 0.6f * k),
                            p.getX() + cx * 0.5, p.getY() + rng.nextDouble() * p.getBbHeight(), p.getZ() + cz * 0.5,
                            cx * out, 0.04 + rng.nextDouble() * 0.08, cz * out);
                }
                if (k > 0.25f && now % 3 == 0) {
                    double theta = rng.nextDouble() * Math.PI * 2.0, yy = rng.nextDouble() * 2.0 - 1.0;
                    double planar = Math.sqrt(Math.max(0.0, 1.0 - yy * yy)), rr = 0.8 + 0.8 * k;
                    double nx = planar * Math.cos(theta), nz = planar * Math.sin(theta);
                    // La velocidad es la dirección del rayo (KiArcParticle no se mueve).
                    level.addParticle(ModParticles.arc(c.rgb(), 0.35f + 0.3f * k),
                            p.getX() + nx * rr, cy + yy * rr, p.getZ() + nz * rr, nx, yy, nz);
                }
            }
            if (now % 6 == 0 && p.onGround()) {
                AuraChargeGroundFx.Ground g = AuraChargeGroundFx.findGround(level, p, p.getX(), p.getY(), p.getZ());
                if (g != null) AuraChargeGroundFx.burst(level, p, g, 4 + Math.round(8f * k), 1 + Math.round(3f * k), true);
            }
        }
    }

    // ── Dibujo ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || (BLASTS.isEmpty() && BUILDUPS.isEmpty())) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        PoseStack pose = e.getPoseStack();
        long now = level.getGameTime();

        for (Blast b : BLASTS) {
            final float t = (now - b.start()) + pt;
            if (t < 0f || t > FADE_END) continue;
            final float ox = (float) (b.center().x - cam.x), oy = (float) (b.center().y - cam.y), oz = (float) (b.center().z - cam.z);
            final float gy = (float) (b.groundY() + 0.06 - cam.y);
            final float[] col = rgbf(b.rgb());
            final float radius = b.radius();
            final boolean ground = b.hasGround();
            KiVfxFrameQueue.submit(pose, new Vec3(ox, oy, oz), (ps, buf) -> {
                float w = KiVfxFrameQueue.bloomPass() ? 0.6f : 1f;
                VertexConsumer vc = buf.getBuffer(AuraComposeShaders.ribbons());
                Matrix4f m = ps.last().pose();
                drawDome(vc, m, ox, oy, oz, t, radius, col, w);
                if (ground) {
                    for (int i = 0; i < 3; i++) shockRing(vc, m, ox, gy, oz, t - i * 3f, radius * (1.0f + 0.15f * i), col, w);
                }
                buf.endBatch(AuraComposeShaders.ribbons());
            });
        }

        for (Map.Entry<Integer, Buildup> en : BUILDUPS.entrySet()) {
            if (!(level.getEntity(en.getKey()) instanceof Player p) || !p.onGround()) continue;
            Vec3 pos = p.getPosition(pt);
            AuraChargeGroundFx.Ground g = AuraChargeGroundFx.findGround(level, p, pos.x, pos.y, pos.z);
            if (g == null) continue;
            final Buildup bu = en.getValue();
            final float ox = (float) (pos.x - cam.x), oz = (float) (pos.z - cam.z);
            final float gy = (float) (g.surfaceY() + 0.04 - cam.y);
            final float[] col = rgbf(lighten(bu.rgb, 0.35f));
            final float k = bu.progress;
            final float reach = (1.2f + 0.5f * bu.size) * (float) Math.pow(k, 0.7);
            final float pulse = ((now + en.getKey() * 7L) % 12) + pt;
            final int seed = en.getKey();
            KiVfxFrameQueue.submit(pose, new Vec3(ox, gy, oz), (ps, buf) -> {
                float w = KiVfxFrameQueue.bloomPass() ? 0.6f : 1f;
                VertexConsumer vc = buf.getBuffer(AuraComposeShaders.ribbons());
                Matrix4f m = ps.last().pose();
                drawCracks(vc, m, ox, gy, oz, reach, seed, col, w);
                if (k > 0.2f) shockRing(vc, m, ox, gy + 0.02f, oz, pulse * 1.3f, 1.2f + reach, col, w * 0.5f * k);
                buf.endBatch(AuraComposeShaders.ribbons());
            });
        }
    }

    /** Cúpula maciza: tres esferas concéntricas (color, color claro, núcleo blanco). Cada vértice se aclara cuanto más de
     *  frente mira a la cámara, así la bola se lee con volumen (centro cegador, borde de color) y no como un disco plano. */
    private static void drawDome(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float t, float radius,
                                 float[] col, float weight) {
        float grow = t >= EXPAND ? 1f : 1f - (float) Math.pow(1f - t / EXPAND, 3.0);
        float fade = t <= HOLD ? 0f : Mth.clamp((t - HOLD) / (FADE_END - HOLD), 0f, 1f);
        float breath = t > EXPAND && t < HOLD ? 1f + 0.03f * Mth.sin(t * 1.7f) : 1f;
        // No nace de un punto: sale del tamaño que ya tenía la esfera de carga (KiVfxChargeRenderer.explosionRadius).
        float r = Mth.lerp(grow, Math.min(radius, DOME_START_RADIUS), radius) * breath;
        float alpha = (float) Math.pow(1f - fade, 1.5) * weight;
        if (alpha <= 0.004f) return;
        if (ox * ox + oy * oy + oz * oz < r * r) alpha *= INSIDE_DAMPEN;
        // La luz se apaga de dentro hacia fuera en tamaño (el núcleo encoge antes) y de fuera hacia dentro en brillo.
        sphere(vc, m, ox, oy, oz, r, col[0], col[1], col[2], 0.34f * alpha * (1f - 0.5f * fade));
        sphere(vc, m, ox, oy, oz, r * 0.8f, Mth.lerp(0.55f, col[0], 1f), Mth.lerp(0.55f, col[1], 1f), Mth.lerp(0.55f, col[2], 1f),
                0.5f * alpha);
        sphere(vc, m, ox, oy, oz, r * Mth.lerp(fade, 0.56f, 0.25f), 1f, 1f, 1f, 0.85f * alpha);
    }

    private static void sphere(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float r,
                               float cr, float cg, float cb, float alpha) {
        if (r <= 0.01f || alpha <= 0.004f) return;
        for (int i = 0; i < LAT; i++) {
            float p0 = Mth.PI * i / LAT, p1 = Mth.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                float t0 = Mth.TWO_PI * j / LON, t1 = Mth.TWO_PI * (j + 1) / LON;
                vertex(vc, m, ox, oy, oz, r, p0, t0, cr, cg, cb, alpha);
                vertex(vc, m, ox, oy, oz, r, p1, t0, cr, cg, cb, alpha);
                vertex(vc, m, ox, oy, oz, r, p1, t1, cr, cg, cb, alpha);
                vertex(vc, m, ox, oy, oz, r, p0, t1, cr, cg, cb, alpha);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float r, float phi, float theta,
                               float cr, float cg, float cb, float alpha) {
        float sp = Mth.sin(phi);
        float nx = sp * Mth.cos(theta), ny = Mth.cos(phi), nz = sp * Mth.sin(theta);
        float x = ox + nx * r, y = oy + ny * r, z = oz + nz * r;
        // Las coordenadas ya son relativas a la cámara: la dirección de vista es la del propio vértice.
        float len = Mth.sqrt(x * x + y * y + z * z);
        float facing = len < 1.0e-4f ? 1f : Math.abs(nx * x + ny * y + nz * z) / len;
        vc.addVertex(m, x, y, z).setColor(cr, cg, cb, alpha * (0.3f + 0.7f * facing));
    }

    /** Onda de choque por el suelo: un anillo ancho que se abre hasta maxR y se adelgaza. */
    private static void shockRing(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float t, float maxR,
                                  float[] col, float weight) {
        final float life = 16f;
        if (t <= 0f || t > life || weight <= 0.004f) return;
        float k = 1f - (float) Math.exp(-t * 0.28);
        float outer = 0.4f + maxR * k;
        float width = Math.max(0.12f, maxR * 0.14f) * (1f - t / life) + 0.1f;
        float inner = Math.max(0f, outer - width);
        float a = 0.85f * (1f - t / life) * weight;
        float r = Mth.lerp(0.4f, col[0], 1f), g = Mth.lerp(0.4f, col[1], 1f), b = Mth.lerp(0.4f, col[2], 1f);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / RING_SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / RING_SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vc.addVertex(m, ox + c0 * inner, oy, oz + s0 * inner).setColor(r, g, b, a * 0.3f);
            vc.addVertex(m, ox + c0 * outer, oy, oz + s0 * outer).setColor(r, g, b, a);
            vc.addVertex(m, ox + c1 * outer, oy, oz + s1 * outer).setColor(r, g, b, a);
            vc.addVertex(m, ox + c1 * inner, oy, oz + s1 * inner).setColor(r, g, b, a * 0.3f);
        }
    }

    /** Grietas luminosas que salen de los pies y avanzan con la carga. La forma de cada una es fija por jugador (semilla). */
    private static void drawCracks(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float reach, int seed,
                                   float[] col, float weight) {
        if (reach <= 0.05f) return;
        for (int i = 0; i < CRACKS; i++) {
            float ang = i * Mth.TWO_PI / CRACKS + hash(seed, i, 0) * 0.5f;
            float len = reach * (0.6f + 0.4f * hash(seed, i, 1));
            float px = ox, pz = oz;
            for (int s = 0; s < CRACK_STEPS; s++) {
                float bend = ang + (hash(seed, i, 2 + s) - 0.5f) * 0.9f;
                float step = len / CRACK_STEPS;
                float qx = px + Mth.cos(bend) * step, qz = pz + Mth.sin(bend) * step;
                // Ancho perpendicular al tramo; se afina hacia la punta.
                float w0 = 0.07f * (1f - s / (float) CRACK_STEPS) + 0.015f, w1 = 0.07f * (1f - (s + 1) / (float) CRACK_STEPS) + 0.015f;
                float nx = -Mth.sin(bend), nz = Mth.cos(bend);
                float a0 = 0.9f * weight, a1 = 0.9f * weight * (1f - (s + 1) / (float) (CRACK_STEPS + 1));
                vc.addVertex(m, px - nx * w0, oy, pz - nz * w0).setColor(col[0], col[1], col[2], a0);
                vc.addVertex(m, px + nx * w0, oy, pz + nz * w0).setColor(col[0], col[1], col[2], a0);
                vc.addVertex(m, qx + nx * w1, oy, qz + nz * w1).setColor(col[0], col[1], col[2], a1);
                vc.addVertex(m, qx - nx * w1, oy, qz - nz * w1).setColor(col[0], col[1], col[2], a1);
                px = qx;
                pz = qz;
            }
        }
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private static float hash(int seed, int a, int b) {
        int h = seed * 73856093 ^ a * 19349663 ^ b * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535f;
    }

    private static float[] rgbf(int rgb) {
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    private static int lighten(int rgb, float k) {
        int r = Math.round(Mth.lerp(k, (rgb >> 16) & 0xFF, 255)), g = Math.round(Mth.lerp(k, (rgb >> 8) & 0xFF, 255)),
                b = Math.round(Mth.lerp(k, rgb & 0xFF, 255));
        return (r << 16) | (g << 8) | b;
    }

    public static void clear() {
        BLASTS.clear();
        BUILDUPS.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
