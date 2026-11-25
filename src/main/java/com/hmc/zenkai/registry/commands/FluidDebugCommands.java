package com.hmc.zenkai.registry.commands;

import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * {@code /zenkai debug fluid}: instantánea de lo que el SERVIDOR sabe del jugador dentro de un fluido, para diagnosticar
 * el atasco en fosos de 1×1 con agua curativa (2026-11-25; el gametest {@code FluidPhysicsGameTests} descarta la física
 * del fluido, así que la causa está en el cliente o en algún sistema del mod). Úsalo ESTANDO dentro del foso. Con
 * {@code <ticks>} repite la lectura cada segundo durante ese tiempo (máx. 600) para verla mientras mantienes Espacio.
 */
public final class FluidDebugCommands {
    private FluidDebugCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("fluid")
                .executes(c -> snapshot(c))
                .then(Commands.argument("ticks", IntegerArgumentType.integer(20, 600))
                        .executes(c -> {
                            ServerPlayer me = c.getSource().getPlayerOrException();
                            int ticks = IntegerArgumentType.getInteger(c, "ticks");
                            for (int t = 0; t < ticks; t += 20) {
                                final int delay = t;
                                me.getServer().tell(new net.minecraft.server.TickTask(me.getServer().getTickCount() + delay,
                                        () -> send(me)));
                            }
                            c.getSource().sendSuccess(() -> Component.literal("Muestreando cada segundo durante " + ticks / 20 + " s…"), false);
                            return 1;
                        }));
    }

    private static int snapshot(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        send(c.getSource().getPlayerOrException());
        return 1;
    }

    private static void send(ServerPlayer p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        FluidType maxType = p.getMaxHeightFluidType();
        var feet = p.level().getBlockState(p.blockPosition());
        var fluid = p.level().getFluidState(p.blockPosition());
        StringBuilder sb = new StringBuilder();
        sb.append("§e[fluid]§r ")
                .append("bloque=").append(feet.getBlock().getDescriptionId())
                .append(" fluido=").append(fluid.isEmpty() ? "-" : fluid.getType().builtInRegistryHolder().key().location())
                .append(" tipoMásAlto=").append(maxType.isAir() ? "aire" : maxType.getDescriptionId())
                .append(" altura=").append(String.format("%.2f", maxType.isAir() ? 0.0 : p.getFluidTypeHeight(maxType)))
                .append("\n  isInWater=").append(p.isInWater())
                .append(" isInFluidType=").append(p.isInFluidType())
                .append(" ojosEnFluido=").append(!p.getEyeInFluidType().isAir())
                .append(" onGround=").append(p.onGround())
                .append(" pose=").append(p.getPose())
                .append(" nada=").append(p.isSwimming())
                .append("\n  mayFly=").append(p.getAbilities().mayfly)
                .append(" flying=").append(p.getAbilities().flying)
                .append(" isFlyEnabled=").append(att.isFlyEnabled())
                .append(" afectadoPorFluidos=").append(p.isAffectedByFluids())
                .append(" gameMode=").append(p.gameMode.getGameModeForPlayer())
                .append("\n  raza=").append(att.isRaceChosen() ? att.getRace() : "sin raza")
                .append(" derribado=").append(att.flags().isDowned())
                .append(" cargaPesas=").append(String.format("%.2f", att.getWeightLoad()))
                .append("\n  ").append(attr(p, "SALTO", Attributes.JUMP_STRENGTH))
                .append(" ").append(attr(p, "VELOCIDAD", Attributes.MOVEMENT_SPEED))
                .append(" ").append(attr(p, "GRAVEDAD", Attributes.GRAVITY))
                .append(" ").append(attr(p, "NATACIÓN", NeoForgeMod.SWIM_SPEED))
                .append("\n  vel(servidor)=").append(p.getDeltaMovement());
        p.sendSystemMessage(Component.literal(sb.toString()));
    }

    private static String attr(ServerPlayer p, String label, Holder<Attribute> holder) {
        AttributeInstance a = p.getAttribute(holder);
        if (a == null) return label + "=∅";
        StringBuilder sb = new StringBuilder(label).append('=').append(String.format("%.3f", a.getValue()));
        if (!a.getModifiers().isEmpty()) {
            sb.append('[');
            a.getModifiers().forEach(m -> sb.append(m.id().getPath()).append(':').append(String.format("%.2f", m.amount())).append(' '));
            sb.append(']');
        }
        return sb.toString();
    }
}
