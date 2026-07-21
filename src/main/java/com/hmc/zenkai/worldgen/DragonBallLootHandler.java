package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.util.ModTags;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Marca como recogida cualquier esfera del dragón que un jugador rompa. */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class DragonBallLootHandler {
    private DragonBallLootHandler() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getState().is(ModTags.Blocks.DRAGON_BALLS_BLOCK)) return;
        LootedDragonBalls.get(level).markLooted(event.getPos());
    }
}