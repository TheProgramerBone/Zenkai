package com.hmc.db_renewed.network;

import com.hmc.db_renewed.DragonBlockRenewed;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record VehicleControlPayload(boolean up, boolean down) implements CustomPacketPayload {

    public static final Type<VehicleControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "vehicle_ctrl"));

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
            if (p.getVehicle() instanceof VerticalControlVehicle vehicle) {
                vehicle.setVerticalInput(msg.up(), msg.down());
            }
        });
    }
}
