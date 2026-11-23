package com.hmc.zenkai.content.item;

import com.hmc.zenkai.feature.armor.ArmorSets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Patrón de armadura (Fase 5b de la 0.7.0). El set va en el componente {@code zenkai:armor_pattern}; el nombre del ítem
 * lo lleva («Patrón: Armadura Saiyan»), así se distinguen en el inventario sin texturas distintas. Se pone en la
 * ranura de patrón de la máquina de fabricación y NO se gasta.
 */
public class ArmorPatternItem extends Item {

    public ArmorPatternItem(Properties props) { super(props); }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        ResourceLocation set = ArmorSets.setOf(stack);
        if (set == null) return Component.translatable("item.zenkai.armor_pattern.blank");
        return Component.translatable("item.zenkai.armor_pattern.named", ArmorSets.name(set));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx, @NotNull List<Component> tip,
                                @NotNull TooltipFlag flag) {
        tip.add(Component.translatable("item.zenkai.armor_pattern.tip").withStyle(ChatFormatting.GRAY));
    }
}
