package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ModParticles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Aura morada en los puños mientras Ultra Ego está activo (interruptor encendido o forma puesta,
 * con la skill comprada): simula golpear con el poder de la destrucción, y es el aviso visual de
 * la perforación de defensa de las técnicas físicas (ver OverlayMetersSystem.uePenetration).
 *
 * Se dibuja como chispas de arco en cada mano, ancladas a los huesos ya animados por PAL
 * (PlayerHandTracker) — el mismo anclaje que usa la bola de carga de ki, así que sigue los puños
 * en tercera persona y en primera. Las anclas solo existen dentro de la ventana AFTER_PARTICLES
 * del render del mundo, de ahí que esto viva en ese stage y no en un tick de cliente. Sin dato de
 * hueso (primera persona sin animación corporal de PAL) cae a un punto fijo delante del jugador,
 * igual que KiVfxChargeRenderer.
 *
 * Lo ven los demás jugadores: el interruptor y el nivel de la skill viajan en el attachment de
 * stats, que se sincroniza a quienes lo rastrean.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class UltraEgoFistAura {
    private UltraEgoFistAura() {}

    /** Morado de Ultra Ego (mismo tono que aura_rgb de la forma). */
    private static final int PURPLE = 0x9A3CFF;

    /** Chispas por mano y por tick. Con nivel más alto (más perforación) sale algo más. */
    private static final float BASE_RATE = 1.2f;
    private static final float RATE_PER_LEVEL = 0.12f;
    private static final float SPARK_RADIUS = 0.10f;
    private static final float SPARK_SIZE = 0.22f;

    /** Respaldo de primera persona sin hueso: los mismos números que la bola de carga. */
    private static final float FP_FORWARD = 0.35f;
    private static final float FP_SIDE    = 0.10f;
    private static final float FP_DOWN    = 0.25f;

    private static final Map<Integer, Long> LAST_TICK = new HashMap<>();

    private static boolean active(Player p) {
        if (SkillEffects.level(p, SkillEffects.ULTRA_EGO) <= 0) return false;
        PlayerFormAttachment fm = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        return FormIds.ULTRA_EGO.equals(fm.getFormId())
                || SkillToggles.isOn(p, SkillEffects.ULTRA_EGO);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = mc.level.getGameTime();
        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);

        for (Player p : mc.level.players()) {
            if (!active(p)) continue;
            // Una vez por tick de juego, no por fotograma: el render corre a cualquier FPS.
            Long last = LAST_TICK.get(p.getId());
            if (last != null && last == now) continue;
            LAST_TICK.put(p.getId(), now);

            Vec3 right;
            Vec3 left;
            var anchors = PlayerHandTracker.get(p.getId());
            if (anchors != null) {
                right = camPos.add(anchors.rightHand());
                left = camPos.add(anchors.leftHand());
            } else if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                double rad = Math.toRadians(Mth.lerp(pt, p.yBodyRotO, p.yBodyRot));
                Vec3 look = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
                Vec3 side = new Vec3(-look.z, 0.0, look.x);
                Vec3 base = p.getPosition(pt).add(0, p.getEyeHeight(), 0)
                        .add(look.scale(FP_FORWARD)).subtract(0.0, FP_DOWN, 0.0);
                right = base.add(side.scale(FP_SIDE));
                left = base.subtract(side.scale(FP_SIDE));
            } else {
                continue;   // sin hueso y sin respaldo: mejor nada que una chispa mal puesta
            }

            int lvl = Math.min(10, SkillEffects.level(p, SkillEffects.ULTRA_EGO));
            float rate = BASE_RATE + RATE_PER_LEVEL * lvl;
            emit(mc, right, rate);
            emit(mc, left, rate);
        }
        if ((now & 0xFF) == 0) LAST_TICK.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private static void emit(Minecraft mc, Vec3 at, float rate) {
        RandomSource rnd = mc.level.random;
        int count = (int) rate;
        if (rnd.nextFloat() < rate - count) count++;
        for (int i = 0; i < count; i++) {
            double theta = rnd.nextDouble() * Math.PI * 2.0;
            double z = rnd.nextDouble() * 2.0 - 1.0;
            double planar = Math.sqrt(Math.max(0.0, 1.0 - z * z));
            mc.level.addParticle(ModParticles.arc(PURPLE, SPARK_SIZE),
                    at.x + planar * Math.cos(theta) * SPARK_RADIUS,
                    at.y + z * SPARK_RADIUS,
                    at.z + planar * Math.sin(theta) * SPARK_RADIUS,
                    0, 0, 0);
        }
    }
}
