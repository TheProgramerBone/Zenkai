package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.MindBudget;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S técnicas físicas: op 0 = UNLOCK (compra TP), op 1 = BIND (position; -1 desasigna).
 * El servidor revalida (TP suficiente, desbloqueada antes de bindear).
 */
public record PhysicalTechniquePacket(int op, int tech, int position) implements CustomPacketPayload {

    public static final Type<PhysicalTechniquePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "physical_technique"));

    public static final StreamCodec<FriendlyByteBuf, PhysicalTechniquePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PhysicalTechniquePacket::op,
                    ByteBufCodecs.VAR_INT, PhysicalTechniquePacket::tech,
                    ByteBufCodecs.VAR_INT, PhysicalTechniquePacket::position,
                    PhysicalTechniquePacket::new);

    public static PhysicalTechniquePacket unlock(PhysicalTechnique t) {
        return new PhysicalTechniquePacket(0, t.ordinal(), -1);
    }

    public static PhysicalTechniquePacket bind(PhysicalTechnique t, int position) {
        return new PhysicalTechniquePacket(1, t.ordinal(), position);
    }

    public static PhysicalTechniquePacket forget(PhysicalTechnique t) {
        return new PhysicalTechniquePacket(2, t.ordinal(), -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PhysicalTechniquePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PhysicalTechnique t = PhysicalTechnique.byOrdinal(pkt.tech());
            if (t == null || !t.enabled()) return;
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

            if (pkt.op() == 0) {
                if (att.techniques().isUnlocked(t)) return;
                // MIND como capacidad: ocupa mientras la tengas. Ver MindBudget.
                if (!MindBudget.canUnlock(att, t)) return;
                if (att.getTP() < t.tpCost()) return;
                att.addTP(-t.tpCost());
                att.techniques().unlock(t);
            } else if (pkt.op() == 1) {
                if (!att.techniques().isUnlocked(t)) return;
                att.techniques().bindPhysical(pkt.position(), t);
            } else if (pkt.op() == 2) {
                // Olvidar: reembolso íntegro y liberación de MIND. Ver handleForget en
                // TechniquePacket para el porqué de no penalizar.
                if (!att.techniques().isUnlocked(t)) return;
                att.techniques().forget(t);
                att.addTP(t.tpCost());
            } else {
                return;
            }
            PlayerLifeCycle.sync(sp);
        });
    }
}