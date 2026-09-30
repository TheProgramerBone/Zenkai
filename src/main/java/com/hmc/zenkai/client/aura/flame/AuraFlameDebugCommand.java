package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.Zenkai;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Comando SOLO-CLIENTE (mismo patrón que {@code ZenkaiArmCalibrationCommand}) para elegir en
 * caliente qué sistema de aura dibuja el cuerpo, sin recompilar. Deliberadamente client-only:
 * es un interruptor de calibración visual puramente local, no necesita ida y vuelta al servidor.
 * Pone el override de sesión de {@link AuraSystemDispatcher}, solo en memoria: la opción
 * persistente es {@code aura.system} de {@code ClientConfig} (Fase 5 del plan, ver
 * .claude/docs/tecnicas-y-vfx.md, "Aura nueva estilo dbrebirth").
 *
 * {@code /zenkaiauraflame <auto|legacy|new>} — override de sesión.
 * {@code /zenkaiauraflame config} — quita el override y vuelve a mandar la config.
 * {@code /zenkaiauraflame set <param> <valor>} / {@code reset} / {@code list} — calibración de la
 * llama exterior ({@link AuraFlameTuning}).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraFlameDebugCommand {

    private AuraFlameDebugCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("zenkaiauraflame")
                        .then(Commands.literal("auto").executes(ctx ->
                                set(ctx.getSource(), AuraSystemMode.AUTO)))
                        .then(Commands.literal("legacy").executes(ctx ->
                                set(ctx.getSource(), AuraSystemMode.FORCE_LEGACY)))
                        .then(Commands.literal("new").executes(ctx ->
                                set(ctx.getSource(), AuraSystemMode.FORCE_NEW)))
                        // Quita el override: vuelve a mandar la opción aura.system de ClientConfig.
                        .then(Commands.literal("config").executes(ctx ->
                                set(ctx.getSource(), null)))
                        // Calibración en caliente de la llama exterior (AuraFlameTuning), solo en
                        // memoria, mismo espíritu que /zkvfx.
                        .then(Commands.literal("set")
                                .then(Commands.argument("param", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(AuraFlameTuning.Param.values()).map(p -> p.key), b))
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(-20f, 64f))
                                                .executes(ctx -> tune(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "param"),
                                                        FloatArgumentType.getFloat(ctx, "value"))))))
                        .then(Commands.literal("reset").executes(ctx -> {
                            AuraFlameTuning.reset();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[AuraFlame] Parámetros de la llama devueltos a sus valores por defecto"), false);
                            return 1;
                        }))
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource()))));
    }

    private static int tune(CommandSourceStack src, String key, float value) {
        AuraFlameTuning.Param p = AuraFlameTuning.Param.byKey(key);
        if (p == null) {
            src.sendFailure(Component.literal("[AuraFlame] Parámetro desconocido: " + key));
            return 0;
        }
        AuraFlameTuning.set(p, value);
        src.sendSuccess(() -> Component.literal("[AuraFlame] " + p.key + " = " + value
                + " (defecto " + p.def + ")"), false);
        return 1;
    }

    private static int list(CommandSourceStack src) {
        StringBuilder sb = new StringBuilder("[AuraFlame] Parámetros de la llama:");
        for (AuraFlameTuning.Param p : AuraFlameTuning.Param.values()) {
            sb.append("\n  ").append(p.key).append(" = ").append(AuraFlameTuning.get(p));
            if (AuraFlameTuning.isOverridden(p)) sb.append("  (defecto ").append(p.def).append(')');
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        // También al log, para copiar los valores buenos de vuelta al código.
        Zenkai.LOGGER.info(text);
        return 1;
    }

    /** @param mode override de sesión, o null para volver a seguir la config. */
    private static int set(CommandSourceStack src, @Nullable AuraSystemMode mode) {
        AuraSystemDispatcher.setSessionOverride(mode);
        AuraSystemMode effective = AuraSystemDispatcher.effectiveMode();
        boolean flame = AuraSystemDispatcher.useFlame();
        String origin = mode == null ? "config" : "override de sesión";
        String extra = effective == AuraSystemMode.FORCE_NEW && !flame
                ? " (los shaders del aura nueva no compilaron: se sigue usando el legacy)" : "";
        src.sendSuccess(() -> Component.literal("[AuraFlame] Modo " + effective.name() + " (" + origin
                + ") -> sistema " + (flame ? "NUEVO" : "legacy") + extra), false);
        return 1;
    }
}
