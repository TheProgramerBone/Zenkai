package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxCompositeRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxGeometry;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Energía de la destrucción (hakai) en los puños mientras Ultra Ego está activo (interruptor
 * encendido o forma puesta, con la skill comprada): simula golpear con el poder de la destrucción,
 * y es el aviso visual de la perforación de defensa de las técnicas físicas (ver
 * OverlayMetersSystem.uePenetration).
 *
 * Es una ESFERA pequeña por mano, no partículas: el mismo cuerpo que la bola de ki (cáscara +
 * núcleo de {@link KiVfxCompositeRenderer}, diferido por {@link KiVfxFrameQueue} para que entre en
 * el orden y el bloom del resto del VFX) con el perfil de la supernova —limbo oscuro, interior
 * granulado y centro encendido— que es justo la lectura de la bola de destrucción de Beerus: borde
 * casi negro, magenta por dentro y un núcleo rosa blanquecino (dos colores: tinte + segundo color).
 *
 * Anclada a los huesos ya animados por PAL (PlayerHandTracker), igual que la bola de carga. Las
 * anclas solo existen dentro de la ventana AFTER_PARTICLES del render del mundo, de ahí que esto
 * viva en ese stage.
 *
 * SOLO TERCERA PERSONA: en primera persona no se dibuja nada (pedido explícito tras probar una
 * versión contenida: incluso pequeña estorbaba).
 *
 * Lo ven los demás jugadores: el interruptor y el nivel de la skill viajan en el attachment de
 * stats, que se sincroniza a quienes lo rastrean.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class UltraEgoFistAura {
    private UltraEgoFistAura() {}

    /** Tinte (magenta violáceo del centro de la bola de Beerus) y segundo color (núcleo rosado). */
    // Más oscuro y saturado que antes: con un núcleo casi blanco la esfera se leía como un destello
    // blanco con halo (captura del usuario); la referencia es magenta con limbo negro.
    private static final int TINT = 0x8A1FE0;
    private static final int HOT = 0xD04CFF;

    /** Radio de la esfera en bloques. El nivel de la skill la agranda un poco. */
    private static final float BASE_RADIUS = 0.2f;
    private static final float RADIUS_PER_LEVEL = 0.007f;
    private static final float ALPHA = 1f;

    /** Desplazamiento respecto al hueso de la mano: hacia fuera del cuerpo y hacia abajo (bloques). */
    private static final float HAND_OUTWARD = 0.04f;
    private static final float HAND_DOWN = 0.07f;

    /** Segundos en aparecer / apagarse del todo. */
    private static final float FADE_IN = 0.35f;
    private static final float FADE_OUT = 0.5f;

    private static final Map<Integer, Float> RAMP = new HashMap<>();
    private static long lastNanos = 0L;

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

        for (Player p : mc.level.players()) {
            boolean on = active(p);
            Float prev = RAMP.get(p.getId());
            if (prev == null && !on) continue;
            float ramp = Mth.clamp((prev == null ? 0f : prev) + (on ? dt / FADE_IN : -dt / FADE_OUT), 0f, 1f);
            if (ramp <= 0f) {
                RAMP.remove(p.getId());
                continue;
            }
            RAMP.put(p.getId(), ramp);
            float eased = ramp * ramp * (3f - 2f * ramp);

            // Solo en tercera persona (pedido explícito tras probarlo): en primera persona no se
            // dibuja, ni siquiera como respaldo.
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            var anchors = PlayerHandTracker.get(p.getId());
            if (anchors == null) continue;   // sin hueso: mejor nada que una bola mal puesta
            // Un poco hacia fuera del cuerpo y más abajo que el hueso de la mano: así envuelve el
            // puño en vez de quedar sobre la muñeca.
            double rad = Math.toRadians(Mth.lerp(pt, p.yBodyRotO, p.yBodyRot));
            Vec3 side = new Vec3(-Math.cos(rad), 0.0, -Math.sin(rad)).scale(HAND_OUTWARD);
            Vec3 down = new Vec3(0.0, -HAND_DOWN, 0.0);
            Vec3 right = camPos.add(anchors.rightHand()).add(down).add(side);
            Vec3 left = camPos.add(anchors.leftHand()).add(down).subtract(side);

            int lvl = Math.min(10, SkillEffects.level(p, SkillEffects.ULTRA_EGO));
            float radius = (BASE_RADIUS + RADIUS_PER_LEVEL * lvl)
                    * (1f + 0.06f * Mth.sin((now + pt) * 0.35f)) * (0.5f + 0.5f * eased);
            float alpha = ALPHA * eased;
            if (alpha <= 0.01f) continue;

            submit(pose, camPos, right, radius, alpha);
            submit(pose, camPos, left, radius, alpha);
        }
    }

    private static void submit(PoseStack pose, Vec3 camPos, Vec3 origin, float radius, float alpha) {
        KiVfxFrameQueue.submit(pose, origin.subtract(camPos), (ps, buf) -> {
            KiVfxProfile v = KiVfxProfile.of(KiTechniqueType.SUPERNOVA);
            // Segundo color propio: cada tarea declara el suyo al empezar (ver KiVfxColors).
            KiVfxColors.begin(HOT);
            ps.pushPose();
            ps.translate(origin.x - camPos.x, origin.y - camPos.y, origin.z - camPos.z);
            float proximity = KiVfxRenderTypes.proximity(camPos.distanceTo(origin), radius);
            KiVfxCompositeRenderer.render(buf, v, KiVfxGeometry.chargeSphere(), KiVfxGeometry.core(v, false),
                    ps, radius * 2f, ((TINT >> 16) & 0xFF) / 255f, ((TINT >> 8) & 0xFF) / 255f,
                    (TINT & 0xFF) / 255f, alpha, proximity);
            ps.popPose();
        });
    }
}
