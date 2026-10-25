package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.api.event.MasterPurchaseEvent;
import com.hmc.zenkai.config.ServerConfig;
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
import net.neoforged.neoforge.common.NeoForge;
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
            // Las skills del skill tree de transformaciones solo se compran con TP en el árbol
            // (SkillTreeBuyPacket, que además exige el nodo padre). Ante un maestro sí: su
            // nivel 1 lo sigue enseñando él.
            if (pkt.masterId().isEmpty()
                    && com.hmc.zenkai.feature.skilltree.SkillTreeNodes.isTreeSkill(def.id())) return;
            purchase(sp, def, pkt.masterId());
        });
    }

    /**
     * Compra UN nivel de la skill con todas las comprobaciones de siempre. La comparten esta
     * pantalla de habilidades/el maestro y el skill tree (SkillTreeBuyPacket), para que las
     * reglas de TP, MIND, maestro y alineamiento vivan en un único sitio.
     * @return true si compró.
     */
    public static boolean purchase(ServerPlayer sp, SkillDef def, String masterId) {
        if (!def.purchasable()) return false; // solo maestros / comando

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return false;

        // Con levels_from_forms el techo y el precio salen de la cadena de formas de SU
        // raza: un saiyan no puede comprar el nivel que solo existe para arcosianos
        // (FormDrivenSkills decide).
        int max = FormDrivenSkills.maxLevel(def, sp);

        int current = att.skills().level(def.id());
        if (current >= max) return false;                        // ya al máximo
        // Doctrinas de los dioses (UI/UE): exigen God Ki también ante el maestro (skill tree F5).
        // Antes Tori-Bot vendía su nivel 1 sin él: el árbol lo pedía, pero esta vía no.
        if ((SkillEffects.ULTRA_INSTINCT.equals(def.id()) || SkillEffects.ULTRA_EGO.equals(def.id()))
                && !EndgamePaths.hasGodKi(sp)) {
            sp.displayClientMessage(Component.translatable("messages.zenkai.needs_god_ki"), true);
            return false;
        }
        // El nivel 1 de una habilidad CON maestro solo lo da UNO DE SUS maestros (puede
        // enseñarla más de uno), en persona, ante el maestro concreto que mandó el paquete.
        boolean masterPurchase = current <= 0 && def.hasMaster();
        if (masterPurchase) {
            if (!def.masters().contains(masterId)) return false;

            Entity master = MasterManager.findNearby(sp, masterId);
            if (master == null) return false;                       // no estás delante de él

            MasterManager.Result r = MasterManager.check(sp, masterId, master);
            if (!r.ok()) { MasterManager.tell(sp, masterId, r); return false; }
        }

        int next = current + 1;
        int cost = FormDrivenSkills.tpCostForLevel(def, sp, next);

        // LECTURA A: mind_req[n-1] es el TOTAL que la habilidad ocupa EN ese nivel. Subir
        // del 4 al 5 libera los 26 del 4 y ocupa los 34 del 5: hacen falta 8 LIBRES.
        // Antes se comparaba mindReqFor(next) contra la MIND total, o sea un umbral — y por
        // eso MIND dejaba de significar nada en cuanto pasabas el listón más alto.
        int mindDelta = def.mindReqFor(next) - def.mindReqFor(current);
        if (att.mindFree() < mindDelta) return false;
        if (SkillEffects.POTENTIAL_UNLOCK.equals(def.id()) && !PotentialUnlock.canPurchase(sp)) {
            sp.displayClientMessage(Component.translatable(
                    "messages.zenkai.alignment_too_low",
                    ServerConfig.potentialUnlockAlignmentReq()), true);
            return false;
        }
        if (att.getTP() < cost) return false;

        if (masterPurchase) {
            var event = new MasterPurchaseEvent(sp, masterId, def.id());
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) return false;
        }

        att.addTP(-cost);
        att.skills().raise(def.id(), max);
        PlayerLifeCycle.syncIfServer(sp);
        return true;
    }
}