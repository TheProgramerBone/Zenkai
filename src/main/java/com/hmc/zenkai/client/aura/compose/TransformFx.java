package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.client.aura.AuraChargeGroundFx;
import com.hmc.zenkai.client.aura.compose.component.AuraComponent;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Locale;

/**
 * SECUENCIA DE TRANSFORMACIÓN del aura compuesta:
 * <pre>
 * acumulación (mientras dura el hold: AuraDynamics.transforming concentra y agita el cuerpo)
 *   → estallido (el cuerpo se hincha de golpe y vuelve)  → onda expansiva en el suelo
 *   → ráfaga de polvo/rocas y tanda extra de motas/rayos  → estabilización (la rampa de siempre)
 * </pre>
 * El cliente la detecta solo (cambio de {@code formId} sincronizado, o {@code /zenkaiaura
 * transform}): no hace falta ningún packet nuevo. El TIPO sale del estilo (campo "transform") y es
 * lo que diferencia una transformación de otra: una divina no levanta polvo, una salvaje levanta
 * rocas que se quedan flotando, una oscura primero IMPLOSIONA.
 * <p>
 * Primera persona propia: la escala del estallido se acota (±10%) y no hay pico de alfa — un
 * destello que llena la pantalla no se debe comer la vista.
 * <p>
 * SECUENCIA LARGA (Rage Forms: la PRIMERA vez que cada jugador se transforma en cada forma, sea cual sea la vía): el mismo tipo de
 * estilo, pero en TRES pulsos — el golpe inicial (más fuerte), un segundo golpe a los 0,9 s y la intensificación final a los 1,9 s,
 * cada uno con su onda de suelo (la final, más ancha), su ráfaga de polvo/rocas y su tanda de componentes —, y 3 s en total. La
 * corta sigue siendo la de siempre. Lo decide el compositor con el aviso del servidor ({@code LongTransformPacket}).
 */
final class TransformFx {

    enum Type {
        NONE(0f, 0f, 0, 0, false, 0f),
        BURST(0.45f, 0.35f, 24, 6, false, 0.6f),
        ELECTRIC_BURST(0.45f, 0.35f, 20, 4, false, 1f),
        RADIANT(0.3f, 0.2f, 0, 0, false, 0.8f),
        VIOLENT(0.6f, 0.4f, 32, 12, true, 0.8f),
        HEAVY(0.25f, 0.2f, 28, 6, true, 0.5f),
        CALM_SHIFT(0.15f, 0.1f, 0, 0, false, 0.5f),
        // Golden: el aura sostenida es tenue (body.opacity 0,4), así que el estallido sube el alfa
        // mucho (×2,5 en el pico = opaca) y el pico de tamaño es el más alto; escombros que caen
        // (levitate=false) y todos los rayos del componente electricity de un solo disparo.
        GOLDEN_BURST(0.7f, 1.5f, 16, 10, false, 1f);

        final float peak, alphaBoost;
        final int dust, rocks;
        final boolean levitate;
        final float componentBurst;

        Type(float peak, float alphaBoost, int dust, int rocks, boolean levitate, float componentBurst) {
            this.peak = peak;
            this.alphaBoost = alphaBoost;
            this.dust = dust;
            this.rocks = rocks;
            this.levitate = levitate;
            this.componentBurst = componentBurst;
        }

