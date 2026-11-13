package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.feature.skills.SkillDef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S: comprar un nodo del skill tree de transformaciones (SkillTreeScreen). Validación 100 %
 * servidor: se re-evalúa el nodo con SkillTreeNodes.evaluate (la MISMA función con la que la
 * pantalla decidió pintarlo comprable) y, si sale AVAILABLE, se compra el nivel de skill que
 * representa por SkillBuyPacket.purchase, la rutina única de TP/MIND/maestro/alineamiento, o
 * —si es un nodo propio sin skill detrás— por SkillTree.buyOwned.
 * Sin reembolso: no existe el packet inverso.
 */
public record SkillTreeBuyPacket(ResourceLocation node) implements CustomPacketPayload {

    public static final Type<SkillTreeBuyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "skill_tree_buy"));

    public static final StreamCodec<FriendlyByteBuf, SkillTreeBuyPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeResourceLocation(pkt.node()),
                    buf -> new SkillTreeBuyPacket(buf.readResourceLocation()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkillTreeBuyPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;

            SkillTreeNodes.Node node = SkillTreeNodes.find(att.getRace(), pkt.node());
            if (node == null) return;                       // no es un nodo de SU árbol
            SkillTreeNodes.Eval e = SkillTreeNodes.evaluate(sp, node);
            if (!e.purchasable()) return;
            // Gancho de addons: cancelable, antes de cobrar nada (ver SkillTreeNodePurchaseEvent).
            if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new com.hmc.zenkai.api.event.SkillTreeNodePurchaseEvent(sp, node.id())).isCanceled()) return;
            // Nodo propio (pasiva de raza, forma divina): sin skill detrás, lo cobra SkillTree.
            if (e.ownNode()) {
                SkillTree.buyOwned(sp, node, e);
                return;
            }
            if (e.skill() == null) return;

            SkillDef def = SkillDef.get(e.skill());
            if (def == null) return;
            // purchase() sube UN nivel desde el actual; evaluate() ya exigió que el objetivo sea
            // justo el siguiente, así que no puede saltarse peldaños.
            SkillBuyPacket.purchase(sp, def, "");
        });
    }
}
