package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Genera y CACHEA las mallas de un {@link KiVfxProfile}, en ESPACIO LOCAL con el eje de vuelo
 * siempre +Z (ver STEP 5-6 de la auditoría: nada aquí depende de cámara ni de espacio de vista —
 * eso es responsabilidad exclusiva de {@code ki_energy.vsh}, más abajo en el pipeline).
 *
 * LA LONGITUD SE HORNEA EN LA MALLA, no se aplica escalando Z en el PoseStack. Con escalado no
 * uniforme un cilindro aguanta (es un tubo sobre Z, su sección no cambia), pero el TUBO DE LA
 * HÉLICE se estira con él: cada tramo de cinta pasa de sección circular a lóbulo alargado y la
 * espiral se lee como una hilera de salchichas.
 *
 * ANCLAJE. Una bola va centrada en la entidad, pero un HAZ no: si se centra, la mitad del haz
 * sobresale por delante del proyectil y el visual atraviesa al objetivo antes de que el golpe
 * ocurra. Con {@code anchorTip} la malla ocupa z ∈ [−longitud, 0]: la punta está en la entidad y
 * el haz se extiende hacia atrás, enlazando con la estela.
 */
public final class KiVfxGeometry {

    private KiVfxGeometry() {}

    // Resolución. Subir estos números multiplica los vértices; con estos valores una esfera son
    // 128 quads y la doble hélice unos 800, que a veinte proyectiles en pantalla no se nota.
    private static final int SPHERE_RINGS = 14;
    private static final int SPHERE_SECTORS = 28;
    // 8, no 6: perfil de cinta más redondo sin tocar ninguna textura (resolución de malla, no
    // sujeta a la regla de pixel-art del mod, que es sobre texturas) — ver
    // .claude/pendiente/technique-visuals-referencia-mods.md §4, candidato de bajo riesgo ya
    // identificado y aplicado en esta reconstrucción.
    private static final int TUBE_RADIAL = 8;
    private static final int HELIX_STEPS_PER_UNIT = 20;
    private static final int DISK_SECTORS = 24;
    private static final int CYL_SEGMENTS_PER_UNIT = 4;

    /** Radio de la cola de un haz respecto al de la punta. Un tubo de sección constante se lee
     *  como una barra; el estrechamiento es lo que dice que la energía SALE de un punto. */
    private static final float BEAM_TAPER = 0.55f;
    private static final float HELIX_TUBE_RATIO = 0.26f;
    private static final float HELIX_TWISTS_PER_UNIT = 1.55f;

    private static final Map<String, KiVfxMesh> CACHE = new ConcurrentHashMap<>();

    /** La cáscara: una sola entrada, la geometría de una técnica sale entera de su perfil. */
    public static KiVfxMesh shell(KiVfxProfile p) {
        KiVfxProfile.Shell s = p.shell();
        String key = p.shape().name() + '|' + s.meshLength() + '|' + s.meshRadius()
                + '|' + s.headScale() + '|' + p.anchorTip();
        return CACHE.computeIfAbsent(key, k -> build(p));
    }

    /** Esfera desnuda de radio 0.5, sin cabeza ni estrechamiento — la malla de la bola que se
     *  carga en la mano. Cargando, la técnica AÚN no tiene forma: un Kamehameha no es un tubo en
     *  la palma, es energía que se contiene y solo se estira en un haz al soltarse. Excepción:
     *  DISK usa su malla real desde que empieza a cargar (ver KiVfxChargeRenderer). */
    private static final KiVfxMesh CHARGE_SPHERE = sphere(0.5f, 0f);

    public static KiVfxMesh chargeSphere() { return CHARGE_SPHERE; }

    /**
     * NÚCLEO EXPLÍCITO: malla aparte, mucho más fina/pequeña que la cáscara, para el centro
     * brillante — ver "EL NÚCLEO ES GEOMETRÍA, NO UNA BANDA DE SHADER" en KiVfxProfile. Para
     * HELIX el núcleo es un hilo RECTO (cilindro simple), no otra doble hélice trenzada: la
     * identidad de "energía enroscada" ya la da la cáscara, el núcleo solo necesita leerse como
     * un centro brillante.
     * @param real false = está CARGANDO (bola genérica): el núcleo también es una esfera lisa.
     * @return null si esta técnica no lleva núcleo explícito o es DISK (su canto incandescente ya
     *         es la lectura de núcleo, ver {@link #DISK_RIM_U}).
     */
    public static KiVfxMesh core(KiVfxProfile p, boolean real) {
        if (!p.core().enabled() || p.shape() == KiVfxShape.DISK) return null;
        if (!real || p.shape() == KiVfxShape.SPHERE) return sphereOfRadius(0.5f * p.core().scale());
        KiVfxProfile.Shell s = p.shell();
        String key = "CORE|" + p.shape().name() + '|' + s.meshLength() + '|' + s.meshRadius()
                + '|' + p.core().scale() + '|' + p.anchorTip();
        return CACHE.computeIfAbsent(key,
                k -> cylinder(s.meshRadius() * p.core().scale(), s.meshLength(), p.anchorTip()));
    }

