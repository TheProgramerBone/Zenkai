package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.aura.AuraLod;
import com.hmc.zenkai.feature.aura.AuraManager;
import com.hmc.zenkai.feature.aura.AuraProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * LLAMA EXTERIOR del sistema de aura nuevo (estilo dbrebirth-0.3): la superficie grande de picos
 * que sube 1-3 bloques sobre la cabeza. La capa interior ceñida al cuerpo es otra clase
 * ({@link AuraFlameRenderer}, RenderLayer sobre el PlayerModel); esta NO usa el modelo del jugador
 * sino su propia malla ({@link AuraFlameShellMesh}), porque el modelo son cubos que se separan al
 * desplazarlos a esta escala (confirmado en juego, ver el javadoc de la malla).
 * <p>
 * Se encola desde {@code RenderLevelStageEvent} en AFTER_PARTICLES (prioridad NORMAL: el
 * {@code KiVfxBloomPipeline} que consume la cola corre en LOWEST del mismo stage) y NO como
 * RenderLayer: no necesita la pose de los huesos, y fuera del render de entidades cada jugador
 * tiene su propio draw — lleva sus propios uniforms (color, giro, crecimiento) y un ShaderInstance
 * solo tiene un juego de uniforms por draw.
 * <p>
 * FASE 4 del plan (2026-09-29): el dibujado va por {@code KiVfxFrameQueue.submit}, así que la
 * llama entra en el bloom real con la misma malla, ordenada de atrás hacia delante junto a las
 * técnicas de ki. FASE 6: los números salen de {@link AuraFlameParams#from} — la personalidad de
 * raza+forma ({@code AuraProfile}) alrededor de los centros de {@link AuraFlameTuning}
 * ({@code /zenkaiauraflame set}).
 * <p>
 * Estado por jugador: solo la rampa de crecimiento. Se poda cada frame (los ids que ya no están
 * en el nivel se borran), así que no necesita engancharse a la limpieza del legacy.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraFlameShellRenderer {

    private AuraFlameShellRenderer() {}

    /** Alfa de vértice a crecimiento pleno, antes de la opacidad de primera persona. */
    private static final float ALPHA = 1.0f;

    private static final Map<Integer, Float> RAMP = new HashMap<>();
    private static final Map<Integer, Long> LAST_NANOS = new HashMap<>();
    /** Último perfil visible por jugador, para el desvanecido (ver la Fase 6 en el bucle). */
    private static final Map<Integer, AuraProfile> LAST_PROFILE = new HashMap<>();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !AuraFlameRenderType.available()) {
            RAMP.clear();
            LAST_NANOS.clear();
            LAST_PROFILE.clear();
            return;
        }
        boolean flame = AuraSystemDispatcher.useFlame();
        // Con el legacy activo no se enciende ninguna llama; las que quedaban encendidas del
        // cambio en caliente terminan su apagado (rampa hacia 0) y después ya no se entra aquí.
        if (!flame && RAMP.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        // Mismo reloj que AuraRenderer usa para el pulso del perfil (gameTime/20 + partialTick).
        float seconds = (float) ((mc.level.getGameTime() + pt) / 20.0);
        RenderType type = AuraFlameRenderType.shell();
        long now = System.nanoTime();
        Set<Integer> seen = new HashSet<>();

        for (Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer p)) continue;
            int id = p.getId();
            seen.add(id);

            float ramp = advanceRamp(id, now, flame && AuraClientState.isAuraActive(p));
            if (ramp <= 0.002f) continue;

            // Mismo criterio que el legacy: en primera persona propia la opacidad del aura es
            // configurable y puede estar a 0. (Con la cámara dentro de la malla casi toda la
            // superficie visible es cara trasera y el shader la oculta sola.)
            boolean selfFirstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            float fpOpacity = selfFirstPerson ? ClientConfig.auraFirstPersonOpacityFrac() : 1f;
            if (selfFirstPerson && fpOpacity <= 0f) continue;

            Vec3 at = p.getPosition(pt);
            if (AuraLod.byDistanceSq(camPos.distanceToSqr(at)) == AuraLod.SIGNATURE) continue;

            AuraColors.Layers layers = AuraColors.resolveLayers(p);
            int coreRgb = layers.inner();
            int outerRgb = layers.hasOuter() ? layers.outer() : layers.inner();

            // minecraft:generic.scale es el cuerpo real (Oozaru, formas grandes): la llama escala
            // con él, igual que el faldón legacy. getAttribute y no getAttributeValue por el
            // mismo motivo defensivo que AuraRenderer.
            AttributeInstance scaleAttr = p.getAttribute(Attributes.SCALE);
            float bodyScale = scaleAttr == null ? 1f : (float) scaleAttr.getValue();
            // Misma convención que LivingEntityRenderer (180 - yaw de cuerpo interpolado): los
            // picos giran con el cuerpo en vez de quedarse fijos al mundo.
            float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);

            // FASE 6: personalidad de raza+forma. Mientras el aura está activa se usa el perfil
            // vivo y se guarda; al apagarse (la rampa baja con el aura ya inactiva, perfil OFF)
            // se sigue con el último visible, para que la llama no cambie de forma de golpe justo
            // mientras se desvanece.
            AuraProfile profile = AuraClientState.isAuraActive(p)
                    ? AuraManager.profileOf(p, AuraClientState.isTurbo(p) ? 1f : 0f) : null;
            if (profile != null && profile.isVisible()) {
                LAST_PROFILE.put(id, profile);
            } else {
                profile = LAST_PROFILE.get(id);
            }
            AuraFlameParams params = AuraFlameParams.from(profile, seconds);

            float eased = ramp * ramp * (3f - 2f * ramp);
            int color = FastColor.ARGB32.color(
                    Math.round(255f * Mth.clamp(ALPHA * fpOpacity * eased * params.opacity(), 0f, 1f)),
                    255, 255, 255);
            // ═══ COLOR POR CAPA (2026-09-29) ═══ Antes UNA sola llama iba del color interior (en
            // el centro, de frente) al exterior (en el borde), y el centro usaba la capa caliente
            // ENTERA del interior: en Blue+Kaioken y en Beast el azul quedaba como un cian pálido
            // en la columna central que, al solaparse las lenguas, se lavaba a blanco — el azul
            // desaparecía y solo se leía el rojo del borde (capturas del usuario). Las referencias
            // (aura_2, aura_8, aura_10) dicen otra cosa: con dos tonos son DOS VOLÚMENES, cada uno
            // con su color SATURADO — una nube interior ceñida al cuerpo y la llama de picos por
            // fuera — y el blanco es solo un acento. Ahora:
            //   - cada capa usa SU tinte: saturado en el borde, algo calentado en el centro
            //     (hot_mix de su capa caliente, nunca la capa caliente entera);
            //   - con dos tonos se dibujan dos llamas: la exterior con el color exterior y una
            //     interior más pequeña (AuraFlameParams.innerLayer) con el interior.
            // AuraColors sigue siendo la única autoridad de QUÉ colores son (Kaioken incluido);
            // esto solo decide cómo se pintan.
            boolean twoTone = layers.hasOuter();
            float[] outerEdge = saturated(outerRgb);
            float[] innerEdge = saturated(coreRgb);
            float[] outerCenter = heatedCenter(outerEdge, params.coreWhite());
            float[] innerCenter = heatedCenter(innerEdge, params.coreWhite());
            // Con la cámara dentro de la propia llama el bloom sería un velo a pantalla completa:
            // en primera persona la llama propia se dibuja pero no brilla. El interruptor de bloom
            // del aura de ClientConfig vale para los dos sistemas.
            final boolean noBloom = selfFirstPerson || !ClientConfig.auraBloomEnabled();

            PoseStack pose = e.getPoseStack();
            pose.pushPose();
            pose.translate(at.x - camPos.x, at.y - camPos.y, at.z - camPos.z);
            pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
            pose.scale(bodyScale, bodyScale, bodyScale);
            // FASE 4: dibujado DIFERIDO en la cola del VFX de ki. KiVfxBloomPipeline la reproduce
            // dos veces (mundo y bloom) con la misma malla, shader y depth test: el bloom sale de la
            // llama real, no de un sustituto. La tarea se ejecuta DOS veces por frame: aquí dentro
            // no hay estado (la rampa ya se avanzó arriba). La cola vuelca el lote tras cada tarea,
            // así que los uniforms de cada capa valen solo para su draw.
            Vec3 fromCam = at.subtract(camPos);
            submitLayer(pose, fromCam, type, params, eased, outerCenter, outerEdge, color, noBloom);
            if (twoTone) {
                // La interior va ENCIMA de la exterior (se encola con una distancia un pelo MENOR y
                // la cola dibuja de lejos a cerca). No es el orden físico — la cara de delante de la
                // exterior está más cerca de la cámara — sino la convención del anime (aura_2,
                // aura_8): la nube interior se ve limpia y la llama de fuera solo asoma alrededor de
                // su silueta. Con el orden físico, el rojo del Kaioken tapaba el azul de Blue
                // ("el rojo prevalece", usuario 2026-09-29).
                AuraFlameParams inner = params.innerLayer();
                submitLayer(pose, fromCam.scale(0.9995), type, inner, eased, innerCenter, innerEdge, color, noBloom);
            }
            pose.popPose();
        }

        RAMP.keySet().retainAll(seen);
        LAST_NANOS.keySet().retainAll(seen);
        // Sin rampa no hay llama que desvanecer: el perfil guardado ya no hace falta.
        LAST_PROFILE.keySet().retainAll(RAMP.keySet());
    }

    /** Encola UNA capa de llama. Sin estado dentro de la tarea (se ejecuta dos veces por frame). */
    private static void submitLayer(PoseStack pose, Vec3 fromCam, RenderType type, AuraFlameParams params,
                                    float growth, float[] center, float[] edge, int color, boolean noBloom) {
        KiVfxFrameQueue.submit(pose, fromCam, (ps, buf) -> {
            boolean bloomPass = KiVfxFrameQueue.bloomPass();
            if (bloomPass && noBloom) return;
            AuraFlameRenderType.setupShell(new Matrix4f(ps.last().pose()), params, growth, center,
                    edge, bloomPass);
            VertexConsumer vc = buf.getBuffer(type);
            AuraFlameShellMesh.emit(vc, color);
        });
    }

    /**
     * Tinte de una capa con la saturación realzada ({@code saturation}): se aleja cada canal de
     * la luminancia. Un 0x5FE8FF (Blue) pasa de cian pastel a cian vivo sin cambiar de tono. Se
     * aplica ANTES de calentar el centro, para que el centro también parta del color vivo.
     */
    private static float[] saturated(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float s = Math.max(0f, AuraFlameTuning.get(AuraFlameTuning.Param.SATURATION));
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        return new float[]{
                Mth.clamp(luma + (r - luma) * s, 0f, 1f),
                Mth.clamp(luma + (g - luma) * s, 0f, 1f),
                Mth.clamp(luma + (b - luma) * s, 0f, 1f)};
    }

    /**
     * Color del CENTRO de una capa: su tinte calentado un poco hacia su capa caliente
     * ({@code KiVfxColors.derivedHot}, la misma del ki: azul hacia cian, amarillo hacia blanco
     * cálido) y, con mucho poder, un poco más hacia blanco. NUNCA la capa caliente entera: eso
     * dejaba el centro casi blanco y, sumado al solape de lenguas, borraba el tinte (ver
     * "COLOR POR CAPA" en el bucle).
     */
    private static float[] heatedCenter(float[] tint, float coreWhite) {
        float r = tint[0], g = tint[1], b = tint[2];
        float[] hot = KiVfxColors.derivedHot(r, g, b);
        float h = Mth.clamp(AuraFlameTuning.get(AuraFlameTuning.Param.HOT_MIX), 0f, 1f);
        float cr = Mth.lerp(h, r, hot[0]), cg = Mth.lerp(h, g, hot[1]), cb = Mth.lerp(h, b, hot[2]);
        return new float[]{Mth.lerp(coreWhite, cr, 1f), Mth.lerp(coreWhite, cg, 1f), Mth.lerp(coreWhite, cb, 1f)};
    }

    /** Rampa 0..1 en tiempo REAL (render corre por frame, no por tick), como AuraRimRenderer. */
    private static float advanceRamp(int id, long now, boolean active) {
        Long last = LAST_NANOS.put(id, now);
        float dt = last == null ? 0f : Math.min(0.25f, (now - last) / 1.0e9f);
        float ramp = RAMP.getOrDefault(id, 0f);
        if (active) {
            ramp += dt / Math.max(0.01f, AuraFlameTuning.get(AuraFlameTuning.Param.FADE_IN));
        } else {
            ramp -= dt / Math.max(0.01f, AuraFlameTuning.get(AuraFlameTuning.Param.FADE_OUT));
        }
        ramp = Mth.clamp(ramp, 0f, 1f);
        if (ramp <= 0f && !active) {
            RAMP.remove(id);
        } else {
            RAMP.put(id, ramp);
        }
        return ramp;
    }
}
