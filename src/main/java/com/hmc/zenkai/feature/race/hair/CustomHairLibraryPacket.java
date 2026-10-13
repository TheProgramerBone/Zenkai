package com.hmc.zenkai.feature.race.hair;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.race.hair.strand.HairCodeException;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: añadir o quitar un peinado de la biblioteca personal (pestaña Pelo). Va aparte de
 * UpdatePlayerVisualPacket porque la biblioteca se guarda AL MOMENTO (no se deshace al cancelar
 * la pantalla) y porque un código puede medir 30 KB.
 *
 * El servidor no se fía de la pantalla: vuelve a leer el código entero (CustomHairLibrary.prepare)
 * y comprueba la opción del mundo, la raza y los topes. Responde con la biblioteca resultante
 * (CustomHairLibrarySyncPacket), que corrige la copia optimista del cliente si algo falló.
 *
 * @param add  true = añadir (name, code); false = quitar la entrada con id = code.
 */
public record CustomHairLibraryPacket(boolean add, String name, String code) implements CustomPacketPayload {

    public static final Type<CustomHairLibraryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "custom_hair_library"));

    public static final StreamCodec<FriendlyByteBuf, CustomHairLibraryPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.add);
                buf.writeUtf(p.name, CustomHairLibrary.MAX_NAME_LENGTH * 4);
                buf.writeUtf(p.code, CustomHairLibrary.MAX_CODE_LENGTH + 64);
            },
            buf -> new CustomHairLibraryPacket(buf.readBoolean(),
                    buf.readUtf(CustomHairLibrary.MAX_NAME_LENGTH * 4),
                    buf.readUtf(CustomHairLibrary.MAX_CODE_LENGTH + 64)));

    public static CustomHairLibraryPacket add(String name, String code) {
        return new CustomHairLibraryPacket(true, name, code);
    }

    public static CustomHairLibraryPacket remove(String id) {
        return new CustomHairLibraryPacket(false, "", id);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CustomHairLibraryPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PlayerVisualAttachment visual = sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
            Race race = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).getRace();
            if (pkt.add) {
                try {
                    CustomHairLibrary.Entry e = tryAdd(visual, race, ServerConfig.allowCustomHair(), pkt.name, pkt.code);
                    sp.displayClientMessage(Component.translatable("zenkai.hair_library.added", e.name()), true);
                } catch (HairCodeException ex) {
                    sp.displayClientMessage(ex.component(), true);
                }
            } else if (remove(visual, race, pkt.code)) {
                // Llevaba puesto el que se borró: los demás tienen que dejar de verlo.
                PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
            }
            PacketDistributor.sendToPlayer(sp, new CustomHairLibrarySyncPacket(visual.getCustomHairs()));
        });
    }

    /** Reglas de añadir (función pura sobre el attachment, con gametest). */
    public static CustomHairLibrary.Entry tryAdd(PlayerVisualAttachment visual, Race race, boolean allowed,
                                                 String name, String code) throws HairCodeException {
        if (!allowed) throw new HairCodeException("disabled");
        if (HairCatalog.options(race).isEmpty()) throw new HairCodeException("no_hair_race");
        CustomHairLibrary.Entry e = CustomHairLibrary.prepare(visual.getCustomHairs(), name, code);
        visual.addCustomHair(e);
        return e;
    }

    /** Quita una entrada. Devuelve true si era el peinado puesto (y lo cambia por el de la raza). */
    public static boolean remove(PlayerVisualAttachment visual, Race race, String id) {
        boolean wasActive = visual.activeCustomHair() != null && visual.activeCustomHair().id().equals(id);
        if (!visual.removeCustomHair(id)) return false;
        if (wasActive) HairCatalog.reconcile(race, visual);
        return wasActive;
    }
}
