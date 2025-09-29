package com.hmc.db_renewed.network;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.gui.ShenlongWishScreen;
import com.hmc.db_renewed.gui.wishes.StackWishMenu;
import com.hmc.db_renewed.network.wishes.*;
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
                ConfirmWishPayload.TYPE,
                ConfirmWishPayload.STREAM_CODEC,
                ConfirmWishPayloadHandler::handle
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

    }
}