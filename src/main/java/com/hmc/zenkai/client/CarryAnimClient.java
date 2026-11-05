package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.render_and_model_entities.entity.PlayerBodyRenderer;
import com.hmc.zenkai.event.ZenkaiPalLayers;
import com.hmc.zenkai.feature.player.death.CarriedBody;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.math.Axis;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Cliente de «llevar un cuerpo en brazos»:
 *  - la POSE de brazos es un clip PAL ({@code zenkai.carry_body}) en la capa {@link ZenkaiPalLayers#CARRY_LAYER}, la de
 *    mayor prioridad, que se enciende y se apaga mirando el adjunto {@code CARRIED_BODY} de CADA jugador visible
 *    (llega sincronizado a todos), no solo del local;
 *  - en PRIMERA PERSONA la capa de render del jugador no se dibuja (PAL deja pasar muy pocas capas en esa pasada),
 *    así que el cuerpo se pinta aquí, tumbado delante de la cámara, para que se note que se lleva.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class CarryAnimClient {
    private CarryAnimClient() {}

    private static final ResourceLocation CLIP = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "zenkai.carry_body");

    private static PlayerBodyRenderer.Models models;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            var c = (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(p, ZenkaiPalLayers.CARRY_LAYER);
            if (c == null) continue;
            boolean carrying = !p.getData(ZenkaiDataAttachments.CARRIED_BODY.get()).isNone();
            boolean playing = CLIP.equals(ZenkaiTransitions.currentClip(c));
            if (carrying && !playing) ZenkaiTransitions.play(c, CLIP, ZenkaiTransitions.COMBAT);
            else if (!carrying && playing) ZenkaiTransitions.stop(c, ZenkaiTransitions.COMBAT);
        }
    }

    /**
     * Cuerpo tumbado delante de la cámara en 1ª PERSONA. Se dibuja como una entidad más (etapa AFTER_ENTITIES, coordenadas
     * relativas a la cámara) y NO en la pasada de mano: ahí un RenderHandEvent no llegaba (PAL se encarga de los brazos en esa
     * pasada) y con shaders la mano se pierde antes del compuesto. La colocación usa el marco del CUERPO del jugador (como la 3ª persona).
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) return;
        CarriedBody c = mc.player.getData(ZenkaiDataAttachments.CARRIED_BODY.get());
        if (c.isNone()) return;
        if (models == null) models = PlayerBodyRenderer.Models.bake(mc.getEntityModels());

        var cam = e.getCamera();
        var pl = mc.player;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        var ps = e.getPoseStack();
        ps.pushPose();
        // MISMO marco que la capa de 3ª persona (CarriedBodyLayer): pies del jugador, girado con su CUERPO. Los brazos de PAL
        // cuelgan del cuerpo, que gira más despacio que la cabeza; anclarlo a la cámara los desajustaba al girar o cabecear.
        ps.translate(Mth.lerp(pt, pl.xOld, pl.getX()) - cam.getPosition().x,
                Mth.lerp(pt, pl.yOld, pl.getY()) - cam.getPosition().y,
                Mth.lerp(pt, pl.zOld, pl.getZ()) - cam.getPosition().z);
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - Mth.rotLerp(pt, pl.yBodyRotO, pl.yBodyRot)));
        float body = PlayerBodyRenderer.PLAYER_SCALE * pl.getScale();
        ps.scale(body, body, body);
        ps.translate(0.0, 1.05, -0.78);                                          // a la altura de las manos
        ps.mulPose(Axis.YP.rotationDegrees(90.0f));
        float ratio = c.scale() / Math.max(0.1f, pl.getScale());
        ps.scale(ratio, ratio, ratio);
        ps.translate(0.0, 0.0, 0.84);
        var buffers = mc.renderBuffers().bufferSource();
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(cam.getPosition()));
        PlayerBodyRenderer.drawLying(ps, buffers, light,
                PlayerBodyRenderer.skinOf(c.owner(), c.ownerName(), c.skin()), models, c.gear(), mc.player);
        buffers.endBatch();
        ps.popPose();
    }
}
