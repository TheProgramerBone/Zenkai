package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import com.hmc.zenkai.feature.player.death.BodyGear;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Pinta el cuerpo de un jugador muerto: su skin de Minecraft (SIEMPRE la skin vanilla de la cuenta, aunque el
 * personaje tenga modelo propio de raza), tumbado boca arriba y al tamaño del jugador (vanilla escala el modelo
 * del jugador a 0,9375). Sin capas: ni pelo, ni armadura, ni cola.
 *
 * SKIN POR ENTIDAD: cada cuerpo resuelve la suya con el UUID y la propiedad {@code textures} que guardó al morir,
 * y la caché de {@code SkinManager} es por perfil, así que muchos cuerpos de jugadores distintos conviven sin
 * pisarse. Si el cuerpo no trae esa propiedad (servidor offline) se usa la skin del dueño si está en línea, y si no
 * la de por defecto de su UUID.
 *
 * El modelo se dibuja en su pose de reposo (brazos pegados al cuerpo): no se llama a {@code setupAnim}, que exige
 * un LivingEntity. Tumbarlo es un giro de -90º sobre X: la cabeza va hacia -Z, la cara hacia arriba.
 */
public class PlayerBodyRenderer extends EntityRenderer<PlayerBodyEntity> {

    /** Escala con la que vanilla pinta al jugador (PlayerRenderer.scale). */
    public static final float PLAYER_SCALE = 0.9375f;

    /** Modelos del cuerpo (ancho y delgado) y de la armadura (capa interior para las grebas, exterior para el resto). */
    public record Models(PlayerModel<LivingEntity> wide, PlayerModel<LivingEntity> slim,
                         HumanoidModel<LivingEntity> inner, HumanoidModel<LivingEntity> outer) {
        public static Models bake(EntityModelSet set) {
            return new Models(new PlayerModel<>(set.bakeLayer(ModelLayers.PLAYER), false),
                    new PlayerModel<>(set.bakeLayer(ModelLayers.PLAYER_SLIM), true),
                    new HumanoidModel<>(set.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidModel<>(set.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)));
        }
    }

    private final Models models;

    public PlayerBodyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.models = Models.bake(ctx.getModelSet());
        this.shadowRadius = 0.4f;
    }

    @Override
    public void render(PlayerBodyEntity body, float yaw, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - body.getYRot()));
        ps.scale(body.scale(), body.scale(), body.scale());   // la escala (raza) del dueño
        ps.translate(0.0, 0.0, 0.84);   // centrado: el modelo tumbado nace con los pies en el origen y la cabeza a -1,7 en Z
        drawLying(ps, buffers, light, skinOf(body.owner().orElse(null), body.ownerName(), body.skinTextures()), models, body.gear(), body);
        ps.popPose();
        super.render(body, yaw, partialTick, ps, buffers, light);
    }

    /**
     * Dibuja un modelo de jugador tumbado boca arriba con los pies en el origen, apoyado en el suelo.
     * Compartido con la capa del jugador que lleva un cuerpo en brazos.
     */
    public static void drawLying(PoseStack ps, MultiBufferSource buffers, int light, PlayerSkin skin,
                                 Models models, BodyGear gear, Entity wearer) {
        PlayerModel<LivingEntity> model = skin.model() == PlayerSkin.Model.SLIM ? models.slim() : models.wide();
        // `young` vale TRUE por defecto y solo setupAnim lo pone según la entidad: como aquí no se llama, el modelo salía como un
        // BEBÉ (cabeza x2, cuerpo x0,5). Medido con BodyShots y bloques de oro de 1 m: cada medida era justo la mitad de lo esperado.
        model.young = false;
        ps.pushPose();
        ps.translate(0.0, 0.2, 0.0);                       // medio grosor del cuerpo: tumbado, apoyado en el suelo
        // BOCA ARRIBA con la cabeza hacia -Z: giro de +90º sobre X (cara hacia arriba, cabeza a +Z) y media vuelta sobre Y.
        // Con -90º sobre X la cara quedaba hacia el suelo.
        ps.mulPose(Axis.YP.rotationDegrees(180.0f));
        ps.mulPose(Axis.XP.rotationDegrees(90.0f));
        ps.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
        ps.scale(-1.0f, -1.0f, 1.0f);                      // convención del modelo del jugador (ver LivingEntityRenderer)
        ps.translate(0.0, -1.501, 0.0);
        model.renderToBuffer(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(skin.texture())),
                light, OverlayTexture.NO_OVERLAY);
        if (gear != null && !gear.isEmpty()) BodyGearRenderer.render(ps, buffers, light, gear, model, models, wearer);
        ps.popPose();
    }

    public static PlayerSkin skinOf(UUID id, String name, String textures) {
        if (id == null) return new PlayerSkin(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"),
                null, null, null, PlayerSkin.Model.WIDE, true);
        var mc = Minecraft.getInstance();
        if (!textures.isEmpty()) {
            GameProfile profile = new GameProfile(id, name);
            profile.getProperties().put("textures", new Property("textures", textures));
            return mc.getSkinManager().getInsecureSkin(profile);
        }
        if (mc.level != null && mc.level.getPlayerByUUID(id) instanceof AbstractClientPlayer online) return online.getSkin();
        return mc.getSkinManager().getInsecureSkin(new GameProfile(id, name));
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerBodyEntity body) {
        return skinOf(body.owner().orElse(null), body.ownerName(), body.skinTextures()).texture();
    }
}
