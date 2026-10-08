package com.hmc.zenkai.feature.technique.clash;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C: el RIVAL acaba de detener su barra. Solo avisa de que lo hizo (para el icono de check
 * junto a su nombre); no dice qué valor sacó, que se revela en ClashResultPacket.
 */
public record ClashPressedPacket(int clashId) implements CustomPacketPayload {

    public static final Type<ClashPressedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "clash_pressed"));

    public static final StreamCodec<FriendlyByteBuf, ClashPressedPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ClashPressedPacket::clashId,
                    ClashPressedPacket::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ClashPressedPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.hmc.zenkai.client.clash.ClashClientState.rivalPressed(pkt));
    }
}
