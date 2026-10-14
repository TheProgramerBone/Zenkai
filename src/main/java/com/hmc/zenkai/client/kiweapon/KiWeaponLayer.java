package com.hmc.zenkai.client.kiweapon;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.kiweapon.KiWeaponState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Capa que cuelga el arma de ki de la mano principal (y el escudo de ki de la secundaria) en 3ª persona (y en la pasada de cuerpo
 * entero de PAL en 1ª). Extiende PlayerItemInHandLayer SIN llamar a super.render() por la misma
 * razón que HandAnchorLayer: el filtro de capas de PAL en 1ª persona es una lista blanca por TIPO
 * y esta es la única clase que deja pasar. Léase el javadoc de HandAnchorLayer antes de cambiar
 * la jerarquía.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public class KiWeaponLayer extends PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public KiWeaponLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                         ItemInHandRenderer itemInHandRenderer) {
        super(parent, itemInHandRenderer);
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffer, int light,
                       @NotNull AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;

        // Un brazo por vuelta: el arma en el principal y, si lo hay, el escudo de ki en el otro.
        for (HumanoidArm arm : HumanoidArm.values()) {
            if (KiWeaponState.variantForArm(player, arm) == null) continue;
            pose.pushPose();
            getParentModel().translateToHand(arm, pose);
            // Marco de ítem de vanilla (ItemInHandLayer.renderArmWithItem).
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            boolean left = arm == HumanoidArm.LEFT;
            pose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
            KiWeaponVfx.queue(pose, player, partialTick, arm);
            pose.popPose();
        }
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            renderer.addLayer(new KiWeaponLayer(
                    renderer, Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()));
        }
    }
}
