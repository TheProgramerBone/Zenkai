package com.hmc.zenkai.network;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import com.hmc.zenkai.feature.master.MasterManager;
import com.hmc.zenkai.feature.master.MasterService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: reclama un servicio desde la pestaña "Servicios" de MasterScreen (ver MasterService).
 * Revalida por el MISMO embudo que Skills/Técnicas (MasterManager.check) — la pantalla solo
 * llegó a mostrarse porque el clic derecho ya pasó, pero un cliente modificado podría mandar
 * este packet sin haber abierto nunca la pantalla, así que se repite la comprobación aquí,
 * igual que SkillBuyPacket/TechniquePacket ya hacen para sus propias compras.
 */
public record MasterServicePacket(String masterId, int entityId, String serviceId)
        implements CustomPacketPayload {

    public static final Type<MasterServicePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "master_service"));

    public static final StreamCodec<FriendlyByteBuf, MasterServicePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MasterServicePacket::masterId,
                    ByteBufCodecs.VAR_INT,     MasterServicePacket::entityId,
                    ByteBufCodecs.STRING_UTF8, MasterServicePacket::serviceId,
                    MasterServicePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MasterServicePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            Entity e = sp.level().getEntity(pkt.entityId());
            if (!(e instanceof ZenkaiMasterEntity master) || !pkt.masterId().equals(master.masterId())) return;

            if (!MasterManager.check(sp, pkt.masterId(), master).ok()) return;

            MasterService service = master.service(pkt.serviceId());
            if (service == null) return;

            service.claim(sp);

            // Refresca la pantalla (contador de Korin, etiqueta de Kami/Kaio) sin reabrirla —
            // reenviar OpenMasterPayload entero reiniciaría el modo (mode = HUB) de golpe.
            PacketDistributor.sendToPlayer(sp,
                    new MasterServicesUpdatePayload(master.serviceEntries(sp)));
        });
    }
}
