package com.hmc.zenkai.core.network.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.technique.PhysicalCombatServer;
import com.hmc.zenkai.core.technique.PhysicalTechnique;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** C2S: ejecutar la física de la posición seleccionada. El servidor revalida. */
public record PhysicalFirePacket(int tech) implements CustomPacketPayload {

    public static final Type<PhysicalFirePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "physical_fire"));

    public static final StreamCodec<FriendlyByteBuf, PhysicalFirePacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PhysicalFirePacket::tech,
                    PhysicalFirePacket::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PhysicalFirePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PhysicalTechnique t = PhysicalTechnique.byOrdinal(pkt.tech());
            if (t != null) PhysicalCombatServer.tryExecute(sp, t);
        });
    }
}