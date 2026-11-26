package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.CapsuleBlock;
import com.hmc.zenkai.content.blockentity.CapsuleBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Shift + clic derecho sobre una cápsula LLENA colocada: gira 90° lo que pegará (pedido del usuario 2026-11-26; elegido
 * girar directo frente a abrir una pantalla, porque el holograma ya es la vista previa y se ve contra el terreno real).
 *
 * Va por evento y no por {@code useWithoutItem}: vanilla no llama al clic del bloque si el jugador va agachado con algo en
 * la mano (isSecondaryUseActive), y casi siempre lo lleva. Se cancela en los dos lados para que el cliente no coloque el
 * ítem de la mano ni dispare otra acción. La cápsula vacía y la lanzada no giran (decisión del usuario).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class CapsuleRotateEvents {
    private CapsuleRotateEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND || !e.getEntity().isShiftKeyDown()) return;
        BlockState st = e.getLevel().getBlockState(e.getPos());
        if (!(st.getBlock() instanceof CapsuleBlock) || !st.getValue(CapsuleBlock.FULL)) return;
        if (e.getEntity() instanceof ServerPlayer sp && e.getLevel().getBlockEntity(e.getPos()) instanceof CapsuleBlockEntity be) {
            be.rotate(sp);
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }
}
