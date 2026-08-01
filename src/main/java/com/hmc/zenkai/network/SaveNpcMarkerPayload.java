package com.hmc.zenkai.network;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente → servidor: guarda los valores editados en el marcador de NPC.
 * El servidor NO confía en nada de aquí: valida permiso, distancia, chunk cargado
 * y que el id de entidad exista antes de aplicar (ver ModNetworking).
 *
 * @param respawn true = "Aplicar y respawnear" (mata el NPC actual y lo recrea ya);
 *                false = "Solo guardar" (los cambios se ven al siguiente respawn).
 */
public record SaveNpcMarkerPayload(BlockPos pos, String npcType, float yaw,
                                   double offX, double offY, double offZ,
                                   boolean respawn)
        implements CustomPacketPayload {

    public static final Type<SaveNpcMarkerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "npc_marker_save"));

    public static final StreamCodec<FriendlyByteBuf, SaveNpcMarkerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeUtf(p.npcType(), 128);
                        buf.writeFloat(p.yaw());
                        buf.writeDouble(p.offX());
                        buf.writeDouble(p.offY());
                        buf.writeDouble(p.offZ());
                        buf.writeBoolean(p.respawn());
                    },
                    buf -> new SaveNpcMarkerPayload(
                            buf.readBlockPos(),
                            buf.readUtf(128),
                            buf.readFloat(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}