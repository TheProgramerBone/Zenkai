package com.hmc.zenkai.mixin.client;

import com.hmc.zenkai.core.network.feature.race.FirstPersonRaceArmorSwap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancela el dibujado del PlayerModel (la SKIN: cuerpo, brazos, sleeves, jacket, hat...)
 * durante la pasada de 1ª persona de PAL con skin racial activa. Las CAPAS (armadura geo
 * inyectada por FirstPersonRaceArmorSwap) usan sus propios modelos y no pasan por aquí,
 * así que se dibujan normal: solo se ve el geo, posado por la animación PAL real.
 * Se inyecta en AgeableListModel (donde está declarado renderToBuffer) y se filtra a
 * PlayerModel por instanceof — los HumanoidModel de armadura NO son PlayerModel.
 */
@Mixin(AgeableListModel.class)
public abstract class PlayerModelHideMixin {

    @Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true)
    private void zenkai$hideSkinInFirstPersonPass(PoseStack poseStack, VertexConsumer buffer,
                                                  int packedLight, int packedOverlay, int color,
                                                  CallbackInfo ci) {
        if ((Object) this instanceof PlayerModel<?> && FirstPersonRaceArmorSwap.hideSkinNow()) {
            ci.cancel();
        }
    }
}