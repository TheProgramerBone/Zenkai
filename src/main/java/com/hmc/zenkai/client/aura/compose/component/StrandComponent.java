package com.hmc.zenkai.client.aura.compose.component;

import com.google.gson.JsonObject;
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
import com.hmc.zenkai.client.aura.AuraTiltController;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Set;

/**
 * AGUJAS de energía (strands). Esto es lo que hace que un aura de llama se lea como la de
 * DragonMine Z o la del anime (aura_1/aura_3, vídeo comparado el 2026-09-30) y no como un huevo con
 * dientes: muchas agujas FINAS y LARGAS, de largos distintos, casi verticales, en varios anillos
 * alrededor del cuerpo, también por DELANTE de él (el jugador queda dentro de la energía). Cada
 * aguja vive poco: crece rápido desde su base, aguanta y se recoge; al morir nace otra en otro
 * sitio, así la corona centellea sin un patrón repetido. En los pies, un abanico de agujas casi
 * horizontales sale por el suelo (el "salpicón" de DMZ).
 * <p>
 * La cáscara de la familia sigue dando volumen y silueta; las agujas dan la TEXTURA de trazos que
 * una superficie continua no puede dar.
 * <pre>
 * { "type": "strands", "count": 40, "radius": [0.25, 0.6], "height": [0.0, 1.5], "length": [0.6, 1.6],
 *   "width": 0.07, "lean": 0.15, "life": [0.25, 0.55], "top_bias": 0.6,
 *   "ground_count": 14, "ground_length": [0.5, 1.1], "color": "outer", "core_color": "hot", "alpha": 0.85, "bloom": 0.8 }
 * </pre>
 * Dos capas por aguja: un halo ancho del tinte y un núcleo fino claro (el corte cel del anime, sin
 * degradado continuo). (Se probó un sprite pixel-art al estilo de DMZ el 2026-09-30; el usuario lo
 * revirtió: prefiere este aspecto propio del mod.) Escala con el poder (radio, altura y largo × escala por PL). En primera
 * persona propia no se dibuja (las agujas del propio cuerpo tapan la vista); sin agujas en LOD
 * lejano y la mitad a media distancia. Arrays fijos, sin asignaciones por frame.
 */
public final class StrandComponent implements AuraComponent {

    public static final String TYPE = "strands";
    private static final int CAP = 72;
    private static final int GROUND_CAP = 24;

    public record Config(int count, float[] radius, float[] height, float[] length, float width, float lean,
                         float[] life, float topBias, int groundCount, float[] groundLength, TintSpec color,
                         TintSpec core, float alpha, float bloom) implements AuraComponentConfig {

        private static final Set<String> KEYS = Set.of("count", "radius", "height", "length", "width", "lean",
                "life", "top_bias", "ground_count", "ground_length", "color", "core_color", "alpha", "bloom");

        @Override public String type() { return TYPE; }

        @Override public AuraComponent create() { return new StrandComponent(this); }

        public static Config parse(JsonObject o) {
            String unknown = AuraComponentTypes.unknownKeys(o, KEYS);
            if (unknown != null) Zenkai.LOGGER.warn("[Zenkai] Agujas de aura: claves desconocidas ignoradas: {}", unknown);
            return new Config(
                    Mth.clamp((int) AuraComponentTypes.f(o, "count", 40f), 0, CAP),
                    AuraComponentTypes.range(o, "radius", 0.25f, 0.6f),
                    AuraComponentTypes.range(o, "height", 0.0f, 1.5f),
                    AuraComponentTypes.range(o, "length", 0.6f, 1.6f),
                    Math.max(0.005f, AuraComponentTypes.f(o, "width", 0.07f)),
                    AuraComponentTypes.f(o, "lean", 0.15f),
                    AuraComponentTypes.range(o, "life", 0.25f, 0.55f),
                    Mth.clamp(AuraComponentTypes.f(o, "top_bias", 0.6f), 0f, 1f),
                    Mth.clamp((int) AuraComponentTypes.f(o, "ground_count", 14f), 0, GROUND_CAP),
                    AuraComponentTypes.range(o, "ground_length", 0.5f, 1.1f),
                    TintSpec.parse(AuraComponentTypes.s(o, "color", "outer")),
                    TintSpec.parse(AuraComponentTypes.s(o, "core_color", "hot")),
                    Mth.clamp(AuraComponentTypes.f(o, "alpha", 0.85f), 0f, 1f),
                    Math.max(0f, AuraComponentTypes.f(o, "bloom", 0.8f)));
        }
    }

