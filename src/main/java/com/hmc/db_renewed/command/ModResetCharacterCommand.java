package com.hmc.db_renewed.command;

import com.hmc.db_renewed.api.PlayerStatData;
import com.hmc.db_renewed.common.capability.ModCapabilities;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ModResetCharacterCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("db_renewed")
                        .then(Commands.literal("reset_character")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    PlayerStatData data = player.getCapability(ModCapabilities.PLAYER_STATS);
                                    if (data != null) {
                                        data.resetCharacterCreation();
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("Character creation has been reset."), true);
                                    }
                                    return 1;
                                }))
        );
    }
}
