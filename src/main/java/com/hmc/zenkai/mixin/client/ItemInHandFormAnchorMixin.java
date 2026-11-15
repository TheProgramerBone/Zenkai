package com.hmc.zenkai.mixin.client;

import com.hmc.zenkai.client.FormHandAnchor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Objeto de la mano en 3ª persona colgado del puño del cuerpo de la forma (Oozaru) en vez del brazo vanilla: ver
 * FormHandAnchor. NeoForge no tiene evento para mover el objeto de la mano de un jugador, y la alternativa (quitar la
 * capa vanilla y poner una propia) rompería la lista blanca por tipo del filtro de 1ª persona de PAL (ver el javadoc
 * de HandAnchorLayer) y a cualquier otro mod que espere encontrar ItemInHandLayer.
 * Es un WrapOperation (y no un Redirect) para encadenarse con otros mods que toquen la misma llamada: si no hay cuerpo
 * con mano propia, se llama a la operación original sin cambios.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandFormAnchorMixin {

    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void zenkai$formHand(ArmedModel model, HumanoidArm arm, PoseStack pose, Operation<Void> original,
                                 @Local(argsOnly = true) LivingEntity entity) {
        if (model instanceof HumanoidModel<?> humanoid && FormHandAnchor.translateToHand(entity, humanoid, arm, pose)) {
            return;
        }
        original.call(model, arm, pose);
    }
}
