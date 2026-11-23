package com.hmc.zenkai.content.item;

import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Sangre de un atributo (Fase 5b de la 0.7.0, P2; por raza hasta el 2026-11-22). Cada una sube UN atributo al imbuirla en una pieza (antes: humano DEX, saiyan
 * STR, namekiano SPI, majin CON, arcosiano WIL). La calidad va en {@code zenkai:blood_quality}: 1 normal (mobs
 * vanilla), 2 concentrada (mobs del mod), 5 pura (jefes); el nombre la dice («Sangre de Fuerza pura»). Las de distinta
 * calidad no apilan entre sí, a propósito: valen distinto.
 */
public class BloodItem extends Item {

    private final ZenkaiAttributes attribute;

    public BloodItem(ZenkaiAttributes attribute, Properties props) {
        super(props);
        this.attribute = attribute;
    }

    public ZenkaiAttributes attribute() { return attribute; }

    /** Puntos que sube; nunca menos de 1 (un componente editado a 0 o negativo vale como sangre normal). */
    public static int quality(ItemStack stack) {
        return Math.max(1, stack.getOrDefault(ModDataComponents.BLOOD_QUALITY.get(), 1));
    }

    public static ItemStack of(Item blood, int quality, int count) {
        ItemStack s = new ItemStack(blood, count);
        if (quality > 1) s.set(ModDataComponents.BLOOD_QUALITY.get(), quality);
        return s;
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        Component base = super.getName(stack);
        int q = quality(stack);
        if (q >= 5) return Component.translatable("item.zenkai.blood.pure", base);
        if (q >= 2) return Component.translatable("item.zenkai.blood.concentrated", base);
        return base;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx, @NotNull List<Component> tip,
                                @NotNull TooltipFlag flag) {
        String key = switch (attribute) {
            case STRENGTH -> "str";
            case CONSTITUTION -> "con";
            case DEXTERITY -> "dex";
            case WILLPOWER -> "wil";
            case SPIRIT -> "spi";
            case MIND -> "mnd";
        };
        tip.add(Component.translatable("item.zenkai.blood.tip",
                Component.translatable("tooltip.zenkai.imbued." + key, String.valueOf(quality(stack))))
                .withStyle(ChatFormatting.GRAY));
    }
}