    private final Config cfg;
    private final RandomSource rng = RandomSource.create();
    // Agujas del cuerpo [0, CAP) y del suelo [CAP, CAP+GROUND_CAP). Base y dirección relativas a los
    // pies, ya escaladas por poder al nacer.
    private final float[] bx = new float[CAP + GROUND_CAP], by = new float[CAP + GROUND_CAP], bz = new float[CAP + GROUND_CAP];
    private final float[] dx = new float[CAP + GROUND_CAP], dy = new float[CAP + GROUND_CAP], dz = new float[CAP + GROUND_CAP];
    private final float[] len = new float[CAP + GROUND_CAP], wid = new float[CAP + GROUND_CAP];
    private final float[] age = new float[CAP + GROUND_CAP], life = new float[CAP + GROUND_CAP];
    private final boolean[] live = new boolean[CAP + GROUND_CAP];
    private float cr, cg, cb, kr, kg, kb;
    private float growth;
    private boolean any;

    private StrandComponent(Config cfg) { this.cfg = cfg; }

    @Override public String type() { return TYPE; }

    @Override public boolean alive() { return any; }

    private float lerp(float[] r) { return r[0] + (r[1] - r[0]) * rng.nextFloat(); }

    @Override
    public void update(AuraFrame f) {
        float[] c = cfg.color().of(f);
        cr = c[0]; cg = c[1]; cb = c[2];
        float[] k = cfg.core().of(f);
        // Núcleo: el tono claro llevado un poco a blanco; nunca blanco puro (se lavaría con bloom).
        kr = Mth.lerp(0.2f, k[0], 1f); kg = Mth.lerp(0.2f, k[1], 1f); kb = Mth.lerp(0.2f, k[2], 1f);
        growth = f.growth;
        float dt = f.dt;
        float lodF = f.lod == AuraBodyMesh.LOD_FAR ? 0f : f.lod == AuraBodyMesh.LOD_MID ? 0.5f : 1f;
        boolean spawning = f.active && f.growth > 0.05f;
        int wantBody = spawning ? Math.round(cfg.count() * lodF) : 0;
        // El abanico de los pies solo pisando el suelo: en el aire no hay suelo que salpicar.
        int wantGround = spawning && f.dynamics.charging && f.player.onGround()
                ? Math.round(cfg.groundCount() * lodF) : 0;
        any = false;
        for (int i = 0; i < CAP + GROUND_CAP; i++) {
            boolean ground = i >= CAP;
            int slot = ground ? i - CAP : i;
            if (live[i]) {
                age[i] += dt;
                if (age[i] >= life[i]) live[i] = false;
            }
            // Las agujas renacen escalonadas (vida aleatoria), nunca todas a la vez.
            if (!live[i] && slot < (ground ? wantGround : wantBody)) spawn(f, i, ground);
            any |= live[i];
        }
    }

    private void spawn(AuraFrame f, int i, boolean ground) {
        float s = f.powerScale * f.burstScale * f.bodyScale;
        float ang = rng.nextFloat() * Mth.TWO_PI;
        float ca = Mth.cos(ang), sa = Mth.sin(ang);
        if (ground) {
            // Abanico de los pies: casi horizontal, hacia fuera, apenas sube.
            float r = (0.2f + 0.2f * rng.nextFloat()) * s;
            bx[i] = ca * r; by[i] = 0.02f; bz[i] = sa * r;
            float up = 0.12f + 0.15f * rng.nextFloat();
            float n = Mth.sqrt(1f + up * up);
            dx[i] = ca / n; dy[i] = up / n; dz[i] = sa / n;
            len[i] = lerp(cfg.groundLength()) * s;
            wid[i] = cfg.width() * 0.8f * Mth.lerp(0.6f, 1f, f.powerScale);
        } else {
            // Altura sesgada hacia arriba (top_bias): las agujas se concentran en hombros y cabeza.
            float u = rng.nextFloat();
            float h = cfg.height()[0] + (cfg.height()[1] - cfg.height()[0]) * (float) Math.pow(u, 1f - cfg.topBias() * 0.7f);
            float r = lerp(cfg.radius()) * s;
            bx[i] = ca * r; by[i] = h * s; bz[i] = sa * r;
            // Casi verticales, algo abiertas hacia fuera; más abiertas abajo, más rectas arriba.
            float lean = cfg.lean() * (1.3f - 0.6f * (h / Math.max(0.01f, cfg.height()[1])));
            float jx = (rng.nextFloat() - 0.5f) * 0.12f, jz = (rng.nextFloat() - 0.5f) * 0.12f;
            float vx = ca * lean + jx, vz = sa * lean + jz, vy = 1f;
            float n = Mth.sqrt(vx * vx + vy * vy + vz * vz);
            dx[i] = vx / n; dy[i] = vy / n; dz[i] = vz / n;
            // Más largas cuanto más arriba nacen (la corona sube más).
            len[i] = lerp(cfg.length()) * s * (0.75f + 0.5f * (h / Math.max(0.01f, cfg.height()[1])));
            wid[i] = cfg.width() * Mth.lerp(0.6f, 1f, f.powerScale) * (0.7f + 0.6f * rng.nextFloat());
        }
        age[i] = 0f;
        life[i] = lerp(cfg.life());
        live[i] = true;
    }

