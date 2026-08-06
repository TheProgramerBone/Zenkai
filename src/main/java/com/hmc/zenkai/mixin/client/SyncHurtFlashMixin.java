package com.hmc.zenkai.mixin.client;

import com.hmc.zenkai.client.HurtFlashGate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Quita el flash y el tilt de las bajadas de vida que NO vienen de un golpe.
 * Va en TAIL y no cancela hurtTo: la vida hay que ponerla igual, y lastHurt/invulnerableTime
 * los usa el cliente para otras cosas. Lo único que se revierte son los dos campos de la
 * animación.
 * hurtTime y hurtDuration son campos públicos de LivingEntity, así que basta el cast y no
 * hace falta @Shadow.
 */
@Mixin(LocalPlayer.class)
public abstract class SyncHurtFlashMixin {

    @Inject(method = "hurtTo", at = @At("TAIL"))
    private void zenkai$silenceSyncHurt(float health, CallbackInfo ci) {
        if (HurtFlashGate.recentRealDamage()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        self.hurtTime = 0;
        self.hurtDuration = 0;
    }
}