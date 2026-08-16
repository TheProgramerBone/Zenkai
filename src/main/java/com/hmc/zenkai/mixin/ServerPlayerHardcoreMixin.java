package com.hmc.zenkai.mixin;

import com.hmc.zenkai.feature.player.HardcoreRespawnWindow;
import com.hmc.zenkai.feature.player.OtherworldManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hardcore + Otro Mundo: red de seguridad contra el modo espectador forzado.
procedimiento * NO ES QUIEN ARREGLA EL PROBLEMA. El espectador se deshace en
 * OtherworldManager#respawnIntoOtherworld, que corre un tick después del respawn y solo tiene
 * que leer el estado final. Este mixin cubre el hueco intermedio: si algo pone espectador
 * mientras el jugador está viajando al Otro Mundo, aquí se bloquea.
procedimiento * Se enganchó a ServerPlayer#setGameMode y no a PlayerList#respawn porque aquel es público y
 * su nombre no es ambiguo; el intento sobre respawn no encontró siquiera el procedimiento objetivo
 * ("Scanned 0 targets"), y la firma cambia entre versiones.
procedimiento * EL PRECIO de enganchar un procedimiento público es que lo llama el mundo, /gamemode incluido.
 * De ahí HardcoreRespawnWindow: fuera del instante del respawn este mixin no hace nada.
procedimiento * LA RECURSIÓN ES SEGURA: la llamada de dentro pasa SURVIVAL, que sale en la primera línea.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerHardcoreMixin {

    /**
     * ⚠ setGameMode devuelve boolean (true si el modo cambió de verdad), de ahí el
     * CallbackInfoReturnable. Se devuelve el resultado de la llamada a SURVIVAL y no un true
     * fijo: quien pidió el cambio merece saber si ocurrió, y mentirle rompe a cualquier mod
     * que encadene lógica a ese retorno.
     */
    @Inject(method = "setGameMode", at = @At("HEAD"), cancellable = true)
    private void zenkai$keepSurvivalForOtherworld(GameType mode, CallbackInfoReturnable<Boolean> cir) {
        if (mode != GameType.SPECTATOR) return;

        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!HardcoreRespawnWindow.isOpen(self)) return;
        if (!OtherworldManager.shouldSurviveHardcore(self)) return;

        // La recursión es segura: esta llamada pasa SURVIVAL, que sale en la primera línea.
        cir.setReturnValue(self.setGameMode(GameType.SURVIVAL));
    }
}