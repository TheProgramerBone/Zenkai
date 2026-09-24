package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /zkvfx} — calibración EN VIVO del VFX de ki (ver {@link KiVfxTuning}). Solo cliente,
 * mismo patrón que {@code AuraFlameDebugCommand}: es un ajuste visual local, no necesita servidor.
 * <pre>
 * /zkvfx list                          valores actuales y ayuda de cada parámetro
 * /zkvfx set &lt;param&gt; &lt;valor&gt;             global
 * /zkvfx set &lt;param&gt; &lt;valor&gt; &lt;técnica&gt;   solo esa técnica (si el parámetro lo admite)
 * /zkvfx reset [técnica]               vuelve a fábrica (todo, o los overrides de una técnica)
 * /zkvfx dump                          lo cambiado, en chat Y en latest.log
 * </pre>
 * El dump va también al log a propósito: así el asistente puede leer los valores calibrados
 * directamente de {@code run/logs/latest.log} sin que el usuario tenga que copiarlos a mano.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiVfxTuningCommand {
    private KiVfxTuningCommand() {}

    private static final SuggestionProvider<CommandSourceStack> PARAMS = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(KiVfxTuning.Param.values()).map(p -> p.key), b);

    private static final SuggestionProvider<CommandSourceStack> TECHNIQUES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(KiTechniqueType.values())
                    .map(t -> t.name().toLowerCase(Locale.ROOT)), b);

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zkvfx")
                .then(Commands.literal("list").executes(KiVfxTuningCommand::list))
                .then(Commands.literal("dump").executes(KiVfxTuningCommand::dump))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            KiVfxTuning.resetAll();
                            return say(ctx, "Todo a valores de fábrica.");
                        })
                        .then(Commands.argument("technique", StringArgumentType.word()).suggests(TECHNIQUES)
                                .executes(ctx -> {
                                    KiTechniqueType t = technique(ctx);
                                    if (t == null) return 0;
                                    KiVfxTuning.reset(t);
                                    return say(ctx, "Overrides de " + t.name().toLowerCase(Locale.ROOT) + " borrados.");
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("param", StringArgumentType.word()).suggests(PARAMS)
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 10f))
                                        .executes(ctx -> set(ctx, false))
                                        .then(Commands.argument("technique", StringArgumentType.word())
                                                .suggests(TECHNIQUES)
                                                .executes(ctx -> set(ctx, true)))))));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean perTechnique) {
        KiVfxTuning.Param p = KiVfxTuning.Param.byKey(StringArgumentType.getString(ctx, "param"));
        if (p == null) return fail(ctx, "Parámetro desconocido. /zkvfx list");
        float v = FloatArgumentType.getFloat(ctx, "value");
        if (!perTechnique) {
            KiVfxTuning.setGlobal(p, v);
            return say(ctx, p.key + " = " + v + " (global)");
        }
        if (!p.perTechnique) return fail(ctx, p.key + " es solo global.");
        KiTechniqueType t = technique(ctx);
        if (t == null) return 0;
        KiVfxTuning.setFor(t, p, v);
        return say(ctx, p.key + "[" + t.name().toLowerCase(Locale.ROOT) + "] = " + v);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        for (KiVfxTuning.Param p : KiVfxTuning.Param.values()) {
            float v = KiVfxTuning.get(p, (KiTechniqueType) null);
            String mark = v != p.def ? " *" : "";
            say(ctx, String.format(Locale.ROOT, "%s = %.3f%s%s — %s", p.key, v, mark,
                    p.perTechnique ? " [por técnica]" : "", p.help));
        }
        return 1;
    }

    private static int dump(CommandContext<CommandSourceStack> ctx) {
        String d = KiVfxTuning.dump();
        Zenkai.LOGGER.info("[Zenkai KiVfxTuning] dump:\n{}", d);
        for (String line : d.split("\n")) say(ctx, line);
        return 1;
    }

    private static KiTechniqueType technique(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "technique");
        for (KiTechniqueType t : KiTechniqueType.values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        fail(ctx, "Técnica desconocida: " + name);
        return null;
    }

    private static int say(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("[zkvfx] " + msg), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal("[zkvfx] " + msg));
        return 0;
    }
}
