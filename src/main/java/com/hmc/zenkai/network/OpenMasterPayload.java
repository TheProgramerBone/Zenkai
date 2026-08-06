package com.hmc.zenkai.network;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor → cliente: abre la tienda de un maestro.
 * Solo dos campos porque no hace falta más: lo que enseña sale de las SkillDef, que ya están
 * sincronizadas, y los requisitos no se muestran (si el paquete llega, es que ya pasaste).
 * El entityId es para dibujar al maestro en el panel izquierdo.
 */
public record OpenMasterPayload(String masterId, int entityId) implements CustomPacketPayload {

    public static final Type<OpenMasterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "master_open"));

    public static final StreamCodec<FriendlyByteBuf, OpenMasterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenMasterPayload::masterId,
                    ByteBufCodecs.VAR_INT,     OpenMasterPayload::entityId,
                    OpenMasterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}