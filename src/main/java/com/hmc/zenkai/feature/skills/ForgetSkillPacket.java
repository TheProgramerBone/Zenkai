package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S: OLVIDAR un nivel de habilidad. Baja uno; desde el nivel 1 la borra.
 * POR QUÉ EXISTE. Con MIND como moneda (se ocupa al comprar y no se recupera), un jugador que
 * gasta mal en la hora 3 descubre en la hora 40 que no le llega para Kaioken, y su única salida
 * sería un respec total que además le tira los atributos. Olvidar devuelve el TP Y libera la
 * MIND, así que la decisión deja de ser irreversible sin volverse gratis: mientras la tienes,
 * te está ocupando concentración.
 * REEMBOLSO COMPLETO, sin penalización. La decisión ya pesa por la MIND que ocupa; cobrar
 * además por rectificar solo castiga experimentar.
 * NO baja del suelo otorgado por un maestro — esa regla vive en PlayerSkills.lower().
 */
public record ForgetSkillPacket(String skillId) implements CustomPacketPayload {

    public static final Type<ForgetSkillPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "forget_skill"));

    public static final StreamCodec<FriendlyByteBuf, ForgetSkillPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.skillId()),
                    buf -> new ForgetSkillPacket(buf.readUtf()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ForgetSkillPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            SkillDef def = SkillDef.get(pkt.skillId());
            if (def == null) return;

            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            if (!att.isRaceChosen()) return;

            // lower() valida nivel y suelo de maestro, y devuelve el nivel que ha quitado.
            // El reembolso se calcula con ESE nivel y no con el nuevo: con levels_from_forms
            // cada escalón cuesta distinto, y devolver el precio del nivel de abajo sería
            // regalar o robar TP en cada operación.
            int removed = att.skills().lower(def.id());
            if (removed <= 0) return;

            // ⚠ Verificar la sobrecarga: SkillBuyPacket usa tpCostForLevel(sp, nivel), respec()
            // usa tpCostForLevel(race, nivel). Aquí hace falta la misma que usó la compra.
            int refund = def.levelsFromForms()
                    ? SuperForms.tpCostForLevel(sp, removed)
                    : def.tpCost();

            att.addTP(refund);
            PlayerLifeCycle.syncIfServer(sp);
        });
    }
}