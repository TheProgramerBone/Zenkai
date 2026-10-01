package com.hmc.zenkai.client.aura.compose.component;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.compose.AuraAnchor;
import com.hmc.zenkai.client.aura.compose.AuraBodyMesh;
import com.hmc.zenkai.client.aura.compose.AuraComposeShaders;
import com.hmc.zenkai.client.aura.compose.AuraFrame;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.Set;

/**
 * WISPS: lenguas de energía que se desprenden del aura, como CINTAS con estela (no puntos): la
 * cabeza se mueve y deja detrás una cola que se estrecha y se apaga. El comportamiento las hace
 * leerse distinto en cada familia:
 * <ul>
 *   <li>{@link Behavior#RISE}: suben desde la superficie de la cáscara meciéndose (divina).</li>
 *   <li>{@link Behavior#FLOW}: nacen pegadas a una parte del cuerpo y trepan enroscándose,
 *       finas y cortas (Ultra Instinct, aura_12/aura_13).</li>
 *   <li>{@link Behavior#SINK}: gruesas y pesadas, salen hacia fuera y CAEN rodando (oscura).</li>
 * </ul>
 * NO son las wisps legacy (AuraWispRenderer), que sobre la llama de vela se leían como una
 * columna saliendo de la cabeza (2026-09-30): estas nacen en la superficie o el cuerpo, viven poco
 * y se curvan. Pool fijo, estela en arrays de mundo, sin asignaciones por frame. En primera
 * persona propia no se dibujan.
 */
public final class WispComponent implements AuraComponent {

    public static final String TYPE = "wisps";
    private static final int CAP = 12;
    private static final int TRAIL = 10;

    public enum Behavior { RISE, FLOW, SINK }

