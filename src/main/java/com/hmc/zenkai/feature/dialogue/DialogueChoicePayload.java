package com.hmc.zenkai.feature.dialogue;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Cliente → servidor: el jugador eligió una opción del diálogo. El servidor solo comprueba que el NPC existe, habla y está
 * cerca; qué se puede elegir lo decide cada {@link DialogueSpeaker} contra el estado real del jugador.
 */
public record DialogueChoicePayload(int entityId, String choiceId) implements CustomPacketPayload {

    public static final Type<DialogueChoicePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "dialogue_choice"));

    public static final StreamCodec<FriendlyByteBuf, DialogueChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DialogueChoicePayload::entityId,
            ByteBufCodecs.stringUtf8(32), DialogueChoicePayload::choiceId,
            DialogueChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DialogueChoicePayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            Entity e = sp.level().getEntity(pkt.entityId());
            if (!(e instanceof DialogueSpeaker speaker)) return;
            if (e.distanceToSqr(sp) > DialogueService.RANGE * DialogueService.RANGE) return;
            if ("leave".equals(pkt.choiceId())) return;
            speaker.onDialogueChoice(sp, pkt.choiceId());
        });
    }
}
