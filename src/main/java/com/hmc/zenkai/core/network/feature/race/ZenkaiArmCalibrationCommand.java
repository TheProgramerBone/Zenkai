package com.hmc.zenkai.core.network.feature.race;

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
 *   /zenkaiarm get
 *   /zenkaiarm pos  <x> <y> <z>     -> fija OFF_X/Y/Z (bloques)
 *   /zenkaiarm rot  <x> <y> <z>     -> fija ROT_X/Y/Z (grados)
 *   /zenkaiarm scale <s>            -> fija SCALE
 *   /zenkaiarm nudge <eje> <delta>  -> suma delta a un eje. eje: x y z rx ry rz s
 * Flujo tipico de calibracion:
 *   /zenkaiarm nudge z -0.1   (repite hasta acercar)
 *   /zenkaiarm nudge y 0.1    (sube/baja)
 *   /zenkaiarm nudge ry 15    (gira si apunta mal)
 *   /zenkaiarm get            (lee los valores finales para pegarlos al codigo)
 * NOTA GeckoLib/NeoForge no verificable offline:
 *   - RegisterClientCommandsEvent se dispara en el game bus (no el mod bus).
 *   - CommandSourceStack#sendSuccess(Supplier<Component>, boolean) en 1.21.1.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiArmCalibrationCommand {

    private ZenkaiArmCalibrationCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(
                Commands.literal("zenkaiarm")
                        .then(Commands.literal("get")
                                .executes(ctx -> report(ctx.getSource())))
                        .then(Commands.literal("pos")
                                .then(Commands.argument("x", FloatArgumentType.floatArg())
                                        .then(Commands.argument("y", FloatArgumentType.floatArg())
                                                .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                        .executes(ctx -> {
                                                            ZenkaiFirstPersonArmHooks.OFF_X = FloatArgumentType.getFloat(ctx, "x");
                                                            ZenkaiFirstPersonArmHooks.OFF_Y = FloatArgumentType.getFloat(ctx, "y");
                                                            ZenkaiFirstPersonArmHooks.OFF_Z = FloatArgumentType.getFloat(ctx, "z");
                                                            return report(ctx.getSource());
                                                        })))))
                        .then(Commands.literal("rot")
                                .then(Commands.argument("x", FloatArgumentType.floatArg())
                                        .then(Commands.argument("y", FloatArgumentType.floatArg())
                                                .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                        .executes(ctx -> {
                                                            ZenkaiFirstPersonArmHooks.ROT_X = FloatArgumentType.getFloat(ctx, "x");
                                                            ZenkaiFirstPersonArmHooks.ROT_Y = FloatArgumentType.getFloat(ctx, "y");
                                                            ZenkaiFirstPersonArmHooks.ROT_Z = FloatArgumentType.getFloat(ctx, "z");
                                                            return report(ctx.getSource());
                                                        })))))
                        .then(Commands.literal("scale")
                                .then(Commands.argument("s", FloatArgumentType.floatArg())
                                        .executes(ctx -> {
                                            ZenkaiFirstPersonArmHooks.SCALE = FloatArgumentType.getFloat(ctx, "s");
                                            return report(ctx.getSource());
                                        })))
                        .then(Commands.literal("nudge")
                                .then(nudgeAxis("x"))
                                .then(nudgeAxis("y"))
                                .then(nudgeAxis("z"))
                                .then(nudgeAxis("rx"))
                                .then(nudgeAxis("ry"))
                                .then(nudgeAxis("rz"))
                                .then(nudgeAxis("s")))
        );
    }

    /** Construye un sub-literal "nudge <eje> <delta>". */
    private static LiteralArgumentBuilder<CommandSourceStack> nudgeAxis(String axis) {
        return Commands.literal(axis)
                .then(Commands.argument("delta", FloatArgumentType.floatArg())
                        .executes(ctx -> nudge(axis, FloatArgumentType.getFloat(ctx, "delta"), ctx.getSource())));
    }

    /** Suma delta al eje indicado. */
    private static int nudge(String axis, float delta, CommandSourceStack src) {
        switch (axis) {
            case "x"  -> ZenkaiFirstPersonArmHooks.OFF_X += delta;
            case "y"  -> ZenkaiFirstPersonArmHooks.OFF_Y += delta;
            case "z"  -> ZenkaiFirstPersonArmHooks.OFF_Z += delta;
            case "rx" -> ZenkaiFirstPersonArmHooks.ROT_X += delta;
            case "ry" -> ZenkaiFirstPersonArmHooks.ROT_Y += delta;
            case "rz" -> ZenkaiFirstPersonArmHooks.ROT_Z += delta;
            case "s"  -> ZenkaiFirstPersonArmHooks.SCALE += delta;
            default   -> {
                src.sendFailure(Component.literal("Eje no valido: " + axis));
                return 0;
            }
        }
        return report(src);
    }

    private static int report(CommandSourceStack src) {
        String msg = String.format(
                "[ZenkaiArm] pos=(%.2f, %.2f, %.2f)  rot=(%.1f, %.1f, %.1f)  scale=%.2f",
                ZenkaiFirstPersonArmHooks.OFF_X, ZenkaiFirstPersonArmHooks.OFF_Y, ZenkaiFirstPersonArmHooks.OFF_Z,
                ZenkaiFirstPersonArmHooks.ROT_X, ZenkaiFirstPersonArmHooks.ROT_Y, ZenkaiFirstPersonArmHooks.ROT_Z,
                ZenkaiFirstPersonArmHooks.SCALE);
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}