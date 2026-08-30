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
 * Servidor → cliente: abre la tienda de un maestro.
 * Lo que enseña sale de las SkillDef/TechniqueDef, que ya están sincronizadas, y los
 * requisitos no se muestran (si el paquete llega, es que ya pasaste). El entityId es para
 * dibujar al maestro en el panel izquierdo.
 * services: lo que ese maestro ofrece en la pestaña "Servicios" (ZenkaiMasterEntity.services()),
 * YA resuelto por jugador server-side (p. ej. "Semilla del día (3/5)") — a diferencia de
 * Skills/Técnicas, un servicio no tiene estado sincronizado por su cuenta (KorinSenzuData no
 * viaja al cliente para nada más), así que viaja aquí en el momento de abrir; un claim exitoso
 * lo refresca con MasterServicesUpdatePayload sin reabrir la pantalla entera.
 */
public record OpenMasterPayload(String masterId, int entityId, List<ServiceEntry> services)
        implements CustomPacketPayload {

    /** label ya como String resuelto (no Component): un servicio manda su propio texto
     *  final ("3/5 hoy"), no una clave de traducción que el cliente tenga que rellenar. */
    public record ServiceEntry(String id, String label) {
        public static final StreamCodec<FriendlyByteBuf, ServiceEntry> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ServiceEntry::id,
                        ByteBufCodecs.stringUtf8(256), ServiceEntry::label,
                        ServiceEntry::new);
    }

    public static final Type<OpenMasterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "master_open"));

    public static final StreamCodec<FriendlyByteBuf, OpenMasterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, OpenMasterPayload::masterId,
                    ByteBufCodecs.VAR_INT,     OpenMasterPayload::entityId,
                    ByteBufCodecs.collection(ArrayList::new, ServiceEntry.STREAM_CODEC),
                    OpenMasterPayload::services,
                    OpenMasterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}