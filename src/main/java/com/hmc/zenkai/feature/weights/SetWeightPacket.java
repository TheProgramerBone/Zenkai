package com.hmc.zenkai.feature.weights;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.item.WeightArmorItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: fija el peso de la pesa que el jugador tiene en la mano. El servidor reclampa
 *  al rango de ESA pesa, así un cliente modificado no puede meter 10^9 toneladas. */
public record SetWeightPacket(boolean mainHand, double tons) implements CustomPacketPayload {

    public static final Type<SetWeightPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "set_weight"));

    public static final StreamCodec<FriendlyByteBuf, SetWeightPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeBoolean(pkt.mainHand()); buf.writeDouble(pkt.tons()); },
                    buf -> new SetWeightPacket(buf.readBoolean(), buf.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetWeightPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            InteractionHand hand = pkt.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = sp.getItemInHand(hand);
            if (!(stack.getItem() instanceof WeightArmorItem w)) return;
            w.setTons(stack, pkt.tons());
        });
    }
}