    /**
     * Solo la bola de la punta de un haz ANCLADO ({@link KiVfxProfile#column()}), en z=0 — sin el
     * tubo horneado de longitud fija: el cuerpo del haz lo pone {@link #emitTube} con la longitud
     * real de ese frame. La vista previa de TechniqueEditScreen y la bola de carga siguen usando
     * {@link #shell}, a propósito: ahí no hay punto de disparo del que tirar un tubo.
     */
    public static KiVfxMesh head(KiVfxProfile p) {
        return sphereOfRadius(Math.max(p.shell().headScale(), p.shell().meshRadius()));
    }

    private static final int TUBE_SECTORS = 24;
    /** Largo máximo de un tramo del tubo anclado. El hervor del shader depende de la normal y la
     *  UV por vértice: tramos muy largos lo dejarían interpolar en línea recta entre extremos. */
    private static final float TUBE_SEGMENT_LEN = 1.25f;

    /**
     * Tubo de radio constante de z=−length a z=0 (eje +Z, igual que el resto de mallas), emitido
     * DIRECTAMENTE: su longitud cambia cada frame (el haz se alarga con la cabeza), así que
     * cachearlo como las demás formas no tiene sentido — son pocos quads.
     * UV: x = vuelta al tubo (0..1), y = distancia en BLOQUES desde la punta — la usan las vetas
     * de flujo de ki_energy.fsh, que así mantienen su tamaño sea cual sea el largo del haz. Por
     * eso NO se escala en Z con el PoseStack (escalar estiraría las vetas).
     * @param rootRadiusMul radio en el extremo del disparo respecto a la punta (1 = cilindro).
     */
    public static void emitTube(com.mojang.blaze3d.vertex.VertexConsumer vc,
                                com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                float radius, float rootRadiusMul, float length,
                                float r, float g, float b, float alpha, boolean flatUv) {
        if (length <= 1.0e-3f || radius <= 1.0e-4f) return;
        int segments = Math.max(2, (int) Math.ceil(length / TUBE_SEGMENT_LEN));
        float slope = radius * (1f - rootRadiusMul) / length;
        float nl = (float) Math.sqrt(1f + slope * slope);
        for (int s = 0; s < segments; s++) {
            float f0 = (float) s / segments, f1 = (float) (s + 1) / segments;
            for (int j = 0; j < TUBE_SECTORS; j++) {
                float a0 = (float) j / TUBE_SECTORS, a1 = (float) (j + 1) / TUBE_SECTORS;
                tubeVertex(vc, pose, radius, rootRadiusMul, length, slope, nl, f0, a0, r, g, b, alpha, flatUv);
                tubeVertex(vc, pose, radius, rootRadiusMul, length, slope, nl, f1, a0, r, g, b, alpha, flatUv);
                tubeVertex(vc, pose, radius, rootRadiusMul, length, slope, nl, f1, a1, r, g, b, alpha, flatUv);
                tubeVertex(vc, pose, radius, rootRadiusMul, length, slope, nl, f0, a1, r, g, b, alpha, flatUv);
            }
        }
    }

