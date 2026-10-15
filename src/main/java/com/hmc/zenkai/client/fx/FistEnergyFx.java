package com.hmc.zenkai.client.fx;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.PlayerHandTracker;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiRibbon;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxCompositeRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxGeometry;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.combat.KiFist;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.kiweapon.KiWeaponState;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ModParticles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Energía alrededor de los PUÑOS: Ki Fist (color del aura de quien pega, con el interruptor encendido) y Ultra Ego
 * (energía de la destrucción, magenta oscuro). Sustituye a la antigua UltraEgoFistAura (una esfera morada por mano).
 *
 * UNA CAJA que ABRAZA la mano (2026-10-14). La primera versión de esta clase era una esfera con tres arcos en órbita y
 * un chorro de rayos: se leía como "muchos efectos" y la esfera flotaba EN la mano en vez de cubrirla. Ahora es un
 * prisma alineado con el brazo (ejes que captura HandAnchorLayer en {@link PlayerHandTracker#axes}): aristas de ki
 * (tubos finos del pipeline de las armas) y caras translúcidas, centrado en la mano y lo bastante largo para tapar la
 * muñeca. Solo un par de chispas por segundo; Ultra Ego es la misma caja en magenta, con más arcos y chispas que suben.
 *
 * Anclada a los huesos ya animados por PAL; solo existe dentro de la ventana AFTER_PARTICLES del render del mundo.
 * SOLO TERCERA PERSONA. Un puño con algo en la mano (ítem o arma de ki) no lleva caja: el bonus de Ki Fist es de puños.
 * Se apaga con ClientConfig.kiFistAura().
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class FistEnergyFx {
    private FistEnergyFx() {}

    /** Cómo se ve cada canal. */
    private record Style(float arcRate, float sparkRate, boolean rising, KiVfxProfile edges) {}

    // Ultra Ego: borde casi negro, magenta por dentro y núcleo rosa (la bola de Beerus).
    public static final int UE_TINT = 0x8A1FE0, UE_HOT = 0xD04CFF;
    private static final Style ULTRA_EGO = new Style(0f, 0.35f, true, KiVfxProfile.KI_WEAPON_JAGGED);
    private static final Style KI_FIST = new Style(0f, 0.20f, false, KiVfxProfile.KI_WEAPON);

    /** Cuánto sobresale la caja del extremo del brazo, en píxeles de modelo (la malla del puño llega justo ahí). */
    private static final float TIP_PAD_PX = 0.3f;
    private static final float FADE_IN = 0.30f, FADE_OUT = 0.45f;

    /** Rampa 0..1 por jugador: [0] = Ki Fist, [1] = Ultra Ego. */
    private static final Map<Integer, float[]> RAMP = new HashMap<>();
    private static final Map<Integer, Long> LAST_SPARK_TICK = new HashMap<>();
    private static long lastNanos = 0L;

    /** ¿Está activo Ultra Ego (forma puesta o interruptor, con la skill comprada)? Lo lee también el halo de las armas. */
    public static boolean ultraEgoActive(Player p) {
        if (SkillEffects.level(p, SkillEffects.ULTRA_EGO) <= 0) return false;
        PlayerFormAttachment fm = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        return FormIds.ULTRA_EGO.equals(fm.getFormId()) || SkillToggles.isOn(p, SkillEffects.ULTRA_EGO);
    }

    /** ¿Este brazo tiene el puño libre? (nada en la mano ni arma/escudo de ki). */
    private static boolean fistFree(Player p, HumanoidArm arm) {
        if (KiWeaponState.variantForArm(p, arm) != null) return false;
        return (arm == p.getMainArm() ? p.getMainHandItem() : p.getOffhandItem()).isEmpty();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        RAMP.clear();
        LAST_SPARK_TICK.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            RAMP.clear();
            return;
        }
        long nanos = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (nanos - lastNanos) / 1.0e9f);
        lastNanos = nanos;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        long now = mc.level.getGameTime();
        boolean enabled = ClientConfig.kiFistAura();

        for (Player p : mc.level.players()) {
            float[] ramp = RAMP.get(p.getId());
            boolean wantFist = enabled && KiFist.isOn(p);
            boolean wantUe = enabled && ultraEgoActive(p);
            if (ramp == null && !wantFist && !wantUe) continue;
            if (ramp == null) ramp = new float[2];
            ramp[0] = Mth.clamp(ramp[0] + (wantFist ? dt / FADE_IN : -dt / FADE_OUT), 0f, 1f);
            ramp[1] = Mth.clamp(ramp[1] + (wantUe ? dt / FADE_IN : -dt / FADE_OUT), 0f, 1f);
            if (ramp[0] <= 0f && ramp[1] <= 0f) {
                RAMP.remove(p.getId());
                continue;
            }
            RAMP.put(p.getId(), ramp);

            // Solo en tercera persona: en primera no se dibuja, ni siquiera como respaldo.
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            var anchors = PlayerHandTracker.get(p.getId());
            Vec3[] axes = PlayerHandTracker.axes(p.getId());
            if (anchors == null || axes == null) continue;   // sin hueso: mejor nada que una caja mal puesta

            Vec3[] rAx = {axes[0], axes[1], axes[2]}, lAx = {axes[3], axes[4], axes[5]};
            // Medidas del puño del modelo que se ve (geo racial o brazo vanilla): la caja se mide desde SU pivote de
            // hombro y termina en la punta REAL de ese brazo.
            ArmGeometry.Arm ra = ArmGeometry.of(p, true), la = ArmGeometry.of(p, false);
            Vec3 right = camPos.add(axes[6]);   // pivote del hombro del modelo visible (ver HandAnchorLayer)
            Vec3 left = camPos.add(axes[7]);

            boolean spark = LAST_SPARK_TICK.getOrDefault(p.getId(), -1L) != now;
            if (spark) LAST_SPARK_TICK.put(p.getId(), now);

            boolean rightFree = fistFree(p, HumanoidArm.RIGHT), leftFree = fistFree(p, HumanoidArm.LEFT);
            if (ramp[0] > 0.01f) {
                int rgb = AuraColors.resolve(p) & 0xFFFFFF;
                if (rightFree) box(pose, camPos, right, rAx, KI_FIST, rgb, hotOf(rgb), 0, ramp[0], now + pt, spark, ra);
                if (leftFree) box(pose, camPos, left, lAx, KI_FIST, rgb, hotOf(rgb), 1, ramp[0], now + pt, spark, la);
            }
            if (ramp[1] > 0.01f) {
                // Ultra Ego va en los dos puños, con la misma caja que Ki Fist (ajustada al brazo, sin sobresalir).
                box(pose, camPos, right, rAx, ULTRA_EGO, UE_TINT, UE_HOT, 2, ramp[1], now + pt, spark, ra);
                box(pose, camPos, left, lAx, ULTRA_EGO, UE_TINT, UE_HOT, 3, ramp[1], now + pt, spark, la);
            }
        }
    }

    /** Color caliente del núcleo: el del aura aclarado hacia el blanco. */
    private static int hotOf(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (255 - r) * 0.55; g += (255 - g) * 0.55; b += (255 - b) * 0.55;
        return (r << 16) | (g << 8) | b;
    }

    private static void box(PoseStack pose, Vec3 camPos, Vec3 pivot, Vec3[] ax, Style st, int tint, int hot, int slot,
                            float ramp, float time, boolean spark, ArmGeometry.Arm arm) {
        float eased = ramp * ramp * (3f - 2f * ramp);
        // Se abre desde el puño al encenderse; un latido lento la mantiene viva.
        // El latido solo encoge (nunca pasa de 1): a tamaño completo la caja no sobresale del brazo.
        float k = (0.6f + 0.4f * eased) * (1f - 0.03f * (0.5f + 0.5f * Mth.sin(time * 0.35f + slot)));
        final float px = 1f / 16f;
        Vec3 ex = ax[0].scale(arm.hx() * px * k), ey = ax[1].scale(arm.hand() / 2f * px * k),
                ez = ax[2].scale(arm.hz() * px * k);
        // La caja TERMINA en la punta del brazo (+ un margen): su centro queda medio puño antes de la punta, y a
        // medio abrir (k < 1) se encoge hacia la punta en vez de hundirse en la muñeca.
        final Vec3 center = pivot.add(ax[0].scale(arm.cx() * px))
                .add(ax[1].scale((arm.tip() + TIP_PAD_PX) * px)).subtract(ey);

        KiVfxFrameQueue.submit(pose, center.subtract(camPos), (ps, buf) -> {
            if (!KiVfxRenderTypes.available()) return;
            float r = ((tint >> 16) & 0xFF) / 255f, g = ((tint >> 8) & 0xFF) / 255f, b = (tint & 0xFF) / 255f;
            ps.pushPose();
            ps.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
            Vec3[] c = new Vec3[8];   // bit0 = X, bit1 = Y, bit2 = Z
            for (int i = 0; i < 8; i++) {
                c[i] = ex.scale((i & 1) == 0 ? -1 : 1).add(ey.scale((i & 2) == 0 ? -1 : 1))
                        .add(ez.scale((i & 4) == 0 ? -1 : 1));
            }

            // Caras translúcidas.
            RenderType soft = KiVfxRenderTypes.soft(KiVfxRenderTypes.TRAIL_TEXTURE);
            VertexConsumer vc = buf.getBuffer(soft);
            int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
            float[] a = {0.28f * eased, 0.28f * eased};
            for (int[] f : faces) {
                KiRibbon.surface(vc, ps.last(), List.of(c[f[0]], c[f[1]]), List.of(c[f[3]], c[f[2]]), a,
                        Vec3.ZERO, r, g, b);
            }
            buf.endBatch(soft);

            // Aristas de ki: 4 a lo largo del brazo + los dos marcos de los extremos.
            KiVfxColors.begin(hot);
            KiVfxRenderTypes.resetAxis();
            int[][] edges = {{0, 2}, {1, 3}, {4, 6}, {5, 7},
                    {0, 1}, {1, 5}, {5, 4}, {4, 0}, {2, 3}, {3, 7}, {7, 6}, {6, 2}};
            for (int[] e : edges) {
                Vec3 p0 = c[e[0]], p1 = c[e[1]];
                List<Vec3> pts = new ArrayList<>(List.of(p0, p0.add(p1).scale(0.5), p1));
                KiVfxCompositeRenderer.renderColumn(buf, st.edges, ps, KiVfxGeometry.tubePath(pts, 0F),
                        0.011f, 1f, r, g, b, eased, false, 1f);
            }
            ps.popPose();
        });

        if (spark && ramp > 0.3f) sparks(center, st, tint, ex, ey, ez);
    }

    private static void sparks(Vec3 center, Style st, int rgb, Vec3 ex, Vec3 ey, Vec3 ez) {
        Minecraft mc = Minecraft.getInstance();
        var rng = mc.level.random;
        int arcs = (int) st.arcRate + (rng.nextFloat() < st.arcRate - (int) st.arcRate ? 1 : 0);
        int sparks = (int) st.sparkRate + (rng.nextFloat() < st.sparkRate - (int) st.sparkRate ? 1 : 0);
        for (int i = 0; i < arcs + sparks; i++) {
            Vec3 p = center.add(ex.scale(rng.nextDouble() * 2 - 1)).add(ey.scale(rng.nextDouble() * 2 - 1))
                    .add(ez.scale(rng.nextDouble() * 2 - 1));
            Vec3 d = p.subtract(center).normalize();
            if (i < arcs) {
                mc.level.addParticle(ModParticles.arc(rgb, 0.22f), p.x, p.y, p.z, d.x, d.y, d.z);
            } else {
                double up = st.rising ? 0.05 : 0.0;
                mc.level.addParticle(ModParticles.spark(rgb, 0.3f), p.x, p.y, p.z,
                        d.x * 0.04, d.y * 0.04 + up, d.z * 0.04);
            }
        }
    }
}
