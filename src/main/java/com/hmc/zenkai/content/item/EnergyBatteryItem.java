package com.hmc.zenkai.content.item;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Batería de FE (0.7.0, Fase 2b). La energía vive en un data component del stack y se expone con
 * la capability ESTÁNDAR de NeoForge (Capabilities.EnergyStorage.ITEM, registrada en
 * Zenkai.registerCapabilities): por eso se carga en la cámara de contención de ki y también en
 * cualquier cargador de otro mod, sin puente propio (compat = coexistir).
 *
 * CÓMO SE GASTA dentro de Zenkai, donde las máquinas no tienen ranura de batería: clic derecho
 * sobre un bloque que acepte FE (el banco de scouter, por ejemplo) vuelca en él lo que quepa. Es
 * la versión manual de «generador pegado al banco»: llevar la energía en la mano.
 *
 * NO SE APILA: dos baterías con carga distinta no pueden compartir stack, y una pila de vacías
 * que se cargan a la vez multiplicaría la energía recibida.
 */
public class EnergyBatteryItem extends Item {

    public EnergyBatteryItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Vista de energía de un stack. Único sitio que conoce capacidad y ritmo (config del servidor). */
    public static IEnergyStorage storage(ItemStack stack) {
        return new ComponentEnergyStorage(stack, ModDataComponents.STORED_ENERGY.get(),
                ServerConfig.batteryCapacity(), ServerConfig.batteryMaxTransfer());
    }

    public static int stored(ItemStack stack) {
        return Math.max(0, stack.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0));
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                ctx.getClickedPos(), ctx.getClickedFace());
        if (target == null || !target.canReceive()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        // Vuelca de golpe lo que el bloque acepte. Se lee directo del componente y no por la
        // capability del ítem: el tope por tick de la batería es para la automatización, y un
        // clic que solo pasara 1 000 FE obligaría a hacer cien clics para vaciarla.
        ItemStack stack = ctx.getItemInHand();
        int have = stored(stack);
        int moved = 0;
        // receiveEnergy puede aceptar menos de lo ofrecido por llamada (tope de entrada del
        // bloque): se insiste mientras siga aceptando, con un límite de vueltas por si miente.
        for (int i = 0; i < 256 && have > 0; i++) {
            int accepted = target.receiveEnergy(have, false);
            if (accepted <= 0) break;
            have -= accepted;
            moved += accepted;
        }
        if (moved <= 0) return InteractionResult.PASS;

        stack.set(ModDataComponents.STORED_ENERGY.get(), have);
        level.playSound(null, ctx.getClickedPos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.4f, 1.6f);
        if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.translatable(
                    "messages.zenkai.battery.discharged", ZenkaiNumbers.screen((long) moved)), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;    // también vacía: una batería sin barra parece un ítem cualquiera
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        int cap = Math.max(1, ServerConfig.batteryCapacity());
        return Math.round(13f * Mth.clamp(stored(stack) / (float) cap, 0f, 1f));
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return 0x43E88D;    // == ZenkaiTechPalette.LED_GREEN_MID (clase de cliente, no se puede importar aquí)
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal(ZenkaiNumbers.screen((long) stored(stack)) + " / "
                + ZenkaiNumbers.screen((long) ServerConfig.batteryCapacity()) + " FE").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
