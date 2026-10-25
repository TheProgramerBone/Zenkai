package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S2C: el JSON crudo de cada nodo del árbol (login y /reload). El cliente lo parsea con
 * SkillTreeData, la misma lectura que el servidor, y reemplaza su registro entero.
 */
public record SkillTreeDataSyncPacket(Map<ResourceLocation, String> raw) implements CustomPacketPayload {

    public static final Type<SkillTreeDataSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "skill_tree_data_sync"));

    public static final StreamCodec<FriendlyByteBuf, SkillTreeDataSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.raw().size());
                        pkt.raw().forEach((id, json) -> {
                            buf.writeResourceLocation(id);
                            buf.writeUtf(json, 1 << 16);
                        });
                    },
                    buf -> {
                        int n = buf.readVarInt();
                        Map<ResourceLocation, String> m = new LinkedHashMap<>();
                        for (int i = 0; i < n; i++) m.put(buf.readResourceLocation(), buf.readUtf(1 << 16));
                        return new SkillTreeDataSyncPacket(m);
                    });

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkillTreeDataSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SkillTreeData.replaceAllFromRaw(pkt.raw()));
    }
}
