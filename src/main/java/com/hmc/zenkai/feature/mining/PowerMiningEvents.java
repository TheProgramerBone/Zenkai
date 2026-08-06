package com.hmc.zenkai.feature.mining;

import com.hmc.zenkai.Zenkai;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Cableado del minado por poder. Toda la lógica vive en PowerMining; aquí solo se enchufa
 * a los tres puntos donde vainilla decide algo.
 * Por qué DOS eventos y no uno: BreakSpeed corre en cliente y servidor y es lo que dibuja
 * las grietas, así que sin él el bloque parece que se rompe y luego rebota. BreakEvent
 * corre solo en servidor y es la autoridad real: un cliente modificado puede saltarse el
 * primero, nunca el segundo. El cobro de ki va en el segundo por eso mismo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PowerMiningEvents {
    private PowerMiningEvents() {}

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        BlockState state = event.getState();
        if (!PowerMining.isPowerMined(state)) return;

        Player player = event.getEntity();
        if (player.isCreative()) return;

        // La dureza real del bloque en su sitio. Sin posición (caso raro) tiramos de un
        // valor de referencia para no dividir por nada.
        float hardness = event.getPosition()
                .map(pos -> state.getDestroySpeed(player.level(), pos))
                .orElse(30.0F);

        float speed = PowerMining.breakSpeed(player, state, hardness);
        if (speed <= 0.0F) {
            event.setCanceled(true);   // irrompible: ni grietas ni progreso
            return;
        }
        event.setNewSpeed(speed);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (!PowerMining.isPowerMined(state)) return;

        Player player = event.getPlayer();
        if (player.isCreative()) return;
        if (player.level().isClientSide) return;

        if (!PowerMining.tryBreak(player, state)) {
            event.setCanceled(true);
        }
    }

    /**
     * Tooltip del item: cuánto poder pide el bloque que vas a colocar. Es la única pista
     * que tiene el jugador de que este material no va por picos, así que no es adorno.
     */
    @EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private Client() {}

        @SubscribeEvent
        public static void onTooltip(ItemTooltipEvent event) {
            if (!(event.getItemStack().getItem() instanceof BlockItem bi)) return;

            BlockState state = bi.getBlock().defaultBlockState();
            if (!PowerMining.isPowerMined(state)) return;

            event.getToolTip().add(Component.translatable(
                    "tooltip.zenkai.power_required",
                    (long) PowerMining.required(state)).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}