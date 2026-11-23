package com.hmc.zenkai.content.item;

import com.hmc.zenkai.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Dron de entrenamiento (Fase 4 de la 0.7.0; diseño en pendientes-0.7.0.md §3.3 y P9). Se mete en
 * las ranuras de drones de la consola de la cámara de gravedad; en cada ronda la consola GENERA la
 * entidad a partir de él (el ítem nunca sale de la máquina). Un solo tier.
 *
 * USOS: la durabilidad vanilla hace de contador (máximo {@link ServerConfig#DRONE_MAX_USES}). Gasta un
 * uso el dron golpeado (modo pasivo) o destruido (modo agresivo) en una ronda, se complete o no; al
 * agotarse se rompe. El desgaste se ve en 4 estados de textura (100–76, 75–51, 50–26, 25–0 %), en el
 * ítem por el predicado {@code zenkai:wear} y en la entidad por {@link #wearStage}.
 */
public class TrainingDroneItem extends Item {

    public TrainingDroneItem(Properties props) {
        super(props.durability(ServerConfig.DRONE_MAX_USES));
    }

    public static int usesLeft(ItemStack stack) {
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    /**
     * Estado de desgaste 0..3 (0 = nuevo). Fracción de usos restantes: &gt;75 % → 0, &gt;50 % → 1,
     * &gt;25 % → 2, el resto → 3. Compartido por el predicado del ítem y la textura de la entidad.
     */
    public static int wearStage(int usesLeft, int maxUses) {
        double f = maxUses <= 0 ? 0 : usesLeft / (double) maxUses;
        if (f > 0.75) return 0;
        if (f > 0.50) return 1;
        if (f > 0.25) return 2;
        return 3;
    }

    public static int wearStage(ItemStack stack) {
        return wearStage(usesLeft(stack), stack.getMaxDamage());
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack stack) { return false; }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx,
                                @NotNull List<Component> tip, @NotNull TooltipFlag flag) {
        tip.add(Component.translatable("item.zenkai.training_drone.uses", usesLeft(stack), stack.getMaxDamage())
                .withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("item.zenkai.training_drone.desc").withStyle(ChatFormatting.GRAY));
    }
}
