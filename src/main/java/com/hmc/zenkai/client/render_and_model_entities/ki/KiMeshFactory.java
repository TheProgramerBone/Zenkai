package com.hmc.zenkai.client.render_and_model_entities.ki;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Genera y CACHEA las mallas de ki. La clave lleva el conjunto de parámetros de geometría, así que
 * dos técnicas con la misma forma comparten malla sin recalcular nada.
// * LA LONGITUD SE HORNEA EN LA MALLA, no se aplica escalando Z en el PoseStack. Con el escalado
 * no uniforme un cilindro aguanta (es un tubo sobre Z y su sección no cambia), pero el TUBO DE
 * LA HÉLICE se estira con él: cada tramo de cinta pasa de sección circular a lóbulo alargado y
 * la espiral se lee como una hilera de salchichas. Hornearla cuesta una entrada más de caché
 * por combinación y hace imposible ese fallo.
// * ANCLAJE. Una bola va centrada en la entidad, pero un HAZ no: si se centra, la mitad del haz
 * sobresale por delante del proyectil y el visual atraviesa al objetivo antes de que el golpe
 * ocurra. Con {@code anchorTip} la malla ocupa z ∈ [−longitud, 0]: la punta está en la entidad
 * y el haz se extiende hacia atrás, enlazando con la estela.
// * El eje de vuelo es +Z y se genera con GROSOR de referencia 1 (la esfera mide 0.5 de
 * radio); el renderer escala UNIFORMEMENTE con el tamaño del proyectil.
 * Las UV se guardan REALES; quién las aplana (o no) es KiMesh.emit, que es quien sabe con qué
 * textura o shader se va a dibujar.
 */
public final class KiMeshFactory {

    private KiMeshFactory() {}

    // Resolución. Subir estos números multiplica los vértices; con estos valores una esfera son
    // 128 quads y la doble hélice unos 600, que a veinte proyectiles en pantalla no se nota.
    private static final int SPHERE_RINGS = 14;
    private static final int SPHERE_SECTORS = 28;
    private static final int TUBE_RADIAL = 6;
    /** Pasos de hélice POR UNIDAD de longitud: una espiral larga no puede tener el mismo número
     *  de segmentos que una corta o las cintas se ven poligonales. */
    private static final int HELIX_STEPS_PER_UNIT = 20;
    private static final int DISK_SECTORS = 24;
    private static final int CYL_SEGMENTS_PER_UNIT = 4;

    /** Radio de la cola de un haz respecto al de la punta. Un tubo de sección constante se lee
     *  como una barra; el estrechamiento es lo que dice que la energía SALE de un punto. */
    private static final float BEAM_TAPER = 0.55f;
    /** Grosor de cada cinta de la hélice respecto a su radio de giro. */
    private static final float HELIX_TUBE_RATIO = 0.26f;
    /** Vueltas completas por unidad de longitud. */
    private static final float HELIX_TWISTS_PER_UNIT = 1.55f;

    private static final Map<String, KiMesh> CACHE = new ConcurrentHashMap<>();

    /** Una sola entrada: la geometría de una técnica sale entera de su {@link KiVisual}. */
    public static KiMesh get(KiVisual v) {
        String key = v.shape().name() + '|' + v.meshLength() + '|' + v.meshRadius()
                + '|' + v.headScale() + '|' + v.anchorTip();
        return CACHE.computeIfAbsent(key, k -> build(v));
    }

    /** Esfera desnuda de radio 0.5, sin cabeza ni estrechamiento: la malla de la bola que se
     *  carga en la mano. Cargando, la técnica AÚN no tiene forma — un Kamehameha no es un tubo
     *  en la palma, es energía que se contiene y solo se estira en un haz al soltarse — así que
     *  la carga es siempre esta esfera sea cual sea {@link KiVisual#shape()} de la técnica; lo
     *  que SÍ toma de su KiVisual real son color, bandas y alfas, para que sea reconociblemente
     *  la misma energía que el proyectil que sale después. */
    private static final KiMesh CHARGE_SPHERE = sphere(0.5f, 0f);

    public static KiMesh chargeSphere() { return CHARGE_SPHERE; }

    private static KiMesh build(KiVisual v) {
        float len = v.meshLength();
        boolean tip = v.anchorTip();
        KiMesh mesh = switch (v.shape()) {
            case SPHERE -> sphere(0.5f, 0f);
            case CYLINDER -> cylinder(v.meshRadius(), len, tip);
            case HELIX -> helix(v.meshRadius(), v.meshRadius() * HELIX_TUBE_RATIO, len, tip);
            case DISK -> disk();
        };
        // CABEZA: la bola de energía en la punta. Es lo que hace que un chorro se lea como
        // Kamehameha y no como una barra de luz — el haz EMPUJA algo, no es el algo.
        if (v.headScale() > 0f) {
            mesh = merge(mesh, sphere(v.headScale(), tip ? 0f : len * 0.5f));
        }
        return mesh;
    }

    /** z del extremo delantero y del trasero según el anclaje. */
    private static float zFront(float len, boolean tip) { return tip ? 0f : len * 0.5f; }
    private static float zBack(float len, boolean tip) { return tip ? -len : -len * 0.5f; }

