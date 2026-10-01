package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.client.aura.AuraTrailRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * ESTELA DE VUELO del aura compuesta. Sustituye, con el sistema compuesto, a la del legacy
 * ({@code AuraTrailRenderer}), que era una cinta con su propia textura de ki, un solo color plano y
 * un RenderType vanilla aparte: no se parecía en nada al aura que lleva detrás (pedido del usuario,
 * 2026-09-30: "que no sea tan distinto del sistema actual").
 * <p>
 * Lo que la une al aura:
 * <ul>
 *   <li>TRES BANDAS CEL anidadas (halo ancho, cuerpo, núcleo claro), el mismo corte por bandas que
 *       la cáscara, sin degradado continuo entre ellas;</li>
 *   <li>DOS TONOS: halo del color exterior y núcleo del interior (Kaioken, Blue, Beast...);</li>
 *   <li>el BORDE de su familia ({@link AuraTrailSpec}): dentado como lenguas en las llamas, redondo
 *       en la divina, denso en la oscura, fino en Ultra Instinct;</li>
 *   <li>va por {@code KiVfxFrameQueue.submitAura}: ordenada con el aura y con su bloom.</li>
 * </ul>
 * Nace en los pies siguiendo la inclinación de vuelo ({@code AuraTrailRenderer.bodyFeet}, el mismo
 * ancla del legacy). Se alarga mientras se vuela por encima de una velocidad mínima; en hover o al
 * aterrizar se recoge punto a punto. El dentado de cada punto se sortea al nacer y no cambia: el
 * borde se queda quieto en el mundo, no centellea. Arrays fijos, sin asignaciones por frame.
 */
final class FlightTrail {

    static final int CAP = 24;
    private static final double MIN_STEP = 0.15;
    /** Bloques por segundo por debajo de los que la estela no crece (legacy: 0.18 bloques/tick). */
    private static final float MIN_SPEED = 3.6f;

    private final double[] px = new double[CAP], py = new double[CAP], pz = new double[CAP];
    private final float[] jag = new float[CAP];
    private int count;
    private int seed;
    private double lastX, lastY, lastZ;
    private boolean hasLast;
    private float speed;

    // Datos de este frame para la tarea diferida.
    private double headX, headY, headZ;
    private float halfW, alpha, coreW;
    private final float[] outer = new float[3], mid = new float[3], core = new float[3];
    private AuraTrailSpec spec = AuraTrailSpec.FLAME;

    boolean alive() { return count > 0; }

