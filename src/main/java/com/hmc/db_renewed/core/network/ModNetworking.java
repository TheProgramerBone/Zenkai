package com.hmc.db_renewed.core.network;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.client.gui.screens.ShenlongWishScreen;
import com.hmc.db_renewed.client.gui.StackWishMenu;
import com.hmc.db_renewed.core.network.feature.ki.*;
import com.hmc.db_renewed.core.network.feature.stats.*;
import com.hmc.db_renewed.core.network.feature.wishes.*;
import com.hmc.db_renewed.core.network.vehicle.VehicleControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetworking {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(DragonBlockRenewed.MOD_ID).versioned("1");

        registrar.playToServer(
                StackWishPayload.TYPE,
                StackWishPayload.STREAM_CODEC,
                StackWishPayload.StackWishPayloadHandler::handle
        );

        registrar.playToClient(
                OpenWishScreenPayload.TYPE,
                OpenWishScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        Minecraft.getInstance().setScreen(new ShenlongWishScreen());
                    });
                }
        );

        registrar.playToServer(
                OpenStackWishPayload.TYPE,
                OpenStackWishPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        ServerPlayer sp = (ServerPlayer) context.player();
                        sp.openMenu(new SimpleMenuProvider(
                                (id, inv, ply) -> new StackWishMenu(id, inv),
                                Component.translatable("screen.db_renewed.option.stack")
                        ));
                    });
                }
        );

        registrar.playToServer(
                SetGhostSlotPayload.TYPE,
                SetGhostSlotPayload.STREAM_CODEC,
                SetGhostSlotPayload.SetGhostSlotPayloadHandler::handle
        );

        registrar.playToServer(
                WishImmortalPayload.TYPE,
                WishImmortalPayload.STREAM_CODEC,
                WishImmortalPayload.WishImmortalPayloadHandler::handle
        );

        registrar.playToServer(
                WishRevivePlayerPayload.TYPE,
                WishRevivePlayerPayload.STREAM_CODEC,
                WishRevivePlayerPayload.WishRevivePlayerPayloadHandler::handle
        );

        registrar.playToClient(
                SyncPlayerStatsPacket.TYPE,
                SyncPlayerStatsPacket.STREAM_CODEC,
                SyncPlayerStatsPacket::handle
        );

        registrar.playToClient(
                SyncPlayerVisualPacket.TYPE,
                SyncPlayerVisualPacket.STREAM_CODEC,
                SyncPlayerVisualPacket::handle
        );

        registrar.playToServer(
                SpendTpPacket.TYPE,
                SpendTpPacket.STREAM_CODEC,
                SpendTpPacket::handle);

        registrar.playToServer(ToggleFlyPacket.TYPE, ToggleFlyPacket.STREAM_CODEC, ToggleFlyPacket::handle);
        registrar.playToServer(KiChargePacket.TYPE,  KiChargePacket.STREAM_CODEC,  KiChargePacket::handle);

        registrar.playToServer(
                UpdateKiAttackColorPacket.TYPE,
                UpdateKiAttackColorPacket.CODEC,
                UpdateKiAttackColorPacket::handle
        );

        registrar.playToServer(
                ChargeKiAttackPacket.TYPE,
                ChargeKiAttackPacket.STREAM_CODEC,
                ChargeKiAttackPacket::handle
        );

        registrar.playToServer(
                ChooseRacePacket.TYPE,
                ChooseRacePacket.STREAM_CODEC,
                ChooseRacePacket::handle
        );

        registrar.playToServer(
                ChooseStylePacket.TYPE,
                ChooseStylePacket.STREAM_CODEC,
                ChooseStylePacket::handle
        );

        registrar.playToServer(
                VehicleControlPayload.TYPE,
                VehicleControlPayload.STREAM_CODEC,
                VehicleControlPayload::handle
        );

        registrar.playBidirectional(
                TransformHoldPacket.TYPE,
                TransformHoldPacket.STREAM_CODEC,
                TransformHoldPacket::handle
        );
    }
}