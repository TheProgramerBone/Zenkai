package com.hmc.zenkai.core.network;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.ShenlongWishScreen;
import com.hmc.zenkai.client.gui.StackWishMenu;
import com.hmc.zenkai.client.gui.screens.wishes.ClientWishToggles;
import com.hmc.zenkai.core.network.feature.combat.BlockingPacket;
import com.hmc.zenkai.core.network.feature.combat.BlockingSyncPacket;
import com.hmc.zenkai.core.network.feature.combat.CombatModePacket;
import com.hmc.zenkai.core.network.feature.combat.CombatModeSyncPacket;
import com.hmc.zenkai.core.network.feature.ki.*;
import com.hmc.zenkai.core.network.feature.player.SyncPlayerFormPacket;
import com.hmc.zenkai.core.network.feature.player.SyncPlayerStatsPacket;
import com.hmc.zenkai.core.network.feature.player.SyncPlayerVisualPacket;
import com.hmc.zenkai.core.network.feature.race.UpdatePlayerVisualPacket;
import com.hmc.zenkai.core.network.feature.sense.*;
import com.hmc.zenkai.core.network.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.core.network.feature.skills.SkillSyncPacket;
import com.hmc.zenkai.core.network.feature.stats.*;
import com.hmc.zenkai.core.network.feature.technique.KiFirePacket;
import com.hmc.zenkai.core.network.feature.technique.TechniquePacket;
import com.hmc.zenkai.core.network.feature.training.TrainingSwingPacket;
import com.hmc.zenkai.core.network.feature.wishes.*;
import com.hmc.zenkai.core.network.vehicle.VehicleControlPayload;
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
        PayloadRegistrar registrar = event.registrar(Zenkai.MOD_ID).versioned("1");

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
                                Component.translatable("screen.zenkai.option.stack")
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
                SyncPlayerFormPacket.TYPE,
                SyncPlayerFormPacket.STREAM_CODEC,
                SyncPlayerFormPacket::handle
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
        registrar.playToServer(FlyBoostPacket.TYPE,  FlyBoostPacket.STREAM_CODEC,  FlyBoostPacket::handle);

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

        registrar.playToServer(
                TransformHoldPacket.TYPE,
                TransformHoldPacket.STREAM_CODEC,
                TransformHoldPacket::handle
        );

        registrar.playToServer(
                UpdatePlayerVisualPacket.TYPE,
                UpdatePlayerVisualPacket.STREAM_CODEC,
                UpdatePlayerVisualPacket::handle
        );

        registrar.playToServer(
                ConfirmVillagerWishPayload.TYPE,
                ConfirmVillagerWishPayload.STREAM_CODEC,
                ConfirmVillagerWishPayload.ConfirmVillagerWishPayloadHandler::handle
        );

        registrar.playToServer(
                WishTrainingPointsPayload.TYPE,
                WishTrainingPointsPayload.STREAM_CODEC,
                WishTrainingPointsPayload.WishTrainingPointsPayloadHandler::handle
        );

        registrar.playToClient(
                SyncWishTogglesPayload.TYPE,
                SyncWishTogglesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientWishToggles.apply(payload))
        );

        registrar.playToServer(
                WishRevivePetPayload.TYPE,
                WishRevivePetPayload.STREAM_CODEC,
                WishRevivePetPayload.Handler::handle
        );

        registrar.playToServer(
                SenseKiScanPacket.TYPE,
                SenseKiScanPacket.STREAM_CODEC,
                SenseKiScanPacket::handle);
        registrar.playToClient(
                SenseKiDataPacket.TYPE,
                SenseKiDataPacket.STREAM_CODEC,
                SenseKiDataPacket::handle);

        registrar.playToServer(
                ScouterScanPacket.TYPE,
                ScouterScanPacket.STREAM_CODEC,
                ScouterScanPacket::handle);
        registrar.playToClient(
                ScouterDataPacket.TYPE,
                ScouterDataPacket.STREAM_CODEC,
                ScouterDataPacket::handle);

        registrar.playToServer(
                ScouterAreaScanPacket.TYPE,
                ScouterAreaScanPacket.STREAM_CODEC,
                ScouterAreaScanPacket::handle);

        registrar.playToClient(
                ScouterAreaDataPacket.TYPE,
                ScouterAreaDataPacket.STREAM_CODEC,
                ScouterAreaDataPacket::handle);

        registrar.playToServer(
                TrainingSwingPacket.TYPE,
                TrainingSwingPacket.STREAM_CODEC,
                TrainingSwingPacket::handle);

        registrar.playToServer(
                FlyAnimPacket.TYPE,
                FlyAnimPacket.STREAM_CODEC,
                FlyAnimPacket::handle);
        registrar.playToClient(
                FlyAnimSyncPacket.TYPE,
                FlyAnimSyncPacket.STREAM_CODEC,
                FlyAnimSyncPacket::handle);

        registrar.playToServer(
                SkillBuyPacket.TYPE,
                SkillBuyPacket.STREAM_CODEC,
                SkillBuyPacket::handle);

        registrar.playToServer(
                TechniquePacket.TYPE,
                TechniquePacket.STREAM_CODEC,
                TechniquePacket::handle);

        registrar.playToServer(
                KiFirePacket.TYPE,
                KiFirePacket.STREAM_CODEC,
                KiFirePacket::handle);

        registrar.playToServer(
                CombatModePacket.TYPE,
                CombatModePacket.STREAM_CODEC,
                CombatModePacket::handle);

        registrar.playToClient(
                CombatModeSyncPacket.TYPE,
                CombatModeSyncPacket.STREAM_CODEC,
                CombatModeSyncPacket::handle);

        registrar.playToServer(
                BlockingPacket.TYPE,
                BlockingPacket.STREAM_CODEC,
                BlockingPacket::handle);

        registrar.playToClient(
                BlockingSyncPacket.TYPE,
                BlockingSyncPacket.STREAM_CODEC,
                BlockingSyncPacket::handle);

        registrar.playToClient(
                SkillSyncPacket.TYPE,
                SkillSyncPacket.STREAM_CODEC,
                SkillSyncPacket::handle);
    }
}