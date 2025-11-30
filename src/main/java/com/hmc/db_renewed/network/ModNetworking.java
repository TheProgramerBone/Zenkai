package com.hmc.db_renewed.network;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.gui.ShenlongWishScreen;
import com.hmc.db_renewed.gui.wishes.StackWishMenu;
import com.hmc.db_renewed.network.ki.*;
import com.hmc.db_renewed.network.stats.SpendTpPacket;
import com.hmc.db_renewed.network.stats.SyncPlayerStatsPacket;
import com.hmc.db_renewed.network.wishes.*;
import com.hmc.db_renewed.network.wishes.StackWishPayloadHandler;
import com.hmc.db_renewed.network.wishes.SetGhostSlotPayloadHandler;
import com.hmc.db_renewed.network.wishes.WishImmortalPayloadHandler;
import com.hmc.db_renewed.network.wishes.WishRevivePlayerPayloadHandler;
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
                StackWishPayloadHandler::handle
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
                SetGhostSlotPayloadHandler::handle
        );

        registrar.playToServer(
                WishImmortalPayload.TYPE,
                WishImmortalPayload.STREAM_CODEC,
                WishImmortalPayloadHandler::handle
        );

        registrar.playToServer(
                WishRevivePlayerPayload.TYPE,
                WishRevivePlayerPayload.STREAM_CODEC,
                WishRevivePlayerPayloadHandler::handle
        );

        registrar.playToClient(
                SyncPlayerStatsPacket.TYPE,
                SyncPlayerStatsPacket.STREAM_CODEC,
                SyncPlayerStatsPacket::handle
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
    }
}