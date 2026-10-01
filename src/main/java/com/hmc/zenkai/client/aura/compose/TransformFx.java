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
 */
final class TransformFx {

    enum Type {
        NONE(0f, 0f, 0, 0, false, 0f),
        BURST(0.45f, 0.35f, 24, 6, false, 0.6f),
        ELECTRIC_BURST(0.45f, 0.35f, 20, 4, false, 1f),
        RADIANT(0.3f, 0.2f, 0, 0, false, 0.8f),
        VIOLENT(0.6f, 0.4f, 32, 12, true, 0.8f),
        HEAVY(0.25f, 0.2f, 28, 6, true, 0.5f),
        CALM_SHIFT(0.15f, 0.1f, 0, 0, false, 0.5f);

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
    private static final int RING_SEGMENTS = 48;

    private Type type = Type.NONE;
    private float t = -1f;
    private boolean hasGround;
    private double groundY;
    private final float[] ringColor = new float[3];

    boolean running() { return t >= 0f; }

    void trigger(Type type, AuraFrame f, List<AuraComponent> components) {
        if (type == Type.NONE) return;
        this.type = type;
        this.t = 0f;
        Minecraft mc = Minecraft.getInstance();
        AuraChargeGroundFx.Ground g = mc.level == null ? null
                : AuraChargeGroundFx.findGround(mc.level, f.player, f.x, f.y, f.z);
        // Onda y ráfaga de suelo solo PISANDO el suelo: transformarse en el aire no levanta polvo
        // ni dibuja un anillo bajo los pies (pedido del usuario, 2026-09-30).
        if (!f.player.onGround()) g = null;
        hasGround = g != null;
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
        if (t > DURATION) {
            t = -1f;
            return;
        }
        float scale;
        float decay;
        if (type == Type.HEAVY) {
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
    }

    /** Onda expansiva en el suelo: uno o dos anillos planos que se abren y se apagan. */
    void submit(AuraFrame f, PoseStack pose) {
        if (t < 0f || !hasGround || type == Type.NONE) return;
        final float tt = t;
        final Type ty = type;
        final float ox = (float) (f.x - f.camX), oz = (float) (f.z - f.camZ);
        final float oy = (float) (groundY + 0.05 - f.camY);
        final float r = ringColor[0], g = ringColor[1], b = ringColor[2];
        final float maxR = (ty == Type.VIOLENT ? 9f : ty == Type.CALM_SHIFT ? 4f : 7f) * Mth.lerp(0.5f, 1f, f.powerScale);
        final boolean bloomOk = f.bloomAllowed;
        KiVfxFrameQueue.submitAura(pose, new Vec3(ox, oy, oz), (ps, buf) -> {
            boolean bloom = KiVfxFrameQueue.bloomPass();
            if (bloom && !bloomOk) return;
            VertexConsumer vc = buf.getBuffer(AuraComposeShaders.ribbons());
            Matrix4f m = ps.last().pose();
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
