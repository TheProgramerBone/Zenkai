package com.hmc.zenkai.mixin.client;

import com.hmc.zenkai.client.LockOnClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ancla la cámara al objetivo del lock-on (habilidad Ki Sense).
 * Patrón del mod TFAR Lock-On: en vez de empujar la cámara cada tick (lo que marea en
 * primera persona), se intercepta el giro del RATÓN. Cuando hay objetivo fijado, se
 * reemplaza el giro libre por "mirar al objetivo" y se cancela el turn original. Con el
 * ratón quieto no pasa nada; al moverlo, la vista se mantiene sobre el objetivo.
 * SEGUNDO mixin del proyecto (el primero es PlayerModelHideMixin). Se acepta como excepción
 * porque no hay forma limpia de interceptar el ratón sin pelear con él: cualquier corrección
 * hecha fuera de este punto se suma al giro del jugador en vez de sustituirlo, y eso es
 * justo lo que producía el mareo.
 * ⚠ El descriptor de turnPlayer(D) puede variar entre mappings de NeoForge 1.21.1. Si el
 * mixin no aplica, la alternativa SIN mixin es NeoForge CalculatePlayerTurnEvent.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void zenkai$lockOnTurn(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || !(mc.player instanceof LocalPlayer player)) return;
        if (!LockOnClientState.hasTarget()) return;

        float[] delta = LockOnClientState.aimDelta(mc);
        if (delta == null) return; // sin línea de visión: el ratón manda este frame

        // Cancelamos turnPlayer entero, así que el factor 0.15 de sensibilidad NO se aplica:
        // LocalPlayer.turn toma los grados directos (a diferencia de Entity.turn). Pasamos el
        // error angular tal cual, en grados de mundo. (Igual que TFAR: player.turn(dyaw,dpitch).)
        player.turn(delta[0], delta[1]);
        ci.cancel();
    }
}