    /** Largo visible según la edad: crece rápido (25%), aguanta y se recoge desde la base al final. */
    private float extent(int i) {
        float t = age[i] / life[i];
        return t < 0.25f ? t / 0.25f : 1f;
    }

    /** Recogida de la base al final de la vida (0 = base en su sitio, 1 = base en la punta). */
    private float retract(int i) {
        float t = age[i] / life[i];
        return t > 0.65f ? (t - 0.65f) / 0.35f : 0f;
    }

    @Override
    public void submit(AuraFrame f, PoseStack pose) {
        if (!any || f.selfFirstPerson) return;
        final float ox = (float) (f.x - f.camX), oy = (float) (f.y - f.camY), oz = (float) (f.z - f.camZ);
        final boolean bloomOk = f.bloomAllowed;
        final float fp = f.fpOpacity;
        // Las agujas son del CUERPO: siguen su inclinación de vuelo (las del suelo no salen en el
        // aire, así que girarlas también es inofensivo). Capturada al encolar, no al reproducir.
        final Quaternionf tilt = AuraTiltController.currentRotation(f.player.getId());
        KiVfxFrameQueue.submitAura(pose, new Vec3(ox, oy, oz), (ps, buf) -> {
            boolean bloom = KiVfxFrameQueue.bloomPass();
            if (bloom && (!bloomOk || cfg.bloom() <= 0f)) return;
            float weight = bloom ? cfg.bloom() : 1f;
            VertexConsumer vc = buf.getBuffer(AuraComposeShaders.ribbons());
            Matrix4f m = ps.last().pose();
            float a = cfg.alpha() * weight * growth * fp;
            if (a <= 0.01f) return;
            Vector3f p0 = new Vector3f(), p1 = new Vector3f();
            for (int i = 0; i < CAP + GROUND_CAP; i++) {
                if (!live[i]) continue;
                float l = len[i] * extent(i);
                float r0 = retract(i);
                p0.set(bx[i] + dx[i] * l * r0, by[i] + dy[i] * l * r0, bz[i] + dz[i] * l * r0);
                p1.set(bx[i] + dx[i] * l, by[i] + dy[i] * l, bz[i] + dz[i] * l);
                if (tilt != null) {
                    AuraTiltController.rotateAboutPivot(tilt, p0);
                    AuraTiltController.rotateAboutPivot(tilt, p1);
                }
                float x0 = ox + p0.x, y0 = oy + p0.y, z0 = oz + p0.z;
                float x1 = ox + p1.x, y1 = oy + p1.y, z1 = oz + p1.z;
                float w = wid[i] * (1f - 0.5f * r0);
                // Halo del tinte (ancho, base tenue) y núcleo claro (fino): dos bandas planas.
                Ribbons.segment(vc, m, x0, y0, z0, x1, y1, z1, w * 2f, 0f, cr, cg, cb, a * 0.25f, a * 0.5f);
                // Núcleo más tenue en la base: muchas agujas solapadas junto al cuerpo sumaban
                // hacia blanco (primera prueba, 2026-09-30).
                Ribbons.segment(vc, m, x0, y0, z0, x1, y1, z1, w * 0.7f, 0f, kr, kg, kb, a * 0.25f, a * 0.85f);
            }
        });
    }
}
