package com.hmc.zenkai.client.aura.compose.component;

import com.google.gson.JsonObject;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraTiltController;
import com.hmc.zenkai.client.aura.compose.AuraBodyMesh;
import com.hmc.zenkai.client.aura.compose.AuraComposeShaders;
import com.hmc.zenkai.client.aura.compose.AuraFrame;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Set;

/**
 * LÁMINAS VERTICALES de llama (2026-10-26, rework de las auras divinas,
 * .claude/pendiente/aura-divinas-plan.md). En los fotogramas de SSG (ReferenciaAuraGokuGod2,
 * VideoSinRecortarGokuGOD2) el aura no es un volumen con contorno: es una pared de lenguas verticales
 * que suben muy por encima de la cabeza, con núcleos casi blancos y sombras rojas entre ellas. Una
 * cáscara cerrada no puede dar eso (siempre se lee "huevo"); esto son quads planos orientados a
 * cámara solo en Y, en anillo alrededor del cuerpo, y la llama la dibuja el shader
 * {@code aura_flame_sheet} por UV.
 * <pre>
 * { "type": "sheets", "count": 12, "radius": [0.35, 0.6], "width": [0.8, 1.2], "height": [2.4, 3.4],
 *   "base": -0.15, "life": [0.9, 1.6], "front": 0.2, "mode": "flame", "color": "inner",
 *   "shadow_color": "outer", "alpha": 0.9, "bloom": 0.5, "charging_only": false }
 * </pre>
 * <ul>
 *   <li>Las láminas que quedan ENTRE la cámara y el jugador salen más bajas y con {@code front} de
 *       alfa: en el anime la pared está detrás del personaje, nunca le tapa la cara. Con la cámara
 *       encima del jugador (1ª persona) "delante" es hacia donde mira.</li>
 *   <li>Cada lámina crece, aguanta y se recoge con su propia vida; nunca renacen todas a la vez.</li>
 *   <li>Modo {@code light}: columna recta de luz (Ultra Instinct al transformarse).</li>
 *   <li>Escala con el poder (radio, ancho y alto); un tercio de las láminas en LOD lejano y la mitad a media
 *       distancia. Arrays fijos, sin asignaciones por frame salvo los vectores de la tarea.</li>
 * </ul>
 */
public final class FlameSheetComponent implements AuraComponent {

    public static final String TYPE = "sheets";
    private static final int CAP = 24;
    /** Tramos por lámina: los justos para que el ondulado y el arrastre se lean curvos. */
    private static final int SEG = 6;

    public enum Mode { FLAME, LIGHT }

