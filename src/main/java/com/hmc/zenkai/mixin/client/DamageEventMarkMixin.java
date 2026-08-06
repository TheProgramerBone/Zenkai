package com.hmc.zenkai.mixin.client;

import com.hmc.zenkai.client.HurtFlashGate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marca el tick en el que el jugador local recibió daño REAL.
 * ⚠ Verifica la firma en tu IDE: en 1.21.1 debería ser
 * LivingEntity#handleDamageEvent(DamageSource). Es el manejador cliente de
 * ClientboundDamageEventPacket.
 */
@Mixin(LivingEntity.class)
public abstract class DamageEventMarkMixin {

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void zenkai$markRealDamage(DamageSource source, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            HurtFlashGate.markRealDamage();
        }
    }
}