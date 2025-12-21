package com.hmc.db_renewed.core.network.feature.stats;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.core.network.feature.Race;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record ChooseRacePacket(Race race) implements CustomPacketPayload {

    public static final Type<ChooseRacePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DragonBlockRenewed.MOD_ID, "choose_race"));

    // OJO: estos métodos SON estáticos y usan FriendlyByteBuf
    public static final StreamCodec<FriendlyByteBuf, ChooseRacePacket> STREAM_CODEC =
            StreamCodec.of(ChooseRacePacket::encode, ChooseRacePacket::decode);

    // --- codec ---

    public static void encode(FriendlyByteBuf buf, ChooseRacePacket pkt) {
        // puedes guardar por nombre o por ordinal; por nombre es más robusto
        buf.writeEnum(pkt.race());
    }

    public static ChooseRacePacket decode(FriendlyByteBuf buf) {
        Race race = buf.readEnum(Race.class);
        return new ChooseRacePacket(race);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // --- handler ---

    public static void handle(ChooseRacePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp)) return;

            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

            // Aplicar raza seleccionada
            att.setRace(pkt.race());
            // Aquí puedes dejar hooks / TODO para personalización inicial
            // (color de aura, pelo, etc.)

            // Si tienes un SyncPlayerStatsPacket, podrías mandarlo aquí.
            // ModNetworking.syncPlayerStats(sp);
        });
    }
}