    public record Config(Behavior behavior, float rate, float[] life, float[] width, float[] speed, float curl,
                         float spacing, TintSpec color, float alpha, float bloom) implements AuraComponentConfig {

        private static final Set<String> KEYS = Set.of("behavior", "rate", "life", "width", "speed", "curl",
                "spacing", "color", "alpha", "bloom");

        @Override public String type() { return TYPE; }

        @Override public AuraComponent create() { return new WispComponent(this); }

        public static Config parse(JsonObject o) {
            String unknown = AuraComponentTypes.unknownKeys(o, KEYS);
            if (unknown != null) Zenkai.LOGGER.warn("[Zenkai] Wisps de aura: claves desconocidas ignoradas: {}", unknown);
            Behavior b;
            try {
                b = Behavior.valueOf(AuraComponentTypes.s(o, "behavior", "rise").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new JsonParseException("behavior de wisps desconocido (rise, flow, sink)");
            }
            return new Config(b,
                    Math.max(0f, AuraComponentTypes.f(o, "rate", 3f)),
                    AuraComponentTypes.range(o, "life", 0.6f, 1.1f),
                    AuraComponentTypes.range(o, "width", 0.05f, 0.09f),
                    AuraComponentTypes.range(o, "speed", 0.8f, 1.4f),
                    AuraComponentTypes.f(o, "curl", 2f),
                    Math.max(0.02f, AuraComponentTypes.f(o, "spacing", 0.07f)),
                    TintSpec.parse(AuraComponentTypes.s(o, "color", "outer")),
                    Mth.clamp(AuraComponentTypes.f(o, "alpha", 0.75f), 0f, 1f),
                    Math.max(0f, AuraComponentTypes.f(o, "bloom", 0.6f)));
        }
    }

    private final Config cfg;
    private final RandomSource rng = RandomSource.create();
    /** Estela en mundo: [wisp][punto*3], el 0 es la cabeza. */
    private final double[][] trail = new double[CAP][TRAIL * 3];
    private final int[] points = new int[CAP];
    private final float[] vx = new float[CAP], vy = new float[CAP], vz = new float[CAP];
    private final float[] age = new float[CAP], life = new float[CAP], width = new float[CAP], phase = new float[CAP];
    private int count;
    private float carry;
    private float cr, cg, cb;

    private WispComponent(Config cfg) { this.cfg = cfg; }

    @Override public String type() { return TYPE; }

    @Override public boolean alive() { return count > 0; }

    private float lerp(float[] r) { return r[0] + (r[1] - r[0]) * rng.nextFloat(); }

    @Override
    public void update(AuraFrame f) {
        float[] c = cfg.color().of(f);
        cr = c[0]; cg = c[1]; cb = c[2];
        float dt = f.dt;
        if (f.active && f.growth > 0.2f && f.lod != AuraBodyMesh.LOD_FAR) {
            float lodF = f.lod == AuraBodyMesh.LOD_MID ? 0.5f : 1f;
            carry += cfg.rate() * lodF * f.growth * f.dynamics.emissionFactor() * dt;
            while (carry >= 1f) {
                carry -= 1f;
                if (count < CAP) spawn(f);
            }
        } else {
            carry = 0f;
        }
        for (int i = 0; i < count; ) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                remove(i);
                continue;
            }
            // Enroscarse: giro lateral propio de cada wisp.
            float swirl = age[i] * 2.5f + phase[i];
            vx[i] += Mth.cos(swirl) * cfg.curl() * dt;
            vz[i] += Mth.sin(swirl) * cfg.curl() * dt;
            switch (cfg.behavior()) {
                case RISE -> { vx[i] *= 1f - 1.2f * dt; vz[i] *= 1f - 1.2f * dt; }
                case FLOW -> { vx[i] *= 1f - 2.5f * dt; vz[i] *= 1f - 2.5f * dt; vy[i] += 0.4f * dt; }
                case SINK -> { vy[i] -= 0.9f * dt; vx[i] *= 1f - 0.5f * dt; vz[i] *= 1f - 0.5f * dt; }
            }
            double[] t = trail[i];
            double hx = t[0] + vx[i] * dt, hy = t[1] + vy[i] * dt, hz = t[2] + vz[i] * dt;
            // La cola se desplaza solo cuando la cabeza se alejó "spacing": una cinta de largo
            // parecido a cualquier velocidad de frames.
            double ddx = hx - t[3], ddy = hy - t[4], ddz = hz - t[5];
            if (points[i] < 2 || ddx * ddx + ddy * ddy + ddz * ddz > cfg.spacing() * cfg.spacing()) {
                System.arraycopy(t, 0, t, 3, (TRAIL - 1) * 3);
                if (points[i] < TRAIL) points[i]++;
            }
            t[0] = hx; t[1] = hy; t[2] = hz;
            i++;
        }
    }

    private void spawn(AuraFrame f) {
        int i = count++;
        double x, y, z;
        float sp = lerp(cfg.speed()) * Mth.lerp(0.5f, 1f, f.powerScale);
        float ang = rng.nextFloat() * Mth.TWO_PI;
        float ca = Mth.cos(ang), sa = Mth.sin(ang);
        switch (cfg.behavior()) {
            case FLOW -> {
                AuraAnchor.Pose ap = f.anchor;
                int seg = rng.nextInt(AuraAnchor.SEGMENTS);
                float t = rng.nextFloat();
                // Por FUERA de la cápsula hinchada del cuerpo FLOW: su pre-pasada de profundidad
                // taparía una wisp que naciera dentro.
                float inflate = f.body == null ? 0.5f : f.body.get(com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key.INFLATE);
                float r = ap.r[seg] * (1.15f + inflate);
                x = Mth.lerp(t, ap.ax[seg], ap.bx[seg]) + ca * r;
                y = Mth.lerp(t, ap.ay[seg], ap.by[seg]);
                z = Mth.lerp(t, ap.az[seg], ap.bz[seg]) + sa * r;
                vx[i] = ca * sp * 0.1f; vy[i] = sp; vz[i] = sa * sp * 0.1f;
            }
            case SINK -> {
                float r = f.shellRadius() * 0.9f;
                x = f.x + ca * r;
                y = f.y + (0.5f + rng.nextFloat() * 0.5f) * f.shellTop();
                z = f.z + sa * r;
                vx[i] = ca * sp * 0.4f; vy[i] = -sp * 0.2f; vz[i] = sa * sp * 0.4f;
            }
            default -> {
                float r = f.shellRadius() * (0.6f + 0.4f * rng.nextFloat());
                x = f.x + ca * r;
                y = f.y + (0.15f + rng.nextFloat() * 0.6f) * f.shellTop();
                z = f.z + sa * r;
                vx[i] = ca * sp * 0.15f; vy[i] = sp; vz[i] = sa * sp * 0.15f;
            }
        }
        double[] t = trail[i];
        for (int k = 0; k < TRAIL; k++) { t[k * 3] = x; t[k * 3 + 1] = y; t[k * 3 + 2] = z; }
        points[i] = 1;
        age[i] = 0f;
        life[i] = lerp(cfg.life());
        width[i] = lerp(cfg.width()) * Mth.lerp(0.5f, 1f, f.powerScale);
        phase[i] = rng.nextFloat() * Mth.TWO_PI;
    }

    private void remove(int i) {
        int last = --count;
        if (i == last) return;
        double[] tt = trail[i]; trail[i] = trail[last]; trail[last] = tt;
        points[i] = points[last];
        vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last];
        age[i] = age[last]; life[i] = life[last]; width[i] = width[last]; phase[i] = phase[last];
    }

    @Override
    public void submit(AuraFrame f, PoseStack pose) {
        if (count == 0 || f.selfFirstPerson) return;
        final double camX = f.camX, camY = f.camY, camZ = f.camZ;
        final boolean bloomOk = f.bloomAllowed;
        KiVfxFrameQueue.submitAura(pose, new Vec3(f.x - camX, f.y - camY, f.z - camZ), (ps, buf) -> {
            boolean bloom = KiVfxFrameQueue.bloomPass();
            if (bloom && (!bloomOk || cfg.bloom() <= 0f)) return;
            float weight = bloom ? cfg.bloom() : 1f;
            VertexConsumer vc = buf.getBuffer(AuraComposeShaders.ribbons());
            Matrix4f m = ps.last().pose();
            for (int i = 0; i < count; i++) {
                int n = points[i];
                if (n < 2) continue;
                float t = age[i] / life[i];
                float a = cfg.alpha() * weight * Math.min(t / 0.15f, 1f) * Math.min((1f - t) / 0.4f, 1f);
                if (a <= 0.02f) continue;
                double[] tr = trail[i];
                for (int k = 0; k < n - 1; k++) {
                    float f0 = k / (float) (n - 1), f1 = (k + 1) / (float) (n - 1);
                    int o0 = k * 3, o1 = o0 + 3;
                    // Cabeza ancha y clara, cola fina y apagada.
                    Ribbons.segment(vc, m,
                            (float) (tr[o0] - camX), (float) (tr[o0 + 1] - camY), (float) (tr[o0 + 2] - camZ),
                            (float) (tr[o1] - camX), (float) (tr[o1 + 1] - camY), (float) (tr[o1 + 2] - camZ),
                            width[i] * (1f - f0), width[i] * (1f - f1), cr, cg, cb, a * (1f - f0), a * (1f - f1));
                }
            }
        });
    }
}
