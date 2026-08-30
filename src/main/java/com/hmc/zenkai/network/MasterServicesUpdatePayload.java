package com.hmc.zenkai.network;

import com.hmc.zenkai.Zenkai;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: refresca la lista de servicios de la pantalla ABIERTA tras un claim exitoso (contador
 * de Korin bajando, etiqueta de Kami/Kaio cambiando) — sin reabrir MasterScreen entera, que
 * reiniciaría su Mode a HUB. Ver MasterServicePacket, quien lo dispara.
 */
public record MasterServicesUpdatePayload(List<OpenMasterPayload.ServiceEntry> services)
        implements CustomPacketPayload {

    public static final Type<MasterServicesUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "master_services_update"));

    public static final StreamCodec<FriendlyByteBuf, MasterServicesUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, OpenMasterPayload.ServiceEntry.STREAM_CODEC),
                    MasterServicesUpdatePayload::services,
                    MasterServicesUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
