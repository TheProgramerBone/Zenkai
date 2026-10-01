package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdatePlayerVisualPacket(CompoundTag data) implements CustomPacketPayload {

    public static final Type<UpdatePlayerVisualPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "update_player_visual"));

    public static final StreamCodec<FriendlyByteBuf, UpdatePlayerVisualPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> buf.writeNbt(pkt.data),
                    buf -> new UpdatePlayerVisualPacket(buf.readNbt()));

    public static UpdatePlayerVisualPacket from(PlayerVisualAttachment visual) {
        return new UpdatePlayerVisualPacket(visual.save());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdatePlayerVisualPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (pkt.data == null) return;

            // Si lo recibe el SERVIDOR (cliente -> servidor)
            if (ctx.player() instanceof ServerPlayer sp) {
                var visual = sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
                // Lista blanca de campos cosméticos, NUNCA load() completo: ver el javadoc de
                // loadCosmeticFromClient (majinControlled y compañía son del servidor).
                visual.loadCosmeticFromClient(pkt.data);
                Race race = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).getRace();
                // Misma regla que RaceSelectionScreen: solo Human/Saiyan pueden volver a la
                // skin vanilla; el resto lleva siempre su cuerpo de raza.
                if (race != Race.HUMAN && race != Race.SAIYAN) {
                    visual.setRenderRaceSkin(true);
                    visual.setHideVanillaBody(true);
                }
                // Un cliente modificado podría mandar un índice de cara baneado para su raza.
                FaceBans.reconcile(race, visual);

                // Re-broadcast a self + trackers (server -> clients)
                PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
                return;
            }

            // Si lo recibe el CLIENTE (server -> client)
            // ctx.player() aquí es LocalPlayer
            var p = ctx.player();

            var visual = p.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());

            visual.load(pkt.data);
        });
    }
}