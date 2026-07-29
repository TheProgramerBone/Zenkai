package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.forms.PotentialUnlock;
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
            if (!def.purchasable()) return; // solo maestros / comando

            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;

            // Con levels_from_forms el techo y el precio salen de la cadena de formas de SU
            // raza: un saiyan no puede comprar el nivel que solo existe para arcosianos.
            int max  = def.levelsFromForms()
                    ? Math.min(def.maxLevel(), SuperForms.maxLevel(sp)) : def.maxLevel();

            int current = att.skills().level(def.id());
            if (current >= max) return;                        // ya al máximo
            // El nivel 1 de una habilidad CON maestro solo lo da el maestro.
            if (current <= 0 && def.master() != null) return;

            int next = current + 1;
            int cost = def.levelsFromForms()
                    ? SuperForms.tpCostForLevel(sp, next) : def.tpCost();

            if (att.getAttribute(ZenkaiAttributes.MIND) < def.mindReqFor(next)) return;
            // Potential Unlock exige alineamiento. Gate propio y no campo genérico en
            // SkillDef porque de momento es el único caso; si aparece un segundo, toca
            // convertirlo en 'alignment_req' del JSON en vez de acumular ifs aquí.
            if (SkillEffects.POTENTIAL_UNLOCK.equals(def.id())
            && !PotentialUnlock.canPurchase(sp)) return;
            if (att.getTP() < cost) return;

            att.addTP(-cost);
            att.skills().raise(def.id(), max);
            PlayerLifeCycle.syncIfServer(sp);
        });
    }
}