package com.hmc.zenkai.registry.commands;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /zenkaiface mark add|remove|clear|list} (2026-11-01): marcas faciales (cicatrices, pinturas) del propio jugador,
 * sin permiso de operador (es personalización, como el menú de Apariencia). Hasta {@link PlayerVisualAttachment#MAX_MARKINGS}.
 *
 * Es el acceso PROVISIONAL a las marcas hasta que la pantalla de Apariencia tenga su pestaña. Las marcas disponibles son
 * los PNG de {@code assets/zenkai/textures/customization/face/markings/} (generados por {@code tools/gen_face_markings.py});
 * el servidor no ve esos assets, así que acepta cualquier id válido y el cliente que no tenga la textura no pinta nada.
 */
public final class FaceCommands {
    private FaceCommands() {}

    /** Marcas que trae el mod (para autocompletar). */
    public static final List<String> SHIPPED = List.of("scar_left_eye", "scar_cheek", "cross_scar", "war_paint", "forehead_dot");

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("zenkaiface")
                .then(Commands.literal("mark")
                        .then(Commands.literal("add")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SHIPPED, b))
                                        .executes(c -> add(c, StringArgumentType.getString(c, "id")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SHIPPED, b))
                                        .executes(c -> remove(c, StringArgumentType.getString(c, "id")))))
                        .then(Commands.literal("clear").executes(FaceCommands::clear))
                        .then(Commands.literal("list").executes(FaceCommands::list)));
    }

    private static ResourceLocation id(String raw) {
        ResourceLocation rl = raw.contains(":") ? ResourceLocation.tryParse(raw)
                : ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, raw);
        return rl;
    }

    private static int add(CommandContext<CommandSourceStack> c, String raw) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ResourceLocation id = id(raw);
        if (id == null) {
            c.getSource().sendFailure(Component.literal("[Zenkai] id de marca inválido: " + raw));
            return 0;
        }
        PlayerVisualAttachment vis = sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        if (!vis.addMarking(id)) {
            c.getSource().sendFailure(Component.literal("[Zenkai] ya la tienes o llegaste al máximo de "
                    + PlayerVisualAttachment.MAX_MARKINGS + " marcas"));
            return 0;
        }
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        c.getSource().sendSuccess(() -> Component.literal("[Zenkai] marca añadida: " + id), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c, String raw) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ResourceLocation id = id(raw);
        PlayerVisualAttachment vis = sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        if (id == null || !vis.removeMarking(id)) {
            c.getSource().sendFailure(Component.literal("[Zenkai] no tienes esa marca"));
            return 0;
        }
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        c.getSource().sendSuccess(() -> Component.literal("[Zenkai] marca quitada: " + id), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).clearMarkings();
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        c.getSource().sendSuccess(() -> Component.literal("[Zenkai] marcas borradas"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        List<ResourceLocation> m = sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).getMarkings();
        c.getSource().sendSuccess(() -> Component.literal("[Zenkai] tus marcas: " + (m.isEmpty() ? "(ninguna)" : m)
                + " | disponibles: " + SHIPPED), false);
        return m.size();
    }
}
