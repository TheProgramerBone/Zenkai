package com.hmc.zenkai.core.network.vehicle;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record VehicleControlPayload(boolean up, boolean down) implements CustomPacketPayload {

    public static final Type<VehicleControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "vehicle_ctrl"));

    public static final StreamCodec<FriendlyByteBuf, VehicleControlPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeBoolean(msg.up);
                        buf.writeBoolean(msg.down);
                    },
                    buf -> new VehicleControlPayload(buf.readBoolean(), buf.readBoolean())
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VehicleControlPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player p = (Player) ctx.player();
            Entity v = p.getVehicle();
            // Solo el CONDUCTOR (primer pasajero) puede controlar subida/bajada.
            if (v instanceof VerticalControlVehicle vehicle && v.getControllingPassenger() == p) {
                vehicle.setVerticalInput(msg.up(), msg.down());
            }
        });
    }
}