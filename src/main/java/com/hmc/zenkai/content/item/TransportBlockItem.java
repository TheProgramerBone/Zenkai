package com.hmc.zenkai.content.item;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.block.TransportBlock;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Ítem de los tanques de transporte. Lleva el contenido (FE o fluido) y los modos de cara que el
 * bloque tenía al romperse, y los enseña en el tooltip y en una barra de carga, como los tanques
 * de Mekanism. Los componentes los escribe y los lee el block entity (TransportBlockEntity).
 */
public class TransportBlockItem extends BlockItem {

    private final TransportBlock.Kind kind;

    public TransportBlockItem(TransportBlock block, Properties properties) {
        super(block, properties);
        this.kind = block.kind();
    }

    /** La config del servidor puede no estar cargada (menú principal, buscadores de recetas): valor por defecto. */
    private int capacity() {
        return safe(kind == TransportBlock.Kind.ENERGY ? ServerConfig::energyTankCapacity : ServerConfig::fluidTankCapacity,
                kind == TransportBlock.Kind.ENERGY ? 2_000_000 : 32_000);
    }

    private static int safe(IntSupplier s, int fallback) {
        try { return s.getAsInt(); } catch (IllegalStateException e) { return fallback; }
    }

    private int amount(ItemStack stack) {
        if (kind == TransportBlock.Kind.ENERGY) return Math.max(0, stack.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0));
        SimpleFluidContent fluid = stack.get(ModDataComponents.TRANSPORT_FLUID.get());
        return fluid == null ? 0 : fluid.copy().getAmount();
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) { return amount(stack) > 0; }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return Math.round(13f * Mth.clamp(amount(stack) / (float) Math.max(1, capacity()), 0f, 1f));
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return kind == TransportBlock.Kind.ENERGY ? 0x43E88D : 0x4FB6D6;   // verde de energía / cian de fluidos
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        int cap = capacity();
        if (kind == TransportBlock.Kind.ENERGY) {
            tooltip.add(Component.literal(ZenkaiNumbers.screen((long) amount(stack)) + " / "
                    + ZenkaiNumbers.screen((long) cap) + " FE").withStyle(ChatFormatting.AQUA));
        } else {
            SimpleFluidContent content = stack.get(ModDataComponents.TRANSPORT_FLUID.get());
            FluidStack fluid = content == null ? FluidStack.EMPTY : content.copy();
            tooltip.add(fluid.isEmpty()
                    ? Component.translatable("messages.zenkai.transport.fluid_empty", cap).withStyle(ChatFormatting.GRAY)
                    : Component.translatable("messages.zenkai.transport.fluid", fluid.getHoverName(), fluid.getAmount(), cap)
                            .withStyle(ChatFormatting.AQUA));
        }
        if (stack.has(ModDataComponents.TRANSPORT_MODES.get())) {
            tooltip.add(Component.translatable("messages.zenkai.transport.configured").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
