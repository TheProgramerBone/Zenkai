package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.aura.AuraLod;
import com.hmc.zenkai.feature.aura.AuraManager;
import com.hmc.zenkai.feature.aura.AuraProfile;
import com.hmc.zenkai.feature.aura.AuraSkirts;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Orquestador del aura. NO CALCULA NADA.
 * Su trabajo entero es: por cada jugador con aura activa, pedir el perfil a
 * AuraManager, el color a AuraColors, montar el Plan y pasárselo a las capas. Ni una
 * línea de aritmética de aura vive aquí; si aparece alguna, está en el sitio
 * equivocado.
 *   AuraManager -> AuraState -> AuraProfile -> AuraSkirts.plan() -> capas
 * Se engancha a AFTER_PARTICLES: el jugador ya escribió profundidad, así que el
 * depth-test LEQUAL lo ocluye correctamente. Sin mixins y sin entidades auxiliares —
 * el aura pertenece al render del jugador, no al mundo.
 * ORDEN DE CAPAS: ground, masa+núcleo, wisps, chispas. Es el orden LÓGICO; la garantía
 * visual la da sortOnUpload=true del RenderType, que ordena los quads translúcidos por
 * profundidad al subirlos. Por eso el núcleo lleva CORE_Z_OFFSET: masa y núcleo del
 * mismo faldón comparten z y el sort no sabría cuál va delante.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraRenderer {
    private AuraRenderer() {}

    /** Última banda mínima por calidad. TODO(L7): leerla de ClientConfig. */
    private static final AuraLod QUALITY_FLOOR = null;

    private static long lastTick = Long.MIN_VALUE;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        long t = mc.level.getGameTime();
        double ticks = t + pt;
        float seconds = (float) (ticks / 20.0);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        long dtTicks = (lastTick == Long.MIN_VALUE) ? 0 : Math.min(10, Math.max(0, t - lastTick));
        lastTick = t;

        for (Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer p)) continue;
            if (!AuraClientState.isAuraActive(p)) continue;

            // Ajuste de opacidad SOLO para el propio jugador en primera persona: los demás se
            // ven siempre a fuerza completa. Con la opción al 0% (comportamiento de siempre) ni
            // se calcula el perfil, para no gastar nada en un aura que no se va a dibujar.
            boolean selfFirstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            float fpOpacity = selfFirstPerson ? ClientConfig.auraFirstPersonOpacityFrac() : 1f;
            if (selfFirstPerson && fpOpacity <= 0f) continue;

            Vec3 at = p.getPosition(pt);
            double distSq = camPos.distanceToSqr(at);

            // 1. Estado -> perfil. El significado se resuelve aquí dentro.
            float turbo = AuraClientState.isTurbo(p) ? 1f : 0f;
            AuraProfile profile = AuraManager.profileOf(p, turbo);
            if (fpOpacity < 1f) profile = profile.faded(fpOpacity);
            if (!profile.isVisible()) continue;

            // 2. Color: AuraColors sigue siendo la única autoridad del tinte.
            AuraColors.Layers layers = AuraColors.resolveLayers(p);
            AuraSkirts.Plan plan = AuraSkirts.plan(profile, distSq, QUALITY_FLOOR,
                    layers.inner(), layers.hasOuter() ? layers.outer() : -1);
            if (plan.isEmpty()) continue;

            // 3. Movimiento (una sola muestra por frame, la comparten tilt y estela).
            AuraTiltController.Motion mo = AuraTiltController.sample(p, at);

            // Estela: coordenadas de mundo, antes del translate al jugador.
            AuraTrailRenderer.render(pose, buffers, p, at, camPos, layers.inner(), mo);

            // Emisores anclados al mundo: se dibujan luego, en conjunto.
            AuraWispRenderer.spawn(p, at, plan, t);
            AuraSparkRenderer.spawn(p, at, plan, t, dtTicks);

            // Dirección jugador->cámara en XZ, para atenuar el sector frontal.
            double dcx = camPos.x - at.x, dcz = camPos.z - at.z;
            double dlen = Math.sqrt(dcx * dcx + dcz * dcz);
            float toCamX = dlen < 1.0e-4 ? Float.NaN : (float) (dcx / dlen);
            float toCamZ = dlen < 1.0e-4 ? Float.NaN : (float) (dcz / dlen);

            pose.pushPose();
            pose.translate(at.x - camPos.x, at.y - camPos.y, at.z - camPos.z);
            // El "size"/"pulsedSize" del perfil (AuraProfile) es PODER, no cuerpo — nunca
            // depende de la escala de la forma a propósito (ver el javadoc de AuraProfile).
            // Pero minecraft:generic.scale SÍ es el cuerpo real (FormSystem.applyFormScale ya
            // lo escribe por forma), y el aura vive pegada a él: sin este escalado, un Oozaru a
            // scale 3.0 queda con la misma aura de tamaño normal, minúscula sobre un cuerpo
            // gigante. Se aplica ANTES del tilt a propósito: así el pivote de inclinación
            // (BODY_PIVOT_Y) también escala con el cuerpo en vez de quedarse a la altura de un
            // jugador normal.
            // getAttribute (no getAttributeValue): el atributo puede faltar en algún borde
            // (entidad recién creada, mod externo) y getAttributeValue no se defiende de un
            // null ahí — mismo patrón defensivo que FormSystem.applyFormScale.
            AttributeInstance scaleAttr = p.getAttribute(Attributes.SCALE);
            float bodyScale = scaleAttr == null ? 1f : (float) scaleAttr.getValue();
            if (bodyScale != 1f) pose.scale(bodyScale, bodyScale, bodyScale);
            AuraTiltController.apply(pose, p, mo);

            AuraGroundRenderer.render(pose, buffers, plan, p, ticks, seconds, p.getId());
            AuraSkirtRenderer.render(pose, buffers, plan, ticks, seconds, p.getId(),
                    toCamX, toCamZ);

            pose.popPose();
        }

        // Wisps y chispas del conjunto de jugadores: siguen vivas aunque el aura se apague,
        // así que se simulan y dibujan fuera del bucle.
        AuraWispRenderer.renderAll(pose, buffers, cam, camPos, t, pt);
        AuraSparkRenderer.renderAll(pose, buffers, cam, camPos, t, pt);

        buffers.endBatch();
    }

    /**
     * Limpieza al dejar de trackear a un jugador. SIN ESTO SE FILTRA MEMORIA: el
     * renderer anterior tenía un clearTilt() público que no llamaba nadie, y cada
     * jugador que se desconectaba dejaba entradas vivas en seis mapas.
     */
    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking e) {
        if (!(e.getTarget() instanceof Player target)) return;
        int id = target.getId();
        AuraTiltController.clear(id);
        AuraTrailRenderer.clear(id);
        AuraWispRenderer.clear(id);
        AuraSparkRenderer.clear(id);
    }
}