package com.hmc.zenkai.feature.race.hair;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * S2C: la biblioteca de peinados importados del propio jugador, tras añadir o quitar uno. Solo
 * la lista: un SyncPlayerVisualPacket completo pisaría lo que el jugador está editando en la
 * pantalla de apariencia (colores, peinado elegido) sin haberlo confirmado todavía.
 */
public record CustomHairLibrarySyncPacket(List<CustomHairLibrary.Entry> entries) implements CustomPacketPayload {

    public static final Type<CustomHairLibrarySyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "custom_hair_library_sync"));

    public static final StreamCodec<FriendlyByteBuf, CustomHairLibrarySyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                CompoundTag t = new CompoundTag();
                t.put("l", CustomHairLibrary.save(p.entries));
                buf.writeNbt(t);
            },
            buf -> {
                CompoundTag t = buf.readNbt();
                return new CustomHairLibrarySyncPacket(t == null ? List.of()
                        : CustomHairLibrary.load(t.getList("l", Tag.TAG_COMPOUND)));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CustomHairLibrarySyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ctx.player().getData(ZenkaiDataAttachments.PLAYER_VISUAL.get())
                .setCustomHairs(pkt.entries()));
    }
}
