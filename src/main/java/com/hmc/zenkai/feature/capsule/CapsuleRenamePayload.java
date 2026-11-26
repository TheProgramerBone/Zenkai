package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.content.blockentity.CapsuleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: nombre de una cápsula colocada, escrito en el campo de la cabecera de su pantalla (se manda al cerrarla si cambió).
 * Sirve para distinguirlas en el control remoto. El servidor comprueba distancia y que haya una cápsula, y limpia el texto
 * igual que el yunque (sin caracteres de control ni de formato, como mucho {@link #MAX_LENGTH}).
 */
public record CapsuleRenamePayload(BlockPos pos, String name) implements CustomPacketPayload {

    /** El mismo tope que el yunque de vanilla. */
    public static final int MAX_LENGTH = 50;

    public static final Type<CapsuleRenamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "capsule_rename"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapsuleRenamePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CapsuleRenamePayload::pos,
            ByteBufCodecs.stringUtf8(MAX_LENGTH), CapsuleRenamePayload::name,
            CapsuleRenamePayload::new);

    /** Como CapsuleArmPayload: alcance de un bloque con margen. */
    private static final double MAX_DIST_SQ = 8.0 * 8.0;

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CapsuleRenamePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!sp.level().isLoaded(p.pos) || sp.distanceToSqr(p.pos.getCenter()) > MAX_DIST_SQ) return;
            if (!(sp.level().getBlockEntity(p.pos) instanceof CapsuleBlockEntity be)) return;
            String clean = net.minecraft.util.StringUtil.filterText(p.name).replace("§", "");
            if (clean.length() > MAX_LENGTH) clean = clean.substring(0, MAX_LENGTH);
            be.rename(clean);
        });
    }
}
