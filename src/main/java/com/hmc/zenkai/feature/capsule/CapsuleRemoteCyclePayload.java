package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.content.item.CapsuleRemoteItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: Shift + rueda del ratón con el control remoto en la mano. Solo viaja la dirección (±1); el servidor busca el control
 * en la mano principal y si no en la secundaria, así que un cliente trucado no puede tocar otro ítem.
 */
public record CapsuleRemoteCyclePayload(int dir) implements CustomPacketPayload {

    public static final Type<CapsuleRemoteCyclePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zenkai", "capsule_remote_cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapsuleRemoteCyclePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CapsuleRemoteCyclePayload::dir, CapsuleRemoteCyclePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CapsuleRemoteCyclePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ItemStack stack = sp.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(stack.getItem() instanceof CapsuleRemoteItem)) stack = sp.getItemInHand(InteractionHand.OFF_HAND);
            if (!(stack.getItem() instanceof CapsuleRemoteItem)) return;
            CapsuleRemote.cycle(sp, stack, Integer.signum(p.dir()));
        });
    }
}