    public record Config(int count, float[] radius, float[] width, float[] height, float base, float[] life,
                         float front, Mode mode, TintSpec color, TintSpec shadow, float alpha, float bloom,
                         boolean chargingOnly) implements AuraComponentConfig {

        private static final Set<String> KEYS = Set.of("count", "radius", "width", "height", "base", "life",
                "front", "mode", "color", "shadow_color", "alpha", "bloom", "charging_only");

        @Override public String type() { return TYPE; }

        @Override public AuraComponent create() { return new FlameSheetComponent(this); }

        public static Config parse(JsonObject o) {
            String unknown = AuraComponentTypes.unknownKeys(o, KEYS);
            if (unknown != null) Zenkai.LOGGER.warn("[Zenkai] Láminas de aura: claves desconocidas ignoradas: {}", unknown);
            Mode mode;
            try {
                mode = Mode.valueOf(AuraComponentTypes.s(o, "mode", "flame").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new com.google.gson.JsonParseException("modo de láminas desconocido (flame o light)");
            }
            return new Config(
                    Mth.clamp((int) AuraComponentTypes.f(o, "count", 12f), 0, CAP),
                    AuraComponentTypes.range(o, "radius", 0.35f, 0.6f),
                    AuraComponentTypes.range(o, "width", 0.8f, 1.2f),
                    AuraComponentTypes.range(o, "height", 2.4f, 3.4f),
                    AuraComponentTypes.f(o, "base", -0.15f),
                    AuraComponentTypes.range(o, "life", 0.9f, 1.6f),
                    Mth.clamp(AuraComponentTypes.f(o, "front", 0.2f), 0f, 1f),
                    mode,
                    TintSpec.parse(AuraComponentTypes.s(o, "color", "inner")),
                    TintSpec.parse(AuraComponentTypes.s(o, "shadow_color", "outer")),
                    Mth.clamp(AuraComponentTypes.f(o, "alpha", 0.9f), 0f, 1f),
                    Math.max(0f, AuraComponentTypes.f(o, "bloom", 0.5f)),
                    o.has("charging_only") && o.get("charging_only").getAsBoolean());
        }
    }

    private final Config cfg;
    private final RandomSource rng = RandomSource.create();
    // Posición en anillo (ángulo y radio ya escalados por poder), tamaño, vida y semilla del shader.
    private final float[] ang = new float[CAP], rad = new float[CAP], wid = new float[CAP], hei = new float[CAP];
    private final float[] age = new float[CAP], life = new float[CAP];
    private final int[] seed = new int[CAP];
    /** Altura de la base sobre la común y desvío de la punta (fracción del alto, en X/Z sin girar). */
    private final float[] lift = new float[CAP], leanX = new float[CAP], leanZ = new float[CAP];
    private final boolean[] live = new boolean[CAP];
    /** Orden de dibujado de atrás hacia delante (se rellena en submit, sin asignar). */
    private final int[] order = new int[CAP];
    private final float[] depth = new float[CAP];
    private float cr, cg, cb, sr, sg, sb;
    private float growth;
    private boolean any;
    private int nextSeed;

    private FlameSheetComponent(Config cfg) { this.cfg = cfg; }

    @Override public String type() { return TYPE; }

    @Override public boolean alive() { return any; }

    private float lerp(float[] r) { return r[0] + (r[1] - r[0]) * rng.nextFloat(); }

    @Override
    public void update(AuraFrame f) {
        float[] c = cfg.color().of(f);
        cr = c[0]; cg = c[1]; cb = c[2];
        float[] s = cfg.shadow().of(f);
        sr = s[0]; sg = s[1]; sb = s[2];
        growth = f.growth;
        // Unas pocas también de lejos: en divine_flame la lágrima de fondo está apagada y sin láminas el
        // aura desaparecía a distancia (2026-10-27). Son quads, apenas cuestan.
        float lodF = f.lod == AuraBodyMesh.LOD_FAR ? 0.35f : f.lod == AuraBodyMesh.LOD_MID ? 0.5f : 1f;
        boolean on = f.active && f.growth > 0.05f
                && (!cfg.chargingOnly() || f.dynamics.charging || f.dynamics.transforming);
        int want = on ? Math.round(cfg.count() * lodF) : 0;
        any = false;
        for (int i = 0; i < CAP; i++) {
            if (live[i]) {
                age[i] += f.dt;
                if (age[i] >= life[i]) live[i] = false;
            }
            if (!live[i] && i < want) spawn(f, i, want);
            any |= live[i];
        }
    }

    private void spawn(AuraFrame f, int i, int want) {
        float s = f.powerScale * f.burstScale * f.bodyScale;
        // Ángulos ESTRATIFICADOS (cada lámina en su sector, con holgura): al azar puro quedaban
        // huecos y montones, y la pared se leía como dos o tres llamas sueltas.
        float sector = Mth.TWO_PI / Math.max(1, want);
        ang[i] = sector * i + sector * (rng.nextFloat() - 0.5f) * 0.9f;
        rad[i] = lerp(cfg.radius()) * s;
        wid[i] = lerp(cfg.width()) * s;
        hei[i] = lerp(cfg.height()) * s;
        // Cada lámina nace a otra altura y con su propia inclinación (torcida de lado): todas paralelas y
        // con la base en el mismo plano, al tumbarse en vuelo se apilaban como tablillas con un borde
        // recto común (capturas volando de lado, 2026-10-27). Inclinadas hacia DENTRO: las puntas
        // convergen sobre la cabeza y el conjunto se lee como UNA lágrima de llama cerrada (referencia
        // GokuGod.jpg del usuario, 2026-10-27); abiertas hacia fuera formaban una V con hueco arriba.
        lift[i] = rng.nextFloat() * 0.3f * s;
        float out = -(0.08f + 0.08f * rng.nextFloat()), side = (rng.nextFloat() - 0.5f) * 0.24f;

        leanX[i] = Mth.cos(ang[i]) * out - Mth.sin(ang[i]) * side;
        leanZ[i] = Mth.sin(ang[i]) * out + Mth.cos(ang[i]) * side;
        seed[i] = (nextSeed++ & 63);
        age[i] = 0f;
        // Vida aleatoria y edad inicial al azar la primera vez: así no nacen (ni mueren) a la vez.
        life[i] = lerp(cfg.life());
        if (!any) age[i] = rng.nextFloat() * life[i] * 0.5f;
        live[i] = true;
    }

    /**
     * OPACIDAD según la edad: aparece y se funde despacio (un cuarto de la vida cada cosa). Antes era el
     * ALTO lo que crecía desde cero y se recogía, y con vidas de 1-1,6 s las columnas subían y bajaban
     * deprisa: el usuario pidió fuego lento, plasma en calma (2026-10-27). El alto ya no depende de la
     * edad; solo respira (ver {@link #breath}).
     */
    private float extent(int i) {
        float t = age[i] / life[i];
        if (t < 0.25f) return Mth.sin(t / 0.25f * Mth.HALF_PI);
        if (t > 0.75f) return Math.max(0f, Mth.cos((t - 0.75f) / 0.25f * Mth.HALF_PI));
        return 1f;
    }

    /** Respiración lenta del alto (±8 %, periodo de unos 7 s, desfasada por lámina). */
    private float breath(int i) {
        return 0.92f + 0.08f * Mth.sin(age[i] * 0.9f + seed[i] * 1.3f);
    }

    @Override
    public void submit(AuraFrame f, PoseStack pose) {
        if (!any || !AuraComposeShaders.sheetsAvailable()) return;
        final float ox = (float) (f.x - f.camX), oy = (float) (f.y - f.camY), oz = (float) (f.z - f.camZ);
        final boolean bloomOk = f.bloomAllowed;
        final float fp = f.fpOpacity;
        final float s = f.powerScale * f.burstScale * f.bodyScale;
        final Quaternionf tilt = AuraTiltController.currentRotation(f.player.getId());

        // "Delante" = hacia la cámara en horizontal; con la cámara encima del jugador (1ª persona), hacia
        // donde mira.
        float fx = -ox, fz = -oz;
        float fl = Mth.sqrt(fx * fx + fz * fz);
        if (fl < 0.5f) {
            Vector3f look = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
            fx = look.x(); fz = look.z();
            fl = Mth.sqrt(fx * fx + fz * fz);
        }
        if (fl < 1.0e-4f) { fx = 0f; fz = 1f; fl = 1f; }
        fx /= fl; fz /= fl;

        // Orden de atrás hacia delante: la mezcla es translúcida y sin depth entre láminas.
        int n = 0;
        for (int i = 0; i < CAP; i++) {
            if (!live[i]) continue;
            float px = Mth.cos(ang[i]) * rad[i] + ox, pz = Mth.sin(ang[i]) * rad[i] + oz;
            float d = px * px + pz * pz;
            int j = n++;
            while (j > 0 && depth[j - 1] < d) { depth[j] = depth[j - 1]; order[j] = order[j - 1]; j--; }
            depth[j] = d;
            order[j] = i;
        }
        final int count = n;
        final float ffx = fx, ffz = fz;
        final float time = f.seconds;
        // Arrastre: la punta se queda donde estaba el jugador hace ~0,1 s (velocidad real por delta de
        // posición, en bloques/s). Tope de 1,4 bloques por el poder: a toda velocidad la llama se tumba
        // en cola, no se estira hasta el horizonte.
        double vx = (f.player.getX() - f.player.xo) * 20.0, vy = (f.player.getY() - f.player.yo) * 20.0,
                vz = (f.player.getZ() - f.player.zo) * 20.0;
        float dx = (float) -vx * 0.1f, dy = (float) -vy * 0.1f, dz = (float) -vz * 0.1f;
        float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz), dMax = 1.4f * s;
        if (dl > dMax) { float k = dMax / dl; dx *= k; dy *= k; dz *= k; }
        final float dragX = dx, dragY = dy, dragZ = dz;
        KiVfxFrameQueue.submitAura(pose, new Vec3(ox, oy, oz), (ps, buf) -> {
            boolean bloom = KiVfxFrameQueue.bloomPass();
            if (bloom && (!bloomOk || cfg.bloom() <= 0f)) return;
            float weight = bloom ? cfg.bloom() : 1f;
            float a = cfg.alpha() * weight * growth * fp;
            if (a <= 0.01f) return;
            VertexConsumer vc = buf.getBuffer(AuraComposeShaders.sheets());
            Matrix4f m = ps.last().pose();
            Vector3f b = new Vector3f(), top = new Vector3f(), tip = new Vector3f(), radial = new Vector3f();
            float vOff = cfg.mode() == Mode.LIGHT ? 2f : 0f;
            for (int k = 0; k < count; k++) {
                int i = order[k];
                float e = extent(i);
                if (e <= 0.01f) continue;
                float ca = Mth.cos(ang[i]), sa = Mth.sin(ang[i]);
                // 1 = justo entre la cámara y el jugador, 0 = de lado o detrás. Solo las de delante de
                // verdad: si se apagan ya en los flancos, la pared queda DETRÁS del jugador y se lee como
                // columnas sueltas; el usuario prefirió la llama que envuelve el cuerpo (2026-10-26).
                float front = Mth.clamp((ca * ffx + sa * ffz - 0.15f) / 0.6f, 0f, 1f);
                float h = hei[i] * breath(i) * Mth.lerp(front, 1f, 0.55f);
                float al = a * e * Mth.lerp(front, 1f, cfg.front());
                // Volando HACIA la cámara el aura se tumba y las láminas apuntan a la pantalla: crecían
                // hacia el objetivo y lo llenaban (v12, 2026-10-27). Cuanto más apunta la punta hacia la
                // cámara, más baja y más tenue; es justo cuando el jugador más necesita ver.
                tip.set(leanX[i], 1f, leanZ[i]);
                if (tilt != null) tilt.transform(tip);
                float tl = tip.length(), cl = Mth.sqrt(ox * ox + oy * oy + oz * oz);
                float away = 0f;
                if (tl > 1.0e-4f && cl > 1.0e-4f) {
                    float facing = (-tip.x * ox - tip.y * oy - tip.z * oz) / (tl * cl);
                    // Solo en vuelo inclinado: con el aura recta, una cámara normal algo elevada ya daba
                    // "punta hacia la cámara" y encogía el aura sin motivo (medido en la v28, 2026-10-27).
                    if (tilt != null) {
                        float toward = Mth.clamp((facing - 0.15f) / 0.5f, 0f, 1f);
                        h *= Mth.lerp(toward, 1f, 0.4f);
                        al *= Mth.lerp(toward, 1f, 0.55f);
                    }
                    // Y al revés: puntas que se ALEJAN de la cámara (vuelo rápido visto desde detrás, cámara
                    // a -90°). La base queda en el lado de la cámara y, abiertas en corona, las láminas
                    // llenaban media pantalla (2026-10-27): más cortas y con la base subida a los pies.
                    // Con la cámara normal (facing > 0) no se activa nunca.
                    away = Mth.clamp((-facing - 0.3f) / 0.5f, 0f, 1f);
                    h *= Mth.lerp(away, 1f, 0.5f);
                }
                if (al <= 0.01f) continue;
                // Algo DETRÁS del jugador visto desde la cámara: el cuerpo tapa la llama por depth y queda
                // delante de ella, como en la referencia (GokuGod.jpg); centradas en el anillo, las de los
                // flancos le cruzaban medio cuerpo (v18, 2026-10-27).
                b.set(ca * rad[i] - ffx * 0.3f * s, cfg.base() * s * (1f - away) + lift[i], sa * rad[i] - ffz * 0.3f * s);
                top.set(b.x + leanX[i] * h, b.y + h, b.z + leanZ[i] * h);
                if (tilt != null) {
                    AuraTiltController.rotateAboutPivot(tilt, b);
                    AuraTiltController.rotateAboutPivot(tilt, top);
                }
                float bx = ox + b.x, by = oy + b.y, bz = oz + b.z;
                float tx = ox + top.x, ty = oy + top.y, tz = oz + top.z;
                // De cara a la cámara girando sobre el EJE de la lámina (base → punta), no sobre la
                // vertical del mundo: volando, el cuerpo se inclina y con el eje fijo en Y las láminas se
                // quedaban de canto, planas (pedido del usuario: probar volando, 2026-10-26). La cámara
                // está en el origen, así que la visual es el punto medio.
                float ax = tx - bx, ay = ty - by, az = tz - bz;
                float mx = (bx + tx) * 0.5f, my = (by + ty) * 0.5f, mz = (bz + tz) * 0.5f;
                float al2 = Mth.sqrt(ax * ax + ay * ay + az * az), ml = Mth.sqrt(mx * mx + my * my + mz * mz);
                if (al2 < 1.0e-5f || ml < 1.0e-5f) continue;
                // Vista casi a lo largo del eje (desde encima o desde debajo, o desde detrás con el jugador
                // tumbado en vuelo rápido): la lámina se vería de canto, como una carta. Antes se apagaba
                // (fundido 0.45-0.75) y en esas vistas desaparecía el aura ENTERA (bug del usuario,
                // 2026-10-27: vuelo rápido en 3ª persona y cámara a ±90°). Ahora se ABRE hacia fuera según
                // lo de punta que se vea: desde arriba, abajo o detrás queda una corona de llamas alrededor
                // del jugador, y la lámina vuelve a tener cara hacia la cámara.
                float along = Math.abs(ax * mx + ay * my + az * mz) / (al2 * ml);
                // Desde 0.6: con 0.4 una cámara normal elevada (+50°) ya abría las láminas y el aura se leía como
                // una flor; solo hace falta cerca de ±90° o con el cuerpo tumbado (v29, 2026-10-27).
                float spread = Mth.clamp((along - 0.6f) / 0.3f, 0f, 1f);
                if (spread > 0f) {
                    radial.set(ca, 0f, sa);
                    if (tilt != null) tilt.transform(radial);
                    // Casi horizontales (radiales) con la vista justo a lo largo del eje: abiertas a medias (keep 0.4)
                    // seguían casi de punta desde abajo y el fundido de abajo las dejaba pálidas (v25-v26).
                    float keep = 1f - 0.85f * spread, open = spread * al2;
                    ax = ax * keep + radial.x * open;
                    ay = ay * keep + radial.y * open;
                    az = az * keep + radial.z * open;
                    float nl = Mth.sqrt(ax * ax + ay * ay + az * az);
                    if (nl < 1.0e-5f) continue;
                    // Y más cortas: abiertas a su largo entero, en vuelo rápido visto desde detrás eran
                    // dos alas que llenaban media pantalla (2026-10-27).
                    float sc = al2 * (1f - 0.45f * spread) / nl;
                    ax *= sc; ay *= sc; az *= sc;
                    tx = bx + ax; ty = by + ay; tz = bz + az;
                    mx = (bx + tx) * 0.5f; my = (by + ty) * 0.5f; mz = (bz + tz) * 0.5f;
                    al2 = Mth.sqrt(ax * ax + ay * ay + az * az);
                    ml = Mth.sqrt(mx * mx + my * my + mz * mz);
                    if (ml < 1.0e-5f) continue;
                    along = Math.abs(ax * mx + ay * my + az * mz) / (al2 * ml);
                }
                // Solo lo que queda casi de punta aun abierto (la lámina justo bajo la cámara).
                al *= 1f - Mth.clamp((along - 0.85f) / 0.12f, 0f, 1f);
                if (al <= 0.01f) continue;
                float rx = ay * mz - az * my, ry = az * mx - ax * mz, rz = ax * my - ay * mx;
                float rl = Mth.sqrt(rx * rx + ry * ry + rz * rz);
                if (rl < 1.0e-5f) continue;
                rx /= rl; ry /= rl; rz /= rl;
                // Arrastre SIN la parte que empuja la punta contra su propia base: subiendo, "donde estaba
                // el jugador" queda debajo y la llama se aplastaba contra sí misma (bug del usuario,
                // 2026-10-27). Se quita esa componente; lo que queda dobla la llama de lado como un cometa.
                float dot = (dragX * ax + dragY * ay + dragZ * az) / al2;
                float pull = Math.min(dot, 0f) / al2;
                float sdx = dragX - ax * pull, sdy = dragY - ay * pull, sdz = dragZ - az * pull;
                float hw = wid[i] * 0.5f;
                float u0 = seed[i], u1 = seed[i] + 0.999f;
                // La lámina va en SEG tramos y cada fila se desplaza: la llama se dobla y ondula como fuego
                // líquido en vez de ser un quad rígido (pedido del usuario, 2026-10-27: "picos sólidos").
                // El ondulado (dos senos lentos desfasados por lámina) crece con la altura: la base queda
                // quieta. El ARRASTRE crece con t²: la base va con el jugador y la punta se queda atrás,
                // y en vuelo la llama se curva como la cola de un cometa.
                float px0 = 0f, py0 = 0f, pz0 = 0f, w0 = 0f;
                for (int r = 0; r <= SEG; r++) {
                    float t = r / (float) SEG;
                    float ph = seed[i] * 1.7f;
                    float sway = (Mth.sin(t * 2.6f - time * 1.1f + ph) * 0.12f
                            + Mth.sin(t * 5.3f - time * 1.7f + ph * 0.6f) * 0.05f) * wid[i] * t;
                    float t2 = t * t;
                    float px = bx + ax * t + rx * sway + sdx * t2;
                    float py = by + ay * t + ry * sway + sdy * t2;
                    float pz = bz + az * t + rz * sway + sdz * t2;
                    // Perfil de LÁGRIMA: estrecha en los pies, ancha a media altura (pecho) y fina arriba.
                    // Con el ancho entero desde la base el conjunto era un volcán ancho abajo (v18).
                    float w = hw * (t < 0.4f ? 0.45f + 0.55f * Mth.sin(t / 0.4f * Mth.HALF_PI)
                            : Mth.lerp((t - 0.4f) / 0.6f, 1f, 0.35f));
                    if (r > 0) {
                        float tp = (r - 1) / (float) SEG;
                        vc.addVertex(m, px0 - rx * w0, py0 - ry * w0, pz0 - rz * w0).setUv(u0, vOff + tp).setColor(cr, cg, cb, al).setNormal(sr, sg, sb);
                        vc.addVertex(m, px0 + rx * w0, py0 + ry * w0, pz0 + rz * w0).setUv(u1, vOff + tp).setColor(cr, cg, cb, al).setNormal(sr, sg, sb);
                        vc.addVertex(m, px + rx * w, py + ry * w, pz + rz * w).setUv(u1, vOff + t).setColor(cr, cg, cb, al).setNormal(sr, sg, sb);
                        vc.addVertex(m, px - rx * w, py - ry * w, pz - rz * w).setUv(u0, vOff + t).setColor(cr, cg, cb, al).setNormal(sr, sg, sb);
                    }
                    px0 = px; py0 = py; pz0 = pz; w0 = w;
                }
            }
        });
    }
}