    /** @param f 0 = punta (z=0), 1 = extremo del disparo (z=−length). */
    private static void tubeVertex(com.mojang.blaze3d.vertex.VertexConsumer vc,
                                   com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                   float radius, float rootMul, float length, float slope, float nl,
                                   float f, float around,
                                   float r, float g, float b, float alpha, boolean flatUv) {
        double th = 2 * Math.PI * around;
        float cx = (float) Math.cos(th), cy = (float) Math.sin(th);
        float rr = radius * (1f + (rootMul - 1f) * f);
        vc.addVertex(pose, cx * rr, cy * rr, -length * f)
                .setColor(r, g, b, alpha)
                .setUv(flatUv ? 0.5f : around, flatUv ? 0.5f : length * f)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, cx / nl, cy / nl, -slope / nl);
    }

    /** Núcleo de la cabeza de un haz anclado: la esfera de {@link #head} reducida por
     *  {@code core.scale} — {@link #core}(p, false) mide respecto al diámetro de la bola de
     *  carga, no al radio de la cabeza, y casi la llenaría entera. */
    public static KiVfxMesh headCore(KiVfxProfile p) {
        if (!p.core().enabled()) return null;
        return sphereOfRadius(Math.max(p.shell().headScale(), p.shell().meshRadius()) * p.core().scale());
    }

    /** Mallas esféricas cacheadas (cabezas y núcleos) — ver {@link #isRound}. */
    private static final java.util.Set<KiVfxMesh> ROUND = ConcurrentHashMap.newKeySet();

    /** ¿Es {@code mesh} una de las esferas de este cache? El núcleo en dos capas la encoge entera;
     *  un hilo de haz, solo en sección. */
    public static boolean isRound(KiVfxMesh mesh) { return ROUND.contains(mesh); }

    private static KiVfxMesh sphereOfRadius(float radius) {
        String key = "CORE_SPHERE|" + radius;
        return CACHE.computeIfAbsent(key, k -> {
            KiVfxMesh m = sphere(radius, 0f);
            ROUND.add(m);
            return m;
        });
    }

    private static KiVfxMesh build(KiVfxProfile p) {
        KiVfxProfile.Shell s = p.shell();
        float len = s.meshLength();
        boolean tip = p.anchorTip();
        KiVfxMesh mesh = switch (p.shape()) {
            case SPHERE -> sphere(0.5f, 0f);
            case BEAM -> cylinder(s.meshRadius(), len, tip);
            case HELIX -> helix(s.meshRadius(), s.meshRadius() * HELIX_TUBE_RATIO, len, tip);
            case DISK -> disk();
        };
        // CABEZA: la bola de energía en la punta. Es lo que hace que un chorro se lea como
        // Kamehameha y no como una barra de luz — el haz EMPUJA algo, no es el algo.
        if (s.headScale() > 0f) {
            mesh = merge(mesh, sphere(s.headScale(), tip ? 0f : len * 0.5f));
        }
        return mesh;
    }

    private static float zFront(float len, boolean tip) { return tip ? 0f : len * 0.5f; }
    private static float zBack(float len, boolean tip) { return tip ? -len : -len * 0.5f; }

    // ── Constructor de quads ────────────────────────────────────────────────

    private static final class Buf {
        float[] d = new float[4096 * KiVfxMesh.STRIDE];
        int n = 0;

        void v(float x, float y, float z, float u, float vv,
               float nx, float ny, float nz, float w) {
            if ((n + 1) * KiVfxMesh.STRIDE > d.length) {
                float[] bigger = new float[d.length * 2];
                System.arraycopy(d, 0, bigger, 0, d.length);
                d = bigger;
            }
            int o = n * KiVfxMesh.STRIDE;
            d[o] = x; d[o + 1] = y; d[o + 2] = z;
            d[o + 3] = u;
            d[o + 4] = vv;
            d[o + 5] = nx; d[o + 6] = ny; d[o + 7] = nz;
            d[o + 8] = w;
            n++;
        }

        KiVfxMesh done() {
            float[] exact = new float[n * KiVfxMesh.STRIDE];
            System.arraycopy(d, 0, exact, 0, exact.length);
            return new KiVfxMesh(exact, n / 4);
        }
    }

    private static KiVfxMesh merge(KiVfxMesh a, KiVfxMesh b) {
        float[] out = new float[a.data().length + b.data().length];
        System.arraycopy(a.data(), 0, out, 0, a.data().length);
        System.arraycopy(b.data(), 0, out, a.data().length, b.data().length);
        return new KiVfxMesh(out, a.quadCount() + b.quadCount());
    }

    // ── Formas ──────────────────────────────────────────────────────────────

    private static KiVfxMesh sphere(float radius, float zOff) {
        Buf b = new Buf();
        for (int i = 0; i < SPHERE_RINGS; i++) {
            double p0 = Math.PI * i / SPHERE_RINGS;
            double p1 = Math.PI * (i + 1) / SPHERE_RINGS;
            for (int j = 0; j < SPHERE_SECTORS; j++) {
                double t0 = 2 * Math.PI * j / SPHERE_SECTORS;
                double t1 = 2 * Math.PI * (j + 1) / SPHERE_SECTORS;
                sphereVert(b, radius, zOff, p0, t0, i, j);
                sphereVert(b, radius, zOff, p1, t0, i + 1, j);
                sphereVert(b, radius, zOff, p1, t1, i + 1, j + 1);
                sphereVert(b, radius, zOff, p0, t1, i, j + 1);
            }
        }
        return b.done();
    }

    private static void sphereVert(Buf b, float radius, float zOff,
                                   double phi, double theta, int ri, int si) {
        float nx = (float) (Math.sin(phi) * Math.cos(theta));
        float ny = (float) Math.cos(phi);
        float nz = (float) (Math.sin(phi) * Math.sin(theta));
        float w = 0.18f * (float) Math.pow(Math.sin(phi), 2.0);
        b.v(nx * radius, ny * radius, nz * radius + zOff,
                (float) si / SPHERE_SECTORS, (float) ri / SPHERE_RINGS, nx, ny, nz, w);
    }

    /**
     * Tubo cónico a lo largo del eje de vuelo, tapas abiertas (NO_CULL enseña el otro lado por
     * dentro, lo que da sensación de volumen hueco). La normal se inclina con el cono: la radial
     * pura dejaba una arista de brillo donde el fresnel cambiaba de golpe.
     */
    private static KiVfxMesh cylinder(float radius, float length, boolean tip) {
        Buf b = new Buf();
        float zf = zFront(length, tip), zb = zBack(length, tip);
        int segments = Math.max(4, Math.round(CYL_SEGMENTS_PER_UNIT * length));
        float slope = (radius - radius * BEAM_TAPER) / Math.max(1.0e-4f, length);

        for (int s = 0; s < segments; s++) {
            float f0 = (float) s / segments, f1 = (float) (s + 1) / segments;
            for (int j = 0; j < SPHERE_SECTORS; j++) {
                double t0 = 2 * Math.PI * j / SPHERE_SECTORS;
                double t1 = 2 * Math.PI * (j + 1) / SPHERE_SECTORS;
                cylVert(b, radius, zb, zf, slope, f0, t0, j);
                cylVert(b, radius, zb, zf, slope, f1, t0, j);
                cylVert(b, radius, zb, zf, slope, f1, t1, j + 1);
                cylVert(b, radius, zb, zf, slope, f0, t1, j + 1);
            }
        }
        return b.done();
    }

    /** @param f 0 = cola, 1 = punta. */
    private static void cylVert(Buf b, float radius, float zb, float zf, float slope,
                                float f, double theta, int ji) {
        float rr = radius * (BEAM_TAPER + (1f - BEAM_TAPER) * f);
        float z = zb + (zf - zb) * f;
        float cx = (float) Math.cos(theta), cy = (float) Math.sin(theta);
        float nl = (float) Math.sqrt(1f + slope * slope);
        b.v(cx * rr, cy * rr, z, (float) ji / SPHERE_SECTORS, f,
                cx / nl, cy / nl, -slope / nl, 0.22f);
    }

    /**
     * Dos cintas tubulares barridas sobre una hélice alrededor del eje de vuelo, desfasadas media
     * vuelta: energía enroscada, no un muelle. Vueltas POR UNIDAD DE LONGITUD, no en total, o una
     * espiral larga sale con el paso estirado y una corta apelmazada.
     */
    private static KiVfxMesh helix(float radius, float tube, float length, boolean tip) {
        Buf b = new Buf();
        float zf = zFront(length, tip), zb = zBack(length, tip);
        int steps = Math.max(24, Math.round(HELIX_STEPS_PER_UNIT * length));
        double twists = HELIX_TWISTS_PER_UNIT * length;
        for (int s = 0; s < 2; s++) {
            double phase = Math.PI * s;
            for (int i = 0; i < steps; i++) {
                double a0 = phase + 2 * Math.PI * twists * i / steps;
                double a1 = phase + 2 * Math.PI * twists * (i + 1) / steps;
                float z0 = zb + (zf - zb) * i / steps;
                float z1 = zb + (zf - zb) * (i + 1) / steps;
                for (int j = 0; j < TUBE_RADIAL; j++) {
                    double r0 = 2 * Math.PI * j / TUBE_RADIAL;
                    double r1 = 2 * Math.PI * (j + 1) / TUBE_RADIAL;
                    tubeVert(b, radius, tube, a0, z0, r0, (float) i / steps, j);
                    tubeVert(b, radius, tube, a1, z1, r0, (float) (i + 1) / steps, j);
                    tubeVert(b, radius, tube, a1, z1, r1, (float) (i + 1) / steps, j + 1);
                    tubeVert(b, radius, tube, a0, z0, r1, (float) i / steps, j + 1);
                }
            }
        }
        return b.done();
    }

    /**
     * Ángulo de una hebra de la hélice horneada por {@link #helix}, medido HACIA ATRÁS desde la
     * PUNTA (d=0 en la punta, d=length en la cola). Se puede evaluar con d > length, más allá de
     * la cola, para que la estela continúe la MISMA torsión sin costura visible donde la malla
     * horneada termina — ver {@code KiRibbon}/{@code KiVfxProfile#helixTrail}. `phase` es la fase
     * de la hebra (0 o π).
     */
    public static double helixAngleFromTip(float length, double d, double phase) {
        return phase + 2 * Math.PI * HELIX_TWISTS_PER_UNIT * (length - d);
    }

    private static void tubeVert(Buf b, float radius, float tube,
                                 double along, float z, double around, float v, int ri) {
        float cx = (float) (Math.cos(along) * radius);
        float cy = (float) (Math.sin(along) * radius);
        float ox = (float) (Math.cos(along) * Math.cos(around));
        float oy = (float) (Math.sin(along) * Math.cos(around));
        float oz = (float) Math.sin(around);
        b.v(cx + ox * tube, cy + oy * tube, z + oz * tube,
                (float) ri / TUBE_RADIAL, v, ox, oy, oz, 0.35f);
    }

    private static final float DISK_HALF_THICK = 0.045f;
    /** Coordenada de banda del canto. Va ALTA a propósito: con el valor geométrico (1.0) el
     *  fresnel lo trataría como borde exterior y el filo se desvanecería justo donde está la
     *  lectura del arma. A 0.42 cae cerca del núcleo y el canto sale incandescente. */
    private static final float DISK_RIM_U = 0.42f;

    /**
     * Disco como LENTE: dos caras de quads concéntricos más un canto que las une — coronas, no
     * abanicos de triángulos (un abanico mete dos vértices en el mismo punto central, cada cara
     * es un quad degenerado y los huecos se ven como dientes de engranaje).
     */
    private static KiVfxMesh disk() {
        Buf b = new Buf();
        final int rings = 4;

        for (int side = 0; side < 2; side++) {
            float z = (side == 0) ? DISK_HALF_THICK : -DISK_HALF_THICK;
            float nz = (side == 0) ? 1f : -1f;
            for (int ri = 0; ri < rings; ri++) {
                float r0 = 0.5f * ri / rings;
                float r1 = 0.5f * (ri + 1) / rings;
                float w0 = 0.85f * (1f - (float) ri / rings);
                float w1 = 0.85f * (1f - (float) (ri + 1) / rings);
                for (int j = 0; j < DISK_SECTORS; j++) {
                    double t0 = 2 * Math.PI * j / DISK_SECTORS;
                    double t1 = 2 * Math.PI * (j + 1) / DISK_SECTORS;
                    diskVert(b, r0, t0, z, nz, w0);
                    diskVert(b, r1, t0, z, nz, w1);
                    diskVert(b, r1, t1, z, nz, w1);
                    diskVert(b, r0, t1, z, nz, w0);
                }
            }
        }

        for (int j = 0; j < DISK_SECTORS; j++) {
            double t0 = 2 * Math.PI * j / DISK_SECTORS;
            double t1 = 2 * Math.PI * (j + 1) / DISK_SECTORS;
            rimVert(b, t0,  DISK_HALF_THICK);
            rimVert(b, t0, -DISK_HALF_THICK);
            rimVert(b, t1, -DISK_HALF_THICK);
            rimVert(b, t1,  DISK_HALF_THICK);
        }
        return b.done();
    }

    private static void diskVert(Buf b, float radius, double theta, float z, float nz, float w) {
        float x = (float) Math.cos(theta) * radius;
        float y = (float) Math.sin(theta) * radius;
        b.v(x, y, z, radius / 0.5f, (float) (theta / (2 * Math.PI)), 0, 0, nz, w);
    }

    private static void rimVert(Buf b, double theta, float z) {
        float cx = (float) Math.cos(theta), cy = (float) Math.sin(theta);
        b.v(cx * 0.5f, cy * 0.5f, z, DISK_RIM_U, (float) (theta / (2 * Math.PI)), cx, cy, 0, 0.85f);
    }
}
