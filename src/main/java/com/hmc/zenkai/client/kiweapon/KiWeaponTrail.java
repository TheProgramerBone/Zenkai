package com.hmc.zenkai.client.kiweapon;

import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiRibbon;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estela del swing de las armas de ki: una SUPERFICIE barrida entre dos puntos del arma (la punta
 * o el filo y una base más cerca del puño) que se desvanece sola. Cada frame que el jugador está
 * en mitad de un golpe ({@code getAttackAnim > 0}) se guarda una muestra; el trozo vivo se dibuja
 * con la cinta de ki ({@link KiRibbon#surface}) y se apaga en {@link #LIFE} ticks.
 *
 * DOS RUTAS, igual que el arma ({@link KiWeaponVfx}):
 *  - MUNDO: las muestras se guardan en coordenadas de MUNDO (cámara + relativas) porque la cámara
 *    se mueve entre frames, y se reconvierten al encolar. Se dibuja en la cola de ki, con bloom.
 *  - MANO (1ª persona): las muestras van en ESPACIO DE VISTA (KiWeaponVfx.drawNow lo prepara: la
 *    pasada de mano vanilla va en ejes de mundo), que es fijo respecto a la pantalla, así que se
 *    guardan tal cual y se dibujan al momento, sin bloom.
 * Solo el brazo principal deja estela: el escudo no golpea.
 */
public final class KiWeaponTrail {
    private KiWeaponTrail() {}

    /** Ticks que tarda en apagarse una muestra. */
    private static final float LIFE = 7.0F;
    /** Si pasa más de esto entre dos muestras es OTRO golpe: no se unen (saldría una banda a través del aire). */
    private static final float STROKE_GAP = 1.6F;
    /** Distancia mínima entre muestras seguidas (a 240 fps salían cientos por golpe). */
    private static final double MIN_STEP = 0.012;

    private record Sample(Vec3 tip, Vec3 base, float time) {}

    private static final Map<Integer, ArrayDeque<Sample>> WORLD = new HashMap<>();
    private static final ArrayDeque<Sample> HAND = new ArrayDeque<>();

    // ── Muestreo ─────────────────────────────────────────────────────────────

    public static void add(int entityId, Vec3 tipWorld, Vec3 baseWorld, float now) {
        push(WORLD.computeIfAbsent(entityId, k -> new ArrayDeque<>()), tipWorld, baseWorld, now);
    }

    public static void addHand(Vec3 tip, Vec3 base, float now) {
        push(HAND, tip, base, now);
    }

    private static void push(ArrayDeque<Sample> dq, Vec3 tip, Vec3 base, float now) {
        Sample head = dq.peekFirst();
        if (head != null) {
            if (now - head.time() > STROKE_GAP) dq.clear();
            else if (head.tip().distanceToSqr(tip) < MIN_STEP * MIN_STEP) return;
        }
        dq.addFirst(new Sample(tip, base, now));
    }

    /** Quita las muestras que ya se apagaron y devuelve las vivas, la más nueva primero. */
    private static List<Sample> live(ArrayDeque<Sample> dq, float now) {
        while (!dq.isEmpty() && now - dq.peekLast().time() > LIFE) dq.pollLast();
        return new ArrayList<>(dq);
    }

    /** Los ids de entidad se reutilizan entre mundos: se llama al cerrar sesión. */
    public static void clear() {
        WORLD.clear();
        HAND.clear();
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    /** Encola el trozo vivo de la estela de este jugador en la cola de ki del mundo. */
    public static void submitWorld(int entityId, float now, Vec3 cam, int rgb) {
        ArrayDeque<Sample> dq = WORLD.get(entityId);
        if (dq == null) return;
        List<Sample> samples = live(dq, now);
        if (dq.isEmpty()) WORLD.remove(entityId);
        if (samples.size() < 2) return;
        Vec3 near = samples.get(0).tip().subtract(cam);
        KiVfxFrameQueue.submit(new PoseStack(), near,
                (ps, buf) -> draw(buf, ps.last(), samples, cam, now, rgb));
    }

    /** Dibuja ahora la estela de la mano (1ª persona), en el espacio de la pasada de mano. */
    public static void drawHand(MultiBufferSource.BufferSource buf, float now, int rgb) {
        List<Sample> samples = live(HAND, now);
        if (samples.size() < 2) return;
        draw(buf, new PoseStack().last(), samples, Vec3.ZERO, now, rgb);
    }

    private static void draw(MultiBufferSource.BufferSource buf, PoseStack.Pose mat, List<Sample> samples,
                             Vec3 origin, float now, int rgb) {
        if (!KiVfxRenderTypes.available()) return;
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        int n = samples.size();
        List<Vec3> tips = new ArrayList<>(n), bases = new ArrayList<>(n), cores = new ArrayList<>(n);
        float[] alpha = new float[n];
        for (int i = 0; i < n; i++) {
            Sample s = samples.get(i);
            tips.add(s.tip());
            bases.add(s.base());
            // El núcleo es la franja más cercana al filo: más clara y más estrecha que la tintada.
            cores.add(s.tip().add(s.base().subtract(s.tip()).scale(0.35)));
            float age = Math.min(1.0F, Math.max(0.0F, (now - s.time()) / LIFE));
            float fade = 1.0F - age;
            alpha[i] = 0.85F * fade * fade;
        }

        RenderType soft = KiVfxRenderTypes.soft(KiVfxRenderTypes.TRAIL_TEXTURE);
        VertexConsumer vc = buf.getBuffer(soft);
        KiRibbon.surface(vc, mat, tips, bases, alpha, origin, r, g, b);
        buf.endBatch(soft);

        RenderType glow = KiVfxRenderTypes.glow(KiVfxRenderTypes.TRAIL_TEXTURE);
        VertexConsumer core = buf.getBuffer(glow);
        float cr = r + (1F - r) * 0.55F, cg = g + (1F - g) * 0.55F, cb = b + (1F - b) * 0.55F;
        float[] coreAlpha = new float[n];
        for (int i = 0; i < n; i++) coreAlpha[i] = alpha[i] * 0.7F;
        KiRibbon.surface(core, mat, tips, cores, coreAlpha, origin, cr, cg, cb);
        buf.endBatch(glow);
    }
}
