package com.hmc.zenkai.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Mejora de máquina (0.7.0, Fase 2b): tolerancia a un tipo de Ki, velocidad o almacenamiento.
 * El ítem NO lleva lógica: qué hace cada mejora lo decide la máquina que la tiene en su ranura
 * (hoy la cámara de contención, ver KiContainmentChamberBlockEntity; mañana la infusora y la
 * máquina de fabricación). Así una misma mejora puede significar cosas distintas según la máquina
 * sin tocar esta clase. Lo único que aporta es la línea de descripción del tooltip, que sale de
 * la clave de lang del propio ítem más «.desc».
 */
public class MachineUpgradeItem extends Item {

    public MachineUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
