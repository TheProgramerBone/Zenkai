package com.hmc.zenkai.event;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Aplanado con pala del pasto namekiano -> camino namekiano.
 * No va por DataMap: NeoForge no expone uno para esto (la lista de NeoForgeDataMaps solo
 * tiene compostables, fuels, strippables, waxables, oxidizables y unos pocos más), y el
 * HashMap estático de vainilla tampoco está disponible. El punto de enganche es el evento
 * de modificación con herramienta.
 * BlockEvent va en el bus de JUEGO, así que la anotación NO lleva bus = Bus.MOD.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ZenkaiToolAbilities {
    private ZenkaiToolAbilities() {}

    @SubscribeEvent
    public static void onToolUse(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.SHOVEL_FLATTEN) return;   // ⚠ nombre
        if (event.getState().getBlock() != ModBlocks.NAMEKIAN_GRASS_BLOCK.get()) return;
        if (event.getState().getBlock() != ModBlocks.NAMEKIAN_DIRT.get()) return;
        var ctx = event.getContext();
        if (ctx.getClickedFace() == Direction.DOWN) return;
        if (!event.getLevel().getBlockState(ctx.getClickedPos().above()).isAir()) return;

        event.setFinalState(ModBlocks.NAMEKIAN_DIRT_PATH.get().defaultBlockState());
    }
}