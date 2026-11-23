package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.armor.ImbuedAttributes;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Tooltip de los atributos imbuidos ({@code zenkai:imbued}). Va por evento y no en appendHoverText porque el componente
 * vale en cualquier pieza, también vanilla o de otro mod, que no son clases nuestras. Las cifras pasan por ZenkaiNumbers
 * (regla de CLAUDE.md); compactas desde 20 000 y el exacto entre paréntesis, que en un tooltip no hay otro tooltip.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ImbuedTooltip {
    private ImbuedTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        List<Component> lines = e.getToolTip();
        // Detrás del nombre (línea 0): la infusión (Fase 5c) y lo imbuido son lo primero que se quiere ver de la pieza.
        int at = Math.min(1, lines.size());
        KiEnergyType inf = e.getItemStack().get(ModDataComponents.KI_INFUSION.get());
        if (inf != null) {
            lines.add(at++, Component.translatable("tooltip.zenkai.ki_infusion",
                    Component.translatable(inf.langKey()).withStyle(s -> s.withColor(inf.argb() & 0xFFFFFF))));
        }
        ImbuedAttributes imb = e.getItemStack().get(ModDataComponents.IMBUED.get());
        if (imb == null || imb.isEmpty()) return;
        lines.add(at++, Component.translatable("tooltip.zenkai.imbued.header").withStyle(ChatFormatting.GRAY));
        for (ZenkaiAttributes a : ImbuedAttributes.IMBUABLE) {
            int v = imb.get(a);
            if (v <= 0) continue;
            String num = ZenkaiNumbers.isCompacted(v)
                    ? ZenkaiNumbers.screen(v) + " (" + ZenkaiNumbers.exact(v) + ")"
                    : ZenkaiNumbers.screen(v);
            lines.add(at++, Component.translatable("tooltip.zenkai.imbued." + key(a), num)
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    private static String key(ZenkaiAttributes a) {
        return switch (a) {
            case STRENGTH     -> "str";
            case CONSTITUTION -> "con";
            case DEXTERITY    -> "dex";
            case WILLPOWER    -> "wil";
            case SPIRIT       -> "spi";
            case MIND         -> "mnd";
        };
    }
}
