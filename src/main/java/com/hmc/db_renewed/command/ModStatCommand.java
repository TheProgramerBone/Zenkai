package com.hmc.db_renewed.command;

import com.hmc.db_renewed.common.capability.StatAllocation;
import com.hmc.db_renewed.common.player.RaceDataHandler;
import com.hmc.db_renewed.common.race.ModRaces;
import com.hmc.db_renewed.common.race.ModRacesStats;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ModStatCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("db_renewed")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("stat")
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .then(Commands.literal("get")
                                        .executes(ModStatCommand::executeGetStat))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ModStatCommand::executeSetStat)))
                                .then(Commands.literal("reset")
                                        .executes(ModStatCommand::executeResetStats)))
                ));
    }

    private static int executeGetStat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String stat = StringArgumentType.getString(context, "stat");

        RaceDataHandler data = player.getCapability(RaceDataHandler.CAPABILITY);
        assert data != null;
        StatAllocation alloc = data.getStatAllocation();

        int value = switch (stat.toLowerCase()) {
            case "strength" -> alloc.getTpStrength();
            case "dexterity" -> alloc.getTpDexterity();
            case "constitution" -> alloc.getTpConstitution();
            case "willpower" -> alloc.getTpWillpower();
            case "mind" -> alloc.getTpMind();
            case "spirit" -> alloc.getTpSpirit();
            default -> {
                context.getSource().sendFailure(Component.literal("Stat not recognized: " + stat));
                yield -1;
            }
        };

        if (value >= 0) {
            context.getSource().sendSuccess(() ->
                    Component.literal("Current TP value of " + stat + ": " + value), false);
        }

        return 1;
    }

    private static int executeSetStat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String stat = StringArgumentType.getString(context, "stat");
        int value = IntegerArgumentType.getInteger(context, "value");

        RaceDataHandler data = player.getCapability(RaceDataHandler.CAPABILITY);
        assert data != null;
        StatAllocation alloc = data.getStatAllocation();

        switch (stat.toLowerCase()) {
            case "strength" -> alloc.setTpStrength(value);
            case "dexterity" -> alloc.setTpDexterity(value);
            case "constitution" -> alloc.setTpConstitution(value);
            case "willpower" -> alloc.setTpWillpower(value);
            case "mind" -> alloc.setTpMind(value);
            case "spirit" -> alloc.setTpSpirit(value);
            default -> {
                context.getSource().sendFailure(Component.literal("Stat not recognized: " + stat));
                return 0;
            }
        }

        context.getSource().sendSuccess(() ->
                Component.literal("TP value for " + stat + " set to " + value), true);
        return 1;
    }

    private static int executeResetStats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        RaceDataHandler data = player.getCapability(RaceDataHandler.CAPABILITY);
        assert data != null;
        StatAllocation alloc = data.getStatAllocation();
        ModRaces race = data.getRace();
        StatAllocation base = ModRacesStats.DEFAULT_STATS.get(race);

        if (base != null) {
            alloc.strength = base.strength;
            alloc.dexterity = base.dexterity;
            alloc.constitution = base.constitution;
            alloc.willpower = base.willpower;
            alloc.mind = base.mind;
            alloc.spirit = base.spirit;

            alloc.setTpStrength(0);
            alloc.setTpDexterity(0);
            alloc.setTpConstitution(0);
            alloc.setTpWillpower(0);
            alloc.setTpMind(0);
            alloc.setTpSpirit(0);

            context.getSource().sendSuccess(() ->
                    Component.literal("Stats have been reset to base values."), true);
        } else {
            context.getSource().sendFailure(Component.literal("No base stats found for race."));
        }

        return 1;
    }
}