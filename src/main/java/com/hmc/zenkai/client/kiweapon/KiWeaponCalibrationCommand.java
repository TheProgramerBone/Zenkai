package com.hmc.zenkai.client.kiweapon;

import com.hmc.zenkai.Zenkai;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Comando SOLO-CLIENTE para calibrar el arma de ki sin recompilar. Hay CUATRO juegos de valores
 * independientes (vista × brazo) (ver {@link KiWeaponCalibration}) y {@code mode} elige cuál editan
 * set/get/reset. Los valores son ABSOLUTOS (cada {@code set} fija el valor, no lo suma):
 *   /zenkaiweapon arm right|left                (qué brazo: son juegos distintos; el izquierdo es para
 *                                                zurdos y para la mano secundaria futura)
 *   /zenkaiweapon mode fp|tp                    (fp = mano de 1ª persona, tp = 3ª persona y cuerpo
 *                                                entero de PAL; por defecto tp)
 *   /zenkaiweapon set x|y|z|rx|ry|rz|s <valor>   (x y z desplazan en bloques, rx ry rz giran en
 *                                                 grados, s escala)
 *   /zenkaiweapon axes                           (muestra u oculta los ejes de la pieza)
 *   /zenkaiweapon get | reset
 * EJES (en el marco de la pieza, ya con la calibración aplicada): X rojo = costado, Y verde = hacia
 * donde apunta la hoja, Z azul = arriba cuando el brazo cuelga con la hoja al frente. Los giros se
 * aplican en el orden X, Y, Z.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class KiWeaponCalibrationCommand {
    private KiWeaponCalibrationCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent e) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("zenkaiweapon")
                .then(Commands.literal("get").executes(ctx -> report(ctx.getSource())))
                .then(Commands.literal("reset").executes(ctx -> {
                    KiWeaponCalibration.editing().reset();
                    return report(ctx.getSource());
                }))
                .then(Commands.literal("mode")
                        .then(Commands.literal("fp").executes(ctx -> {
                            KiWeaponCalibration.editingFirst = true;
                            return report(ctx.getSource());
                        }))
                        .then(Commands.literal("tp").executes(ctx -> {
                            KiWeaponCalibration.editingFirst = false;
                            return report(ctx.getSource());
                        })))
                .then(Commands.literal("arm")
                        .then(Commands.literal("right").executes(ctx -> {
                            KiWeaponCalibration.editingArm = HumanoidArm.RIGHT;
                            return report(ctx.getSource());
                        }))
                        .then(Commands.literal("left").executes(ctx -> {
                            KiWeaponCalibration.editingArm = HumanoidArm.LEFT;
                            return report(ctx.getSource());
                        })))
                .then(Commands.literal("tilt")
                        .then(Commands.argument("deg", FloatArgumentType.floatArg(-90F, 90F)).executes(ctx -> {
                            KiWeaponVfx.tiltDegrees = FloatArgumentType.getFloat(ctx, "deg");
                            return reportShape(ctx.getSource());
                        })))
                .then(Commands.literal("fpscale")
                        .then(Commands.argument("v", FloatArgumentType.floatArg(0.1F, 2F)).executes(ctx -> {
                            KiWeaponVfx.firstPersonScale = FloatArgumentType.getFloat(ctx, "v");
                            return reportShape(ctx.getSource());
                        })))
                .then(Commands.literal("axes").executes(ctx -> {
                    KiWeaponVfx.showAxes = !KiWeaponVfx.showAxes;
                    ctx.getSource().sendSuccess(() -> Component.literal(KiWeaponVfx.showAxes
                            ? "[ZenkaiWeapon] Ejes ON: X rojo = costado, Y verde = hacia donde apunta la hoja, "
                                    + "Z azul = arriba con el brazo colgando."
                            : "[ZenkaiWeapon] Ejes OFF."), false);
                    return 1;
                }));
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        for (String axis : new String[]{"x", "y", "z", "rx", "ry", "rz", "s"}) {
            set.then(Commands.literal(axis).then(Commands.argument("value", FloatArgumentType.floatArg())
                    .executes(ctx -> {
                        float v = FloatArgumentType.getFloat(ctx, "value");
                        KiWeaponCalibration c = KiWeaponCalibration.editing();
                        switch (axis) {
                            case "x" -> c.x = v;
                            case "y" -> c.y = v;
                            case "z" -> c.z = v;
                            case "rx" -> c.rx = v;
                            case "ry" -> c.ry = v;
                            case "rz" -> c.rz = v;
                            default -> c.s = v;
                        }
                        return report(ctx.getSource());
                    })));
        }
        e.getDispatcher().register(root.then(set));
    }

    private static int reportShape(CommandSourceStack src) {
        String msg = String.format("[ZenkaiWeapon] inclinación de empuñadura=%.1f°  escala en 1ª persona=%.2f",
                KiWeaponVfx.tiltDegrees, KiWeaponVfx.firstPersonScale);
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int report(CommandSourceStack src) {
        KiWeaponCalibration c = KiWeaponCalibration.editing();
        String msg = String.format("[ZenkaiWeapon][%s] pos=(%.3f, %.3f, %.3f)  rot=(%.1f, %.1f, %.1f)  scale=%.2f  | 1ª persona usa: %s",
                KiWeaponCalibration.editingName(), c.x, c.y, c.z, c.rx, c.ry, c.rz, c.s,
                KiWeaponFirstPerson.lastPath);
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
