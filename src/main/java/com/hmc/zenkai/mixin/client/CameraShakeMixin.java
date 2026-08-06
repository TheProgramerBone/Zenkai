package com.hmc.zenkai.mixin.client;

import com.hmc.zenkai.client.overlay.SenseKiWarnings;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sacudida de cámara del aviso del sentir el ki.
 *
 * Va al FINAL de Camera.setup y no en ViewportEvent.ComputeCameraAngles: ese evento existe para
 * ángulos, y usarlo para POSICIÓN depende de en qué punto exacto lo dispare NeoForge respecto
 * al setup — algo que no controlamos y que hacía que el desplazamiento se perdiera. El final de
 * setup es lo último que la cámara hace consigo misma antes de que nadie la lea.
 *
 * ⚠ Verifica la firma de setup en tu IDE (Ctrl+click sobre Camera). En 1.21.1 debería ser
 * (BlockGetter, Entity, boolean, boolean, float). Si no coincide, ajusta los parámetros de
 * zenkai$senseShake: @Inject exige que coincidan EXACTAMENTE con los del método destino.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {

    @Shadow
    protected abstract void move(float distanceOffset, float verticalOffset, float horizontalOffset);

    @Inject(method = "setup", at = @At("TAIL"))
    private void zenkai$senseShake(BlockGetter level, Entity entity, boolean detached,
                                   boolean mirrored, float partialTick, CallbackInfo ci) {
        float[] off = SenseKiWarnings.shakeOffset(partialTick);
        if (off != null) move(off[0], off[1], off[2]);
    }
}