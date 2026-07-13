package com.hmc.zenkai.core.network.feature.skills;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.skills.SkillDef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C: snapshot completo de las definiciones de habilidad (login y /reload).
 * El cliente reemplaza su registro entero: la GUI siempre ve lo del servidor.
 */
public record SkillSyncPacket(List<SkillDef> skills) implements CustomPacketPayload {

    public static final Type<SkillSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "skill_sync"));

    public static final StreamCodec<FriendlyByteBuf, SkillSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    SkillDef.STREAM_CODEC.apply(ByteBufCodecs.list()), SkillSyncPacket::skills,
                    SkillSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SkillSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Map<String, SkillDef> map = new LinkedHashMap<>();
            for (SkillDef d : pkt.skills()) map.put(d.id(), d);
            SkillDef.replaceAll(map);
        });
    }
}