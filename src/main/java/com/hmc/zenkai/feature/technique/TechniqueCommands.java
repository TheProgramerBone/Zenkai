package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.TechniqueDef.Kind;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Edición en caliente de los números de las técnicas.
 *
 *   /zenkai tech &lt;ki|physical&gt; &lt;id&gt; info
 *   /zenkai tech &lt;ki|physical&gt; &lt;id&gt; set &lt;campo&gt; &lt;valor&gt;
 *   /zenkai tech &lt;ki|physical&gt; &lt;id&gt; reset &lt;campo|all&gt;
 *   /zenkai tech dump [ki|physical|all]
 *
 * Existe para acortar el ciclo de balance: cambiar un número, verlo aplicado en el mismo tick
 * al conjunto de conectados, y volcarlo a JSON cuando el número es el bueno.
 *
 * VIVE FUERA DE ModCommands a propósito y se cuelga del mismo literal {@code /zenkai}:
 * Brigadier fusiona los hijos cuando dos registros comparten raíz (precedente:
 * ZenkaiArmCalibrationCommand). El requisito de permiso se repite aquí porque el nodo raíz que
 * gana la fusión es el primero en registrarse, y no puedo depender de ese orden.
 *
 * NO PARSEA NI VALIDA NADA POR SU CUENTA: los tipos, rangos y defaults son de
 * {@link TechniqueField}, y la composición de capas es de {@link TechniqueManager}.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class TechniqueCommands {
    private TechniqueCommands() {}

    private static final String ALL = "all";

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("zenkai")
                .requires(cs -> cs.hasPermission(2))
                .then(tech()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tech() {
        return Commands.literal("tech")
                .then(Commands.literal("dump")
                        .executes(ctx -> dump(ctx, Set.of(Kind.KI, Kind.PHYSICAL)))
                        .then(Commands.argument("scope", StringArgumentType.word())
                                .suggests(SCOPES)
                                .executes(ctx -> {
                                    String raw = StringArgumentType.getString(ctx, "scope");
                                    Set<Kind> kinds = TechniqueDump.scope(raw);
                                    if (kinds == null) return fail(ctx, "ámbito desconocido: " + raw
                                            + " (ki|physical|all)");
                                    return dump(ctx, kinds);
                                })))
                .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
                                .then(Commands.literal("info")
                                        .executes(TechniqueCommands::info))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("field", StringArgumentType.word())
                                                .suggests(FIELDS)
                                                .then(Commands.argument("value", StringArgumentType.string())
                                                        .suggests(CURRENT_VALUE)
                                                        .executes(TechniqueCommands::set))))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("field", StringArgumentType.word())
                                                .suggests(FIELDS_OR_ALL)
                                                .executes(TechniqueCommands::reset)))));
    }

    // ── info ─────────────────────────────────────────────────────────────────

    private static int info(CommandContext<CommandSourceStack> ctx) {
        Kind kind = kind(ctx);
        if (kind == null) return fail(ctx, "kind desconocido (ki|physical)");
        String id = id(ctx);
        if (!TechniqueManager.exists(kind, id)) return fail(ctx, "técnica desconocida: " + kind.folder() + "/" + id);

        MinecraftServer server = ctx.getSource().getServer();
        TechniqueOverrides ov = TechniqueOverrides.get(server);
        Map<TechniqueField, Object> overrides = ov.values(kind, id);
        TechniqueManager.Base base = TechniqueManager.base(kind, id);
        TechniqueDef eff = TechniqueDef.get(kind, id);

        final String head = "§6=== " + kind.folder() + "/" + id + " ===  "
                + (eff != null ? "§aactiva" : "§cdesactivada (sin JSON ni overrides)")
                + (overrides.isEmpty() ? "" : "  §e" + overrides.size() + " override(s)");
        ctx.getSource().sendSuccess(() -> Component.literal(head), false);

        for (TechniqueField f : TechniqueField.of(kind)) {
            final Object value = eff != null ? f.get(eff) : f.factoryDefault();
            final boolean over = overrides.containsKey(f);
            final boolean fromPack = base != null && base.declared().contains(f);

            final String origin = over ? "§e[override]" : (fromPack ? "§a[datapack]" : "§8[default]");
            final String line = "§f  " + f.key() + " §7= §b" + f.format(value) + "  " + origin;
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);

            // Solo se detallan las capas de debajo cuando hay alguna: en un campo que es puro
            // default no hay nada que contar y la lista se vuelve ilegible.
            if (over || fromPack) {
                StringBuilder sb = new StringBuilder("§8      ");
                if (over && fromPack) {
                    sb.append("datapack: ").append(f.format(f.get(base.def()))).append(" · ");
                } else if (over) {
                    sb.append("datapack: (no lo declara) · ");
                }
                sb.append("fábrica: ").append(f.format(f.factoryDefault()));
                final String sub = sb.toString();
                ctx.getSource().sendSuccess(() -> Component.literal(sub), false);
            }
        }
        return 1;
    }

    // ── set ──────────────────────────────────────────────────────────────────

    private static int set(CommandContext<CommandSourceStack> ctx) {
        Kind kind = kind(ctx);
        if (kind == null) return fail(ctx, "kind desconocido (ki|physical)");
        String id = id(ctx);
        if (!TechniqueManager.exists(kind, id)) return fail(ctx, "técnica desconocida: " + kind.folder() + "/" + id);

        String fieldRaw = StringArgumentType.getString(ctx, "field");
        TechniqueField f = TechniqueField.byKey(fieldRaw);
        if (f == null || !f.applies(kind)) return fail(ctx, "campo desconocido para "
                + kind.folder() + ": " + fieldRaw + " (" + fieldNames(kind) + ")");

        String valueRaw = StringArgumentType.getString(ctx, "value");
        Object parsed;
        try {
            parsed = f.parse(valueRaw);
        } catch (IllegalArgumentException ex) {
            return fail(ctx, "valor inválido para " + f.key() + " (" + f.type() + "): " + valueRaw);
        }

        MinecraftServer server = ctx.getSource().getServer();
        TechniqueOverrides ov = TechniqueOverrides.get(server);
        TechniqueDef before = TechniqueDef.get(kind, id);
        final boolean wasDisabled = before == null;
        final String old = f.format(before != null ? f.get(before) : f.factoryDefault());

        Object stored = ov.set(kind, id, f, parsed);
        TechniqueManager.rebuildAndSync(server);

        final String line = "§6[Zenkai] §f" + kind.folder() + "/" + id + " §7" + f.key()
                + ": §8" + old + " §7→ §b" + f.format(stored) + " §e[override]";
        ctx.getSource().sendSuccess(() -> Component.literal(line), true);

        if (!f.format(stored).equals(f.format(parsed))) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e[Zenkai] El valor se ajustó al rango legal del campo."), false);
        }
        if (wasDisabled) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a[Zenkai] La técnica no tenía JSON: queda ACTIVADA con los valores de "
                            + "fábrica más este override."), true);
        }
        return 1;
    }

    // ── reset ────────────────────────────────────────────────────────────────

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        Kind kind = kind(ctx);
        if (kind == null) return fail(ctx, "kind desconocido (ki|physical)");
        String id = id(ctx);
        if (!TechniqueManager.exists(kind, id)) return fail(ctx, "técnica desconocida: " + kind.folder() + "/" + id);

        String fieldRaw = StringArgumentType.getString(ctx, "field");
        MinecraftServer server = ctx.getSource().getServer();
        TechniqueOverrides ov = TechniqueOverrides.get(server);

        if (fieldRaw.equalsIgnoreCase(ALL)) {
            int n = ov.clearAll(kind, id);
            if (n == 0) return fail(ctx, kind.folder() + "/" + id + " no tenía overrides.");
            TechniqueManager.rebuildAndSync(server);
            final String line = "§6[Zenkai] §f" + kind.folder() + "/" + id
                    + " §7— " + n + " override(s) descartado(s).";
            ctx.getSource().sendSuccess(() -> Component.literal(line), true);
            warnIfDisabled(ctx, kind, id);
            return 1;
        }

        TechniqueField f = TechniqueField.byKey(fieldRaw);
        if (f == null || !f.applies(kind)) return fail(ctx, "campo desconocido para "
                + kind.folder() + ": " + fieldRaw + " (" + fieldNames(kind) + "|all)");
        if (!ov.clear(kind, id, f)) return fail(ctx, f.key() + " no tenía override en "
                + kind.folder() + "/" + id + ".");

        TechniqueManager.rebuildAndSync(server);
        TechniqueDef after = TechniqueDef.get(kind, id);
        TechniqueManager.Base base = TechniqueManager.base(kind, id);
        final String value = f.format(after != null ? f.get(after) : f.factoryDefault());
        final String origin = (base != null && base.declared().contains(f)) ? "datapack" : "fábrica";
        final String line = "§6[Zenkai] §f" + kind.folder() + "/" + id + " §7" + f.key()
                + " §7→ §b" + value + " §8(" + origin + ")";
        ctx.getSource().sendSuccess(() -> Component.literal(line), true);
        warnIfDisabled(ctx, kind, id);
        return 1;
    }

    /** Un reset puede dejar sin def a una técnica que solo existía por overrides. Eso la
     *  desactiva en cualquier parte, así que no puede pasar en silencio. */
    private static void warnIfDisabled(CommandContext<CommandSourceStack> ctx, Kind kind, String id) {
        if (TechniqueDef.get(kind, id) == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§c[Zenkai] " + kind.folder() + "/" + id + " se queda SIN JSON: DESACTIVADA "
                            + "(no se puede desbloquear, guardar ni disparar)."), true);
        }
    }

    // ── dump ─────────────────────────────────────────────────────────────────

    private static int dump(CommandContext<CommandSourceStack> ctx, Set<Kind> kinds) {
        MinecraftServer server = ctx.getSource().getServer();
        TechniqueDump.Result r = TechniqueDump.run(server, kinds);

        if (r.error() != null) {
            return fail(ctx, "el volcado falló tras " + r.written() + " archivo(s): " + r.error());
        }
        if (r.written() == 0) {
            return fail(ctx, "no hay overrides que volcar en ese ámbito.");
        }

        final String path = TechniqueDump.prettyPath(server, r.packRoot());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6[Zenkai] §f" + r.written() + " técnica(s) volcada(s) a §b" + path), true);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7Actívalo con §f/datapack enable \"file/" + TechniqueDump.PACK_NAME
                        + "\"§7 y luego §f/reload§7. Los overrides se conservan."), false);
        if (r.mirror() != null && r.mirrorError() == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§8Copia de desarrollo en " + r.mirror()), false);
        } else if (r.mirrorError() != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e[Zenkai] La copia de desarrollo falló: " + r.mirrorError()), false);
        }
        return r.written();
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private static Kind kind(CommandContext<CommandSourceStack> ctx) {
        return Kind.byFolder(StringArgumentType.getString(ctx, "kind").toLowerCase(Locale.ROOT));
    }

    private static String id(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
    }

    private static String fieldNames(Kind kind) {
        List<String> names = new ArrayList<>();
        for (TechniqueField f : TechniqueField.of(kind)) names.add(f.key());
        return String.join("|", names);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal("[Zenkai] " + msg));
        return 0;
    }

    // ── Autocompletado ───────────────────────────────────────────────────────

    /** Los argumentos anteriores pueden no estar parseados todavía mientras se escribe. */
    private static Kind kindOrNull(CommandContext<CommandSourceStack> ctx) {
        try {
            return Kind.byFolder(ctx.getArgument("kind", String.class).toLowerCase(Locale.ROOT));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String idOrNull(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getArgument("id", String.class).toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return null;
        }
    }

    private static final SuggestionProvider<CommandSourceStack> KINDS =
            (ctx, b) -> SharedSuggestionProvider.suggest(List.of("ki", "physical"), b);

    private static final SuggestionProvider<CommandSourceStack> SCOPES =
            (ctx, b) -> SharedSuggestionProvider.suggest(TechniqueDump.scopes(), b);

    private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, b) -> {
        Kind kind = kindOrNull(ctx);
        List<String> ids = new ArrayList<>();
        if (kind == null || kind == Kind.KI) {
            for (KiTechniqueType t : KiTechniqueType.values()) ids.add(t.id());
        }
        if (kind == null || kind == Kind.PHYSICAL) {
            for (PhysicalTechnique t : PhysicalTechnique.values()) ids.add(t.id());
        }
        return SharedSuggestionProvider.suggest(ids, b);
    };

    private static final SuggestionProvider<CommandSourceStack> FIELDS = (ctx, b) -> {
        Kind kind = kindOrNull(ctx);
        List<String> names = new ArrayList<>();
        for (TechniqueField f : TechniqueField.values()) {
            if (kind == null || f.applies(kind)) names.add(f.key());
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static final SuggestionProvider<CommandSourceStack> FIELDS_OR_ALL = (ctx, b) -> {
        Kind kind = kindOrNull(ctx);
        List<String> names = new ArrayList<>();
        names.add(ALL);
        for (TechniqueField f : TechniqueField.values()) {
            if (kind == null || f.applies(kind)) names.add(f.key());
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    /** Sugiere el valor efectivo actual: escribir un número encima de lo que hay es más rápido
     *  que consultar el info antes de cada ajuste. */
    private static final SuggestionProvider<CommandSourceStack> CURRENT_VALUE = (ctx, b) -> {
        Kind kind = kindOrNull(ctx);
        String id = idOrNull(ctx);
        if (kind == null || id == null) return b.buildFuture();
        TechniqueField f;
        try {
            f = TechniqueField.byKey(ctx.getArgument("field", String.class));
        } catch (Exception ex) {
            return b.buildFuture();
        }
        if (f == null || !f.applies(kind)) return b.buildFuture();
        TechniqueDef def = TechniqueDef.get(kind, id);
        Object v = def != null ? f.get(def) : f.factoryDefault();
        return SharedSuggestionProvider.suggest(List.of(f.format(v)), b);
    };
}
