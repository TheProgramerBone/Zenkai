package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.overlay.ScouterClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C: resultado del scan del scouter — si hay objetivo en la mira, su PL y el desglose
 * que lo compone (melee + defensa + kiPower). El desglose viaja SIEMPRE que haya objetivo:
 * el gate del scouter es el propio ítem, no hace falta uno extra.
 * breakdown a 0 = la entidad no tiene stats del mod (mob vanilla sin JSON, jugador sin
 * raza): en ese caso solo hay PL de display y el modo ATTRIBUTES no tiene nada que enseñar.
 */
public record ScouterDataPacket(boolean found, long powerLevel,
                                long melee, long defense, long kiPower) implements CustomPacketPayload {

    public static final Type<ScouterDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "scouter_data"));

    public static final StreamCodec<FriendlyByteBuf, ScouterDataPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBoolean(pkt.found());
                        buf.writeLong(pkt.powerLevel());
                        buf.writeLong(pkt.melee());
                        buf.writeLong(pkt.defense());
                        buf.writeLong(pkt.kiPower());
                    },
                    buf -> new ScouterDataPacket(buf.readBoolean(), buf.readLong(),
                            buf.readLong(), buf.readLong(), buf.readLong()));

    /** Sin objetivo en la mira. */
    public static ScouterDataPacket empty() {
        return new ScouterDataPacket(false, 0L, 0L, 0L, 0L);
    }

    /** ¿Trae desglose utilizable? */
    public boolean hasBreakdown() {
        return found && (melee > 0 || defense > 0 || kiPower > 0);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ScouterDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ScouterClientState.onData(
                pkt.found(), pkt.powerLevel(), pkt.melee(), pkt.defense(), pkt.kiPower()));
    }
}
