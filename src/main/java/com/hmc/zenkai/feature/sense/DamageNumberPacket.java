package com.hmc.zenkai.feature.sense;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.overlay.DamageNumberRenderer;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C, solo al ATACANTE: "le acabas de quitar {@code amount} a esta entidad". Lo dibuja
 * DamageNumberRenderer como un número flotante sobre la víctima.
 *
 * Servidor autoritativo: el número lo decide el pipeline de daño (CombatZenkaiHooks), no el
 * cliente, y solo se manda a quien tiene la habilidad Ki Sense. Así quien no la tiene no recibe
 * el dato, en vez de recibirlo y esconderlo en el cliente. El ajuste de cliente
 * {@code sense_ki.damage_numbers} solo decide si se dibuja.
 */
public record DamageNumberPacket(int victimId, int amount, int flags) implements CustomPacketPayload {

    /** Golpe de Black Flash. */
    public static final int FLAG_BLACK_FLASH = 1;
    /** La víctima tenía la guardia arriba. */
    public static final int FLAG_BLOCKED = 2;
    /** Daño de técnica de ki (el resto es físico). */
    public static final int FLAG_KI = 4;

    public static final Type<DamageNumberPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "damage_number"));

    public static final StreamCodec<FriendlyByteBuf, DamageNumberPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DamageNumberPacket::victimId,
                    ByteBufCodecs.VAR_INT, DamageNumberPacket::amount,
                    ByteBufCodecs.VAR_INT, DamageNumberPacket::flags,
                    DamageNumberPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DamageNumberPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> DamageNumberRenderer.add(pkt.victimId(), pkt.amount(), pkt.flags()));
    }

    /**
     * Manda el número al atacante si procede: es un jugador con Ki Sense, la víctima no es él
     * mismo y el daño es positivo.
     * @param amount daño real (en unidades de la víctima: body para entidades con stats, vida vanilla para el resto)
     */
    public static void sendToAttacker(Entity attacker, LivingEntity victim, double amount, int flags) {
        if (!(attacker instanceof ServerPlayer sp) || attacker == victim) return;
        int shown = (int) Math.round(amount);
        if (shown <= 0) return;
        if (SkillEffects.senseLevel(sp) <= 0) return;
        PacketDistributor.sendToPlayer(sp, new DamageNumberPacket(victim.getId(), shown, flags));
    }
}
