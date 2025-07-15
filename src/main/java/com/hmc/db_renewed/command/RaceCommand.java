package com.hmc.db_renewed.command;

import com.hmc.db_renewed.common.player.RaceDataHandler;
import com.hmc.db_renewed.common.race.Race;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class RaceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("race")
                        .then(Commands.literal("get")
                                .executes(ctx -> {
                                    Player player = ctx.getSource().getPlayerOrException();
                                    Race race = RaceDataHandler.loadRace(player);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Tu raza es: " + race.name()), false);
                                    return 1;
                                }))
                        .then(Commands.literal("set")
                                .then(Commands.argument("raza", StringArgumentType.word())
                                        .executes(ctx -> {
                                            Player player = ctx.getSource().getPlayerOrException();
                                            String input = StringArgumentType.getString(ctx, "raza").toUpperCase();

                                            try {
                                                Race race = Race.valueOf(input);
                                                RaceDataHandler.save(player, race, true);
                                                ctx.getSource().sendSuccess(() -> Component.literal("Raza cambiada a: " + race.name()), true);
                                            } catch (IllegalArgumentException e) {
                                                ctx.getSource().sendFailure(Component.literal("Raza no válida: " + input));
                                            }

                                            return 1;
                                        })))
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    StringBuilder sb = new StringBuilder("Razas disponibles: ");
                                    for (Race race : Race.values()) {
                                        sb.append(race.name().toLowerCase()).append(", ");
                                    }
                                    // Elimina la coma final
                                    String result = sb.substring(0, sb.length() - 2);
                                    ctx.getSource().sendSuccess(() -> Component.literal(result), false);
                                    return 1;
                                }))
        );
    }
}