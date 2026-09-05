package com.hmc.zenkai.mixin;

import com.hmc.zenkai.feature.teleport.EndOuterIslandTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Único punto donde el mod se entera de "un jugador acaba de usar un End Gateway de verdad" —
 * necesario porque un Gateway mueve al jugador DENTRO de la misma dimensión (the_end -&gt;
 * the_end), así que {@code PlayerChangedDimensionEvent} (lo que ya usa DimensionEntryTracker
 * para el resto de dimensiones) nunca se dispara para este caso. Verificado leyendo el fuente
 * real de NeoForm: no existe ningún evento de NeoForge para esto (EntityTeleportEvent solo se
 * dispara para perla de ender/fruto del coro/comandos, nunca para un Gateway) — de ahí el
 * Mixin, no un evento que no existe.
 *
 * Se engancha al RETURN de {@code EndGatewayBlock#getPortalDestination}, el mismo procedimiento
 * que vainilla ya llama para calcular a dónde va el jugador (incluye la búsqueda/creación de la
 * isla exterior si hace falta) — se lee el {@link DimensionTransition} YA CALCULADO por
 * vainilla, nunca se recalcula ningún enlace de Gateway por cuenta propia. El filtro de
 * "¿es de verdad una isla exterior, o el gateway de vuelta al spawn?" vive en
 * {@link EndOuterIslandTracker}, no aquí — este Mixin solo reenvía el dato.
 */
@Mixin(EndGatewayBlock.class)
public abstract class EndGatewayBlockMixin {

    @Inject(method = "getPortalDestination", at = @At("RETURN"))
    private void zenkai$trackGatewayDestination(ServerLevel level, Entity entity, BlockPos pos,
                                                 CallbackInfoReturnable<DimensionTransition> cir) {
        if (!(entity instanceof ServerPlayer sp)) return;
        DimensionTransition transition = cir.getReturnValue();
        if (transition == null) return;

        EndOuterIslandTracker.onGatewayTeleport(sp, BlockPos.containing(transition.pos()));
    }
}
