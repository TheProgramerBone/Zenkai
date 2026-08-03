package com.hmc.zenkai.event;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Aplanado con pala: pasto y tierra namekianos -> camino namekiano.
 * No va por DataMap: NeoForge no expone uno para esto (NeoForgeDataMaps solo tiene
 * compostables, fuels, strippables, waxables, oxidizables y poco más) y el HashMap estático
 * de vainilla ya no existe. El enganche es el evento de modificación con herramienta.
 * BlockEvent va en el bus de JUEGO, así que la anotación NO lleva bus = Bus.MOD.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ZenkaiToolAbilities {
    private ZenkaiToolAbilities() {}

    @SubscribeEvent
    public static void onToolUse(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.SHOVEL_FLATTEN) return;
        Block block = event.getState().getBlock();
        if (block != ModBlocks.NAMEKIAN_GRASS_BLOCK.get()
                && block != ModBlocks.NAMEKIAN_DIRT.get()) return;
        // Mismas dos condiciones que vainilla: no vale golpeando desde abajo, y necesita
        // aire encima (si no, quedaría un camino aplastado bajo un bloque sólido).
        var ctx = event.getContext();
        if (ctx.getClickedFace() == Direction.DOWN) return;
        if (!event.getLevel().getBlockState(ctx.getClickedPos().above()).isAir()) return;

        event.setFinalState(ModBlocks.NAMEKIAN_DIRT_PATH.get().defaultBlockState());
    }
}