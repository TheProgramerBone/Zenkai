package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: alternar un interruptor de habilidad. UNO SOLO para el conjunto de interruptores
 * presentes y futuros — la carga útil es el id, así que añadir Ki Fist, el arma de ki o lo
 * que venga después no obliga a registrar otro payload (ni a tocar ModNetworking otra vez).
 *
 * La rueda NO usa este packet: ya tiene el suyo (WheelSelectPacket con kind TOGGLE). Este es
 * para las TECLAS, que es como se van a alternar Ki Fist y el arma de ki en mitad de una
 * pelea. Los dos caminos desembocan en SkillToggles.flip, que es quien valida.
 *
 * No hay respuesta S2C: flip() sincroniza el attachment de stats y con eso el cliente se
 * entera por el canal de siempre.
 */
public record SkillTogglePacket(String skillId) implements CustomPacketPayload {

    public static final Type<SkillTogglePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "skill_toggle"));

    public static final StreamCodec<FriendlyByteBuf, SkillTogglePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeUtf(pkt.skillId()),
                    buf -> new SkillTogglePacket(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkillTogglePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                SkillToggles.flip(sp, pkt.skillId());
            }
        });
    }
}