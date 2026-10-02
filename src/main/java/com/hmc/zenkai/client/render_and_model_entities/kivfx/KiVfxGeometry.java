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
    // 880 quads y la doble hélice unos 800, que a veinte proyectiles en pantalla no se nota.
    // La esfera subió de 14x28 (128 quads, ~13° por faceta) a 22x40 el 2026-10-01: de cerca la
    // silueta de la cabeza del haz se leía poligonal. Los sectores también los usa el cilindro.
    private static final int SPHERE_RINGS = 22;
    private static final int SPHERE_SECTORS = 40;
    // 8, no 6: perfil de cinta más redondo sin tocar ninguna textura (resolución de malla, no
    // sujeta a la regla de pixel-art del mod, que es sobre texturas) — subida heredada del
    // sistema ki_fresnel.fsh anterior al rework de 2026-09-23, aplicada aquí sin volver a medir.
    private static final int TUBE_RADIAL = 8;
    private static final int HELIX_STEPS_PER_UNIT = 20;
    // 24 -> 64 el 2026-10-01: con 24 sectores (15° cada uno) el aro exterior del Kienzan se veía
    // como un polígono. Son 4 anillos x sectores x 2 caras: unos 500 quads.
    private static final int DISK_SECTORS = 64;
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
     * tubo horneado de longitud fija: el cuerpo del haz lo pone {@link #emitSweptTube} con la longitud
     * real de ese frame. La vista previa de TechniqueEditScreen y la bola de carga siguen usando
     * {@link #shell}, a propósito: ahí no hay punto de disparo del que tirar un tubo.
     */
    public static KiVfxMesh head(KiVfxProfile p) {
        return sphereOfRadius(Math.max(p.shell().headScale(), p.shell().meshRadius()));
    }

    private static final int TUBE_SECTORS = 24;

    // ── Tubo CURVO (haz serpiente) ──────────────────────────────────────────

    /** Separación objetivo entre anillos del tubo curvo, en bloques. */
    private static final float PATH_RING_STEP = 0.8f;

    /**
     * Trayectoria ya suavizada y con marcos para barrer un tubo a lo largo de ella. Anillos de la
     * RAÍZ (índice 0, el extremo del disparo) a la PUNTA (último). Cada anillo guarda centro,
     * tangente (hacia la punta), normal y binormal, y la longitud de arco desde la raíz.
     * Los marcos se propagan por TRANSPORTE PARALELO: un marco derivado de la dirección
     * instantánea (o de la cámara) giraría de golpe al curvarse el haz y retorcería el tubo — la
     * misma razón por la que {@code KiProjectileEntity.flightBasis} se fija una sola vez.
     */
    public static final class TubePath {
        final int n;
        final float length;
        final float[] px, py, pz, tx, ty, tz, nx, ny, nz, bx, by, bz, s;

        private TubePath(int n, float length) {
            this.n = n;
            this.length = length;
            px = new float[n]; py = new float[n]; pz = new float[n];
            tx = new float[n]; ty = new float[n]; tz = new float[n];
            nx = new float[n]; ny = new float[n]; nz = new float[n];
            bx = new float[n]; by = new float[n]; bz = new float[n];
            s = new float[n];
        }

        /** Dirección del haz en la punta, en el mismo espacio que los puntos de entrada. */
        public net.minecraft.world.phys.Vec3 tipTangent() {
            return new net.minecraft.world.phys.Vec3(tx[n - 1], ty[n - 1], tz[n - 1]);
        }

        public float length() { return length; }
    }

    /**
     * Construye el {@link TubePath} de una polilínea de la raíz a la punta: la suaviza con
     * Catmull-Rom (con rectas colineales da rectas, así que un disparo sin HOMING sale idéntico al
     * tubo recto de siempre), la remuestrea a ~{@link #PATH_RING_STEP} bloques y calcula los marcos.
     * @param trimTip longitud de arco que se recorta de la punta (la parte que queda DENTRO de la
     *                cabeza, ver HEAD_CUT del renderer).
     * @return null si lo que queda es demasiado corto para dibujar un tubo.
     */
    public static TubePath tubePath(java.util.List<net.minecraft.world.phys.Vec3> pts, float trimTip) {
        int m = pts.size();
        if (m < 2) return null;
        java.util.ArrayList<double[]> dense = new java.util.ArrayList<>();
        for (int i = 0; i < m - 1; i++) {
            net.minecraft.world.phys.Vec3 p1 = pts.get(i), p2 = pts.get(i + 1);
            net.minecraft.world.phys.Vec3 p0 = i > 0 ? pts.get(i - 1) : p1.subtract(p2.subtract(p1));
            net.minecraft.world.phys.Vec3 p3 = i + 2 < m ? pts.get(i + 2) : p2.add(p2.subtract(p1));
            int k = Math.max(1, (int) Math.ceil(p1.distanceTo(p2) / PATH_RING_STEP));
            for (int j = 0; j < k; j++) addDense(dense, catmull(p0, p1, p2, p3, (double) j / k));
        }
        net.minecraft.world.phys.Vec3 last = pts.get(m - 1);
        addDense(dense, new double[] {last.x, last.y, last.z});
        if (dense.size() < 2) return null;

        // Longitud de arco y recorte de la punta.
        int cnt = dense.size();
        double[] arc = new double[cnt];
        for (int i = 1; i < cnt; i++) {
            double[] a = dense.get(i - 1), b = dense.get(i);
            arc[i] = arc[i - 1] + Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
        }
        double total = arc[cnt - 1];
        double keep = total - Math.max(0f, trimTip);
        if (keep < 0.05) return null;
        if (keep < total) {
            int end = cnt - 1;
            while (end > 0 && arc[end - 1] >= keep) end--;
            // end es el primer índice con arc >= keep; se sustituye por el punto interpolado.
            double[] a = dense.get(end - 1), b = dense.get(end);
            double f = (keep - arc[end - 1]) / Math.max(1.0e-9, arc[end] - arc[end - 1]);
            double[] cut = {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
            while (dense.size() > end) dense.remove(dense.size() - 1);
            dense.add(cut);
            cnt = dense.size();
            arc = java.util.Arrays.copyOf(arc, cnt);
            arc[cnt - 1] = keep;
        }
        if (cnt < 2) return null;

        TubePath tp = new TubePath(cnt, (float) arc[cnt - 1]);
        for (int i = 0; i < cnt; i++) {
            double[] c = dense.get(i);
            double[] a = dense.get(Math.max(0, i - 1)), b = dense.get(Math.min(cnt - 1, i + 1));
            double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l < 1.0e-9) { dx = 0; dy = 0; dz = 1; l = 1; }
            tp.px[i] = (float) c[0]; tp.py[i] = (float) c[1]; tp.pz[i] = (float) c[2];
            tp.tx[i] = (float) (dx / l); tp.ty[i] = (float) (dy / l); tp.tz[i] = (float) (dz / l);
            tp.s[i] = (float) arc[i];
        }
        // Marco inicial perpendicular a la tangente de la raíz; después, transporte paralelo.
        float rx = Math.abs(tp.ty[0]) > 0.99f ? 1f : 0f, ry = rx == 1f ? 0f : 1f, rz = 0f;
        float dot = rx * tp.tx[0] + ry * tp.ty[0] + rz * tp.tz[0];
        float ux = rx - tp.tx[0] * dot, uy = ry - tp.ty[0] * dot, uz = rz - tp.tz[0] * dot;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        tp.nx[0] = ux / ul; tp.ny[0] = uy / ul; tp.nz[0] = uz / ul;
        for (int i = 0; i < cnt; i++) {
            if (i > 0) {
                float d = tp.nx[i - 1] * tp.tx[i] + tp.ny[i - 1] * tp.ty[i] + tp.nz[i - 1] * tp.tz[i];
                float vx = tp.nx[i - 1] - tp.tx[i] * d, vy = tp.ny[i - 1] - tp.ty[i] * d, vz = tp.nz[i - 1] - tp.tz[i] * d;
                float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (vl < 1.0e-6f) { vx = tp.nx[i - 1]; vy = tp.ny[i - 1]; vz = tp.nz[i - 1]; vl = 1f; }
                tp.nx[i] = vx / vl; tp.ny[i] = vy / vl; tp.nz[i] = vz / vl;
            }
            // b = t × n: con x=n, y=b, z=t la terna es la misma (derecha) que la del tubo recto.
            tp.bx[i] = tp.ty[i] * tp.nz[i] - tp.tz[i] * tp.ny[i];
            tp.by[i] = tp.tz[i] * tp.nx[i] - tp.tx[i] * tp.nz[i];
            tp.bz[i] = tp.tx[i] * tp.ny[i] - tp.ty[i] * tp.nx[i];
        }
        return tp;
    }

    private static double sq(double v) { return v * v; }

    private static void addDense(java.util.List<double[]> out, double[] p) {
        if (!out.isEmpty()) {
            double[] q = out.get(out.size() - 1);
            if (sq(p[0] - q[0]) + sq(p[1] - q[1]) + sq(p[2] - q[2]) < 1.0e-8) return;
        }
        out.add(p);
    }

    private static double[] catmull(net.minecraft.world.phys.Vec3 p0, net.minecraft.world.phys.Vec3 p1,
                                    net.minecraft.world.phys.Vec3 p2, net.minecraft.world.phys.Vec3 p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return new double[] {
                cr(p0.x, p1.x, p2.x, p3.x, t, t2, t3),
                cr(p0.y, p1.y, p2.y, p3.y, t, t2, t3),
                cr(p0.z, p1.z, p2.z, p3.z, t, t2, t3)};
    }

    private static double cr(double a, double b, double c, double d, double t, double t2, double t3) {
        return 0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
    }

    /**
     * Tubo barrido a lo largo de un {@link TubePath} (con una trayectoria recta es un cilindro
     * ahusado). Los quads se emiten directamente: la longitud cambia cada frame y cachearlos no
     * tiene sentido. UV y y el resto en radios, para que el ruido de ki_energy.vsh tenga la misma
     * escala que el de la cabeza esférica. Convención: radio
     * {@code radius} en la punta, {@code radius*rootRadiusMul} en la raíz; UV x = vuelta (0..1),
     * UV y = distancia desde la punta en radios. Los vértices salen ya en las coordenadas de la
     * trayectoria (sin rotar): la pose que se pase solo debe trasladar.
     */
    public static void emitSweptTube(com.mojang.blaze3d.vertex.VertexConsumer vc,
                                     com.mojang.blaze3d.vertex.PoseStack.Pose pose, TubePath tp,
                                     float radius, float rootRadiusMul,
                                     float r, float g, float b, float alpha, boolean flatUv) {
        if (tp == null || radius <= 1.0e-4f) return;
        float slope = radius * (1f - rootRadiusMul) / tp.length;
        float nl = (float) Math.sqrt(1f + slope * slope);
        for (int k = tp.n - 1; k > 0; k--) {
            for (int j = 0; j < TUBE_SECTORS; j++) {
                float a0 = (float) j / TUBE_SECTORS, a1 = (float) (j + 1) / TUBE_SECTORS;
                pathVertex(vc, pose, tp, k, a0, radius, rootRadiusMul, slope, nl, r, g, b, alpha, flatUv);
                pathVertex(vc, pose, tp, k - 1, a0, radius, rootRadiusMul, slope, nl, r, g, b, alpha, flatUv);
                pathVertex(vc, pose, tp, k - 1, a1, radius, rootRadiusMul, slope, nl, r, g, b, alpha, flatUv);
                pathVertex(vc, pose, tp, k, a1, radius, rootRadiusMul, slope, nl, r, g, b, alpha, flatUv);
            }
        }
    }

    private static void pathVertex(com.mojang.blaze3d.vertex.VertexConsumer vc,
                                   com.mojang.blaze3d.vertex.PoseStack.Pose pose, TubePath tp, int k,
                                   float around, float radius, float rootMul, float slope, float nl,
                                   float r, float g, float b, float alpha, boolean flatUv) {
        double th = 2 * Math.PI * around;
        float c = (float) Math.cos(th), sn = (float) Math.sin(th);
        float dx = tp.nx[k] * c + tp.bx[k] * sn;
        float dy = tp.ny[k] * c + tp.by[k] * sn;
        float dz = tp.nz[k] * c + tp.bz[k] * sn;
        float fromTip = tp.length - tp.s[k];
        float f = fromTip / tp.length; // 0 = punta, 1 = raíz
        float rr = radius * (1f + (rootMul - 1f) * f);
        vc.addVertex(pose, tp.px[k] + dx * rr, tp.py[k] + dy * rr, tp.pz[k] + dz * rr)
                .setColor(r, g, b, alpha)
                .setUv(flatUv ? 0.5f : around, flatUv ? 0.5f : fromTip / radius)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, (dx - tp.tx[k] * slope) / nl, (dy - tp.ty[k] * slope) / nl,
                        (dz - tp.tz[k] * slope) / nl);
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
