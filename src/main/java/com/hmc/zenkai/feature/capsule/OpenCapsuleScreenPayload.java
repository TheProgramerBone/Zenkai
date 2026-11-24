package com.hmc.zenkai.feature.capsule;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: abre la pantalla de una cápsula colocada con el resumen que calculó el SERVIDOR ({@link CapsuleScan}: el cliente no
 * ve el contenido de los cofres, así que no sabría si uno lleva otra cápsula dentro).
 */
public record OpenCapsuleScreenPayload(BlockPos pos, int tier, int radius, int blocks, int mobs, int mobVolume,
                                       int skipped, int budget) implements CustomPacketPayload {

    public static final Type<OpenCapsuleScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "open_capsule_screen"));

    public static final StreamCodec<ByteBuf, OpenCapsuleScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                f.writeBlockPos(p.pos);
                f.writeVarInt(p.tier);
                f.writeVarInt(p.radius);
                f.writeVarInt(p.blocks);
                f.writeVarInt(p.mobs);
                f.writeVarInt(p.mobVolume);
                f.writeVarInt(p.skipped);
                f.writeVarInt(p.budget);
            },
            buf -> {
                FriendlyByteBuf f = new FriendlyByteBuf(buf);
                return new OpenCapsuleScreenPayload(f.readBlockPos(), f.readVarInt(), f.readVarInt(), f.readVarInt(),
                        f.readVarInt(), f.readVarInt(), f.readVarInt(), f.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public int used() { return blocks + mobVolume; }
}
