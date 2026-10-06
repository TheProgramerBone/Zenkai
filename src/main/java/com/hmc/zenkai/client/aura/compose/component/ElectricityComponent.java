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
import net.minecraft.world.phys.Vec3;
import com.hmc.zenkai.client.aura.AuraTiltController;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Set;

/**
 * ELECTRICIDAD del aura como componente propio (SSJ2, Beast, Rosé), no "si ssj2 → rayos vanilla".
 * Cada rayo es una polilínea fractal ({@link LightningPath}) con ramas, que vive unas centésimas,
 * CREPITA en el sitio (se regenera con otra semilla manteniendo los extremos, {@code crackle_hz}) y
 * se dibuja en dos capas: un halo ancho del color del aura y un núcleo fino casi blanco (el
 * rayo de anime: aura_9/aura_10, aura_11).
 * <p>
 * Dónde nace ({@code attach}) es lo que lo integra en el aura en vez de dejarlo flotando:
 * <ul>
 *   <li>{@code body}: de una parte del cuerpo a otra, pegado a la piel (Beast, aura_9);</li>
 *   <li>{@code body_to_shell}: del cuerpo hacia fuera, hasta el borde de la cáscara (SSJ2);</li>
 *   <li>{@code shell}: reptando por la superficie de la cáscara;</li>
 *   <li>{@code mixed}: cualquiera de los tres al azar.</li>
 * </ul>
 * Las posiciones se guardan RELATIVAS a los pies y se dibujan donde está el jugador ahora: un rayo
 * vive tan poco que seguir al cuerpo así basta. Pools y arrays fijos, sin asignaciones por frame.
 * En primera persona propia no se dibuja (los rayos del propio cuerpo taparían la vista).
 */
public final class ElectricityComponent implements AuraComponent {

    public static final String TYPE = "electricity";
    private static final int MAX = 12;
    private static final int MAIN_DEPTH = 4;
    private static final int BRANCH_DEPTH = 2;
    private static final int MAIN_PTS = LightningPath.points(MAIN_DEPTH);
    private static final int BRANCH_PTS = LightningPath.points(BRANCH_DEPTH);

    public enum Attach { BODY, BODY_TO_SHELL, SHELL, MIXED }

