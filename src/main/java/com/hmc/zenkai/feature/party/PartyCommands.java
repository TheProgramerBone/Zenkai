package com.hmc.zenkai.feature.party;

import com.hmc.zenkai.Zenkai;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /zparty}: comandos de party, para CUALQUIER jugador.
 * RAÍZ PROPIA Y APARTE de {@code /zenkai} a propósito: el árbol de ModCommands exige
 * permiso 2 (OP), y party es un sistema para jugadores normales — anidarlo bajo /zenkai lo
 * dejaría inaccesible sin operador. Ver el mismo razonamiento (con otro motivo: fusión de
 * Brigadier) en feature/technique/TechniqueCommands.java.
 * NO VALIDA NI DECIDE NADA POR SU CUENTA: cada rama solo desempaqueta argumentos y llama a
 * PartyService, que es quien conoce las reglas y manda los mensajes de chat.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PartyCommands {
    private PartyCommands() {}

    /** Nombres de los compañeros de party actuales del que teclea, para el autocompletado
     *  de kick. Vacío para cualquiera que escriba sin tener party — Brigadier no sugiere nada. */
    private static final SuggestionProvider<CommandSourceStack> PARTY_MEMBER_NAMES = (ctx, b) -> {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) {
            return SharedSuggestionProvider.suggest(java.util.List.of(), b);
        }
        return SharedSuggestionProvider.suggest(
                PartyService.memberNames(ctx.getSource().getServer(), sp), b);
    };

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var root = e.getDispatcher();

        root.register(Commands.literal("zparty")

                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> PartyService.invite(
                                        ctx.getSource().getServer(),
                                        ctx.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(ctx, "player").getGameProfile().getName())
                                        ? 1 : 0)))

                .then(Commands.literal("accept")
                        .executes(ctx -> PartyService.accept(
                                ctx.getSource().getServer(), ctx.getSource().getPlayerOrException()) ? 1 : 0))

                .then(Commands.literal("deny")
                        .executes(ctx -> PartyService.deny(
                                ctx.getSource().getServer(), ctx.getSource().getPlayerOrException()) ? 1 : 0))

                .then(Commands.literal("leave")
                        .executes(ctx -> PartyService.leave(
                                ctx.getSource().getServer(), ctx.getSource().getPlayerOrException()) ? 1 : 0))

                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .suggests(PARTY_MEMBER_NAMES)
                                .executes(ctx -> PartyService.kick(
                                        ctx.getSource().getServer(),
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "player")) ? 1 : 0)))

                .then(Commands.literal("disband")
                        .executes(ctx -> PartyService.disband(
                                ctx.getSource().getServer(), ctx.getSource().getPlayerOrException()) ? 1 : 0))

                .then(Commands.literal("friendlyfire")
                        .then(Commands.literal("on")
                                .executes(ctx -> PartyService.setFriendlyFire(ctx.getSource().getServer(),
                                        ctx.getSource().getPlayerOrException(), true) ? 1 : 0))
                        .then(Commands.literal("off")
                                .executes(ctx -> PartyService.setFriendlyFire(ctx.getSource().getServer(),
                                        ctx.getSource().getPlayerOrException(), false) ? 1 : 0)))

                .then(Commands.literal("maxsize")
                        .then(Commands.argument("size", IntegerArgumentType.integer(1))
                                .executes(ctx -> PartyService.setMaxSize(
                                        ctx.getSource().getServer(),
                                        ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "size")) ? 1 : 0)))

                .then(Commands.literal("list")
                        .executes(ctx -> PartyService.list(
                                ctx.getSource().getServer(), ctx.getSource().getPlayerOrException()) ? 1 : 0))

                .then(Commands.literal("chat")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> PartyService.chat(
                                        ctx.getSource().getServer(),
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "message")) ? 1 : 0)))
        );
    }
}
