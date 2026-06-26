package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.RaceSelectionScreen;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncPlayerStatsPacket(int entityId, CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncPlayerStatsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "sync_player_stats"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerStatsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,      SyncPlayerStatsPacket::entityId,
                    ByteBufCodecs.COMPOUND_TAG, SyncPlayerStatsPacket::data,
                    SyncPlayerStatsPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Construye el paquete a partir del jugador objetivo (guarda su entityId). */
    public static SyncPlayerStatsPacket from(Player target) {
        PlayerStatsAttachment att = target.getData(DataAttachments.PLAYER_STATS.get());
        return new SyncPlayerStatsPacket(target.getId(), att.save());
    }

    // Cliente: aplicar al jugador correcto (resuelto por entityId), NO siempre al LocalPlayer.
    public static void handle(SyncPlayerStatsPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> applyClient(pkt));
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyClient(SyncPlayerStatsPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || msg.data() == null) return;

        // No pisar el preview del propio jugador mientras está creando personaje.
        if (mc.player != null
                && mc.player.getId() == msg.entityId()
                && mc.screen instanceof RaceSelectionScreen) {
            return;
        }

        Entity e = mc.level.getEntity(msg.entityId());
        if (!(e instanceof Player p)) return;

        PlayerStatsAttachment att = p.getData(DataAttachments.PLAYER_STATS.get());
        att.load(msg.data());
    }
}