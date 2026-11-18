package com.hmc.zenkai.feature.sideconfig;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.transport.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: un clic en una cara de la pantalla de caras. El cliente solo pide «siguiente» o «anterior»
 * (nunca un modo concreto): así dos clics seguidos antes de que vuelva el sync no se pisan, y el
 * servidor decide sobre SU estado. Valida alcance, carga del chunk, canal y cara bloqueada.
 */
public record SideConfigPacket(BlockPos pos, int channel, int face, boolean backwards) implements CustomPacketPayload {

    public static final Type<SideConfigPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "side_config"));

    public static final StreamCodec<FriendlyByteBuf, SideConfigPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.channel());
                buf.writeVarInt(p.face());
                buf.writeBoolean(p.backwards());
            },
            buf -> new SideConfigPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SideConfigPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            // Mismo alcance que un contenedor: un cliente modificado no reconfigura bloques lejanos.
            if (sp.distanceToSqr(p.pos().getCenter()) > 64.0 || !sp.level().isLoaded(p.pos())) return;
            if (!(sp.level().getBlockEntity(p.pos()) instanceof SideConfigurable cfg)) return;
            SideConfigurable.Channel[] channels = SideConfigurable.Channel.values();
            Direction[] faces = Direction.values();
            if (p.channel() < 0 || p.channel() >= channels.length || p.face() < 0 || p.face() >= faces.length) return;
            SideConfigurable.Channel channel = channels[p.channel()];
            Direction face = faces[p.face()];
            if (!cfg.sideChannels().contains(channel) || cfg.sideLocked(face)) return;

            SideMode now = cfg.sideMode(channel, face);
            cfg.setSideMode(channel, face, p.backwards() ? now.prev() : now.next());
            sp.level().playSound(null, p.pos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.4f);
        });
    }
}
