package com.hmc.db_renewed.command;

import com.hmc.db_renewed.common.player.RaceDataHandler;
import com.hmc.db_renewed.common.race.ModRaces;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class ModRaceCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("db_renewed")
                        .then(Commands.literal("race")
                                .then(Commands.literal("get")
                                        .executes(ctx -> {
                                            Player player = ctx.getSource().getPlayerOrException();

                                            RaceDataHandler data = player.getCapability(RaceDataHandler.CAPABILITY, null);
                                            if (data != null) {
                                                ModRaces race = data.getRace();
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("Your race is: " + race.name()), false);
                                            };

                                            return 1;
                                        })
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("raza", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    Player player = ctx.getSource().getPlayerOrException();
                                                    String input = StringArgumentType.getString(ctx, "raza").toUpperCase();

                                                    try {
                                                        ModRaces newRace = ModRaces.valueOf(input);

                                                        RaceDataHandler data = player.getCapability(RaceDataHandler.CAPABILITY, null);
                                                        if (data != null) {
                                                            data.setRace(newRace);
                                                            ctx.getSource().sendSuccess(() ->
                                                                    Component.literal("Race changed to: " + newRace.name()), true);
                                                        };

                                                    } catch (IllegalArgumentException e) {
                                                        ctx.getSource().sendFailure(Component.literal("Invalid race: " + input));
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("list")
                                        .executes(ctx -> {
                                            StringBuilder sb = new StringBuilder("Available Races: ");
                                            for (ModRaces race : ModRaces.values()) {
                                                sb.append(race.name().toLowerCase()).append(", ");
                                            }
                                            String result = sb.substring(0, sb.length() - 2); // remove trailing comma
                                            ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}