package com.hmc.zenkai.feature.technique.clash;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * S2C: empieza un choque de haces y este jugador es uno de los dos participantes. Lleva cuanto
 * que el cliente necesita para pintar la barra por sí solo: los parámetros del minijuego (no se
 * apoya en su copia de la config), la fase inicial de SU cursor y el tick de juego exacto en que
 * arranca. Con eso la barra es una función pura del tiempo (ClashRules.cursor) y no hace falta
 * mandar nada por tick.
 * También identifica al rival para la ficha de la pantalla: nombre y, si es un jugador, su UUID
 * (el cliente saca la cara de su skin de la lista de jugadores, sin depender de que la entidad
 * esté cargada). Un mob viaja con UUID cero y `rivalIsPlayer` a false.
 * El componente se llama `clashId` y no `id`; y no hay ninguno llamado `type` (chocaría con
 * CustomPacketPayload.type(), mismo criterio que ActionStateSyncPacket).
 */
public record ClashStartPacket(int clashId, int rivalEntityId, String rivalName,
                               boolean rivalIsPlayer, UUID rivalUuid,
                               float seed, long minigameStartTick,
                               int duration, int period, int speed,
                               double x, double y, double z)
        implements CustomPacketPayload {

    public static final Type<ClashStartPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "clash_start"));

    public static final StreamCodec<FriendlyByteBuf, ClashStartPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.clashId());
                        buf.writeVarInt(p.rivalEntityId());
                        buf.writeUtf(p.rivalName(), 64);
                        buf.writeBoolean(p.rivalIsPlayer());
                        buf.writeUUID(p.rivalUuid());
                        buf.writeFloat(p.seed());
                        buf.writeVarLong(p.minigameStartTick());
                        buf.writeVarInt(p.duration());
                        buf.writeVarInt(p.period());
                        buf.writeVarInt(p.speed());
                        buf.writeDouble(p.x());
                        buf.writeDouble(p.y());
                        buf.writeDouble(p.z());
                    },
                    buf -> new ClashStartPacket(
                            buf.readVarInt(), buf.readVarInt(), buf.readUtf(64),
                            buf.readBoolean(), buf.readUUID(),
                            buf.readFloat(), buf.readVarLong(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public ClashRules.Params params() {
        return new ClashRules.Params(duration, period, speed);
    }

    public static void handle(ClashStartPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.hmc.zenkai.client.clash.ClashClientState.start(pkt));
    }
}
