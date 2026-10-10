package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Imagen residual del Paso rápido: siluetas translúcidas del jugador, tintadas con el color de su
 * aura, que se quedan donde estaba mientras él ya se ha ido y se desvanecen. Es el efecto
 * clásico de movimiento a alta velocidad de Dragon Ball.
 *
 * SOLO CLIENTE y sin entidades: cada imagen es un registro (posición, giro del cuerpo, pose
 * congelada, color, edad) que se dibuja en RenderLevelStageEvent con el MODELO VANILLA del
 * jugador. A propósito NO se copia el render completo (capas GeckoLib de raza, pelo, cara): es
 * una silueta de un solo color, así que la forma vanilla basta y no hay que reconstruir un
 * pipeline entero con alfa. La pose viene de la última pasada real de ese jugador
 * (RenderPlayerEvent.Post guarda las rotaciones de cada parte ya animadas por PAL), así la
 * imagen congela la postura de salida en vez de mostrar una genérica.
 *
 * Se lanza desde DashAnimPacket (el servidor decide que fue un Paso rápido, así que todos los
 * que ven al jugador ven la imagen). Se dejan tres: la de salida y dos ecos en los siguientes
 * ticks, que dibujan una estela corta del recorrido.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AfterImageClient {
    private AfterImageClient() {}

    /** Vida de cada imagen, en ticks. */
    private static final int LIFE = 14;
    /** Ticks tras el inicio en los que se deja un eco más. */
    private static final int[] ECHO_DELAYS = {2, 4};
    private static final float START_ALPHA = 0.55f;
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** Rotaciones de cada parte del modelo en la última pasada real del jugador. */
    private record Pose(PartPose head, PartPose body, PartPose rightArm, PartPose leftArm,
                        PartPose rightLeg, PartPose leftLeg) {}

    private record Ghost(UUID owner, Vec3 pos, float bodyYaw, Pose pose, int rgb, long startTick) {}
    private record PendingEcho(UUID owner, long atTick) {}

    private static final Map<UUID, Pose> LAST_POSE = new HashMap<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<PendingEcho> ECHOES = new ArrayList<>();

    /** Guarda la pose animada del jugador justo después de dibujarlo. */
    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post e) {
        // El retrato del HUD moderno dibuja al jugador local cada frame con la cabeza girada
        // hacia las barras: esa no es su pose en el mundo y no debe congelarse en la estela.
        if (com.hmc.zenkai.client.overlay.hud.ModernStatsHud.renderingPortrait) return;
        PlayerModel<AbstractClientPlayer> m = e.getRenderer().getModel();
        LAST_POSE.put(e.getEntity().getUUID(), new Pose(m.head.storePose(), m.body.storePose(),
                m.rightArm.storePose(), m.leftArm.storePose(),
                m.rightLeg.storePose(), m.leftLeg.storePose()));
    }

    /**
     * ¿Está esfumada esta entidad? El Paso rápido es invisibilidad vainilla sin partículas, así que
     * basta isInvisible() y vale igual para la poción. Lo consulta GeoLayerArmorRenderer para no dibujar el arte GeckoLib del mod
     * (pelo, cola, halo, scouter...) sobre un cuerpo que no se ve. La armadura vainilla no pasa por
     * ahí y se queda, como en el juego base.
     */
    public static boolean isVanished(net.minecraft.world.entity.Entity e) {
        return e instanceof AbstractClientPlayer p && p.isInvisible();
    }

    /** Lo llama DashAnimPacket cuando el servidor confirma un Paso rápido. */
    public static void spawn(AbstractClientPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        addGhost(p, now);
        for (int d : ECHO_DELAYS) ECHOES.add(new PendingEcho(p.getUUID(), now + d));
    }

    private static void addGhost(AbstractClientPlayer p, long now) {
        Pose pose = LAST_POSE.get(p.getUUID());
        if (pose == null) return; // aún no se ha dibujado nunca: sin pose no hay silueta
        GHOSTS.add(new Ghost(p.getUUID(), p.position(), p.yBodyRot, pose, AuraColors.resolve(p), now));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            GHOSTS.clear();
            ECHOES.clear();
            LAST_POSE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        GHOSTS.removeIf(g -> now - g.startTick() >= LIFE);

        for (Iterator<PendingEcho> it = ECHOES.iterator(); it.hasNext(); ) {
            PendingEcho pe = it.next();
            if (now < pe.atTick()) continue;
            it.remove();
            if (mc.level.getPlayerByUUID(pe.owner()) instanceof AbstractClientPlayer p) addGhost(p, now);
        }
    }

    /**
     * Pelo, cola y halo en la silueta: se vuelven a llamar las capas reales del jugador con la
     * pasada fantasma activa (GeoLayerArmorRenderer.ghostArgb), que las pinta planas, translúcidas
     * y del color de la imagen. Van en el mismo espacio de pose que el cuerpo (tras la transformación
     * base) y copian la postura del modelo vanilla, que acaba de recibir la pose congelada. Si algo
     * falla, la silueta sale sin adornos en vez de romper el render.
     */
    private static void renderGhostAttachments(net.minecraft.client.renderer.entity.player.PlayerRenderer pr,
                                               Ghost g, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                               int argb, float pt, Minecraft mc) {
        if (!(mc.level.getPlayerByUUID(g.owner()) instanceof AbstractClientPlayer owner)) return;
        var layers = com.hmc.zenkai.feature.race.RaceSkinRenderHooks.ghostLayers(pr);
        if (layers.isEmpty()) return;
        com.hmc.zenkai.feature.race.layer.GeoLayerArmorRenderer.ghostArgb = argb;
        try {
            for (var layer : layers) {
                layer.render(pose, buffers, LightTexture.FULL_BRIGHT, owner, 0f, 0f, pt,
                        owner.tickCount + pt, 0f, 0f);
            }
        } catch (RuntimeException ex) {
            // Silueta sin adornos antes que un crash de render en cada Paso rápido.
        } finally {
            com.hmc.zenkai.feature.race.layer.GeoLayerArmorRenderer.ghostArgb = 0;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || GHOSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(mc.player)
                instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr)) return;

        PlayerModel<AbstractClientPlayer> model = pr.getModel();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        long now = mc.level.getGameTime();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType rt = RenderType.entityTranslucentEmissive(WHITE);
        VertexConsumer vc = buffers.getBuffer(rt);

        // Visibilidad: la capa de raza oculta el modelo base durante el dibujado normal; se fuerza
        // visible solo para la silueta y se deja como estaba (la siguiente pasada real lo reajusta).
        model.setAllVisible(false);
        model.head.visible = true;
        model.body.visible = true;
        model.rightArm.visible = true;
        model.leftArm.visible = true;
        model.rightLeg.visible = true;
        model.leftLeg.visible = true;

        // En PRIMERA persona tus propias imágenes residuales no se dibujan (quedaban pegadas a la
        // cámara y tapaban la vista); solo se ven en tercera persona. Las de otros jugadores sí.
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Ghost g : GHOSTS) {
            if (firstPerson && g.owner().equals(mc.player.getUUID())) continue;
            float age = (now - g.startTick()) + pt;
            float fade = 1f - Math.min(1f, age / LIFE);
            int a = (int) (START_ALPHA * fade * fade * 255f); // cae rápido al principio
            if (a <= 2) continue;
            int argb = (a << 24) | (g.rgb() & 0xFFFFFF);

            model.head.loadPose(g.pose().head());
            model.body.loadPose(g.pose().body());
            model.rightArm.loadPose(g.pose().rightArm());
            model.leftArm.loadPose(g.pose().leftArm());
            model.rightLeg.loadPose(g.pose().rightLeg());
            model.leftLeg.loadPose(g.pose().leftLeg());

            pose.pushPose();
            pose.translate(g.pos().x - cam.x, g.pos().y - cam.y, g.pos().z - cam.z);
            // Misma transformación base que LivingEntityRenderer + PlayerRenderer.scale.
            pose.mulPose(Axis.YP.rotationDegrees(180f - g.bodyYaw()));
            pose.scale(-1f, -1f, 1f);
            pose.scale(0.9375f, 0.9375f, 0.9375f);
            pose.translate(0f, -1.501f, 0f);
            model.renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
            renderGhostAttachments(pr, g, pose, buffers, argb, pt, mc);
            pose.popPose();
        }
        model.setAllVisible(true);
        buffers.endBatch(rt);
    }
}
