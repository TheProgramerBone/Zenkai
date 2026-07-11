package com.hmc.zenkai.core.network.feature.skills;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.Dbrattributes;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.skills.SkillDef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: comprar una habilidad. Validación 100% servidor (el cliente solo pide):
 * existe, raza elegida, no comprada ya, MIND suficiente y TP suficiente.
 * addTP(-coste) es seguro: clampa a 0, pero el check previo de getTP() evita compras gratis.
 */
public record SkillBuyPacket(String skillId) implements CustomPacketPayload {

    public static final Type<SkillBuyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "skill_buy"));

    public static final StreamCodec<FriendlyByteBuf, SkillBuyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SkillBuyPacket::skillId,
                    SkillBuyPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkillBuyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            SkillDef def = SkillDef.get(pkt.skillId());
            if (def == null) return;

            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;
            if (att.skills().has(def.id())) return;
            if (att.getAttribute(Dbrattributes.MIND) < def.mindReq()) return;
            if (att.getTP() < def.tpCost()) return;

            att.addTP(-def.tpCost());
            att.skills().unlock(def.id());
            PlayerLifeCycle.syncIfServer(sp);
        });
    }
}