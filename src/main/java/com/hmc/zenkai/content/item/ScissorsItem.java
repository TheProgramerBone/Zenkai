package com.hmc.zenkai.content.item;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.race.hair.HairCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tijeras de peluquero (petición del usuario, 2026-10-12): clic derecho abre la pestaña Pelo de la
 * apariencia, sola, para cambiar de peinado sin rehacer el personaje. Lo que se elige se guarda con
 * "Confirmar" (UpdatePlayerVisualPacket, que el servidor filtra con su lista blanca); "Cancelar" o
 * Escape lo deshacen.
 *
 * Razas sin pelo (Namekiano, Arcosiano): un aviso con una carita triste, sin abrir nada. El Majin
 * tiene su propio aviso: la idea del usuario es darle peinados con el tono fijo de su piel más
 * adelante (pendiente), así que no se le dice que "no hay nada que cortar".
 */
public class ScissorsItem extends Item {

    public ScissorsItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Solo cliente: la pantalla es suya y el aviso también (nada que validar en el servidor
        // hasta que se confirma, y eso ya lo filtra UpdatePlayerVisualPacket).
        if (!level.isClientSide()) return InteractionResultHolder.success(stack);

        PlayerStatsAttachment stats = PlayerStatsAttachment.get(player);
        if (!stats.isRaceChosen()) {
            player.displayClientMessage(Component.translatable("item.zenkai.scissors.no_character"), true);
            return InteractionResultHolder.fail(stack);
        }
        Race race = stats.getRace();
        if (HairCatalog.options(race).isEmpty()) {
            player.displayClientMessage(Component.translatable(race == Race.MAJIN
                    ? "item.zenkai.scissors.majin_soon" : "item.zenkai.scissors.nothing_to_cut"), true);
            return InteractionResultHolder.fail(stack);
        }
        // La clase cliente solo se toca dentro de isClientSide (mismo truco que WeightArmorItem).
        com.hmc.zenkai.client.gui.screens.HeadAppearanceScreen.openHairOnly();
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx,
                                @NotNull List<Component> lines, @NotNull TooltipFlag flag) {
        lines.add(Component.translatable("item.zenkai.scissors.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
