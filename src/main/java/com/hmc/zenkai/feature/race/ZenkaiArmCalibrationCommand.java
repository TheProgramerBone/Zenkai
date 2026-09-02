package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.Zenkai;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Comando SOLO-CLIENTE para calibrar el brazo en 1a persona sin recompilar.
 * Modifica en runtime los campos publicos de {@link ZenkaiFirstPersonArmHooks} y reporta en chat.
 * Dos raíces GEMELAS, nombre explícito de a qué brazo afecta cada una (para que nadie las
 * confunda a media calibración), misma sintaxis, cada una sobre su propio set de campos
 * (derecho original / izquierdo espejo — ver el comentario de esos campos en
 * {@link ZenkaiFirstPersonArmHooks}):
 *   /zenkaiarmright  get|pos|rot|scale|nudge  -> brazo DERECHO (OFF_x, ROT_x, SCALE)
 *   /zenkaiarmleft   get|pos|rot|scale|nudge  -> brazo IZQUIERDO (LEFT_*)
 *   /zenkaiarmright reset / /zenkaiarmleft reset -> vuelve ESE brazo a su calibración de
 *                                                 fábrica ({@code ORIGINAL_*}/{@code
 *                                                 ORIGINAL_LEFT_*} en {@link
 *                                                 ZenkaiFirstPersonArmHooks}), por si alguien
 *                                                 anda calibrando y pierde la cuenta de por
 *                                                 dónde iba
 * Flujo tipico de calibracion:
 *   /zenkaiarmright nudge z -0.1   (repite hasta acercar)
 *   /zenkaiarmright nudge y 0.1    (sube/baja)
 *   /zenkaiarmright nudge ry 15    (gira si apunta mal)
 *   /zenkaiarmright get            (lee los valores finales para pegarlos al codigo)
 * NOTA GeckoLib/NeoForge no verificable offline:
 *   - RegisterClientCommandsEvent se dispara en el game bus (no el mod bus).
 *   - CommandSourceStack#sendSuccess(Supplier<Component>, boolean) en 1.21.1.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiArmCalibrationCommand {

    private ZenkaiArmCalibrationCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(build("zenkaiarmright", false));
        e.getDispatcher().register(build("zenkaiarmleft", true));
    }

    /** Construye el árbol de comandos para un brazo (derecho o izquierdo). */
    private static LiteralArgumentBuilder<CommandSourceStack> build(String rootName, boolean left) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(rootName)
                .then(Commands.literal("get")
                        .executes(ctx -> report(ctx.getSource(), left)))
                .then(Commands.literal("pos")
                        .then(Commands.argument("x", FloatArgumentType.floatArg())
                                .then(Commands.argument("y", FloatArgumentType.floatArg())
                                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                .executes(ctx -> {
                                                    setPos(left,
                                                            FloatArgumentType.getFloat(ctx, "x"),
                                                            FloatArgumentType.getFloat(ctx, "y"),
                                                            FloatArgumentType.getFloat(ctx, "z"));
                                                    return report(ctx.getSource(), left);
                                                })))))
                .then(Commands.literal("rot")
                        .then(Commands.argument("x", FloatArgumentType.floatArg())
                                .then(Commands.argument("y", FloatArgumentType.floatArg())
                                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                .executes(ctx -> {
                                                    setRot(left,
                                                            FloatArgumentType.getFloat(ctx, "x"),
                                                            FloatArgumentType.getFloat(ctx, "y"),
                                                            FloatArgumentType.getFloat(ctx, "z"));
                                                    return report(ctx.getSource(), left);
                                                })))))
                .then(Commands.literal("scale")
                        .then(Commands.argument("s", FloatArgumentType.floatArg())
                                .executes(ctx -> {
                                    setScale(left, FloatArgumentType.getFloat(ctx, "s"));
                                    return report(ctx.getSource(), left);
                                })))
                .then(Commands.literal("nudge")
                        .then(nudgeAxis("x", left))
                        .then(nudgeAxis("y", left))
                        .then(nudgeAxis("z", left))
                        .then(nudgeAxis("rx", left))
                        .then(nudgeAxis("ry", left))
                        .then(nudgeAxis("rz", left))
                        .then(nudgeAxis("s", left)))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            if (left) ZenkaiFirstPersonArmHooks.resetLeft();
                            else ZenkaiFirstPersonArmHooks.resetRight();
                            return report(ctx.getSource(), left);
                        }));
        return root;
    }

    /** Construye un sub-literal "nudge <eje> <delta>" para el brazo indicado. */
    private static LiteralArgumentBuilder<CommandSourceStack> nudgeAxis(String axis, boolean left) {
        return Commands.literal(axis)
                .then(Commands.argument("delta", FloatArgumentType.floatArg())
                        .executes(ctx -> nudge(left, axis, FloatArgumentType.getFloat(ctx, "delta"), ctx.getSource())));
    }

    private static void setPos(boolean left, float x, float y, float z) {
        if (left) {
            ZenkaiFirstPersonArmHooks.LEFT_OFF_X = x;
            ZenkaiFirstPersonArmHooks.LEFT_OFF_Y = y;
            ZenkaiFirstPersonArmHooks.LEFT_OFF_Z = z;
        } else {
            ZenkaiFirstPersonArmHooks.OFF_X = x;
            ZenkaiFirstPersonArmHooks.OFF_Y = y;
            ZenkaiFirstPersonArmHooks.OFF_Z = z;
        }
    }

    private static void setRot(boolean left, float x, float y, float z) {
        if (left) {
            ZenkaiFirstPersonArmHooks.LEFT_ROT_X = x;
            ZenkaiFirstPersonArmHooks.LEFT_ROT_Y = y;
            ZenkaiFirstPersonArmHooks.LEFT_ROT_Z = z;
        } else {
            ZenkaiFirstPersonArmHooks.ROT_X = x;
            ZenkaiFirstPersonArmHooks.ROT_Y = y;
            ZenkaiFirstPersonArmHooks.ROT_Z = z;
        }
    }

    private static void setScale(boolean left, float s) {
        if (left) ZenkaiFirstPersonArmHooks.LEFT_SCALE = s;
        else ZenkaiFirstPersonArmHooks.SCALE = s;
    }

    /** Suma delta al eje indicado, en el set de campos del brazo indicado. */
    private static int nudge(boolean left, String axis, float delta, CommandSourceStack src) {
        switch (axis) {
            case "x" -> { if (left) ZenkaiFirstPersonArmHooks.LEFT_OFF_X += delta; else ZenkaiFirstPersonArmHooks.OFF_X += delta; }
            case "y" -> { if (left) ZenkaiFirstPersonArmHooks.LEFT_OFF_Y += delta; else ZenkaiFirstPersonArmHooks.OFF_Y += delta; }
            case "z" -> { if (left) ZenkaiFirstPersonArmHooks.LEFT_OFF_Z += delta; else ZenkaiFirstPersonArmHooks.OFF_Z += delta; }
            case "rx" -> { if (left) ZenkaiFirstPersonArmHooks.LEFT_ROT_X += delta; else ZenkaiFirstPersonArmHooks.ROT_X += delta; }
            case "ry" -> { if (left) ZenkaiFirstPersonArmHooks.LEFT_ROT_Y += delta; else ZenkaiFirstPersonArmHooks.ROT_Y += delta; }
            case "rz" -> { if (left) ZenkaiFirstPersonArmHooks.LEFT_ROT_Z += delta; else ZenkaiFirstPersonArmHooks.ROT_Z += delta; }
            case "s" -> { if (left) ZenkaiFirstPersonArmHooks.LEFT_SCALE += delta; else ZenkaiFirstPersonArmHooks.SCALE += delta; }
            default -> {
                src.sendFailure(Component.literal("Eje no valido: " + axis));
                return 0;
            }
        }
        return report(src, left);
    }

    private static int report(CommandSourceStack src, boolean left) {
        String tag = left ? "L" : "R";
        String msg = left
                ? String.format(
                        "[ZenkaiArm][%s] pos=(%.2f, %.2f, %.2f)  rot=(%.1f, %.1f, %.1f)  scale=%.2f",
                        tag,
                        ZenkaiFirstPersonArmHooks.LEFT_OFF_X, ZenkaiFirstPersonArmHooks.LEFT_OFF_Y, ZenkaiFirstPersonArmHooks.LEFT_OFF_Z,
                        ZenkaiFirstPersonArmHooks.LEFT_ROT_X, ZenkaiFirstPersonArmHooks.LEFT_ROT_Y, ZenkaiFirstPersonArmHooks.LEFT_ROT_Z,
                        ZenkaiFirstPersonArmHooks.LEFT_SCALE)
                : String.format(
                        "[ZenkaiArm][%s] pos=(%.2f, %.2f, %.2f)  rot=(%.1f, %.1f, %.1f)  scale=%.2f",
                        tag,
                        ZenkaiFirstPersonArmHooks.OFF_X, ZenkaiFirstPersonArmHooks.OFF_Y, ZenkaiFirstPersonArmHooks.OFF_Z,
                        ZenkaiFirstPersonArmHooks.ROT_X, ZenkaiFirstPersonArmHooks.ROT_Y, ZenkaiFirstPersonArmHooks.ROT_Z,
                        ZenkaiFirstPersonArmHooks.SCALE);
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