    public record Config(float rate, int max, int branches, float[] length, float thickness, float[] life,
                         Attach attach, TintSpec color, TintSpec core, float alpha, float roughness,
                         float crackleHz, float bloom) implements AuraComponentConfig {

        private static final Set<String> KEYS = Set.of("rate", "max", "branches", "length", "thickness", "life",
                "attach", "color", "core_color", "alpha", "roughness", "crackle_hz", "bloom");

        @Override public String type() { return TYPE; }

        @Override public AuraComponent create() { return new ElectricityComponent(this); }

        public static Config parse(JsonObject o) {
            String unknown = AuraComponentTypes.unknownKeys(o, KEYS);
            if (unknown != null) Zenkai.LOGGER.warn("[Zenkai] Electricidad de aura: claves desconocidas ignoradas: {}", unknown);
            Attach attach;
            try {
                attach = Attach.valueOf(AuraComponentTypes.s(o, "attach", "body_to_shell").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new JsonParseException("attach desconocido (body, body_to_shell, shell, mixed)");
            }
            return new Config(
                    Math.max(0f, AuraComponentTypes.f(o, "rate", 6f)),
                    Mth.clamp((int) AuraComponentTypes.f(o, "max", 8f), 1, MAX),
                    Mth.clamp((int) AuraComponentTypes.f(o, "branches", 1f), 0, 2),
                    AuraComponentTypes.range(o, "length", 0.5f, 1.2f),
                    Math.max(0.005f, AuraComponentTypes.f(o, "thickness", 0.035f)),
                    AuraComponentTypes.range(o, "life", 0.08f, 0.2f),
                    attach,
                    TintSpec.parse(AuraComponentTypes.s(o, "color", "inner")),
                    TintSpec.parse(AuraComponentTypes.s(o, "core_color", "white")),
                    Mth.clamp(AuraComponentTypes.f(o, "alpha", 1f), 0f, 1f),
                    Mth.clamp(AuraComponentTypes.f(o, "roughness", 0.28f), 0.05f, 0.6f),
                    Math.max(1f, AuraComponentTypes.f(o, "crackle_hz", 20f)),
                    Math.max(0f, AuraComponentTypes.f(o, "bloom", 1f)));
        }
    }

    private final Config cfg;
    private final float[][] main = new float[MAX][MAIN_PTS * 3];
    private final float[][][] branch = new float[MAX][2][BRANCH_PTS * 3];
    /** Extremos relativos a los pies: inicio xyz, fin xyz, y fin de cada rama (xyz ×2). */
    private final float[][] ends = new float[MAX][12];
    private final float[] age = new float[MAX], life = new float[MAX], crackle = new float[MAX];
    private final long[] seed = new long[MAX];
    private int count;
    private float carry;
    private long rng = System.nanoTime();
    private float cr, cg, cb, kr, kg, kb;

    private ElectricityComponent(Config cfg) { this.cfg = cfg; }

    @Override public String type() { return TYPE; }

    @Override public boolean alive() { return count > 0; }

    private float rnd() {
        rng = LightningPath.next(rng);
        return LightningPath.frac(rng);
    }

    private float lerp(float[] r) { return r[0] + (r[1] - r[0]) * rnd(); }

    @Override
    public void update(AuraFrame f) {
        float[] c = cfg.color().of(f);
        cr = c[0]; cg = c[1]; cb = c[2];
        // Núcleo: el color pedido llevado casi a blanco — el rayo de anime tiene el centro blanco
        // y el color en el halo; nunca un blanco que se lave con el bloom (lo acota el alfa).
        float[] k = cfg.core().of(f);
        kr = Mth.lerp(0.75f, c[0], k[0]); kg = Mth.lerp(0.75f, c[1], k[1]); kb = Mth.lerp(0.75f, c[2], k[2]);

        float dt = f.dt;
        if (f.active && f.growth > 0.2f && f.lod != AuraBodyMesh.LOD_FAR) {
            float lodF = f.lod == AuraBodyMesh.LOD_MID ? 0.5f : 1f;
            float rate = cfg.rate() * lodF * f.growth * f.dynamics.electricityFactor();
            carry += rate * dt;
            while (carry >= 1f) {
                carry -= 1f;
                if (count < cfg.max()) spawn(f);
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
            crackle[i] += dt;
            if (crackle[i] >= 1f / cfg.crackleHz()) {
                crackle[i] = 0f;
                seed[i] = LightningPath.next(seed[i]);
                rebuild(i);
            }
            i++;
        }
    }

    @Override
    public void onBurst(AuraFrame f, float strength) {
        int n = Math.round(cfg.max() * strength);
        for (int i = 0; i < n && count < cfg.max(); i++) spawn(f);
    }

    private void spawn(AuraFrame f) {
        int i = count++;
        float[] e = ends[i];
        Attach a = cfg.attach();
        if (a == Attach.MIXED) a = Attach.values()[(int) (rnd() * 3f) % 3];
        AuraAnchor.Pose ap = f.anchor;
        float len = lerp(cfg.length()) * Mth.lerp(0.5f, 1f, f.powerScale);
        switch (a) {
            case BODY -> {
                // De la superficie de una parte a la de otra (o a otro punto de la misma).
                surfacePoint(f, ap, (int) (rnd() * AuraAnchor.SEGMENTS) % AuraAnchor.SEGMENTS, e, 0, 1.15f);
                surfacePoint(f, ap, (int) (rnd() * AuraAnchor.SEGMENTS) % AuraAnchor.SEGMENTS, e, 3, 1.15f);
                clampLength(e, len * 1.6f);
            }
            case BODY_TO_SHELL -> {
                surfacePoint(f, ap, (int) (rnd() * AuraAnchor.SEGMENTS) % AuraAnchor.SEGMENTS, e, 0, 1.1f);
                float hx = e[0], hz = e[2];
                float hl = Mth.sqrt(hx * hx + hz * hz);
                if (hl < 1.0e-3f) { float ang = rnd() * Mth.TWO_PI; hx = Mth.cos(ang); hz = Mth.sin(ang); hl = 1f; }
                float reach = Math.max(len, f.shellRadius() * 1.1f - hl);
                e[3] = e[0] + hx / hl * reach;
                e[4] = e[1] + (rnd() * 0.6f + 0.1f) * reach;
                e[5] = e[2] + hz / hl * reach;
            }
            case SHELL, MIXED -> {
                float r = f.shellRadius() * 1.02f;
                float a0 = rnd() * Mth.TWO_PI, a1 = a0 + (rnd() - 0.5f) * 1.6f;
                float h0 = (0.2f + rnd() * 0.8f) * f.shellTop();
                float h1 = Mth.clamp(h0 + (rnd() - 0.3f) * len, 0.1f, f.shellTop());
                e[0] = Mth.cos(a0) * r; e[1] = h0; e[2] = Mth.sin(a0) * r;
                e[3] = Mth.cos(a1) * r; e[4] = h1; e[5] = Mth.sin(a1) * r;
            }
        }
        // La cáscara se inclina con el vuelo (el cuerpo del aura gira con AuraTiltController), pero
        // estos puntos se calculan con la cáscara VERTICAL: sin girarlos, los rayos de cáscara
        // quedaban de pie con el jugador tumbado (bug reportado con captura, 2026-10-02). BODY no
        // se gira: sus extremos salen de la pose real del cuerpo (AuraAnchor), ya inclinada.
        Quaternionf tilt = AuraTiltController.currentRotation(f.player.getId());
        if (tilt != null && a != Attach.BODY) {
            Vector3f v = new Vector3f();
            if (a == Attach.BODY_TO_SHELL) {
                // El inicio está en el cuerpo real; solo se gira la dirección hacia la cáscara.
                v.set(e[3] - e[0], e[4] - e[1], e[5] - e[2]);
                tilt.transform(v);
                e[3] = e[0] + v.x; e[4] = e[1] + v.y; e[5] = e[2] + v.z;
            } else {
                for (int o = 0; o <= 3; o += 3) {
                    v.set(e[o], e[o + 1], e[o + 2]);
                    AuraTiltController.rotateAboutPivot(tilt, v);
                    e[o] = v.x; e[o + 1] = v.y; e[o + 2] = v.z;
                }
            }
        }
        age[i] = 0f;
        life[i] = lerp(cfg.life());
        crackle[i] = 0f;
        rng = LightningPath.next(rng);
        seed[i] = rng;
        rebuild(i);
    }

    /** Punto de la superficie de una cápsula, relativo a los pies, inflado {@code inflate} veces su radio. */
    private void surfacePoint(AuraFrame f, AuraAnchor.Pose ap, int seg, float[] out, int o, float inflate) {
        float t = rnd();
        double cx = Mth.lerp(t, ap.ax[seg], ap.bx[seg]) - f.x;
        double cy = Mth.lerp(t, ap.ay[seg], ap.by[seg]) - f.y;
        double cz = Mth.lerp(t, ap.az[seg], ap.bz[seg]) - f.z;
        float ang = rnd() * Mth.TWO_PI;
        float r = ap.r[seg] * inflate;
        out[o] = (float) cx + Mth.cos(ang) * r;
        out[o + 1] = (float) cy + (rnd() - 0.5f) * r;
        out[o + 2] = (float) cz + Mth.sin(ang) * r;
    }

    private static void clampLength(float[] e, float max) {
        float dx = e[3] - e[0], dy = e[4] - e[1], dz = e[5] - e[2];
        float l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (l > max && l > 1.0e-4f) {
            float k = max / l;
            e[3] = e[0] + dx * k; e[4] = e[1] + dy * k; e[5] = e[2] + dz * k;
        }
    }

    /** Regenera la polilínea (y sus ramas) con la semilla actual, mismos extremos. */
    private void rebuild(int i) {
        float[] e = ends[i];
        long s = seed[i];
        LightningPath.generate(main[i], MAIN_DEPTH, e[0], e[1], e[2], e[3], e[4], e[5], cfg.roughness(), s);
        for (int b = 0; b < cfg.branches(); b++) {
            s = LightningPath.next(s);
            int from = 2 + (int) (LightningPath.frac(s) * (MAIN_PTS - 4));
            float[] m = main[i];
            float bx = m[from * 3], by = m[from * 3 + 1], bz = m[from * 3 + 2];
            float dx = e[3] - e[0], dy = e[4] - e[1], dz = e[5] - e[2];
            s = LightningPath.next(s); float jx = LightningPath.unit(s);
            s = LightningPath.next(s); float jy = LightningPath.unit(s);
            s = LightningPath.next(s); float jz = LightningPath.unit(s);
            float k = 0.35f;
            LightningPath.generate(branch[i][b], BRANCH_DEPTH, bx, by, bz,
                    bx + dx * k + jx * 0.25f, by + dy * k + jy * 0.25f, bz + dz * k + jz * 0.25f,
                    cfg.roughness(), s);
        }
    }

    private void remove(int i) {
        int last = --count;
        if (i == last) return;
        float[] tm = main[i]; main[i] = main[last]; main[last] = tm;
        float[][] tb = branch[i]; branch[i] = branch[last]; branch[last] = tb;
        float[] te = ends[i]; ends[i] = ends[last]; ends[last] = te;
        age[i] = age[last]; life[i] = life[last]; crackle[i] = crackle[last]; seed[i] = seed[last];
    }

    @Override
    public void submit(AuraFrame f, PoseStack pose) {
        if (count == 0 || f.selfFirstPerson) return;
        final float ox = (float) (f.x - f.camX), oy = (float) (f.y - f.camY), oz = (float) (f.z - f.camZ);
        final boolean bloomOk = f.bloomAllowed;
        KiVfxFrameQueue.submitAura(pose, new Vec3(ox, oy, oz), (ps, buf) -> {
            boolean bloom = KiVfxFrameQueue.bloomPass();
            if (bloom && (!bloomOk || cfg.bloom() <= 0f)) return;
            float weight = bloom ? cfg.bloom() : 1f;
            VertexConsumer vc = buf.getBuffer(AuraComposeShaders.ribbons());
            Matrix4f m = ps.last().pose();
            float w = cfg.thickness();
            for (int i = 0; i < count; i++) {
                // Parpadeo del rayo de anime: sale a pleno y se apaga en la última mitad de su vida.
                float t = age[i] / life[i];
                float a = cfg.alpha() * weight * (t < 0.5f ? 1f : 1f - (t - 0.5f) * 2f);
                if (a <= 0.02f) continue;
                Ribbons.strip(vc, m, main[i], MAIN_PTS, ox, oy, oz, w * 3.2f, cr, cg, cb, a * 0.45f);
                Ribbons.strip(vc, m, main[i], MAIN_PTS, ox, oy, oz, w, kr, kg, kb, a);
                for (int b = 0; b < cfg.branches(); b++) {
                    Ribbons.strip(vc, m, branch[i][b], BRANCH_PTS, ox, oy, oz, w * 2.2f, cr, cg, cb, a * 0.35f);
                    Ribbons.strip(vc, m, branch[i][b], BRANCH_PTS, ox, oy, oz, w * 0.7f, kr, kg, kb, a * 0.8f);
                }
            }
        });
    }
}
