package com.hmc.zenkai.mixin.client;

import com.hmc.zenkai.feature.race.ZenkaiFirstPersonBody;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancela el dibujado del PlayerModel (skin: cuerpo, brazos, sleeves, jacket, hat) durante la
 * pasada de 1ª persona de PAL con skin racial activa.
 * POR QUÉ SE CANCELA EL DRAW Y NO SE APAGA LA VISIBILIDAD
 * -------------------------------------------------------
 * PAL (PlayerModelMixin, inject at RETURN de setupAnim) reescribe la visibilidad de las partes
 * en la pasada FP: apaga y vuelve a encender rightArm/leftArm —y sus mangas— según la
 * FirstPersonConfiguration. Corre después de cualquier evento o mixin nuestro, así que ningún
 * setAllVisible(false) sobrevive. Y bajar esa bandera tampoco vale: el HumanoidArmorLayerMixin
 * de PAL cuelga de ella también los brazos del geo racial, así que apagarla deja al jugador
 * manco (comprobado).
 * La solución es cambiar de etapa: PAL escribe flags, pero nunca cancela draws. Cancelando aquí
 * desaparece la skin vanilla entera y los brazos siguen llegando por HumanoidArmorLayer, que es
 * una de las dos únicas render layers que PAL deja pasar en la pasada FP.
 * El filtro es PlayerModel a propósito: GeoArmorRenderer extiende HumanoidModel, no PlayerModel,
 * así que el cuerpo racial no entra por aquí. Se inyecta en AgeableListModel porque es donde
 * está declarado renderToBuffer.
 */
@Mixin(AgeableListModel.class)
public abstract class PlayerModelHideMixin {

    @Inject(method = "renderToBuffer", at = @At("HEAD"), cancellable = true)
    private void zenkai$hideSkinInFirstPersonPass(PoseStack poseStack, VertexConsumer buffer,
                                                  int packedLight, int packedOverlay, int color,
                                                  CallbackInfo ci) {
        if ((Object) this instanceof PlayerModel<?> && ZenkaiFirstPersonBody.hideSkinNow()) {
            ci.cancel();
        }
    }
}