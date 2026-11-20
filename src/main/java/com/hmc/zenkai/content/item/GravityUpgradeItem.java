package com.hmc.zenkai.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Mejora de gravedad de la cámara de gravedad (Fase 3 de la 0.7.0). Una por tier de material:
 * cobre ×10, hierro ×25, cristal de energía ×50, diamante ×100, katchin ×250, netherita ×500.
 * Va en la ranura de la consola y fija la g MÁXIMA; la g real la elige el jugador en la pantalla.
 * Decisión del usuario (2026-11-19): los tiers son mejoras que se meten en la máquina, no los
 * materiales de la estructura (la pared puede ser de cualquier bloque).
 */
public class GravityUpgradeItem extends Item {

    private final int maxGravity;

    public GravityUpgradeItem(int maxGravity, Properties props) {
        super(props.stacksTo(1));
        this.maxGravity = maxGravity;
    }

    public int maxGravity() { return maxGravity; }

    /** g máxima que permite este stack; 1 si no es una mejora (la cámara no genera gravedad). */
    public static int maxGravityOf(ItemStack stack) {
        return stack.getItem() instanceof GravityUpgradeItem u ? u.maxGravity : 1;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx,
                                @NotNull List<Component> tip, @NotNull TooltipFlag flag) {
        tip.add(Component.translatable("item.zenkai.gravity_upgrade.max", maxGravity).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("item.zenkai.gravity_upgrade.desc").withStyle(ChatFormatting.GRAY));
    }
}
