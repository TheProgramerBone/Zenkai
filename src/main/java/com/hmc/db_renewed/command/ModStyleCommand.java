package com.hmc.db_renewed.command;

import com.hmc.db_renewed.common.player.RaceDataHandler;
import com.hmc.db_renewed.common.style.CombatStyle;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class ModStyleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("db_renewed")
                        .then(Commands.literal("style")
                                .then(Commands.literal("get")
                                        .executes(ctx -> {
                                            Player player = ctx.getSource().getPlayerOrException();
                                            RaceDataHandler data = player.getCapability(RaceDataHandler.CAPABILITY);
                                            if (data != null) {
                                                CombatStyle style = data.getCombatStyle();
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("Your combat style is: " + style.name()), false);
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("Combat style data not available."));
                                            }
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("style", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    Player player = ctx.getSource().getPlayerOrException();
                                                    String input = StringArgumentType.getString(ctx, "style").toUpperCase();

                                                    try {
                                                        CombatStyle style = CombatStyle.valueOf(input);
                                                        RaceDataHandler data = player.getCapability(RaceDataHandler.CAPABILITY);
                                                        if (data != null) {
                                                            data.setCombatStyle(style);
                                                            ctx.getSource().sendSuccess(() ->
                                                                    Component.literal("Combat style changed to: " + style.name()), true);
                                                        } else {
                                                            ctx.getSource().sendFailure(Component.literal("Combat style data not available."));
                                                        }
                                                    } catch (IllegalArgumentException e) {
                                                        ctx.getSource().sendFailure(Component.literal("Invalid style: " + input));
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("list")
                                        .executes(ctx -> {
                                            StringBuilder sb = new StringBuilder("Available Combat Styles: ");
                                            for (CombatStyle style : CombatStyle.values()) {
                                                sb.append(style.name().toLowerCase()).append(", ");
                                            }
                                            String result = sb.substring(0, sb.length() - 2);
                                            ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}