package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.Zenkai;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Comando SOLO-CLIENTE (mismo patrón que {@code ZenkaiArmCalibrationCommand}) para activar/
 * desactivar la Fase 1 del sistema de aura nuevo sin recompilar. Deliberadamente client-only:
 * es un interruptor de calibración visual puramente local, no necesita ida y vuelta al servidor,
 * y evita inventar un packet nuevo solo para probar esta fase. DESECHABLE — se sustituye por el
 * enum real de {@code ClientConfig} (AUTO/FORCE_LEGACY/FORCE_NEW) en la Fase 5 del documento
 * (ver .claude/pendiente/aura-dbrebirth-sistema-propuesta.md).
 *
 * {@code /zenkaiauraflame <true|false>}
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraFlameDebugCommand {

    private AuraFlameDebugCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("zenkaiauraflame")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    AuraFlameRenderer.DEBUG_ENABLED = enabled;
                                    CommandSourceStack src = ctx.getSource();
                                    src.sendSuccess(() -> Component.literal(
                                            "[AuraFlame] Sistema de aura nuevo (dbrebirth, Fase 1): "
                                                    + (enabled ? "ACTIVADO" : "desactivado")), false);
                                    return 1;
                                })));
    }
}
