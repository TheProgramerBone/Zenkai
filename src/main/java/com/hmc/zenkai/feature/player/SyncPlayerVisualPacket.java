package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.RaceSelectionScreen;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
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

public record SyncPlayerVisualPacket(int entityId, CompoundTag data) implements CustomPacketPayload {

    public static final Type<SyncPlayerVisualPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "sync_player_visual"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerVisualPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncPlayerVisualPacket::entityId,
                    ByteBufCodecs.COMPOUND_TAG, SyncPlayerVisualPacket::data,
                    SyncPlayerVisualPacket::new
            );

    /** Para el PROPIO jugador: incluye su biblioteca de peinados importados entera. */
    public static SyncPlayerVisualPacket forSelf(Player target) {
        PlayerVisualAttachment att = target.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        return new SyncPlayerVisualPacket(target.getId(), att.saveFull());
    }

    /**
     * Para quien lo VE: de la biblioteca solo viaja el peinado puesto (el resto es asunto suyo y
     * pesaría hasta 64 KB por jugador). Con eso cada cliente construye el peinado sin archivos.
     */
    public static SyncPlayerVisualPacket forTracker(Player target) {
        PlayerVisualAttachment att = target.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        return new SyncPlayerVisualPacket(target.getId(), att.saveForTracker());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncPlayerVisualPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> applyClient(msg));
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyClient(SyncPlayerVisualPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null
                && mc.player.getId() == msg.entityId()
                && mc.screen instanceof RaceSelectionScreen) {
            return;
        }
        if (mc.level == null) return;

        Entity e = mc.level.getEntity(msg.entityId());
        if (!(e instanceof Player p)) return;

        PlayerVisualAttachment att = p.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        att.load(msg.data());
    }
}