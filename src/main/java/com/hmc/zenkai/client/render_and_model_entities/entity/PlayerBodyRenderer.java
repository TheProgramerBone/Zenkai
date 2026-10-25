package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Pinta el cuerpo de un jugador muerto: su skin, tumbado boca arriba. Versión 1: modelo vainilla del
 * jugador sin capas (ni pelo ni armadura ni cola de la raza). El modelo de la raza con sus capas GeckoLib
 * es una segunda pasada: esas capas piden un {@code Player} real y hay que investigar cómo pintarlas sin él.
 *
 * El modelo se dibuja en su pose de reposo (brazos pegados al cuerpo): no se llama a {@code setupAnim},
 * que exige un LivingEntity. Tumbarlo es un giro de -90º sobre X: la cabeza va hacia -Z, la cara hacia arriba.
 */
public class PlayerBodyRenderer extends EntityRenderer<PlayerBodyEntity> {

    private final PlayerModel<LivingEntity> wide;
    private final PlayerModel<LivingEntity> slim;

    public PlayerBodyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0.4f;
    }

    @Override
    public void render(PlayerBodyEntity body, float yaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int light) {
        PlayerSkin skin = skinOf(body);
        PlayerModel<LivingEntity> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - body.getYRot()));
        ps.translate(0.0, 0.2, 0.0);                       // medio grosor del cuerpo: tumbado, apoyado en el suelo
        ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
        ps.scale(-1.0f, -1.0f, 1.0f);                      // convención del modelo del jugador (ver LivingEntityRenderer)
        ps.translate(0.0, -1.501, 0.0);
        model.renderToBuffer(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture())),
                light, OverlayTexture.NO_OVERLAY);
        ps.popPose();

        super.render(body, yaw, partialTick, ps, buffers, light);
    }

    private static PlayerSkin skinOf(PlayerBodyEntity body) {
        var id = body.owner().orElse(null);
        if (id == null) return new PlayerSkin(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"),
                null, null, null, PlayerSkin.Model.WIDE, true);
        // Si el dueño está conectado se usa su skin real; si no, la del perfil por UUID/nombre (con la caché de vanilla).
        AbstractClientPlayer online = null;
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getPlayerByUUID(id) instanceof AbstractClientPlayer acp) online = acp;
        if (online != null) return online.getSkin();
        return mc.getSkinManager().getInsecureSkin(new GameProfile(id, body.ownerName()));
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerBodyEntity body) {
        return skinOf(body).texture();
    }
}
