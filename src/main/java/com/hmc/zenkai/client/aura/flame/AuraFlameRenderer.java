package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * FASE 1 (geometría) del sistema de aura NUEVO — estilo dbrebirth-0.3, ver
 * .claude/pendiente/aura-dbrebirth-sistema-propuesta.md. Vive en un paquete AISLADO
 * ({@code client.aura.flame}) del sistema legacy ({@code client.aura}, sin el sufijo
 * {@code .flame}): esta clase solo LEE datos ya resueltos del legacy
 * ({@link AuraClientState#isAuraActive}, {@link AuraColors#resolveLayers}), nunca importa ni
 * modifica sus clases de renderizado, y nada del legacy la importa a ella.
 * <p>
 * SIN estado por jugador en esta fase (a diferencia de {@code AuraRimRenderer}: sin ramp de
 * fade, sin mapas) — a propósito, para no tener que añadir ninguna llamada de limpieza a
 * {@code AuraRenderer.onStopTracking} (que sí sería tocar un archivo legacy). Aparece/desaparece
 * de golpe con el toggle de debug; una ramp de entrada/salida igual de suave que la del rim es
 * trabajo de una fase posterior, con su propio mecanismo de limpieza independiente si hace
 * falta.
 * <p>
 * SIN gating de LOD por distancia ni lectura de {@code AuraProfile} todavía (eso es la Fase 6
 * del documento): número de picos, escala y velocidad quedan fijos a mano en esta fase.
 * Únicamente el COLOR sale ya resuelto de {@link AuraColors} (núcleo/envolvente) para que la
 * primera prueba visual se acerque más a las imágenes de referencia de dbrebirth.
 * <p>
 * TOGGLE DE PRUEBA: {@link #DEBUG_ENABLED}, puesto por {@link AuraFlameDebugCommand}
 * ({@code /zenkaiauraflame true|false}, comando de CLIENTE — no pasa por red ni por
 * {@code ModCommands}). Se sustituye por el enum real de {@code ClientConfig}
 * (AUTO/FORCE_LEGACY/FORCE_NEW) en la Fase 5 del documento; hasta entonces el sistema legacy
 * sigue dibujándose también — no hay despacho excluyente todavía.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraFlameRenderer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Toggle de prueba de la Fase 1, ver {@link AuraFlameDebugCommand}. */
    public static boolean DEBUG_ENABLED = false;

    /** Alfa base de la malla, antes de fpOpacity — mismo rol que AuraRimRenderer.ALPHA. */
    private static final float ALPHA = 0.85f;

    /** Inflado base CONSTANTE (independiente del desplazamiento del shader), para que los
     *  valles de la onda de picos (wave=0) no queden exactamente coplanares con el cuerpo real
     *  y parpadeen (z-fighting) — mismo problema que ya resolvió AuraRimRenderer.INFLATE_XZ/_Y;
     *  constante propia, algo mayor porque esta malla se ve mucho más grande en conjunto. */
    private static final float BASE_INFLATE_XZ = 0.10f;
    private static final float BASE_INFLATE_Y  = 0.06f;

    /** Nº de picos angulares alrededor del eje Y (ZenkaiFlameSpikeCount). Fijo a mano en esta
     *  fase; personalidad por forma (AuraProfile.spike()) llega en la Fase 6 del documento. */
    private static final float SPIKE_COUNT = 7f;

    /** Bloques de desplazamiento en el pico más alto — dos órdenes de magnitud mayor que los
     *  0.12 de aura_rim (ver el comentario de escala en aura_flame.vsh). Punto de partida para
     *  la calibración de la Fase 2, no un valor final. */
    private static final float SCALE = 2.0f;

    /** Altura LOCAL (0 = pies, ~1.8 = cabeza) donde el efecto empieza / llega a fuerza plena —
     *  coordenadas de verdad gracias a la corrección de origen (ver aura_flame.vsh), no el
     *  espacio cámara-relativa que causó la "cortina" de la primera prueba en juego de esta
     *  fase. Sin caída de vuelta por encima de FALLOFF_FULL (a diferencia de aura_rim, que sí
     *  baja para no deformar la cabeza): aquí la cabeza es precisamente de donde deben salir los
     *  picos más altos. */
    private static final float FALLOFF_START = 0.3f;
    private static final float FALLOFF_FULL  = 1.5f;

    public AuraFlameRenderer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffer, int light,
                       @NotNull AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        if (!DEBUG_ENABLED) return;
        if (!AuraFlameRenderType.available()) return;
        if (!AuraClientState.isAuraActive(player)) return;

        // Mismo criterio que AuraRenderer/AuraRimRenderer: en primera persona propia, la
        // opacidad del aura es configurable y puede estar a 0.
        Minecraft mc = Minecraft.getInstance();
        boolean selfFirstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
        float fpOpacity = selfFirstPerson ? ClientConfig.auraFirstPersonOpacityFrac() : 1f;
        if (selfFirstPerson && fpOpacity <= 0f) return;

        int color = FastColor.ARGB32.color(Math.round(255f * ALPHA * fpOpacity), 255, 255, 255);

        AuraColors.Layers layers = AuraColors.resolveLayers(player);
        int coreRgb = layers.inner();
        int outerRgb = layers.hasOuter() ? layers.outer() : layers.inner();

        PlayerModel<AbstractClientPlayer> model = getParentModel();

        // La malla necesita las partes VISIBLES aunque el cuerpo vainilla esté oculto por una
        // piel racial (RaceSkinHideBasePlayerHooks.onRenderPlayerPre ya corrió antes de que
        // cualquier layer llegue a renderizar) — mismo truco que AuraRimRenderer.
        boolean wasVisible = model.head.visible;
        if (!wasVisible) model.setAllVisible(true);

        // Misma convención que LivingEntityRenderer.render() (180 - yaw de cuerpo interpolado);
        // se sube la rotación OPUESTA para que aura_flame.vsh pueda deshacerla y medir el ángulo
        // de los picos en espacio de modelo, estable frente al giro real.
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        Matrix4f invBodyRot = new Matrix4f().rotationY((float) Math.toRadians(bodyYaw - 180.0F));

        // Mismo vector que el motor ya usó para trasladar el PoseStack a espacio cámara-relativo
        // (EntityRenderDispatcher.render: entityPos(partialTick) - cameraPos) — se lo pasamos al
        // shader para que pueda restarlo y recuperar coordenadas locales de verdad. Ver el
        // comentario de cabecera de aura_flame.vsh para el porqué hace falta esto.
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 entityPos = player.getPosition(partialTick);
        Vector3f origin = new Vector3f(
                (float) (entityPos.x - camPos.x),
                (float) (entityPos.y - camPos.y),
                (float) (entityPos.z - camPos.z));

        AuraFlameRenderType.setupAuraFlame(1f, SPIKE_COUNT, SCALE, FALLOFF_START, FALLOFF_FULL,
                invBodyRot, origin, coreRgb, outerRgb);

        RenderType type = AuraFlameRenderType.auraFlame(player.getSkin().texture());
        VertexConsumer vc = buffer.getBuffer(type);

        pose.pushPose();
        // Escala centrada en el TORSO, no en los pies (el origen del PoseStack aquí) — mismo
        // motivo que AuraRimRenderer: escalar desde el origen subiría la cabeza mucho más de lo
        // que los pies bajan. bbHeight/2 es una aproximación barata y suficiente del centro real.
        float half = player.getBbHeight() * 0.5f;
        pose.translate(0f, half, 0f);
        pose.scale(1f + BASE_INFLATE_XZ, 1f + BASE_INFLATE_Y, 1f + BASE_INFLATE_XZ);
        pose.translate(0f, -half, 0f);
        model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, color);
        pose.popPose();

        // NO se hace buffer.endBatch(type) aquí — mismo motivo documentado en
        // AuraRimRenderer.render(): esta capa corre a mitad del render de entidades, con más
        // jugadores todavía por dibujar en el mismo frame; volcar el lote aquí arriesga el mismo
        // "IllegalStateException: Not building!" ya diagnosticado ahí.

        if (!wasVisible) model.setAllVisible(false);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            renderer.addLayer(new AuraFlameRenderer(renderer));
        }
    }
}
