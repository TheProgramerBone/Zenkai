package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.death.CarriedBody;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Pinta el cuerpo que un jugador lleva en brazos (estilo CarryOn), con la skin de SU dueño, tumbado de lado delante del
 * pecho. Los demás clientes reciben solo dueño y nombre (ver {@link CarriedBody#SYNC}); la skin sale de la caché del
 * cliente por UUID/nombre, así que un dueño desconectado se ve con la skin que ya tenga cargada o la de por defecto.
 */
public class CarriedBodyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final PlayerBodyRenderer.Models models;

    public CarriedBodyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                            PlayerBodyRenderer.Models models) {
        super(parent);
        this.models = models;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        CarriedBody c = player.getData(ZenkaiDataAttachments.CARRIED_BODY.get());
        if (c.isNone()) return;
        PlayerSkin skin = PlayerBodyRenderer.skinOf(c.owner(), c.ownerName(), c.skin());

        ps.pushPose();
        // El espacio de una capa es el del MODELO (Y hacia abajo, invertido): se deshace para trabajar en metros con Y
        // hacia arriba y los pies en el origen, y se pone el cuerpo de lado a la altura de los brazos.
        // OJO con el orden: TRASLADAR primero e invertir después; al revés el cuerpo sube ~1,5 m. La altura (1,05) y el
        // avance (0,78) son los de las manos con la pose de brazos PAL (capa CARRY, CarryAnimClient).
        ps.translate(0.0, 1.501, 0.0);
        ps.scale(-1.0f, -1.0f, 1.0f);
        ps.translate(0.0, 1.05, -0.78);
        ps.mulPose(Axis.YP.rotationDegrees(90.0f));
        float ratio = c.scale() / Math.max(0.1f, player.getScale());   // el espacio de la capa ya lleva la escala de quien carga
        ps.scale(ratio, ratio, ratio);
        ps.translate(0.0, 0.0, 0.84);
        PlayerBodyRenderer.drawLying(ps, buffers, light, skin, models, c.gear(), player);
        ps.popPose();
    }

    @EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
    public static final class Hooks {
        private Hooks() {}

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer == null) continue;
                renderer.addLayer(new CarriedBodyLayer(renderer, PlayerBodyRenderer.Models.bake(event.getEntityModels())));
            }
        }
    }
}
