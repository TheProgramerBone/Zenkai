package com.hmc.zenkai.client.fx;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiRibbon;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * VFX de las técnicas de espada: cada tajo deja una CINTA de ki barrida en el aire (el mismo
 * {@link KiRibbon#surface} y los mismos RenderTypes que la estela del arma, así que entra en la cola de
 * ki con su bloom) que se revela siguiendo el golpe y se apaga sola. Es lo que da PESO a un tajo: el
 * filo del arma de ki ya dibuja su estela, pero es del tamaño del arma; esto es del tamaño del golpe.
 *
 * Forma por técnica (medido en el marco del que ataca: F delante, L a su izquierda, U arriba; el yaw sale
 * de la cabeza en el instante del tajo y el arco se queda quieto en el mundo, como un corte en el aire):
 *  - RISING_SLASH: arco vertical grande de abajo-derecha a arriba, ladeado 20 grados.
 *  - CROSS_SLASH: una diagonal por tajo (variant 0 y 1, ladeadas +-45 grados): juntas dibujan la X.
 *  - PIERCING_THRUST: dos bandas rectas (vertical y horizontal) del puño hacia delante.
 *  - SPIN_SLASH: aro horizontal entero a la altura del pecho.
 *  - SKY_CLEAVE: arco vertical enorme de arriba hacia delante + onda de choque en el suelo.
 *  - LIGHT_BLADE: arco ladeado 30 grados en el momento de lanzar la hoja.
 * Se apaga con ClientConfig.swordTechniqueFx().
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SwordFx {
    private SwordFx() {}

    private record Fx(PhysicalTechnique tech, int variant, Vec3 pos, double yawRad, int rgb, long born) {}

    private static final List<Fx> LIVE = new ArrayList<>();
    /** Muestras por cinta: más no se nota y cada una cuesta dos vértices por cara. */
    private static final int N = 30;

    public static void spawn(Player p, PhysicalTechnique t, int variant) {
        if (!ClientConfig.swordTechniqueFx()) return;
        LIVE.add(new Fx(t, variant, p.position(), Math.toRadians(p.getYHeadRot()),
                AuraColors.resolve(p) & 0xFFFFFF, p.level().getGameTime()));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { LIVE.clear(); }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        float now = mc.level.getGameTime() + e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cam = e.getCamera().getPosition();
        LIVE.removeIf(f -> now - f.born() > 14);

        for (Fx f : LIVE) {
            float age = now - f.born();
            for (Ribbon r : ribbons(f, age)) {
                if (r.tips.size() < 2) continue;
                Vec3 near = r.tips.get(r.tips.size() - 1).subtract(cam);
                int rgb = f.rgb();
                KiVfxFrameQueue.submit(new PoseStack(), near, (ps, buf) -> draw(buf, ps.last(), r, cam, rgb));
            }
        }
    }

    // ── Geometría ────────────────────────────────────────────────────────────

    private static final class Ribbon {
        final List<Vec3> tips = new ArrayList<>(N), bases = new ArrayList<>(N);
        final List<Float> alpha = new ArrayList<>(N);
    }

    /** Las cintas de un tajo en el instante {@code age} (ticks desde que cayó). */
    private static List<Ribbon> ribbons(Fx f, float age) {
        double s = Math.sin(f.yawRad()), c = Math.cos(f.yawRad());
        Vec3 F = new Vec3(-s, 0, c), L = new Vec3(c, 0, s), U = new Vec3(0, 1, 0);
        Vec3 p = f.pos();
        List<Ribbon> out = new ArrayList<>(2);
        switch (f.tech()) {
            case RISING_SLASH -> out.add(arc(p.add(0, 0.9, 0).add(F.scale(0.2)), F, tilt(U, L, 20),
                    -70, 110, 2.9, 0.9, 4, 9, age, false));
            case CROSS_SLASH -> out.add(arc(p.add(0, 1.2, 0).add(F.scale(0.3)), F,
                    tilt(U, L, f.variant() == 0 ? -45 : 45), 65, -30, 2.7, 0.8, 3, 8, age, false));
            case SPIN_SLASH -> out.add(arc(p.add(0, 1.0, 0), F, L, 0, 360, 2.7, 0.75, 5, 9, age, false));
            case SKY_CLEAVE -> {
                out.add(arc(p.add(0, 1.0, 0).subtract(F.scale(0.3)), F, U, 145, -15, 3.4, 1.15, 4, 11, age, false));
                // Onda de choque en el suelo, delante: un aro que se abre.
                out.add(arc(p.add(F.scale(3.0)).add(0, 0.12, 0), F, L, 0, 360, 0.6 + 3.2 * Mth.clamp(age / 10f, 0, 1),
                        0.6, 1, 10, age, true));
            }
            case LIGHT_BLADE -> out.add(arc(p.add(0, 1.1, 0).add(F.scale(0.2)), F, tilt(U, L, 30),
                    70, -40, 2.2, 0.7, 3, 8, age, false));
            case PIERCING_THRUST -> {
                out.add(line(p.add(0, 1.15, 0).add(F.scale(0.4)), F, U, 5.5, 0.22, 6, age));
                out.add(line(p.add(0, 1.15, 0).add(F.scale(0.4)), F, L, 5.5, 0.22, 6, age));
            }
            default -> { }
        }
        return out;
    }

    /** Vector "arriba" del plano de un arco ladeado {@code deg} grados hacia la IZQUIERDA (negativo = derecha). */
    private static Vec3 tilt(Vec3 up, Vec3 left, double deg) {
        double r = Math.toRadians(deg);
        return up.scale(Math.cos(r)).add(left.scale(Math.sin(r)));
    }

    /**
     * Arco en el plano (A, B): punto(a) = centro + r (cos a A + sin a B). Se revela de a0 a a1 en
     * {@code reveal} ticks y se apaga a los {@code life}. El ancho de la cinta sigue un seno (fino en
     * las puntas, grueso en el medio); el alfa crece hacia la cabeza.
     */
    private static Ribbon arc(Vec3 center, Vec3 A, Vec3 B, double a0Deg, double a1Deg, double rOut, double width,
                              int reveal, int life, float age, boolean ground) {
        Ribbon r = new Ribbon();
        double head = ease(Mth.clamp(age / reveal, 0f, 1f));
        float fade = Math.max(0f, 1f - age / life);
        fade *= fade;
        if (fade <= 0.01f) return r;
        double a0 = Math.toRadians(a0Deg), a1 = Math.toRadians(a1Deg);
        for (int i = 0; i < N; i++) {
            double sN = i / (double) (N - 1);
            double a = a0 + (a1 - a0) * sN * head;
            double w = width * Math.pow(Math.sin(Math.PI * sN), 0.6);
            Vec3 dir = A.scale(Math.cos(a)).add(B.scale(Math.sin(a)));
            r.tips.add(center.add(dir.scale(rOut)));
            r.bases.add(center.add(dir.scale(rOut - w)));
            r.alpha.add((float) ((ground ? 0.55 : 0.9) * fade * (0.15 + 0.85 * sN)));
        }
        return r;
    }

    /** Banda recta de {@code len} bloques desde {@code start} hacia {@code dir}, de {@code half} de semiancho en {@code side}. */
    private static Ribbon line(Vec3 start, Vec3 dir, Vec3 side, double len, double half, int life, float age) {
        Ribbon r = new Ribbon();
        float fade = Math.max(0f, 1f - age / life);
        fade *= fade;
        if (fade <= 0.01f) return r;
        double reach = len * ease(Mth.clamp(age / 2f, 0f, 1f));
        for (int i = 0; i < N; i++) {
            double sN = i / (double) (N - 1);
            Vec3 c = start.add(dir.scale(reach * sN));
            double w = half * (1.0 - 0.6 * sN);
            r.tips.add(c.add(side.scale(w)));
            r.bases.add(c.subtract(side.scale(w)));
            r.alpha.add((float) (0.8 * fade * (1.0 - 0.7 * sN)));
        }
        return r;
    }

    private static double ease(double t) { return 1 - (1 - t) * (1 - t); }

    // ── Dibujo (el de la estela del arma: cinta tintada + núcleo claro) ─────────

    private static void draw(MultiBufferSource.BufferSource buf, PoseStack.Pose mat, Ribbon rb, Vec3 cam, int rgb) {
        if (!KiVfxRenderTypes.available()) return;
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        int n = rb.tips.size();
        float[] alpha = new float[n], coreAlpha = new float[n];
        List<Vec3> cores = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            alpha[i] = rb.alpha.get(i);
            coreAlpha[i] = alpha[i] * 0.8F;
            cores.add(rb.tips.get(i).add(rb.bases.get(i).subtract(rb.tips.get(i)).scale(0.4)));
        }
        RenderType soft = KiVfxRenderTypes.soft(KiVfxRenderTypes.TRAIL_TEXTURE);
        VertexConsumer vc = buf.getBuffer(soft);
        KiRibbon.surface(vc, mat, rb.tips, rb.bases, alpha, cam, r, g, b);
        buf.endBatch(soft);

        RenderType glow = KiVfxRenderTypes.glow(KiVfxRenderTypes.TRAIL_TEXTURE);
        VertexConsumer core = buf.getBuffer(glow);
        float cr = r + (1F - r) * 0.6F, cg = g + (1F - g) * 0.6F, cb = b + (1F - b) * 0.6F;
        KiRibbon.surface(core, mat, rb.tips, cores, coreAlpha, cam, cr, cg, cb);
        buf.endBatch(glow);
    }
}
