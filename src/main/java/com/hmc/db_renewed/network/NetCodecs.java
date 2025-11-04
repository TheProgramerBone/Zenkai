package com.hmc.db_renewed.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public final class NetCodecs {
    private NetCodecs() {}

    public static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buf, UUID uuid) -> buf.writeUUID(uuid),
                    (RegistryFriendlyByteBuf buf) -> buf.readUUID()
            );
}