    // ── Constructor de quads ────────────────────────────────────────────────

    private static final class Buf {
        float[] d = new float[4096 * KiMesh.STRIDE];
        int n = 0;   // vértices escritos

        void v(float x, float y, float z, float u, float vv,
               float nx, float ny, float nz, float w) {
            if ((n + 1) * KiMesh.STRIDE > d.length) {
                float[] bigger = new float[d.length * 2];
                System.arraycopy(d, 0, bigger, 0, d.length);
                d = bigger;
            }
            int o = n * KiMesh.STRIDE;
            d[o] = x; d[o + 1] = y; d[o + 2] = z;
            d[o + 3] = u;
            d[o + 4] = vv;
            d[o + 5] = nx; d[o + 6] = ny; d[o + 7] = nz;
            d[o + 8] = w;
            n++;
        }

        KiMesh done() {
            float[] exact = new float[n * KiMesh.STRIDE];
            System.arraycopy(d, 0, exact, 0, exact.length);
            return new KiMesh(exact, n / 4);
        }
    }

    private static KiMesh merge(KiMesh a, KiMesh b) {
        float[] out = new float[a.data().length + b.data().length];
        System.arraycopy(a.data(), 0, out, 0, a.data().length);
        System.arraycopy(b.data(), 0, out, a.data().length, b.data().length);
        return new KiMesh(out, a.quadCount() + b.quadCount());
    }

    // ── Formas ──────────────────────────────────────────────────────────────

    /** Esfera UV con radio y desplazamiento en Z (el desplazamiento es para la cabeza del haz). */
    private static KiMesh sphere(float radius, float zOff) {
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
        // Blancura por latitud: solo la usa la RUTA DE RESPALDO. Con el shader vale 0 y las
        // bandas se deciden por píxel.
        float w = 0.18f * (float) Math.pow(Math.sin(phi), 2.0);
        b.v(nx * radius, ny * radius, nz * radius + zOff,
                (float) si / SPHERE_SECTORS, (float) ri / SPHERE_RINGS, nx, ny, nz, w);
    }

    /**
     * Tubo cónico a lo largo del eje de vuelo, con las tapas abiertas (por dentro se ve el otro
     * lado gracias a NO_CULL, que es lo que da sensación de volumen hueco).
     * La normal se inclina con el cono: usar la radial pura dejaba una arista de brillo donde
     * el fresnel cambiaba de golpe.
     */
    private static KiMesh cylinder(float radius, float length, boolean tip) {
        Buf b = new Buf();
        float zf = zFront(length, tip), zb = zBack(length, tip);
        int segments = Math.max(4, Math.round(CYL_SEGMENTS_PER_UNIT * length));
        // Componente Z de la normal del cono: cuánto se "abre" la superficie hacia delante.
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
     * Dos cintas tubulares barridas sobre una hélice alrededor del eje de vuelo, desfasadas
     * media vuelta: es lo que se lee como energía enroscada y no como un muelle.
     * Las vueltas son POR UNIDAD DE LONGITUD, no en total: con un número fijo, una espiral larga
     * sale con el paso estirado y una corta apelmazada.
     */
    private static KiMesh helix(float radius, float tube, float length, boolean tip) {
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

    private static void tubeVert(Buf b, float radius, float tube,
                                 double along, float z, double around, float v, int ri) {
        float cx = (float) (Math.cos(along) * radius);
        float cy = (float) (Math.sin(along) * radius);
        // Marco local: radial hacia fuera y el eje Z como "arriba" del tubo.
        float ox = (float) (Math.cos(along) * Math.cos(around));
        float oy = (float) (Math.sin(along) * Math.cos(around));
        float oz = (float) Math.sin(around);
        b.v(cx + ox * tube, cy + oy * tube, z + oz * tube,
                (float) ri / TUBE_RADIAL, v, ox, oy, oz, 0.35f);
    }

    /** Semiespesor de la lente. Un disco de espesor cero desaparece visto de canto. */
    private static final float DISK_HALF_THICK = 0.045f;
    /** Coordenada de banda del canto. Va ALTA a propósito: con el valor geométrico (1.0) el
     *  fresnel lo trataría como borde exterior y el filo se desvanecería justo donde está la
     *  lectura del arma. A 0.42 cae cerca del núcleo y el canto sale incandescente. */
    private static final float DISK_RIM_U = 0.42f;

    /**
     * Disco como LENTE: dos caras de quads concéntricos más un canto que las une.
     * Las caras son coronas y no abanicos de triángulos — el abanico metía dos vértices en el
     * mismo punto central, cada cara era un quad degenerado y los huecos se veían como dientes
     * de engranaje.
     * Se genera en el plano XY con normal +Z; su orientación de vuelo es cosa del renderer.
     */
    private static KiMesh disk() {
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

    /** La U lleva el RADIO NORMALIZADO (0 centro → 1 filo), no una coordenada de textura: es lo
     *  que el shader usa como coordenada de banda en el modo RADIAL. Un disco es plano y el
     *  ángulo con la cámara es el mismo en su cara entera, así que sin esto saldría de un solo
     *  color liso. La V lleva el ángulo, que alimenta el hervor. */
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
