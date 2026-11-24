package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.content.blockentity.CapsuleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: botón Guardar de la pantalla de la cápsula. El servidor solo se fía de la posición: comprueba que el jugador esté
 * cerca y que ahí haya una cápsula vacía; el escaneo y los claims se repiten al capturar.
 */
public record CapsuleArmPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<CapsuleArmPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "capsule_arm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapsuleArmPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, CapsuleArmPayload::pos, CapsuleArmPayload::new);

    /** Distancia máxima (al cuadrado) desde el jugador al centro de la cápsula: la de alcance de un bloque, con margen. */
    private static final double MAX_DIST_SQ = 8.0 * 8.0;

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CapsuleArmPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!sp.level().isLoaded(p.pos) || sp.distanceToSqr(p.pos.getCenter()) > MAX_DIST_SQ) return;
            if (sp.level().getBlockEntity(p.pos) instanceof CapsuleBlockEntity be) be.arm(sp);
        });
    }
}
