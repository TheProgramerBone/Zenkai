package com.hmc.zenkai.client.aura.compose.component;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.hmc.zenkai.Zenkai;
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
 * MOTES: partículas propias del aura (billboards del atlas {@code aura_motes.png}, nunca
 * ParticleTypes vanilla — FLAME se veía fuera de lugar). Cada familia las usa con un COMPORTAMIENTO
 * distinto, que es lo que las hace leerse como cosas diferentes y no como el mismo punto de otro
 * color:
 * <ul>
 *   <li>{@link Behavior#RISE}: chispas que salen disparadas hacia arriba y se estiran a lo largo de
 *       su velocidad (llama, SSJ).</li>
 *   <li>{@link Behavior#FLOAT}: ascuas que suben despacio meciéndose y titilan (divina, SSG).</li>
 *   <li>{@link Behavior#DRIFT}: motas grandes y pesadas que se alejan despacio y caen un poco
 *       (oscura, Ultra Ego).</li>
 *   <li>{@link Behavior#GLINT}: destellos quietos que crecen y se apagan (Ultra Instinct).</li>
 *   <li>{@link Behavior#EMBER}: brasas divinas (SSG, 2026-10-27): suben lentas y constantes, derivan
 *       de lado al azar y se encogen y apagan a lo largo de la vida entera.</li>
 * </ul>
 * Viven en coordenadas de MUNDO: al moverse o volar, las que ya nacieron se quedan atrás.
 * Pool fijo de {@link #CAP} por componente y arrays primitivos: sin asignaciones por frame.
 */
public final class MoteComponent implements AuraComponent {

    public static final String TYPE = "motes";
    private static final int CAP = 64;

    public enum Behavior { RISE, FLOAT, DRIFT, GLINT, EMBER }

    public enum Sprite { SPARK, EMBER, MOTE, GLINT, CUBE }

    public enum Tint { INNER, OUTER, HOT, WHITE, FIXED }

    /**
     * @param rate    motes por segundo con el aura encendida por completo, a poder base.
     * @param life    segundos de vida [min, max].
     * @param size    medio lado del billboard en bloques [min, max] (a poder base).
     * @param speed   bloques por segundo [min, max].
     * @param radius  distancia al eje del cuerpo al nacer [min, max] (a poder base).
     * @param height  altura sobre los pies al nacer [min, max] (a poder base).
     * @param alpha   opacidad máxima.
     * @param stretch largo/ancho de una chispa RISE estirada por su velocidad (1 = sin estirar).
     * @param bloom   peso en la pasada de bloom (0 = no brilla; las motas oscuras no deben).
     */
    public record Config(Behavior behavior, Sprite sprite, float rate, float[] life, float[] size, float[] speed,
                         float[] radius, float[] height, Tint tint, float[] fixedRgb, float alpha, float stretch,
                         float bloom) implements AuraComponentConfig {

        private static final Set<String> KEYS = Set.of("behavior", "sprite", "rate", "life", "size", "speed",
                "radius", "height", "color", "alpha", "stretch", "bloom");

        @Override public String type() { return TYPE; }

        @Override public AuraComponent create() { return new MoteComponent(this); }

        public static Config parse(JsonObject o) {
            String unknown = AuraComponentTypes.unknownKeys(o, KEYS);
            if (unknown != null) Zenkai.LOGGER.warn("[Zenkai] Motes de aura: claves desconocidas ignoradas: {}", unknown);
            Behavior b = parseEnum(Behavior.class, AuraComponentTypes.s(o, "behavior", "rise"));
            Sprite sp = parseEnum(Sprite.class, AuraComponentTypes.s(o, "sprite", "spark"));
            String color = AuraComponentTypes.s(o, "color", "hot");
            Tint tint;
            float[] fixed = {1f, 1f, 1f};
            if (color.startsWith("#")) {
                tint = Tint.FIXED;
                int rgb = Integer.parseInt(color.substring(1), 16);
                fixed = new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
            } else {
                tint = parseEnum(Tint.class, color);
            }
            return new Config(b, sp,
                    Math.max(0f, AuraComponentTypes.f(o, "rate", 8f)),
                    AuraComponentTypes.range(o, "life", 0.4f, 0.8f),
                    AuraComponentTypes.range(o, "size", 0.05f, 0.08f),
                    AuraComponentTypes.range(o, "speed", 1f, 2f),
                    AuraComponentTypes.range(o, "radius", 0.3f, 0.8f),
                    AuraComponentTypes.range(o, "height", 0.1f, 1.7f),
                    tint, fixed,
                    Mth.clamp(AuraComponentTypes.f(o, "alpha", 1f), 0f, 1f),
                    Math.max(1f, AuraComponentTypes.f(o, "stretch", 1f)),
                    Math.max(0f, AuraComponentTypes.f(o, "bloom", 0.8f)));
        }

        private static <E extends Enum<E>> E parseEnum(Class<E> cls, String name) {
            try {
                return Enum.valueOf(cls, name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new JsonParseException("valor desconocido \"" + name + "\" para " + cls.getSimpleName());
            }
        }
    }

    private final Config cfg;
    private final RandomSource rng = RandomSource.create();
    private final double[] px = new double[CAP], py = new double[CAP], pz = new double[CAP];
    private final float[] vx = new float[CAP], vy = new float[CAP], vz = new float[CAP];
    private final float[] age = new float[CAP], life = new float[CAP], size = new float[CAP], phase = new float[CAP];
    private int count;
    private float carry;
    /** Color de este frame. */
    private float cr = 1f, cg = 1f, cb = 1f;

    private MoteComponent(Config cfg) { this.cfg = cfg; }

    @Override public String type() { return TYPE; }

    @Override public boolean alive() { return count > 0; }

    @Override
    public void update(AuraFrame f) {
        float dt = f.dt;
        float[] c = switch (cfg.tint()) {
            case INNER -> f.inner;
            case OUTER -> f.outer;
            case HOT -> f.hot;
            case WHITE, FIXED -> cfg.fixedRgb();
        };
        cr = c[0];
        cg = c[1];
        cb = c[2];

        // Nacer: solo con el aura encendida. A poder alto hay más (presencia), acotado — el
        // tamaño del aura ya lleva el poder; esto solo lo acompaña.
        if (f.active && f.growth > 0.05f && f.lod != AuraBodyMesh.LOD_FAR) {
            float lodF = f.lod == AuraBodyMesh.LOD_MID ? 0.5f : 1f;
            float rate = cfg.rate() * f.growth * lodF * f.dynamics.emissionFactor()
                    * Mth.clamp(f.powerF, 0.8f, 2f);
            carry += rate * dt;
            while (carry >= 1f && count < CAP) {
                spawn(f);
                carry -= 1f;
            }
            if (count >= CAP) carry = 0f;
        } else {
            carry = 0f;
        }

        // Moverse y morir (swap con el último: el orden no importa, van en coordenadas de mundo).
        for (int i = 0; i < count; ) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                remove(i);
                continue;
            }
            switch (cfg.behavior()) {
                case RISE -> {
                    vy[i] += 1.5f * dt;
                    vx[i] *= 1f - 0.8f * dt;
                    vz[i] *= 1f - 0.8f * dt;
                }
                case FLOAT -> {
                    // Vaivén lateral propio de cada ascua.
                    float sway = Mth.sin(phase[i] + age[i] * 3f) * 0.35f;
                    px[i] += sway * dt * Mth.cos(phase[i]);
                    pz[i] += sway * dt * Mth.sin(phase[i]);
                }
                case DRIFT -> {
                    vx[i] *= 1f - 0.6f * dt;
                    vz[i] *= 1f - 0.6f * dt;
                    vy[i] -= 0.12f * dt;
                }
                case GLINT -> { }
                case EMBER -> {
                    // Deriva lateral al azar, amortiguada: flotan sin salir disparadas.
                    vx[i] += (rng.nextFloat() - 0.5f) * 0.8f * dt;
                    vz[i] += (rng.nextFloat() - 0.5f) * 0.8f * dt;
                    vx[i] *= 1f - 0.5f * dt;
                    vz[i] *= 1f - 0.5f * dt;
                }
            }
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
            i++;
        }
    }

    /** Estallido de transformación: una tanda de motas de golpe (hasta llenar el pool). */
    @Override
    public void onBurst(AuraFrame f, float strength) {
        int n = Math.round(24f * strength);
        for (int i = 0; i < n && count < CAP; i++) spawn(f);
    }

    private void spawn(AuraFrame f) {
        int i = count++;
        float ang = rng.nextFloat() * Mth.TWO_PI;
        float r = lerp(cfg.radius()) * f.powerScale;
        float h = lerp(cfg.height()) * f.powerScale;
        float ca = Mth.cos(ang), sa = Mth.sin(ang);
        px[i] = f.x + ca * r;
        py[i] = f.y + h;
        pz[i] = f.z + sa * r;
        float sp = lerp(cfg.speed());
        switch (cfg.behavior()) {
            case RISE -> { vx[i] = ca * sp * 0.25f; vy[i] = sp; vz[i] = sa * sp * 0.25f; }
            case FLOAT -> { vx[i] = ca * sp * 0.1f; vy[i] = sp; vz[i] = sa * sp * 0.1f; }
            case DRIFT -> { vx[i] = ca * sp; vy[i] = (rng.nextFloat() - 0.5f) * 0.2f; vz[i] = sa * sp; }
            case GLINT -> { vx[i] = 0f; vy[i] = sp * 0.1f; vz[i] = 0f; }
            case EMBER -> { vx[i] = ca * sp * 0.08f; vy[i] = sp; vz[i] = sa * sp * 0.08f; }
        }
        age[i] = 0f;
        life[i] = lerp(cfg.life());
        size[i] = lerp(cfg.size()) * Mth.lerp(0.5f, 1f, f.powerScale);
        phase[i] = rng.nextFloat() * Mth.TWO_PI;
    }

    private void remove(int i) {
        int last = --count;
        px[i] = px[last]; py[i] = py[last]; pz[i] = pz[last];
        vx[i] = vx[last]; vy[i] = vy[last]; vz[i] = vz[last];
        age[i] = age[last]; life[i] = life[last]; size[i] = size[last]; phase[i] = phase[last];
    }

    private float lerp(float[] range) {
        return range[0] + (range[1] - range[0]) * rng.nextFloat();
    }

    /** Opacidad según la edad, con la curva de cada comportamiento. */
    private float fade(int i) {
        float t = age[i] / life[i];
        return switch (cfg.behavior()) {
            case RISE -> t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
            case FLOAT -> Math.min(t / 0.2f, 1f) * Math.min((1f - t) / 0.4f, 1f)
                    * (0.75f + 0.25f * Mth.sin(age[i] * 20f + phase[i]));
            case DRIFT -> Math.min(t / 0.25f, 1f) * Math.min((1f - t) / 0.5f, 1f);
            case GLINT -> Mth.sin(t * Mth.PI);
            case EMBER -> Math.min(t / 0.15f, 1f) * (1f - t);
        };
    }

    @Override
    public void submit(AuraFrame f, PoseStack pose) {
        if (count == 0) return;
        final double camX = f.camX, camY = f.camY, camZ = f.camZ;
        final float lx = f.leftX, ly = f.leftY, lz = f.leftZ, ux = f.upX, uy = f.upY, uz = f.upZ;
        final float fp = f.fpOpacity;
        final boolean bloomOk = f.bloomAllowed;
        KiVfxFrameQueue.submitAura(pose, new Vec3(f.x - camX, f.y - camY, f.z - camZ), (ps, buf) -> {
            boolean bloom = KiVfxFrameQueue.bloomPass();
            if (bloom && (!bloomOk || cfg.bloom() <= 0f)) return;
            float weight = bloom ? cfg.bloom() : 1f;
            VertexConsumer vc = buf.getBuffer(AuraComposeShaders.motes());
            Matrix4f m = ps.last().pose();
            float u0 = cfg.sprite().ordinal() / 5f, u1 = (cfg.sprite().ordinal() + 1) / 5f;
            for (int i = 0; i < count; i++) {
                float a = Mth.clamp(cfg.alpha() * fade(i) * fp * weight, 0f, 1f);
                if (a <= 0.01f) continue;
                float x = (float) (px[i] - camX), y = (float) (py[i] - camY), z = (float) (pz[i] - camZ);
                float s = size[i] * switch (cfg.behavior()) {
                    case GLINT -> 0.4f + 0.6f * fade(i);
                    case EMBER -> 1f - 0.7f * age[i] / life[i];
                    default -> 1f;
                };
                if (cfg.behavior() == Behavior.RISE && cfg.stretch() > 1f) {
                    quadAlong(vc, m, x, y, z, i, s, u0, u1, a);
                } else {
                    float ax = lx * s, ay = ly * s, az = lz * s;
                    float bx = ux * s, by = uy * s, bz = uz * s;
                    vc.addVertex(m, x - ax - bx, y - ay - by, z - az - bz).setUv(u1, 1f).setColor(cr, cg, cb, a);
                    vc.addVertex(m, x + ax - bx, y + ay - by, z + az - bz).setUv(u0, 1f).setColor(cr, cg, cb, a);
                    vc.addVertex(m, x + ax + bx, y + ay + by, z + az + bz).setUv(u0, 0f).setColor(cr, cg, cb, a);
                    vc.addVertex(m, x - ax + bx, y - ay + by, z - az + bz).setUv(u1, 0f).setColor(cr, cg, cb, a);
                }
            }
        });
    }

    /** Chispa estirada a lo largo de su velocidad, de cara a la cámara (eje = velocidad, ancho =
     *  perpendicular a velocidad y a la visual). */
    private void quadAlong(VertexConsumer vc, Matrix4f m, float x, float y, float z, int i, float s,
                           float u0, float u1, float a) {
        float dx = vx[i], dy = vy[i], dz = vz[i];
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4f) { dx = 0f; dy = 1f; dz = 0f; len = 1f; }
        dx /= len; dy /= len; dz /= len;
        // Visual: de la chispa a la cámara (la cámara está en el origen de este espacio).
        float tx = -x, ty = -y, tz = -z;
        float wx = dy * tz - dz * ty, wy = dz * tx - dx * tz, wz = dx * ty - dy * tx;
        float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl < 1.0e-4f) return;
        float w = s * 0.5f / wl;
        wx *= w; wy *= w; wz *= w;
        float h = s * cfg.stretch() * 0.5f;
        float hx = dx * h, hy = dy * h, hz = dz * h;
        vc.addVertex(m, x - wx - hx, y - wy - hy, z - wz - hz).setUv(u0, 1f).setColor(cr, cg, cb, a);
        vc.addVertex(m, x + wx - hx, y + wy - hy, z + wz - hz).setUv(u1, 1f).setColor(cr, cg, cb, a);
        vc.addVertex(m, x + wx + hx, y + wy + hy, z + wz + hz).setUv(u1, 0f).setColor(cr, cg, cb, a);
        vc.addVertex(m, x - wx + hx, y - wy + hy, z - wz + hz).setUv(u0, 0f).setColor(cr, cg, cb, a);
    }
}
