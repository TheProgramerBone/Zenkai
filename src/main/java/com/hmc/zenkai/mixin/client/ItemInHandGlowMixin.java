package com.hmc.zenkai.mixin.client;

import com.hmc.zenkai.client.kiweapon.KiWeaponVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Halo de Ki Infuse / Ultra Ego sobre el ítem de la mano en 1ª persona. NeoForge no ofrece un evento DESPUÉS de dibujar
 * el ítem con la pila en su marco exacto (RenderHandEvent es previo y sin la transformación del ítem), y rehacer esa
 * transformación a mano se desfasaría con cada animación de golpe. Aquí la pila ya está donde tiene que estar.
 * Los parámetros deben coincidir EXACTAMENTE con los de ItemInHandRenderer#renderItem.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandGlowMixin {

    @Inject(method = "renderItem", at = @At("TAIL"))
    private void zenkai$infuseGlow(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, boolean left,
                                   PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
        KiWeaponVfx.firstPersonItemGlow(entity, stack, ctx, left, pose, buffer);
    }
}
