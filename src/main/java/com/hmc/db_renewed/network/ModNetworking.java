package com.hmc.db_renewed.network;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.entity.ModEntities;
import com.hmc.db_renewed.gui.ShenlongWishScreen;
import com.hmc.db_renewed.gui.StackWishMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
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
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    Minecraft.getInstance().setScreen(new ShenlongWishScreen());
                })
        );
    }
}