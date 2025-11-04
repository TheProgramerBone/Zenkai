package com.hmc.db_renewed.worldgen;

import com.hmc.db_renewed.network.stats.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class CommandsInit {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var root = e.getDispatcher();

        root.register(Commands.literal("dbr_tp").requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addTp(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))));

        root.register(Commands.literal("dbr_attr").requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("attr", StringArgumentType.string())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setAttr(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "attr"),
                                                        IntegerArgumentType.getInteger(ctx, "value"))))))));

        root.register(Commands.literal("dbr_race").requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("race", StringArgumentType.string())
                                        .executes(ctx -> setRace(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "race")))))));

        root.register(Commands.literal("dbr_style").requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("style", StringArgumentType.string())
                                        .executes(ctx -> setStyle(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "style")))))));

        root.register(Commands.literal("dbr_respec").requires(cs -> cs.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> respec(ctx, EntityArgument.getPlayer(ctx, "player")))));

        root.register(Commands.literal("dbr_stat").requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("temp")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("stat", StringArgumentType.string())
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> tempStat(ctx,
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "stat"),
                                                                IntegerArgumentType.getInteger(ctx, "value"),
                                                                IntegerArgumentType.getInteger(ctx, "duration")))))))));
    }

    private static int addTp(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int amount) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        att.addTP(amount);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("Added TP: " + amount + " to " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String attrName, int value) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        Dbrattributes a = Dbrattributes.fromString(attrName);
        att.setAttribute(a, value);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + a + " to " + value + " for " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setRace(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String raceName) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        Race r = Race.valueOf(raceName.toUpperCase());
        att.setRace(r);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("Set race to " + r + " for " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setStyle(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String styleName) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        Style s = Style.valueOf(styleName.toUpperCase());
        att.setStyle(s);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("Set style to " + s + " for " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int respec(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        att.respec();
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("Respec done for " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int tempStat(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String stat, int value, int duration) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        att.setTempStat(stat, value, duration);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("Temp stat " + stat + "=" + value + " for " + duration + " ticks on " + sp.getGameProfile().getName()), true);
        return 1;
    }
}