package com.hmc.zenkai.compat.create;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: un ajuste de la pantalla del Motor de Ki (modo, velocidad, sentido). Un solo packet con {@code action} en vez de tres
 * porque todos comparten la validación (distancia y tipo de bloque), que es lo que importa aquí:
 * el cliente solo PIDE, el servidor decide. Solo nombra {@link KiMotorControl}, nunca el block
 * entity, para que registrar el packet no cargue Create.
 */
public record KiMotorActionPacket(BlockPos pos, int action, int arg) implements CustomPacketPayload {

    public static final int SET_MODE = 0;      // arg = ordinal de Mode
    public static final int SET_SPEED = 1;     // arg = índice de RPM_STEPS
    public static final int TOGGLE_REVERSE = 2;
    // 3 y 4 (ciclar puertos) se quitaron el 2026-11-17: los puertos van por SideConfigPacket, como
    // cualquier bloque con caras. No reutilizar esos números.

    public static final Type<KiMotorActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_motor_action"));

    public static final StreamCodec<FriendlyByteBuf, KiMotorActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.action()); buf.writeVarInt(p.arg()); },
            buf -> new KiMotorActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiMotorActionPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            // Mismo alcance que abrir un contenedor: un cliente modificado no puede ajustar motores lejanos.
            if (sp.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY() + 0.5, p.pos().getZ() + 0.5) > 64.0) return;
            if (!sp.level().isLoaded(p.pos())) return;
            if (!(sp.level().getBlockEntity(p.pos()) instanceof KiMotorControl motor)) return;

            switch (p.action()) {
                case SET_MODE -> {
                    KiMotorControl.Mode[] modes = KiMotorControl.Mode.values();
                    if (p.arg() >= 0 && p.arg() < modes.length) motor.setMode(modes[p.arg()]);
                }
                case SET_SPEED -> {
                    if (p.arg() >= 0 && p.arg() < KiMotorControl.RPM_STEPS.length) motor.setSpeedStep(p.arg());
                }
                case TOGGLE_REVERSE -> motor.toggleReversed();
                default -> { }
            }
        });
    }
}
