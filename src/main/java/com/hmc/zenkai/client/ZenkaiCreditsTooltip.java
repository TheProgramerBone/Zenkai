package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.registry.ModDataMaps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Línea de créditos en el tooltip de los assets con autoría registrada.
 * El dato sale del DataMap y no de un componente del stack, así que un item recogido antes
 * de que existiera el crédito también lo muestra.
 * El interruptor es config de CLIENTE, no gamerule: las gamerules no viajan al cliente
 * (vainilla solo sincroniza tres concretas) y ClientLevel.getGameRules() devuelve siempre
 * los valores por defecto, así que una regla aquí se leería siempre como true. Además esto
 * es una preferencia de visualización: qué créditos ve cada jugador no lo decide el admin.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiCreditsTooltip {
    private ZenkaiCreditsTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!ClientConfig.showModelCredits()) return;

        ModDataMaps.ModelCredit credit =
                event.getItemStack().getItemHolder().getData(ModDataMaps.MODEL_CREDITS);
        if (credit == null) return;

        String text = credit.detail().isEmpty()
                ? credit.author()
                : credit.author() + " (" + credit.detail() + ")";

        event.getToolTip().add(Component.translatable("tooltip.zenkai.credits", text)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC, ChatFormatting.BOLD));
    }
}