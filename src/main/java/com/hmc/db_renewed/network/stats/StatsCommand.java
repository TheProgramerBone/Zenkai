package com.hmc.db_renewed.network.stats;

import com.hmc.db_renewed.config.StatsConfig;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class StatsCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var d = e.getDispatcher();

        // /stats          -> self
        // /stats <player> -> target
        d.register(Commands.literal("dbr_stats")
                .executes(ctx -> show(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        PlayerStatsAttachment att = sp.getData(DataAttachments.PLAYER_STATS.get());

        // Atributos
        int str = att.getAttribute(Dbrattributes.STRENGTH);
        int con = att.getAttribute(Dbrattributes.CONSTITUTION);
        int dex = att.getAttribute(Dbrattributes.DEXTERITY);
        int wil = att.getAttribute(Dbrattributes.WILLPOWER);
        int spi = att.getAttribute(Dbrattributes.SPIRIT);
        int mnd = att.getAttribute(Dbrattributes.MIND);

        // Derivados
        double melee   = att.computeMeleeFinal();
        double defense = att.computeDefenseFinal();
        double speed   = att.computeSpeedFinal();
        double fly     = att.computeFlyFinal();

        // Pools
        int sCur = att.getStamina(), sMax = att.getStaminaMax();
        int kCur = att.getEnergy(),  kMax = att.getEnergyMax();
        int bCur = att.getBody(),    bMax = att.getBodyMax();

        // Multiplicadores efectivos del movimiento/vuelo (con caps y scaling del server)
        double moveMult = Math.min(1.0 + (speed / 100.0)* StatsConfig.movementScaling(), StatsConfig.speedMultiplierCap());
        double flyMult  = Math.min(1.0 + (fly   / 100.0)* StatsConfig.flyScaling(),      StatsConfig.flyMultiplierCap());

        var name = sp.getGameProfile().getName();
        CommandSourceStack src = ctx.getSource();

        src.sendSuccess(() -> Component.literal("=== Stats for " + name + " ==="), false);
        src.sendSuccess(() -> Component.literal("Race: " + att.getRace() + " | Style: " + att.getStyle()), false);

        src.sendSuccess(() -> Component.literal(String.format(
                "Attributes: STR %d, CON %d, DEX %d, WIL %d, SPI %d, MIND %d (cap=%d)",
                str, con, dex, wil, spi, mnd, StatsConfig.globalAttributeCap())), false);

        src.sendSuccess(() -> Component.literal(String.format(
                "Derived: Melee %.1f, Defense %.1f, Speed %.1f, Fly %.1f",
                melee, defense, speed, fly)), false);

        src.sendSuccess(() -> Component.literal(String.format(
                "Pools: Body %d/%d, Stamina %d/%d, Ki %d/%d",
                bCur, bMax, sCur, sMax, kCur, kMax)), false);

        src.sendSuccess(() -> Component.literal(String.format(
                "Movement Mult: x%.2f (cap=%.2f), Fly Mult: x%.2f (cap=%.2f)",
                moveMult, StatsConfig.speedMultiplierCap(), flyMult, StatsConfig.flyMultiplierCap())), false);

        src.sendSuccess(() -> Component.literal(String.format(
                "Regen/tick: Body +%d, Stamina +%d, Energy +%d",
                StatsConfig.baseRegenBody(), StatsConfig.baseRegenStamina(), StatsConfig.baseRegenEnergy())), false);

        src.sendSuccess(() -> Component.literal("TP: " + att.getTP()), false);
        return 1;
    }
}