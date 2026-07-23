package com.hmc.zenkai.core.network.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.combat.SenseServerState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: "he fijado a esta entidad" (o -1 para soltar). El lock en sí es cliente puro
 * (rota la mirada local); esto existe solo para que el servidor pueda avisar al fijado.
 *
 * El aviso se manda SOLO si el objetivo tiene el sentir el ki encendido: si no lo tiene,
 * no hay motivo narrativo para que note nada, igual que en Xenoverse.
 */
public record LockOnPacket(int targetId) implements CustomPacketPayload {

    public static final Type<LockOnPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "lock_on"));

    public static final StreamCodec<FriendlyByteBuf, LockOnPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, LockOnPacket::targetId, LockOnPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LockOnPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            if (pkt.targetId() < 0) {
                SenseServerState.clearLock(sp);
                return;
            }

            Entity e = sp.serverLevel().getEntity(pkt.targetId());
            if (e == null || e == sp) return;

            // Anti-humo: no aceptamos un lock sobre algo absurdamente lejos.
            if (sp.distanceTo(e) > SenseServerState.MAX_LOCK_DISTANCE) return;

            boolean isNew = SenseServerState.setLock(sp, pkt.targetId());

            if (isNew && e instanceof ServerPlayer victim && SenseServerState.senseActive(victim)) {
                victim.displayClientMessage(
                        Component.translatable("messages.zenkai.lock_on.targeted",
                                sp.getDisplayName()).withStyle(ChatFormatting.RED), true);
            }
        });
    }
}