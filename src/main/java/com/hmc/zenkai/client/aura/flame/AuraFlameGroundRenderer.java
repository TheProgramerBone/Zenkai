package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraChargeGroundFx;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.client.aura.AuraQuads;
import com.hmc.zenkai.client.aura.ModAuraRenderType;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.aura.AuraManager;
import com.hmc.zenkai.feature.aura.AuraProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Estrella de rayas pegada al suelo mientras se carga ki o se transforma (2026-09-30): el tercer
 * efecto de suelo de la comparación con DragonMine Z, que dibuja un quad plano con su textura
 * {@code *_cross.png} bajo el jugador. Aquí es {@code aura_ground_burst.png}
 * (tools/gen_aura_charge_fx.py), pixel-art de bordes duros, tintada con el color EXTERIOR del aura.
 * El polvo y las rocas son partículas aparte ({@link AuraChargeGroundFx}).
 * <p>
 * Solo con la llama nueva: el legacy ya tiene su anillo de lengüetas ({@code AuraGroundRenderer}).
 * <p>
 * Dos estrellas superpuestas que giran en sentidos opuestos y, a saltos (cada {@link #STEP_TICKS}
 * ticks), cambian de escala y de ángulo: el "a pasos" del anime, no un giro liso de decal. La
 * rampa de encendido/apagado va en tiempo real, como la de la llama.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraFlameGroundRenderer {

    private AuraFlameGroundRenderer() {}

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/entity/aura_ground_burst.png");
    /** Semilado de la estrella grande, en bloques, a escala de cuerpo 1 y escala de llama 1. Con
     *  el poder base (size 1.2) sale 1.5, el tamaño de la primera versión. */
    private static final float HALF = 1.25f;
    private static final float ALPHA = 0.8f;
    /** Separación sobre la superficie, para no pelear en profundidad con el bloque. */
    private static final float Y_LIFT = 0.02f;
    private static final int STEP_TICKS = 2;
    private static final float FADE_IN = 0.25f, FADE_OUT = 0.4f;

    private static final Map<Integer, Float> RAMP = new HashMap<>();
    private static final Map<Integer, Long> LAST_NANOS = new HashMap<>();
    /** Última escala por poder de cada jugador, para el apagado. */
    private static final Map<Integer, Float> LAST_POWER = new HashMap<>();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            RAMP.clear();
            LAST_NANOS.clear();
            LAST_POWER.clear();
            return;
        }
        boolean flame = AuraSystemDispatcher.useFlame();
        if (!flame && RAMP.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        double ticks = mc.level.getGameTime() + pt;
        float seconds = (float) (ticks / 20.0);
        int step = (int) Math.floor(ticks / STEP_TICKS);
        long now = System.nanoTime();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        Set<Integer> seen = new HashSet<>();
        PoseStack pose = e.getPoseStack();

        for (Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer p) || p.isSpectator()) continue;
            int id = p.getId();
            seen.add(id);

            // Sin carga y sin estrella que desvanecer, ni se busca el suelo (antes se buscaba para
            // cada jugador en cada frame — auditoría del 2026-09-30).
            boolean charging = AuraClientState.isChargingOrTransforming(p);
            if (!charging && !RAMP.containsKey(id)) continue;
            Vec3 at = p.getPosition(pt);
            AuraChargeGroundFx.Ground g = AuraChargeGroundFx.findGround(mc.level, p, at.x, at.y, at.z);
            // Estilos de aura compuesta con suelo "none" no dejan estrella (el resto sí: también la
            // divina, cuya respuesta "clean" quita polvo y rocas pero conserva esta marca de luz).
            // Solo PISANDO el suelo (pedido del usuario, 2026-09-30): antes bastaba con tener suelo a
            // menos de 2 bloques, y la estrella aparecía bajo un jugador que cargaba flotando o
            // volando bajo. onGround llega sincronizado también para los jugadores remotos.
            boolean active = flame && g != null && charging && p.onGround()
                    && !"none".equals(com.hmc.zenkai.client.aura.compose.AuraComposer.groundModeOf(p.getId()));
            float ramp = advanceRamp(id, now, active);
            if (ramp <= 0.002f || g == null) continue;
            if (camPos.distanceToSqr(at) > 64 * 64) continue;

            AuraColors.Layers layers = AuraColors.resolveLayers(p);
            int rgb = layers.hasOuter() ? layers.outer() : layers.inner();
            float r = AuraQuads.red(rgb), gg = AuraQuads.green(rgb), b = AuraQuads.blue(rgb);
            float near = 1f - (float) (g.height() / (AuraChargeGroundFx.MAX_HEIGHT + 0.5));
            float a = ALPHA * ramp * Mth.clamp(near, 0f, 1f);

            AttributeInstance scaleAttr = p.getAttribute(Attributes.SCALE);
            float bodyScale = scaleAttr == null ? 1f : (float) scaleAttr.getValue();
            float grow = ramp * ramp * (3f - 2f * ramp);
            // PODER (2026-09-30, pedido del usuario): la estrella crece con el MISMO factor que la
            // llama (AuraFlameParams.sizeScale), así un endgame remueve más suelo que un novato y
            // el Kaioken la hace latir. Mientras se apaga usa el último valor: el perfil de un
            // aura ya inactiva no es el que se estaba viendo.
            float power;
            if (active) {
                AuraProfile profile = AuraManager.profileOf(p, AuraClientState.isTurbo(p) ? 1f : 0f);
                power = AuraFlameParams.sizeScale(AuraFlameParams.powerFactor(profile, seconds));
                LAST_POWER.put(id, power);
            } else {
                power = LAST_POWER.getOrDefault(id, AuraFlameParams.sizeScale(1f));
            }

            if (vc == null) vc = buffers.getBuffer(ModAuraRenderType.energyCrisp(TEX));
            pose.pushPose();
            pose.translate(at.x - camPos.x, g.surfaceY() + Y_LIFT - camPos.y, at.z - camPos.z);
            pose.scale(bodyScale, 1f, bodyScale);
            for (int layer = 0; layer < 2; layer++) {
                float jitter = AuraQuads.hash01(id, layer, step, 7);
                float spin = (layer == 0 ? 25f : -40f) * seconds + jitter * 20f;
                float half = HALF * power * grow * (layer == 0 ? 1f : 0.7f) * (0.9f + 0.2f * jitter);
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(spin));
                // Pegada al suelo: cada capa un milímetro más alta que la anterior.
                quad(vc, pose.last(), half, layer * 0.002f, r, gg, b, a * (layer == 0 ? 1f : 0.85f));
                pose.popPose();
            }
            pose.popPose();
        }
        if (vc != null) buffers.endBatch(ModAuraRenderType.energyCrisp(TEX));

        RAMP.keySet().retainAll(seen);
        LAST_NANOS.keySet().retainAll(seen);
        LAST_POWER.keySet().retainAll(RAMP.keySet());
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose m, float half, float y,
                             float r, float g, float b, float a) {
        vert(vc, m, -half, y, -half, 0f, 0f, r, g, b, a);
        vert(vc, m, -half, y, half, 0f, 1f, r, g, b, a);
        vert(vc, m, half, y, half, 1f, 1f, r, g, b, a);
        vert(vc, m, half, y, -half, 1f, 0f, r, g, b, a);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z,
                             float u, float v, float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z)
                .setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(AuraQuads.FULL_BRIGHT)
                .setNormal(m, 0f, 1f, 0f);
    }

    private static float advanceRamp(int id, long now, boolean active) {
        Long last = LAST_NANOS.put(id, now);
        float dt = last == null ? 0f : Math.min(0.25f, (now - last) / 1.0e9f);
        float ramp = RAMP.getOrDefault(id, 0f);
        ramp += active ? dt / FADE_IN : -dt / FADE_OUT;
        ramp = Mth.clamp(ramp, 0f, 1f);
        if (ramp <= 0f && !active) {
            RAMP.remove(id);
        } else {
            RAMP.put(id, ramp);
        }
        return ramp;
    }
}
