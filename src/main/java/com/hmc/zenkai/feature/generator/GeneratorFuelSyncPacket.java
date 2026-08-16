package com.hmc.zenkai.feature.generator;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * S2C: la tabla entera de combustibles (login y /reload).
energy_generator * La pantalla del generador enseña los FE/tick de lo que hay en cada hueco y del ítem que el
 * jugador tiene bajo el cursor. Sin esta tabla en cliente, ese número tendría que viajar
 * dentro del ContainerData en cada tick, o inventárselo.
 */
public record GeneratorFuelSyncPacket(List<GeneratorFuel.Entry> entries)
        implements CustomPacketPayload {

    public static final Type<GeneratorFuelSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "generator_fuel_sync"));

    public static final StreamCodec<FriendlyByteBuf, GeneratorFuelSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    GeneratorFuel.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    GeneratorFuelSyncPacket::entries,
                    GeneratorFuelSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GeneratorFuelSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GeneratorFuels.replaceAll(pkt.entries()));
    }
}