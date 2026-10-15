package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.fx.SwordFx;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, al propio jugador Y a quien lo rastrea: el servidor decide cuándo cae cada tajo de una técnica
 * de espada (ver PhysicalCombatServer.swordFx) y todos dibujan el mismo arco. {@code variant} distingue
 * los dos tajos del CORTE CRUZADO (0 y 1, las dos diagonales de la X); el resto manda 0. El color del
 * arco lo resuelve el cliente desde el aura de quien ataca (AuraColors), así que no viaja.
 */
public record SwordFxPacket(int entityId, int technique, int variant) implements CustomPacketPayload {

    public static final Type<SwordFxPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "sword_fx"));

    public static final StreamCodec<FriendlyByteBuf, SwordFxPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SwordFxPacket::entityId,
                    ByteBufCodecs.VAR_INT, SwordFxPacket::technique,
                    ByteBufCodecs.VAR_INT, SwordFxPacket::variant,
                    SwordFxPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SwordFxPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            PhysicalTechnique t = PhysicalTechnique.byOrdinal(pkt.technique());
            if (t != null && level.getEntity(pkt.entityId()) instanceof Player p) {
                SwordFx.spawn(p, t, pkt.variant());
            }
        });
    }
}