        static Type of(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return BURST;
            }
        }
    }

    private static final float DURATION = 1.1f;
    /** Secuencia larga (~6,3 s, el estallido a los 4 s): cuatro pulsos (inicio, dos golpes intermedios, intensificación final). */
    private static final float LONG_DURATION = 6.3f;
    /** Cuánto se estiró la secuencia respecto a la original de 3 s (estallido de 1,9 s a 4 s): grietas y rayos usan el tiempo normalizado. */
    private static final float LONG_STRETCH = 4.0f / 1.9f;
    private static final float[] LONG_BEAT_AT = {0f, 1.3f, 2.7f, 4.0f};
    private static final float[] LONG_BEAT_GAIN = {1.5f, 1.1f, 1.3f, 1.7f};
    private static final float[] LONG_RING_RADIUS = {1f, 1.15f, 1.35f, 1.7f};
    private static final int RING_SEGMENTS = 48;

    private Type type = Type.NONE;
    private float t = -1f;
    private boolean longSeq;
    private float duration = DURATION;
    /** Pulsos de la secuencia larga que ya se dispararon (el 0 va en trigger). */
    private int beatsFired;
    private List<AuraComponent> comps = List.of();
    private AuraChargeGroundFx.Ground ground;
    private boolean hasGround;
    private double groundY;
    private final float[] ringColor = new float[3];

    boolean running() { return t >= 0f; }

    /** Corta la secuencia en curso (el jugador soltó la tecla de transformar antes de completarse). */
    void cancel() { t = -1f; }

    void trigger(Type type, AuraFrame f, List<AuraComponent> components) { trigger(type, f, components, false); }

    void trigger(Type type, AuraFrame f, List<AuraComponent> components, boolean longSeq) {
        if (type == Type.NONE) return;
        this.type = type;
        this.t = 0f;
        this.longSeq = longSeq;
        this.duration = longSeq ? LONG_DURATION : DURATION;
        this.beatsFired = 1;
        this.comps = components;
        Minecraft mc = Minecraft.getInstance();
        AuraChargeGroundFx.Ground g = mc.level == null ? null
                : AuraChargeGroundFx.findGround(mc.level, f.player, f.x, f.y, f.z);
        // Onda y ráfaga de suelo solo PISANDO el suelo: transformarse en el aire no levanta polvo
        // ni dibuja un anillo bajo los pies (pedido del usuario, 2026-09-30).
        if (!f.player.onGround()) g = null;
        hasGround = g != null;
        this.ground = g;
        this.seed = f.player.getId();
        if (longSeq && g != null) buildCracks(f.player.getId(), f.powerScale);
        if (g != null) {
            groundY = g.surfaceY();
            if (type.dust > 0 || type.rocks > 0) {
                AuraChargeGroundFx.burst(mc.level, f.player, g, type.dust, type.rocks, type.levitate);
            }
        }
        for (AuraComponent c : components) c.onBurst(f, type.componentBurst);
    }

    /** Avanza la secuencia y deja en el frame los multiplicadores de tamaño y alfa. */
    void update(AuraFrame f) {
        f.burstScale = 1f;
        f.burstAlpha = 1f;
        if (t < 0f) return;
        t += f.dt;
        if (t > duration) {
            t = -1f;
            return;
        }
        float scale;
        float decay;
        if (longSeq) {
            // Tres pulsos sumados: cada uno sube rápido y se apaga como el estallido corto.
            float sum = 0f, strongest = 0f;
            for (int i = 0; i < LONG_BEAT_AT.length; i++) {
                float p = pulse(t - LONG_BEAT_AT[i]) * LONG_BEAT_GAIN[i];
                sum += p;
                strongest = Math.max(strongest, p);
            }
            scale = 1f + type.peak * sum;
            decay = Math.min(1f, strongest / LONG_BEAT_GAIN[LONG_BEAT_GAIN.length - 1]);
            fireLongBeats(f);
        } else if (type == Type.HEAVY) {
            // Implosión y luego expansión lenta con un rebote.
            if (t < 0.2f) {
                scale = 1f - 0.3f * (t / 0.2f);
                decay = 0f;
            } else {
                float e = Math.min(1f, (t - 0.2f) / 0.6f);
                scale = 0.7f + 0.3f * e + type.peak * Mth.sin(e * Mth.PI);
                decay = 1f - e;
            }
        } else if (t < 0.12f) {
            decay = t / 0.12f;
            scale = 1f + type.peak * decay;
        } else {
            decay = (float) Math.exp(-(t - 0.12f) * 6.0);
            scale = 1f + type.peak * decay;
        }
        if (f.selfFirstPerson) {
            f.burstScale = Mth.clamp(scale, 0.9f, 1.1f);
        } else {
            f.burstScale = scale;
            f.burstAlpha = 1f + type.alphaBoost * decay;
        }
        float[] c = type == Type.RADIANT ? f.hot : f.outer;
        ringColor[0] = c[0];
        ringColor[1] = c[1];
        ringColor[2] = c[2];
        hotColor[0] = f.hot[0];
        hotColor[1] = f.hot[1];
        hotColor[2] = f.hot[2];
    }

    /** Forma de UN pulso: sube en 0,12 s y se apaga exponencialmente; 0 antes de empezar. */
    private static float pulse(float tt) {
        if (tt < 0f) return 0f;
        if (tt < 0.12f) return tt / 0.12f;
        return (float) Math.exp(-(tt - 0.12f) * 5.0);
    }

    /** Al cruzar el segundo y el tercer pulso: otra ráfaga de suelo y otra tanda de componentes, la final más grande. */
    private void fireLongBeats(AuraFrame f) {
        Minecraft mc = Minecraft.getInstance();
        while (beatsFired < LONG_BEAT_AT.length && t >= LONG_BEAT_AT[beatsFired]) {
            boolean last = beatsFired == LONG_BEAT_AT.length - 1;
            beatsFired++;
            if (last) beat(f, mc, 1.6f, 1.6f); else beat(f, mc, 1.0f, 1.2f);
        }
    }

    private void beat(AuraFrame f, Minecraft mc, float groundMul, float componentMul) {
        if (ground != null && mc.level != null && (type.dust > 0 || type.rocks > 0)) {
            AuraChargeGroundFx.burst(mc.level, f.player, ground, Math.round(type.dust * groundMul),
                    Math.round(type.rocks * groundMul), type.levitate);
        }
        for (AuraComponent c : comps) c.onBurst(f, type.componentBurst * componentMul);
    }

    // ── Secuencia larga: grietas de suelo y rayos finos (Rage Forms) ─────────────────────────────

    private static final int CRACKS = 9, CRACK_SEGS = 8;
    /** Puntos {x, z} (relativos al jugador) de cada grieta, generados al disparar la secuencia larga. */
    private float[][] crackPts = new float[0][];
    private int seed;
    private final float[] hotColor = new float[3];

    /** Grietas radiales y dentadas, deterministas por jugador (no parpadean). */
    private void buildCracks(int playerId, float powerScale) {
        java.util.Random r = new java.util.Random(playerId * 7919L + 17L);
        crackPts = new float[CRACKS][];
        float reach = 3.2f + 2.6f * Mth.clamp(powerScale, 0f, 1.5f);
        for (int k = 0; k < CRACKS; k++) {
            float a = (k + r.nextFloat() * 0.6f) * Mth.TWO_PI / CRACKS;
            float len = reach * (0.55f + 0.45f * r.nextFloat());
            float[] pts = new float[(CRACK_SEGS + 1) * 2];
            float x = 0f, z = 0f, dir = a;
            for (int s = 0; s <= CRACK_SEGS; s++) {
                pts[s * 2] = x;
                pts[s * 2 + 1] = z;
                dir += (r.nextFloat() - 0.5f) * 0.9f;   // quiebros: así se leen como grieta y no como rayo recto
                float step = len / CRACK_SEGS;
                x += Mth.cos(dir) * step;
                z += Mth.sin(dir) * step;
                // se mantiene cerca del rumbo original para que no se enrosque
                dir = Mth.lerp(0.35f, dir, a);
            }
            crackPts[k] = pts;
        }
    }

    private void drawCracks(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float tt, float r, float g, float b, float weight) {
        if (tt < 0.12f) return;
        float reveal = Mth.clamp((tt - 0.12f) / 1.9f, 0f, 1f);
        reveal = 1f - (1f - reveal) * (1f - reveal);   // abre rápido y se asienta
        float fade = tt < 2.3f ? 1f : Math.max(0f, (3.0f - tt) / 0.7f);
        if (fade <= 0f) return;
        float pulse = 0.75f + 0.25f * Mth.sin(tt * 9f);   // el brillo de dentro respira
        for (float[] pts : crackPts) {
            int last = Math.max(1, Math.round(CRACK_SEGS * reveal));
            for (int s = 0; s < last; s++) {
                float w0 = (0.16f * (1f - s / (float) CRACK_SEGS) + 0.04f), w1 = (0.16f * (1f - (s + 1) / (float) CRACK_SEGS) + 0.04f);
                float x0 = pts[s * 2], z0 = pts[s * 2 + 1], x1 = pts[s * 2 + 2], z1 = pts[s * 2 + 3];
                float dx = x1 - x0, dz = z1 - z0;
                float inv = 1f / Math.max(1e-4f, Mth.sqrt(dx * dx + dz * dz));
                float nx = -dz * inv, nz = dx * inv;
                float a = 0.9f * fade * pulse * weight;
                vc.addVertex(m, ox + x0 - nx * w0, oy, oz + z0 - nz * w0).setColor(r, g, b, a);
                vc.addVertex(m, ox + x0 + nx * w0, oy, oz + z0 + nz * w0).setColor(r, g, b, a);
                vc.addVertex(m, ox + x1 + nx * w1, oy, oz + z1 + nz * w1).setColor(r, g, b, a * 0.8f);
                vc.addVertex(m, ox + x1 - nx * w1, oy, oz + z1 - nz * w1).setColor(r, g, b, a * 0.8f);
            }
        }
    }

    /** Rayos finos que saltan del cuerpo hacia fuera: más cada vez, más a partir del estallido. Cambian cada 0,1 s (chisporroteo). */
    private void drawBolts(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float tt, float r, float g, float b, float weight) {
        if (tt < 0.9f) return;
        float k = Mth.clamp((tt - 0.9f) / 2.1f, 0f, 1f);
        int count = 3 + Math.round(6f * k) + (tt >= 1.9f ? 2 : 0);
        float fade = tt < 2.5f ? 1f : Math.max(0f, (3.0f - tt) / 0.5f);
        if (fade <= 0f) return;
        java.util.Random rnd = new java.util.Random(seed * 31L + (long) (tt * 10f));
        // Vector de visión aproximado (la cámara está en el origen): el rayo se dibuja de cara a ella.
        float vx = ox, vy = oy + 1f, vz = oz;
        float vl = Math.max(1e-3f, Mth.sqrt(vx * vx + vy * vy + vz * vz));
        vx /= vl; vy /= vl; vz /= vl;
        for (int i = 0; i < count; i++) {
            float th = rnd.nextFloat() * Mth.TWO_PI;
            float px = Mth.cos(th) * 0.45f, pz = Mth.sin(th) * 0.45f, py = 0.1f + rnd.nextFloat() * 1.7f;
            float dx = Mth.cos(th) * 0.8f, dy = 0.3f + rnd.nextFloat() * 0.6f, dz = Mth.sin(th) * 0.8f;
            float len = 1.0f + rnd.nextFloat() * 1.3f;
            int segs = 5;
            for (int s = 0; s < segs; s++) {
                float jx = (rnd.nextFloat() - 0.5f) * 0.36f, jy = (rnd.nextFloat() - 0.5f) * 0.36f, jz = (rnd.nextFloat() - 0.5f) * 0.36f;
                float sx = dx * len / segs + jx, sy = dy * len / segs + jy, sz = dz * len / segs + jz;
                // ancho = producto vectorial (segmento × visión)
                float wx = sy * vz - sz * vy, wy = sz * vx - sx * vz, wz = sx * vy - sy * vx;
                float wl = Math.max(1e-4f, Mth.sqrt(wx * wx + wy * wy + wz * wz));
                float hw = 0.035f * (1f - s / (float) segs) + 0.012f;
                wx = wx / wl * hw; wy = wy / wl * hw; wz = wz / wl * hw;
                float a = (0.95f - 0.12f * s) * fade * weight;
                vc.addVertex(m, ox + px - wx, oy + py - wy, oz + pz - wz).setColor(r, g, b, a);
                vc.addVertex(m, ox + px + wx, oy + py + wy, oz + pz + wz).setColor(r, g, b, a);
                px += sx; py += sy; pz += sz;
                vc.addVertex(m, ox + px + wx, oy + py + wy, oz + pz + wz).setColor(r, g, b, a * 0.8f);
                vc.addVertex(m, ox + px - wx, oy + py - wy, oz + pz - wz).setColor(r, g, b, a * 0.8f);
            }
        }
    }

    /** Onda expansiva en el suelo: uno o dos anillos planos que se abren y se apagan. */
    void submit(AuraFrame f, PoseStack pose) {
        if (t < 0f || type == Type.NONE) return;
        // Rayos del cuerpo (no hace falta suelo: transformarse en el aire también los saca). En 1ª persona propia taparían la vista.
        if (longSeq && !f.selfFirstPerson && t >= 0.9f * LONG_STRETCH) {
            final float bt = t;
            final float bx = (float) (f.x - f.camX), by = (float) (f.y - f.camY), bz = (float) (f.z - f.camZ);
            final float br = hotColor[0], bg = hotColor[1], bb = hotColor[2];
            final boolean bOk = f.bloomAllowed;
            KiVfxFrameQueue.submitAura(pose, new Vec3(bx, by, bz), (ps, buf) -> {
                boolean bloom = KiVfxFrameQueue.bloomPass();
                if (bloom && !bOk) return;
                drawBolts(buf.getBuffer(AuraComposeShaders.ribbons()), ps.last().pose(), bx, by, bz, bt / LONG_STRETCH, br, bg, bb, bloom ? 0.6f : 1f);
            });
        }
        if (!hasGround) return;
        final float tt = t;
        final Type ty = type;
        final float ox = (float) (f.x - f.camX), oz = (float) (f.z - f.camZ);
        final float oy = (float) (groundY + 0.05 - f.camY);
        final float r = ringColor[0], g = ringColor[1], b = ringColor[2];
        final float maxR = (ty == Type.VIOLENT ? 9f : ty == Type.CALM_SHIFT ? 4f : 7f) * Mth.lerp(0.5f, 1f, f.powerScale);
        final boolean bloomOk = f.bloomAllowed;
        final boolean lng = longSeq;
        KiVfxFrameQueue.submitAura(pose, new Vec3(ox, oy, oz), (ps, buf) -> {
            boolean bloom = KiVfxFrameQueue.bloomPass();
            if (bloom && !bloomOk) return;
            VertexConsumer vc = buf.getBuffer(AuraComposeShaders.ribbons());
            Matrix4f m = ps.last().pose();
            if (lng) {
                drawCracks(vc, m, ox, oy, oz, tt / LONG_STRETCH, r, g, b, bloom ? 0.6f : 1f);
                // Una onda por pulso, cada una más ancha que la anterior.
                for (int i = 0; i < LONG_BEAT_AT.length; i++) {
                    ring(vc, m, ox, oy, oz, tt - LONG_BEAT_AT[i], maxR * LONG_RING_RADIUS[i], ty, r, g, b, bloom ? 0.6f : 1f);
                }
                return;
            }
            float start = ty == Type.HEAVY ? 0.2f : 0f;
            ring(vc, m, ox, oy, oz, tt - start, maxR, ty, r, g, b, bloom ? 0.6f : 1f);
            if (ty == Type.VIOLENT) ring(vc, m, ox, oy, oz, tt - 0.18f, maxR * 0.75f, ty, r, g, b, bloom ? 0.6f : 1f);
        });
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float t, float maxR,
                             Type ty, float r, float g, float b, float weight) {
        if (t <= 0f) return;
        float life = 0.8f;
        if (t > life) return;
        float k = 1f - (float) Math.exp(-t * 5.0);
        float outer = 0.4f + maxR * k;
        float width = (ty == Type.CALM_SHIFT ? 0.12f : 0.35f) * (1f - t / life) + 0.08f;
        float inner = Math.max(0f, outer - width);
        float a = (ty == Type.CALM_SHIFT ? 0.5f : 0.85f) * (1f - t / life) * weight;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / RING_SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / RING_SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            vc.addVertex(m, ox + c0 * inner, oy, oz + s0 * inner).setColor(r, g, b, a * 0.4f);
            vc.addVertex(m, ox + c0 * outer, oy, oz + s0 * outer).setColor(r, g, b, a);
            vc.addVertex(m, ox + c1 * outer, oy, oz + s1 * outer).setColor(r, g, b, a);
            vc.addVertex(m, ox + c1 * inner, oy, oz + s1 * inner).setColor(r, g, b, a * 0.4f);
        }
    }
}
