package com.hmc.zenkai.core;

import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.Dbrattributes;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.Style;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class ModCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var root = e.getDispatcher();

        // ── /zenkai tp ───────────────────────────────────────────────────────
        // Añade TP de entrenamiento al jugador.
        // Uso: /zenkai tp add [jugador] <cantidad>
        root.register(Commands.literal("zenkai")
                .requires(cs -> cs.hasPermission(2))

                .then(Commands.literal("tp")
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addTp(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> addTp(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))))

                // ── /zenkai attr ──────────────────────────────────────────────────
                // Manipula atributos individuales o en bloque.
                // Uso: /zenkai attr set [jugador] <atributo> <valor>
                //      /zenkai attr setall [jugador] <valor>
                //      /zenkai attr maxall [jugador]
                .then(Commands.literal("attr")
                        .then(Commands.literal("set")
                                .then(Commands.argument("attr", StringArgumentType.string())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setAttr(ctx,
                                                        ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "attr"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("attr", StringArgumentType.string())
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> setAttr(ctx,
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "attr"),
                                                                IntegerArgumentType.getInteger(ctx, "value")))))))
                        .then(Commands.literal("setall")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setAllAttr(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "value"))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setAllAttr(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "value"))))))
                        .then(Commands.literal("maxall")
                                .executes(ctx -> maxAllAttr(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> maxAllAttr(ctx, EntityArgument.getPlayer(ctx, "player"))))))

                // ── /zenkai race ──────────────────────────────────────────────────
                // Fuerza la raza de un jugador desde el servidor.
                // Uso: /zenkai race set [jugador] <raza>
                .then(Commands.literal("race")
                        .then(Commands.literal("set")
                                .then(Commands.argument("race", StringArgumentType.string())
                                        .executes(ctx -> setRace(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "race"))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("race", StringArgumentType.string())
                                                .executes(ctx -> setRace(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "race")))))))

                // ── /zenkai style ─────────────────────────────────────────────────
                // Fuerza el estilo de combate de un jugador desde el servidor.
                // Uso: /zenkai style set [jugador] <estilo>
                .then(Commands.literal("style")
                        .then(Commands.literal("set")
                                .then(Commands.argument("style", StringArgumentType.string())
                                        .executes(ctx -> setStyle(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "style"))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("style", StringArgumentType.string())
                                                .executes(ctx -> setStyle(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "style")))))))

                // ── /zenkai reset ─────────────────────────────────────────────────
                // Resetea el progreso del jugador.
                // /zenkai reset stats [jugador] → devuelve los TP invertidos (respec)
                // /zenkai reset full  [jugador] → borra raza, estilo, stats, TP y apariencia
                .then(Commands.literal("reset")
                        .then(Commands.literal("stats")
                                .executes(ctx -> resetStats(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> resetStats(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("full")
                                .executes(ctx -> resetFull(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> resetFull(ctx, EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("revive")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> revivePlayer(ctx, EntityArgument.getPlayer(ctx, "player")))))

                // ── /zenkai pets ──────────────────────────────────────────────────
                // Borra el historial de mascotas muertas (deseo de revivir).
                // Uso: /zenkai pets clear [jugador]
                .then(Commands.literal("pets")
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearPets(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> clearPets(ctx, EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("struct")
                        .then(Commands.literal("place")
                                .then(Commands.argument("which", StringArgumentType.word())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> {
                                                    var lvl = ctx.getSource().getLevel();
                                                    var pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                                    String which = StringArgumentType.getString(ctx, "which");
                                                    boolean ok = com.hmc.zenkai.worldgen.ZenkaiStructurePlacement.forcePlace(lvl, which, pos);
                                                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                                            ok ? "[Zenkai] Colocado " + which : "[Zenkai] Falló (revisa NBT/offsets)"), true);
                                                    return ok ? 1 : 0;
                                                })))))

                .then(Commands.literal("locate")
                        .executes(ctx -> {
                            var src = ctx.getSource();
                            var zones = com.hmc.zenkai.worldgen.NoHostileSpawnZones.getZones();
                            if (zones.isEmpty()) { src.sendSuccess(() -> Component.literal("[Zenkai] No hay estructuras registradas."), false); return 0; }
                            for (var z : zones) {
                                var c = z.box().getCenter();
                                src.sendSuccess(() -> Component.literal(String.format(
                                        "§e%s §7— %s §7@ §f%d %d %d",
                                        z.protector(), z.dimension().location(),
                                        (int)c.x, (int)c.y, (int)c.z)), false);
                            }
                            return 1;
                        }))



        );
    }

    // ── Implementaciones ─────────────────────────────────────────────────────

    private static int addTp(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int amount) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        att.addTP(amount);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] +" + amount + " TP → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setAttr(CommandContext<CommandSourceStack> ctx,
                               ServerPlayer sp, String attrName, int value) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        Dbrattributes a = Dbrattributes.fromString(attrName);
        if (a == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown attribute: " + attrName
                    + ". Valid: STR, CON, DEX, WIL, SPI, MND"));
            return 0;
        }
        att.setAttribute(a, value);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] " + a + " = " + value + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setAllAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int value) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        int v = Math.max(0, Math.min(value, StatsConfig.globalAttributeCap()));
        for (Dbrattributes a : Dbrattributes.values()) att.setAttribute(a, v);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] All attributes = " + v + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int maxAllAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        return setAllAttr(ctx, sp, StatsConfig.globalAttributeCap());
    }

    private static int setRace(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String raceName) {
        Race r;
        try { r = Race.valueOf(raceName.toUpperCase()); }
        catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown race: " + raceName));
            return 0;
        }
        sp.getData(DataAttachments.PLAYER_STATS.get()).setRace(r);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Race = " + r + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setStyle(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String styleName) {
        Style s;
        try { s = Style.valueOf(styleName.toUpperCase()); }
        catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown style: " + styleName));
            return 0;
        }
        sp.getData(DataAttachments.PLAYER_STATS.get()).setStyle(s);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Style = " + s + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Respec: devuelve los TP invertidos sin tocar raza ni estilo. */
    private static int resetStats(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        sp.getData(DataAttachments.PLAYER_STATS.get()).respec();
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Stats respec done → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Reset completo: raza, estilo, stats, TP y apariencia visual (pelo, ojos, colores, etc.).
     * El jugador deberá pasar por la creación de personaje de nuevo.
     */
    private static int resetFull(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        // ── Stats ─────────────────────────────────────────────────────────────
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        att.setRace(Race.HUMAN);
        att.setStyle(Style.MARTIAL_ARTIST);
        att.respec();
        att.addTP(-att.getTP()); // vaciar TP restante
        att.setRaceChosen(false);
        att.setStyleChosen(false);
        att.setFlyEnabled(false);
        att.setImmortal(false);
        PlayerLifeCycle.sync(sp);

        // ── Visual (pelo, ojos, colores, índices) ─────────────────────────────
        // Cargar un attachment limpio con todos los valores por defecto
        var visual = sp.getData(DataAttachments.PLAYER_VISUAL.get());
        visual.load(new PlayerVisualAttachment().save());
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);

        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Full reset done → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int clearPets(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var att = sp.getData(DataAttachments.PLAYER_STATS.get());
        int n = att.getDeadPets().size();
        att.clearDeadPets();
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Cleared " + n + " dead pet(s) → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int revivePlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        boolean ok = com.hmc.zenkai.core.network.feature.player.OtherworldManager.revive(target);
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] " + target.getGameProfile().getName() + " is not in the otherworld."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Revived " + target.getGameProfile().getName()), true);
        return 1;
    }
}