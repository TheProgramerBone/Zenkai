package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.aura.AuraLod;
import com.hmc.zenkai.feature.aura.AuraManager;
import com.hmc.zenkai.feature.aura.AuraProfile;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * PROTOTIPO (2026-09-02, ver el boceto "Rim Glow Aura" — cáscara inflada + depth-test,
 * comparado en vivo contra el barrido a blanco del halo aditivo anterior; retunado tras
 * la primera pasada en juego, ver .claude/pendiente/aura-rim-glow-prototype.md). Aro de
 * borde alrededor del jugador: NO es el faldón de llama de siempre
 * ({@link AuraSkirtRenderer}) — es una copia INFLADA del propio modelo del jugador,
 * dibujada aditiva y detrás de él. El pecho y las piernas nunca se ven afectados: el
 * depth-test recorta la cáscara al aro de la silueta por construcción (ver
 * {@link ModAuraRenderType#energyRim} para el porqué exacto del cull FRONT), no porque
 * el alfa esté ajustado con cuidado.
 *
 * COMBINABLE, NO SUSTITUTO. Es una capa MÁS registrada junto a las demás del jugador;
 * no toca {@link AuraRenderer} ni el faldón existente. Las dos pueden convivir en el
 * mismo jugador.
 *
 * OFFSET NO UNIFORME A PROPÓSITO (ver §1 del feedback en juego): escalar el modelo por
 * igual en los tres ejes desde un pivote central hace que la cabeza —más lejos del
 * pivote que los brazos— se separe mucho más en pantalla que los lados, porque estaba
 * más lejos del pivote para empezar. {@link #INFLATE_Y} va deliberadamente MÁS BAJO que
 * {@link #INFLATE_XZ} para compensar, no porque el aro deba ser ovalado por diseño.
 *
 * APARICIÓN/DESVANECIDO PROGRESIVOS (ver §2-3 del feedback): un ramp 0..1 por jugador,
 * en tiempo real (no ticks — {@code render()} corre por FRAME, no por tick), que sube
 * mientras el aura está activa y baja mucho más despacio cuando deja de estarlo — "se
 * apaga solo", no "se corta". {@link #RAMP}/{@link #LAST_NANOS} viven aquí y no en
 * ningún sitio compartido porque nada más necesita esta cadencia.
 *
 * GATING: {@code FormDef.divineTier()}, NO aura_type. La primera versión comparaba
 * {@code auraType().equals("divine")} — parecía razonable ("divino" = "divine") pero es
 * la clave EQUIVOCADA: "divine" es solo el nombre de una FIRMA VISUAL
 * (turbulencia/pulso, ver zenkai_aura_signatures/divine.json) y SSJ2 la compartía sin
 * ser una forma de God Ki — el rim salía en SSJ2 aunque el jugador nunca hubiera tocado
 * la skill god_ki. {@code divineTier} es el flag MECÁNICO real ("¿esta forma se
 * desbloquea con la skill god_ki?", ver el javadoc de ese campo en FormDef) e incluye
 * exactamente human_god/namek_god/majin_god/ssj_god/ssj_blue/ssj_rose — sin importar
 * que ssj_god/ssj_rose usen sus propios aura_type ("ascension"/"rose") en vez de
 * "divine". SSJ2 se separó a su propio aura_type ("ssj2", antes compartía "divine" solo
 * por casualidad de nombre) para que la colisión no vuelva a confundir a nada que en el
 * futuro sí quiera leer aura_type con otro propósito.
 *
 * LIMITACIÓN CONOCIDA DEL PROTOTIPO: infla el {@code PlayerModel} vainilla (huesos y
 * proporciones reales — siempre posados cada frame aunque el cuerpo base esté oculto
 * por una piel racial, ver más abajo), no la malla GeckoLib real de la raza activa. Los
 * modelos raciales comparten los mismos pivotes de hueso que el rig vainilla (ver
 * CLAUDE.md, nota de animación de jugador), así que el aro sigue razonablemente bien la
 * silueta real — pero no está pensado para sobrevivir a cascos anchos o peinados muy
 * grandes que se salgan mucho de la proporción vainilla. Aceptable para juzgar el
 * mecanismo; no es el arte final.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraRimRenderer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Cuánto crece la cáscara a los LADOS (X/Z) respecto al cuerpo real. */
    private static final float INFLATE_XZ = 0.055f;
    /** Cuánto crece HACIA ARRIBA/ABAJO (Y). Menor que INFLATE_XZ a propósito — ver el
     *  javadoc de la clase, "OFFSET NO UNIFORME A PROPÓSITO". Sube este número para un
     *  aro más "alto/llameante", bájalo (o iguálalo a INFLATE_XZ) para uno más esférico. */
    private static final float INFLATE_Y = 0.028f;

    /** Alfa base del aro a ramp completo (1.0), antes de fpOpacity. */
    private static final float ALPHA = 0.75f;

    /** Nº de picos angulares alrededor del eje Y cuando el shader de aura_rim está activo (ver
     *  ZenkaiSpikeCount en aura_rim.json/.vsh). Mismo valor que el default del uniform. */
    private static final float SPIKE_COUNT = 7f;

    /** Segundos para aparecer del todo una vez el aura se activa. Subido de 0.6 a 1.4
     *  tras feedback en juego ("aparece de golpe") — el aditivo agrava la sensación de
     *  golpe: con alfa bajo el brillo es casi imperceptible contra el fondo, así que
     *  cruza el umbral en el que SÍ se nota en una fracción muy pequeña de la duración
     *  total. Alargar el tiempo total es lo que compensa eso, no cambiar la curva. */
    private static final float FADE_IN_SECONDS = 1.4f;
    /** Segundos para desvanecerse del todo una vez deja de estar activa. Más lento que
     *  la entrada a propósito: "se apaga solo", no "se corta". Subido de 2.2 a 3.5 por
     *  el mismo motivo que FADE_IN_SECONDS. */
    private static final float FADE_OUT_SECONDS = 3.5f;

    /** Ramp 0..1 por jugador; 1 = aro a fuerza completa. */
    private static final Map<Integer, Float> RAMP = new HashMap<>();
    /** Última marca de tiempo real (nanoTime) por jugador, para el delta del ramp. */
    private static final Map<Integer, Long> LAST_NANOS = new HashMap<>();

    public AuraRimRenderer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Limpieza al dejar de trackear a un jugador, llamada desde AuraRenderer.onStopTracking
     *  (mismo patrón que el resto de emisores del aura). */
    public static void clear(int playerId) {
        RAMP.remove(playerId);
        LAST_NANOS.remove(playerId);
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffer, int light,
                       @NotNull AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        boolean active = AuraClientState.isAuraActive(player) && isDivineTier(player);

        // Ramp en tiempo REAL (nanoTime), no en ticks: render() corre una vez por FRAME,
        // no por tick, así que derivarlo de gameTime infra-actualizaría a FPS altos y
        // sobre-actualizaría a FPS bajos si varios jugadores comparten un reloj global.
        int id = player.getId();
        long now = System.nanoTime();
        Long lastNanos = LAST_NANOS.put(id, now);
        // Tope de 0.25s: evita un salto enorme del ramp tras un lag spike o un alt-tab.
        float dt = lastNanos == null ? 0f : Math.min(0.25f, (now - lastNanos) / 1_000_000_000f);

        float ramp = RAMP.getOrDefault(id, 0f);
        ramp += active ? dt / FADE_IN_SECONDS : -dt / FADE_OUT_SECONDS;
        ramp = Math.max(0f, Math.min(1f, ramp));
        RAMP.put(id, ramp);

        if (ramp <= 0.002f) return; // ya invisible: nada que preparar ni dibujar

        // Mismo criterio que AuraRenderer: en primera persona propia, la opacidad del aura
        // es configurable y puede estar a 0.
        Minecraft mc = Minecraft.getInstance();
        boolean selfFirstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
        float fpOpacity = selfFirstPerson ? ClientConfig.auraFirstPersonOpacityFrac() : 1f;
        if (selfFirstPerson && fpOpacity <= 0f) return;

        // Smoothstep en vez de ramp lineal: la entrada/salida se siente orgánica.
        float eased = ramp * ramp * (3f - 2f * ramp);

        int rgb = AuraClientState.resolveColor(player);
        int color = FastColor.ARGB32.color(
                Math.round(255f * ALPHA * eased * fpOpacity),
                (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);

        PlayerModel<AbstractClientPlayer> model = getParentModel();

        // La cáscara necesita las partes VISIBLES aunque el cuerpo vainilla esté oculto.
        // RaceSkinHideBasePlayerHooks.onRenderPlayerPre ya corrió (dispara ANTES de que
        // cualquier layer llegue a renderizar) y apagó model.setAllVisible(false) para
        // cualquier raza con modelo GeckoLib propio — sin revertirlo aquí, renderToBuffer
        // no dibujaría nada. head.visible representa el estado uniforme que puso
        // setAllVisible (es el único sitio que lo toca); se restaura tal cual al salir.
        boolean wasVisible = model.head.visible;
        if (!wasVisible) model.setAllVisible(true);

        // Picos angulares (aura_rim.vsh): solo en las dos bandas cercanas — el shader no sabe
        // nada de LOD, la decisión de gastarlo o no la toma Java, mismo criterio que ya usa
        // AuraSkirts.plan para el resto del aura (ver AuraLod). A distancia, o si el shader no
        // compiló, energyRimSpiked() ya cae a energyRim() por su cuenta (ver su javadoc).
        double distSq = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(player.getPosition(partialTick));
        AuraLod lod = AuraLod.byDistanceSq(distSq);
        boolean wantSpikes = (lod == AuraLod.NEAR || lod == AuraLod.MID);

        RenderType type = wantSpikes
                ? ModAuraRenderType.energyRimSpiked(player.getSkin().texture())
                : ModAuraRenderType.energyRim(player.getSkin().texture());
        boolean usingSpikeShader = wantSpikes && ModAuraRenderType.auraRimSpikeShaderAvailable();

        if (usingSpikeShader) {
            // AuraProfile.spike() ya existe de punta a punta (datapack de aura_type -> aquí),
            // pero hasta ahora ningún renderer lo leía — este es el primer consumidor real.
            float turbo = AuraClientState.isTurbo(player) ? 1f : 0f;
            AuraProfile profile = AuraManager.profileOf(player, turbo);
            // Misma convención que LivingEntityRenderer.render() (180 - yaw de cuerpo
            // interpolado); se sube la rotación OPUESTA para que aura_rim.vsh pueda deshacerla
            // y medir el ángulo de los picos en espacio de modelo, estable frente al giro real.
            float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            Matrix4f invBodyRot = new Matrix4f().rotationY((float) Math.toRadians(bodyYaw - 180.0F));
            ModAuraRenderType.setupAuraRim(profile.spike(), SPIKE_COUNT, invBodyRot);
        }

        VertexConsumer vc = buffer.getBuffer(type);

        pose.pushPose();
        // Escala centrada en el TORSO, no en los pies (el origen del PoseStack aquí): si
        // se escalara desde el origen la cabeza subiría mucho más de lo que los pies
        // bajan, y el aro quedaría más grueso arriba que abajo. bbHeight/2 es una
        // aproximación barata y suficiente del centro real del cuerpo.
        float half = player.getBbHeight() * 0.5f;
        pose.translate(0f, half, 0f);
        pose.scale(1f + INFLATE_XZ, 1f + INFLATE_Y, 1f + INFLATE_XZ);
        pose.translate(0f, -half, 0f);
        model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, color);
        pose.popPose();

        // El shader de picos es una instancia compartida: hay que volcar su lote AQUÍ, antes de
        // que otra técnica (ki, u otro jugador con rim) vuelva a pisar sus uniforms — mismo
        // motivo que KiBodyRenderer.renderShaded hace buffer.endBatch(type) tras emitir.
        if (usingSpikeShader && buffer instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(type);
        }

        if (!wasVisible) model.setAllVisible(false);
    }

    /** Ver "GATING" en el javadoc de la clase: divineTier, no aura_type. */
    private static boolean isDivineTier(AbstractClientPlayer player) {
        FormDef def = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).activeDef();
        return def != null && def.divineTier();
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            renderer.addLayer(new AuraRimRenderer(renderer));
        }
    }
}
