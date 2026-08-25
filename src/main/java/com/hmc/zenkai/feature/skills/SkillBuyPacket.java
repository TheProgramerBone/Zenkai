package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.forms.PotentialUnlock;
import com.hmc.zenkai.feature.master.MasterManager;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: comprar una habilidad. Validación 100% servidor (el cliente solo pide).
 * masterId vacío = compra normal desde la pantalla de habilidades (nivel 2 en adelante).
 * masterId presente = compra ANTE UN MAESTRO, que es la única forma de conseguir el nivel 1
 * de una habilidad con maestro. La admisión no se repite aquí: se delega en MasterManager,
 * que es el mismo embudo que usó la entidad para abrir la tienda.
 */
public record SkillBuyPacket(String skillId, String masterId) implements CustomPacketPayload {

    /** Compra sin maestro (pantalla de habilidades). */
    public SkillBuyPacket(String skillId) { this(skillId, ""); }

    public static final Type<SkillBuyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "skill_buy"));

    public static final StreamCodec<FriendlyByteBuf, SkillBuyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SkillBuyPacket::skillId,
                    ByteBufCodecs.STRING_UTF8, SkillBuyPacket::masterId,
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
            // El nivel 1 de una habilidad CON maestro solo lo da SU maestro, en persona.
            if (current <= 0 && def.master() != null) {
                if (!def.master().equals(pkt.masterId())) return;

                Entity master = MasterManager.findNearby(sp, def.master());
                if (master == null) return;                       // no estás delante de él

                MasterManager.Result r = MasterManager.check(sp, def.master(), master);
                if (!r.ok()) { MasterManager.tell(sp, def.master(), r); return; }
            }

            int next = current + 1;
            int cost = def.levelsFromForms()
                    ? SuperForms.tpCostForLevel(sp, next) : def.tpCost();

            // LECTURA A: mind_req[n-1] es el TOTAL que la habilidad ocupa EN ese nivel. Subir
            // del 4 al 5 libera los 26 del 4 y ocupa los 34 del 5: hacen falta 8 LIBRES.
            // Antes se comparaba mindReqFor(next) contra la MIND total, o sea un umbral — y por
            // eso MIND dejaba de significar nada en cuanto pasabas el listón más alto.
            int mindDelta = def.mindReqFor(next) - def.mindReqFor(current);
            if (att.mindFree() < mindDelta) return;
            if (SkillEffects.POTENTIAL_UNLOCK.equals(def.id()) && !PotentialUnlock.canPurchase(sp)) {
                sp.displayClientMessage(Component.translatable(
                        "messages.zenkai.alignment_too_low",
                        CommonConfig.potentialUnlockAlignmentReq()), true);
                return;
            }
            if (att.getTP() < cost) return;

            att.addTP(-cost);
            att.skills().raise(def.id(), max);
            PlayerLifeCycle.syncIfServer(sp);
        });
    }
}