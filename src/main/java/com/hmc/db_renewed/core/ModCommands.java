package com.hmc.db_renewed.core;

import com.hmc.db_renewed.core.config.StatsConfig;
import com.hmc.db_renewed.core.network.feature.Dbrattributes;
import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.Style;
import com.hmc.db_renewed.core.network.feature.stats.DataAttachments;
import com.hmc.db_renewed.core.network.feature.stats.PlayerLifeCycle;
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

public class ModCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var root = e.getDispatcher();

        // ========== /dbr_tp ==========
        root.register(Commands.literal("dbr_tp")
                .requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("add")
                        // /dbr_tp add <amount>  (a ti mismo)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> addTp(ctx,
                                        ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "amount"))))
                        // /dbr_tp add <player> <amount>
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addTp(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))));

        // ========== /dbr_attr ==========
        root.register(Commands.literal("dbr_attr")
                .requires(cs -> cs.hasPermission(2))

                // /dbr_attr set ...
                .then(Commands.literal("set")
                        // /dbr_attr set <attr> <value>  (a ti mismo)
                        .then(Commands.argument("attr", StringArgumentType.string())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setAttr(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "attr"),
                                                IntegerArgumentType.getInteger(ctx, "value")))))
                        // /dbr_attr set <player> <attr> <value>
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("attr", StringArgumentType.string())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setAttr(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "attr"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))))

                // /dbr_attr setall ...
                .then(Commands.literal("setall")
                        // /dbr_attr setall <value>   (a ti mismo)
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(ctx -> setAllAttr(ctx,
                                        ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "value"))))
                        // /dbr_attr setall <player> <value>
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setAllAttr(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "value"))))))

                // /dbr_attr maxall ...
                .then(Commands.literal("maxall")
                        // /dbr_attr maxall   (a ti mismo)
                        .executes(ctx -> maxAllAttr(ctx, ctx.getSource().getPlayerOrException()))
                        // /dbr_attr maxall <player>
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> maxAllAttr(ctx, EntityArgument.getPlayer(ctx, "player")))))
        );

        // ========== /dbr_race ==========
        root.register(Commands.literal("dbr_race").requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("set")
                        // /dbr_race set <race> (a ti mismo)
                        .then(Commands.argument("race", StringArgumentType.string())
                                .executes(ctx -> setRace(ctx,
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "race"))))
                        // /dbr_race set <player> <race>
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("race", StringArgumentType.string())
                                        .executes(ctx -> setRace(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "race")))))));

        // ========== /dbr_style ==========
        root.register(Commands.literal("dbr_style").requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("set")
                        // /dbr_style set <style> (a ti mismo)
                        .then(Commands.argument("style", StringArgumentType.string())
                                .executes(ctx -> setStyle(ctx,
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "style"))))
                        // /dbr_style set <player> <style>
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("style", StringArgumentType.string())
                                        .executes(ctx -> setStyle(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "style")))))));

        // ========== /dbr_respec (alias viejo) ==========
        root.register(Commands.literal("dbr_respec").requires(cs -> cs.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> respec(ctx, EntityArgument.getPlayer(ctx, "player"))))
                .executes(ctx -> respec(ctx, ctx.getSource().getPlayerOrException()))
        );

        // ========== /dbr_reset ==========
        root.register(Commands.literal("dbr_reset").requires(cs -> cs.hasPermission(2))
                // /dbr_reset stats [player]
                .then(Commands.literal("stats")
                        .executes(ctx -> resetStats(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetStats(ctx, EntityArgument.getPlayer(ctx, "player")))))
                // /dbr_reset full [player]
                .then(Commands.literal("full")
                        .executes(ctx -> resetFull(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetFull(ctx, EntityArgument.getPlayer(ctx, "player")))))
        );
    }

    // ========================= IMPLEMENTACIONES =========================

    private static int addTp(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int amount) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        att.addTP(amount);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Added TP: " + amount + " to " + sp.getGameProfile().getName()),
                true
        );
        return 1;
    }

    private static int setAttr(CommandContext<CommandSourceStack> ctx,
                               ServerPlayer sp, String attrName, int value) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        Dbrattributes a = Dbrattributes.fromString(attrName);
        if (a == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown attribute: " + attrName));
            return 0;
        }
        att.setAttribute(a, value);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set " + a + " to " + value + " for " + sp.getGameProfile().getName()),
                true
        );
        return 1;
    }

    /** Setea TODOS los atributos a un valor concreto. */
    private static int setAllAttr(CommandContext<CommandSourceStack> ctx,
                                  ServerPlayer sp, int value) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        int cap = StatsConfig.globalAttributeCap();
        int v = Math.max(0, Math.min(value, cap));

        for (Dbrattributes a : Dbrattributes.values()) {
            att.setAttribute(a, v);
        }
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set ALL attributes to " + v + " for " + sp.getGameProfile().getName()),
                true
        );
        return 1;
    }

    /** Setea TODOS los atributos al cap global. */
    private static int maxAllAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        int cap = StatsConfig.globalAttributeCap();
        return setAllAttr(ctx, sp, cap);
    }

    private static int setRace(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String raceName) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        Race r;
        try {
            r = Race.valueOf(raceName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("Unknown race: " + raceName));
            return 0;
        }
        att.setRace(r);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set race to " + r + " for " + sp.getGameProfile().getName()),
                true
        );
        return 1;
    }

    private static int setStyle(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String styleName) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        Style s;
        try {
            s = Style.valueOf(styleName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("Unknown style: " + styleName));
            return 0;
        }
        att.setStyle(s);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set style to " + s + " for " + sp.getGameProfile().getName()),
                true
        );
        return 1;
    }

    /** Respec clásico: usa tu lógica actual. */
    private static int respec(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        att.respec();
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Respec done for " + sp.getGameProfile().getName()),
                true
        );
        return 1;
    }

    /** /dbr_reset stats → solo respec, alias explícito. */
    private static int resetStats(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        return respec(ctx, sp);
    }

    /**
     * /dbr_reset full → resetea raza, estilo, stats y TP.
     * (Usa HUMAN + MARTIAL_ARTIST como valores por defecto;
     * puedes cambiarlo si quieres otro "estado base").
     */
    private static int resetFull(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());

        att.setRace(Race.HUMAN);
        att.setStyle(Style.MARTIAL_ARTIST);
        att.respec();
        int currentTp = att.getTP();
        if (currentTp > 0) {
            att.addTP(-currentTp);
        }
        att.setRaceChosen(false);
        att.setFlyEnabled(false);
        att.setImmortal(false);
        PlayerLifeCycle.sync(sp);

        ctx.getSource().sendSuccess(
                () -> Component.literal("Full reset (race, style, stats, TP) for " + sp.getGameProfile().getName()),
                true
        );
        return 1;
    }
}