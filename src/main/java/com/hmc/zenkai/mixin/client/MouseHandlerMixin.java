package com.hmc.zenkai.mixin.client;

import com.hmc.zenkai.client.LockOnClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cede la cámara al lock-on (LockOnClientState#applyAim) mientras hay objetivo fijado.
 * SEGUNDO mixin del proyecto (el primero es PlayerModelHideMixin). Se acepta como excepción:
 * se comprobó contra las fuentes de NeoForge 21.1.233 que CalculatePlayerTurnEvent
 * (ClientHooks.getTurnPlayerValues) solo deja ajustar la sensibilidad y el modo cinemático, no
 * sustituir el giro, así que no hay forma limpia sin tocar turnPlayer.
 *
 * Hechos verificados que el comentario anterior de este archivo tenía mal:
 *  - turnPlayer(double) lo llama handleAccumulatedMovement CADA FRAME de render, con el ratón
 *    quieto o no. Por eso el lock sigue al objetivo a ritmo de frame (suave, sin saltos de 20 Hz).
 *  - Entity.turn multiplica por 0.15 lo que recibe; applyAim lo compensa.
 *  - accumulatedDX/DY se ponen a cero DESPUÉS de turnPlayer, así que cancelar aquí no deja
 *    movimiento pendiente que "salte" al soltar el lock.
 * El descriptor de turnPlayer puede variar entre mappings: si el mixin no aplica, el juego avisa
 * al arrancar y la alternativa es un @WrapOperation sobre la llamada a LocalPlayer.turn.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void zenkai$lockOnTurn(double movementTime, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || !(mc.player instanceof LocalPlayer player)) return;
        if (!LockOnClientState.hasTarget()) return;

        // false = sin objetivo válido o sin línea de vista demasiado rato: manda el ratón.
        if (LockOnClientState.applyAim(mc, player, movementTime, accumulatedDX, accumulatedDY)) {
            ci.cancel();
        }
    }
}