    void update(AuraFrame f, AuraTrailSpec s) {
        spec = s;
        Vec3 head = AuraTrailRenderer.bodyFeet(f.player, new Vec3(f.x, f.y, f.z));
        headX = head.x;
        headY = head.y;
        headZ = head.z;
        if (hasLast && f.dt > 1.0e-4f) {
            double dx = headX - lastX, dy = headY - lastY, dz = headZ - lastZ;
            speed = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) / f.dt);
        }
        lastX = headX;
        lastY = headY;
        lastZ = headZ;
        hasLast = true;

        boolean growing = f.active && f.dynamics.flying && speed > MIN_SPEED && !f.selfFirstPerson;
        if (growing) {
            if (count == 0 || dist(0, headX, headY, headZ) >= MIN_STEP) push();
        } else if (count > 0) {
            count--; // se recoge desde la cola
        }
        while (count > spec.points()) count--;

        float ps = Mth.lerp(0.5f, 1f, f.powerScale) * f.bodyScale;
        halfW = spec.width() * 0.5f * ps;
        alpha = spec.alpha() * Mth.clamp(f.growth, 0f, 1f) * f.fpOpacity;
        // Halo del exterior; cuerpo y núcleo del interior (dos tonos, como la cáscara).
        copy(f.outer, outer);
        if (spec.deep()) {
            // Familia oscura: el cuerpo va hondo y saturado, el borde es lo que brilla.
            for (int i = 0; i < 3; i++) mid[i] = f.inner[i] * 0.55f;
        } else {
            copy(f.inner, mid);
        }
        float lift = 0.25f + 0.35f * spec.core();
        for (int i = 0; i < 3; i++) core[i] = Mth.lerp(lift, f.twoTone ? f.inner[i] : f.hot[i], 1f);
        coreW = 0.18f + 0.12f * spec.core();
    }

    private double dist(int i, double x, double y, double z) {
        double dx = px[i] - x, dy = py[i] - y, dz = pz[i] - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void push() {
        int n = Math.min(count, CAP - 1);
        System.arraycopy(px, 0, px, 1, n);
        System.arraycopy(py, 0, py, 1, n);
        System.arraycopy(pz, 0, pz, 1, n);
        System.arraycopy(jag, 0, jag, 1, n);
        px[0] = headX;
        py[0] = headY;
        pz[0] = headZ;
        // Dentado del punto, sorteado al nacer y fijo desde entonces (hash entero barato).
        seed = seed * 1103515245 + 12345;
        jag[0] = ((seed >>> 16) & 0x7FFF) / 32767f;
        count = Math.min(count + 1, CAP);
    }

    private static void copy(float[] a, float[] b) {
        b[0] = a[0];
        b[1] = a[1];
        b[2] = a[2];
    }

    void submit(AuraFrame f, PoseStack pose) {
        if (count < 2) return;
        final double camX = f.camX, camY = f.camY, camZ = f.camZ;
        final boolean bloomOk = f.bloomAllowed;
        final float t = f.seconds;
        KiVfxFrameQueue.submitAura(pose, new Vec3(headX - camX, headY - camY, headZ - camZ), (ps, buf) -> {
            boolean bloom = KiVfxFrameQueue.bloomPass();
            if (bloom && !bloomOk) return;
            float weight = bloom ? 0.6f : 1f;
            VertexConsumer vc = buf.getBuffer(AuraComposeShaders.ribbons());
            Matrix4f m = ps.last().pose();
            // Las tres bandas, de fuera hacia dentro: la de dentro se pinta encima.
            band(vc, m, camX, camY, camZ, t, 1f, outer, alpha * 0.55f * weight, true);
            band(vc, m, camX, camY, camZ, t, 0.62f, mid, alpha * 0.8f * weight, true);
            band(vc, m, camX, camY, camZ, t, coreW, core, alpha * weight, false);
        });
    }

    /**
     * Una banda de la estela. El primer punto es la posición de este frame (la cabeza no se queda
     * atrás del jugador entre muestras).
     * @param jagged true = el borde de esta banda lleva el dentado de la familia (el núcleo no).
     */
    private void band(VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float time,
                      float widthMul, float[] rgb, float a, boolean jagged) {
        int n = count + 1;
        float prevLx = 0, prevLy = 0, prevLz = 0, prevRx = 0, prevRy = 0, prevRz = 0, prevA = 0;
        for (int i = 0; i < n; i++) {
            double x = i == 0 ? headX : px[i - 1], y = i == 0 ? headY : py[i - 1], z = i == 0 ? headZ : pz[i - 1];
            int j = Math.min(i + 1, n - 1), k = Math.max(i - 1, 0);
            double ax = (j == 0 ? headX : px[j - 1]) - (k == 0 ? headX : px[k - 1]);
            double ay = (j == 0 ? headY : py[j - 1]) - (k == 0 ? headY : py[k - 1]);
            double az = (j == 0 ? headZ : pz[j - 1]) - (k == 0 ? headZ : pz[k - 1]);
            float rx = (float) (x - camX), ry = (float) (y - camY), rz = (float) (z - camZ);
            // Lado = tangente x visual (la cámara está en el origen de este espacio).
            float sx = (float) (ay * -rz - az * -ry), sy = (float) (az * -rx - ax * -rz), sz = (float) (ax * -ry - ay * -rx);
            float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0e-6f) { sx = 0; sy = 1; sz = 0; sl = 1; }
            sx /= sl; sy /= sl; sz /= sl;

            float u = 1f - (float) i / (n - 1);          // 1 en la cabeza, 0 en la cola
            float half = halfW * widthMul * (0.25f + 0.75f * u);
            if (jagged && i > 0) {
                // Lenguas: cada punto con su largo sorteado, los alternos hacia dentro.
                float jg = jag[i - 1];
                half *= 1f + spec.jag() * ((i & 1) == 0 ? jg : -0.6f * jg);
            }
            if (spec.sway() > 0f) {
                float w = spec.sway() * 0.15f * Mth.sin(time * 6f + i * 0.7f) * (1f - u);
                rx += sx * w; ry += sy * w; rz += sz * w;
            }
            float lx = rx + sx * half, ly = ry + sy * half, lz = rz + sz * half;
            float qx = rx - sx * half, qy = ry - sy * half, qz = rz - sz * half;
            float aa = a * u;
            if (i > 0) {
                vc.addVertex(m, prevLx, prevLy, prevLz).setColor(rgb[0], rgb[1], rgb[2], prevA);
                vc.addVertex(m, prevRx, prevRy, prevRz).setColor(rgb[0], rgb[1], rgb[2], prevA);
                vc.addVertex(m, qx, qy, qz).setColor(rgb[0], rgb[1], rgb[2], aa);
                vc.addVertex(m, lx, ly, lz).setColor(rgb[0], rgb[1], rgb[2], aa);
            }
            prevLx = lx; prevLy = ly; prevLz = lz;
            prevRx = qx; prevRy = qy; prevRz = qz;
            prevA = aa;
        }
    }
}
