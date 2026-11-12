package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.client.gui.screens.RaceSelectionScreen;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncPlayerStatsPacket(int entityId, CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncPlayerStatsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "sync_player_stats"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerStatsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,      SyncPlayerStatsPacket::entityId,
                    ByteBufCodecs.COMPOUND_TAG, SyncPlayerStatsPacket::data,
                    SyncPlayerStatsPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Construye el paquete a partir del jugador objetivo (guarda su entityId). */
    public static SyncPlayerStatsPacket from(Player target) {
        PlayerStatsAttachment att = target.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        return new SyncPlayerStatsPacket(target.getId(), att.save());
    }

    // Cliente: aplicar al jugador correcto (resuelto por entityId), NO siempre al LocalPlayer.
    public static void handle(SyncPlayerStatsPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> applyClient(pkt));
    }

    @OnlyIn(Dist.CLIENT)
    private static void applyClient(SyncPlayerStatsPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || msg.data() == null) return;

        Entity e = mc.level.getEntity(msg.entityId());
        if (!(e instanceof Player p)) return;

        PlayerStatsAttachment att = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        // Creación de personaje: la raza elegida es un PREVIEW que solo vive en el cliente hasta Confirmar (StyleSelectionScreen
        // la manda con ChooseRacePacket leyendo este attachment). Un sync a mitad del flujo la pisaba con la del servidor (sin
        // raza, Humano) y el personaje salía Humano: «confirmaba pero no quedaba» (2026-11-12, reproducido con RaceFlowShots;
        // en el mundo del usuario el jugador estaba de visita en la Tierra y EarthVisit sincroniza cada segundo). Antes había
        // un guard que descartaba el sync ENTERO, y solo en la pantalla de raza: las de apariencia, cabeza, cuerpo y estilo
        // quedaban sin proteger. Ahora el sync se aplica (TP, temporizadores…) y solo se conserva el preview de raza mientras el
        // servidor aún no tenga una elegida.
        boolean ownCreation = mc.player != null && mc.player.getId() == msg.entityId() && inCreationFlow(mc);
        Race previewRace = att.getRace();
        boolean previewChosen = att.isRaceChosen();
        att.load(msg.data());
        if (ownCreation && !att.isRaceChosen()) {
            att.setRace(previewRace);
            att.setRaceChosen(previewChosen);
        }
    }

    /** ¿Está abierta alguna pantalla del flujo de creación (raza → apariencia → cabeza → cuerpo → estilo)? */
    @OnlyIn(Dist.CLIENT)
    private static boolean inCreationFlow(Minecraft mc) {
        var s = mc.screen;
        return s instanceof RaceSelectionScreen
                || s instanceof com.hmc.zenkai.client.gui.screens.AppearanceScreen
                || s instanceof com.hmc.zenkai.client.gui.screens.HeadAppearanceScreen
                || s instanceof com.hmc.zenkai.client.gui.screens.BodyColorsScreen
                || s instanceof com.hmc.zenkai.client.gui.screens.StyleSelectionScreen;
    }
}