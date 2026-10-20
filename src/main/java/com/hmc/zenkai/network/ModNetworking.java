package com.hmc.zenkai.network;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.ClientPayloadHandlers;
import com.hmc.zenkai.client.gui.menu.StackWishMenu;
import com.hmc.zenkai.content.block.PieceConnectorBlock;
import com.hmc.zenkai.content.blockentity.NpcMarkerBlockEntity;
import com.hmc.zenkai.content.blockentity.PieceConnectorBlockEntity;
import com.hmc.zenkai.feature.aura.AuraSignatureSyncPacket;
import com.hmc.zenkai.feature.aura.TurboPacket;
import com.hmc.zenkai.feature.aura.TurboSyncPacket;
import com.hmc.zenkai.feature.combat.BlockingPacket;
import com.hmc.zenkai.feature.combat.BlockingSyncPacket;
import com.hmc.zenkai.feature.combat.CombatModePacket;
import com.hmc.zenkai.feature.combat.DashPacket;
import com.hmc.zenkai.feature.combat.CombatModeSyncPacket;
import com.hmc.zenkai.feature.kiweapon.KiWeaponSyncPacket;
import com.hmc.zenkai.feature.forms.FormSyncPacket;
import com.hmc.zenkai.feature.ki.*;
import com.hmc.zenkai.feature.skills.OverlayStateSyncPacket;
import com.hmc.zenkai.feature.player.SyncPlayerFormPacket;
import com.hmc.zenkai.feature.player.SyncPlayerStatsPacket;
import com.hmc.zenkai.feature.player.SyncPlayerVisualPacket;
import com.hmc.zenkai.feature.race.UpdatePlayerVisualPacket;
import com.hmc.zenkai.feature.sense.*;
import com.hmc.zenkai.feature.skills.ForgetSkillPacket;
import com.hmc.zenkai.feature.skills.SkillBuyPacket;
import com.hmc.zenkai.feature.skills.SkillSyncPacket;
import com.hmc.zenkai.feature.skills.SkillTogglePacket;
import com.hmc.zenkai.feature.stats.*;
import com.hmc.zenkai.feature.technique.*;
import com.hmc.zenkai.feature.training.MeditationSessionPacket;
import com.hmc.zenkai.feature.training.ShadowPotentialRequestPacket;
import com.hmc.zenkai.feature.training.ShadowSessionResultPacket;
import com.hmc.zenkai.feature.training.StartShadowTrainingPacket;
import com.hmc.zenkai.feature.training.TargetPracticeSessionPacket;
import com.hmc.zenkai.feature.training.TrainingFatiguePacket;
import com.hmc.zenkai.feature.training.TrainingFatigueRequestPacket;
import com.hmc.zenkai.feature.training.TrainingInfoPacket;
import com.hmc.zenkai.feature.training.TrainingInfoRequestPacket;
import com.hmc.zenkai.feature.training.TrainingSessionRewardPacket;
import com.hmc.zenkai.feature.training.TrainingSwingPacket;
import com.hmc.zenkai.feature.weights.SetWeightPacket;
import com.hmc.zenkai.feature.wheel.WheelSelectPacket;
import com.hmc.zenkai.feature.wishes.*;
import com.hmc.zenkai.network.vehicle.VehicleControlPayload;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.state.BlockState;
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
                // Delegado a ClientPayloadHandlers, NO inline: un new ShenlongWishScreen() (subclase
                // de Screen) metido a mano en este lambda vive dentro de ModNetworking, y cargar esta
                // clase para tomar la función register() (Zenkai.<init>) verificaba también ese cuerpo
                // — RuntimeDistCleaner abortaba con BootstrapMethodError en cualquier dedicated
                // server, sin que ningún cliente lo notara nunca. El resto de playToClient de este
                // archivo ya delega en ClientPayloadHandlers por esta misma razón (ver su javadoc).
                (payload, context) -> context.enqueueWork(ClientPayloadHandlers::openWishScreen)
        );

        registrar.playToServer(
                OpenStackWishPayload.TYPE,
                OpenStackWishPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ServerPlayer sp = (ServerPlayer) context.player();
                    sp.openMenu(new SimpleMenuProvider(
                            (id, inv, ply) -> new StackWishMenu(id, inv),
                            Component.translatable("screen.zenkai.option.stack")
                    ));
                })
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

        registrar.playToServer(RefundTpPacket.TYPE,
                RefundTpPacket.STREAM_CODEC,
                RefundTpPacket::handle);

        registrar.playToServer(SetPowerPercentPacket.TYPE,
                SetPowerPercentPacket.STREAM_CODEC,
                SetPowerPercentPacket::handle);

        registrar.playToServer(
                KiChargePacket.TYPE,
                KiChargePacket.STREAM_CODEC,
                KiChargePacket::handle);
        registrar.playToServer(
                OverdriveChargePacket.TYPE,
                OverdriveChargePacket.STREAM_CODEC,
                OverdriveChargePacket::handle);
        registrar.playToServer(
                PowerPercentPacket.TYPE,
                PowerPercentPacket.STREAM_CODEC,
                PowerPercentPacket::handle);

        registrar.playToServer(
                FlyBoostPacket.TYPE,
                FlyBoostPacket.STREAM_CODEC,
                FlyBoostPacket::handle);

        registrar.playToServer(
                FlyThrustPacket.TYPE,
                FlyThrustPacket.STREAM_CODEC,
                FlyThrustPacket::handle);

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
                StartShadowTrainingPacket.TYPE,
                StartShadowTrainingPacket.STREAM_CODEC,
                StartShadowTrainingPacket::handle);

        registrar.playToServer(
                ShadowPotentialRequestPacket.TYPE,
                ShadowPotentialRequestPacket.STREAM_CODEC,
                ShadowPotentialRequestPacket::handle);

        registrar.playToServer(
                MeditationSessionPacket.TYPE,
                MeditationSessionPacket.STREAM_CODEC,
                MeditationSessionPacket::handle);

        registrar.playToServer(
                TargetPracticeSessionPacket.TYPE,
                TargetPracticeSessionPacket.STREAM_CODEC,
                TargetPracticeSessionPacket::handle);

        // Delegado a ClientPayloadHandlers, no inline (ver el javadoc de ese archivo): toca
        // Minecraft.getInstance().screen, una clase de cliente.
        registrar.playToClient(
                TrainingSessionRewardPacket.TYPE,
                TrainingSessionRewardPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientPayloadHandlers.onTrainingReward(payload.tpGranted(), payload.record())));

        registrar.playToServer(
                TrainingInfoRequestPacket.TYPE,
                TrainingInfoRequestPacket.STREAM_CODEC,
                TrainingInfoRequestPacket::handle);

        registrar.playToClient(
                TrainingInfoPacket.TYPE,
                TrainingInfoPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientPayloadHandlers.onTrainingInfo(
                                payload.minigame(), payload.record(), payload.potentialTp())));

        registrar.playToClient(
                ShadowSessionResultPacket.TYPE,
                ShadowSessionResultPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientPayloadHandlers.onShadowSessionResult(payload.earnedTp(), payload.record())));

        registrar.playToServer(
                TrainingFatigueRequestPacket.TYPE,
                TrainingFatigueRequestPacket.STREAM_CODEC,
                TrainingFatigueRequestPacket::handle);

        registrar.playToClient(
                TrainingFatiguePacket.TYPE,
                TrainingFatiguePacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientPayloadHandlers.onTrainingFatigue(payload.combatEfficiency(),
                                payload.meditationEfficiency(), payload.targetPracticeEfficiency())));

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

        registrar.playToServer(
                DashPacket.TYPE,
                DashPacket.STREAM_CODEC,
                DashPacket::handle);

        registrar.playToClient(
                CombatModeSyncPacket.TYPE,
                CombatModeSyncPacket.STREAM_CODEC,
                CombatModeSyncPacket::handle);

        registrar.playToClient(
                KiWeaponSyncPacket.TYPE,
                KiWeaponSyncPacket.STREAM_CODEC,
                KiWeaponSyncPacket::handle);

        registrar.playToServer(
                BlockingPacket.TYPE,
                BlockingPacket.STREAM_CODEC,
                BlockingPacket::handle);

        registrar.playToClient(
                BlockingSyncPacket.TYPE,
                BlockingSyncPacket.STREAM_CODEC,
                BlockingSyncPacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.combat.BasicStrikePacket.TYPE,
                com.hmc.zenkai.feature.combat.BasicStrikePacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.BasicStrikePacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.combat.BasicStrikeAnimPacket.TYPE,
                com.hmc.zenkai.feature.combat.BasicStrikeAnimPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.BasicStrikeAnimPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.combat.GrabStatePacket.TYPE,
                com.hmc.zenkai.feature.combat.GrabStatePacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.GrabStatePacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.combat.GrabEscapePressPacket.TYPE,
                com.hmc.zenkai.feature.combat.GrabEscapePressPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.GrabEscapePressPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.combat.StunSyncPacket.TYPE,
                com.hmc.zenkai.feature.combat.StunSyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.StunSyncPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.combat.GuardSyncPacket.TYPE,
                com.hmc.zenkai.feature.combat.GuardSyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.GuardSyncPacket::handle);

        registrar.playToClient(
                SkillSyncPacket.TYPE,
                SkillSyncPacket.STREAM_CODEC,
                SkillSyncPacket::handle);

        registrar.playToServer(
                PhysicalTechniquePacket.TYPE,
                PhysicalTechniquePacket.STREAM_CODEC,
                PhysicalTechniquePacket::handle);

        registrar.playToServer(
                PhysicalFirePacket.TYPE,
                PhysicalFirePacket.STREAM_CODEC,
                PhysicalFirePacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.technique.PhysicalChargePacket.TYPE,
                com.hmc.zenkai.feature.technique.PhysicalChargePacket.STREAM_CODEC,
                com.hmc.zenkai.feature.technique.PhysicalChargePacket::handle);

        registrar.playToServer(
                TurboPacket.TYPE,
                TurboPacket.STREAM_CODEC,
                TurboPacket::handle);

        registrar.playToClient(
                TurboSyncPacket.TYPE,
                TurboSyncPacket.STREAM_CODEC,
                TurboSyncPacket::handle);

        registrar.playToClient(
                TechniqueSyncPacket.TYPE,
                TechniqueSyncPacket.STREAM_CODEC,
                TechniqueSyncPacket::handle);

        registrar.playToServer(
                LockOnPacket.TYPE,
                LockOnPacket.STREAM_CODEC,
                LockOnPacket::handle);
        registrar.playToServer(
                com.hmc.zenkai.feature.party.PartyHudSelectionPacket.TYPE,
                com.hmc.zenkai.feature.party.PartyHudSelectionPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.party.PartyHudSelectionPacket::handle);
        registrar.playToClient(
                com.hmc.zenkai.feature.party.PartyVitalsPacket.TYPE,
                com.hmc.zenkai.feature.party.PartyVitalsPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.party.PartyVitalsPacket::handle);
        registrar.playToClient(
                com.hmc.zenkai.feature.sense.DamageNumberPacket.TYPE,
                com.hmc.zenkai.feature.sense.DamageNumberPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.sense.DamageNumberPacket::handle);
        registrar.playToClient(
                com.hmc.zenkai.feature.sense.LockOnAlertPacket.TYPE,
                com.hmc.zenkai.feature.sense.LockOnAlertPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.sense.LockOnAlertPacket::handle);

        registrar.playToClient(
                FormSyncPacket.TYPE,
                FormSyncPacket.STREAM_CODEC,
                FormSyncPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.race.face.FaceCatalogSyncPacket.TYPE,
                com.hmc.zenkai.feature.race.face.FaceCatalogSyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.race.face.FaceCatalogSyncPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.race.hair.HairCatalogSyncPacket.TYPE,
                com.hmc.zenkai.feature.race.hair.HairCatalogSyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.race.hair.HairCatalogSyncPacket::handle);

        // Biblioteca de peinados importados (pestaña Pelo): añadir/quitar y la lista de vuelta.
        registrar.playToServer(
                com.hmc.zenkai.feature.race.hair.CustomHairLibraryPacket.TYPE,
                com.hmc.zenkai.feature.race.hair.CustomHairLibraryPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.race.hair.CustomHairLibraryPacket::handle);
        registrar.playToClient(
                com.hmc.zenkai.feature.race.hair.CustomHairLibrarySyncPacket.TYPE,
                com.hmc.zenkai.feature.race.hair.CustomHairLibrarySyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.race.hair.CustomHairLibrarySyncPacket::handle);

        registrar.playToClient(
                OverlayStateSyncPacket.TYPE,
                OverlayStateSyncPacket.STREAM_CODEC,
                OverlayStateSyncPacket::handle);

        registrar.playToClient(
                AuraSignatureSyncPacket.TYPE,
                AuraSignatureSyncPacket.STREAM_CODEC,
                AuraSignatureSyncPacket::handle);

        registrar.playToServer(
                WheelSelectPacket.TYPE,
                WheelSelectPacket.STREAM_CODEC,
                WheelSelectPacket::handle);

        registrar.playToServer(
                KiChargeStartPacket.TYPE,
                KiChargeStartPacket.STREAM_CODEC,
                KiChargeStartPacket::handle);

        registrar.playToClient(
                KiChargeStatePacket.TYPE,
                KiChargeStatePacket.STREAM_CODEC,
                KiChargeStatePacket::handle);

        registrar.playToClient(
                RaceStatSyncPacket.TYPE,
                RaceStatSyncPacket.STREAM_CODEC,
                RaceStatSyncPacket::handle);

        registrar.playToServer(
                SkillTogglePacket.TYPE,
                SkillTogglePacket.STREAM_CODEC,
                SkillTogglePacket::handle);

        registrar.playToServer(
                SetWeightPacket.TYPE,
                SetWeightPacket.STREAM_CODEC,
                SetWeightPacket::handle);

        registrar.playToClient(OpenNpcMarkerPayload.TYPE, OpenNpcMarkerPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.openNpcMarker(payload)));

        registrar.playToServer(SaveNpcMarkerPayload.TYPE, SaveNpcMarkerPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    if (!sp.canUseGameMasterBlocks()) return;                 // creativo + permiso 2
                    if (!sp.level().isLoaded(payload.pos())) return;
                    if (sp.distanceToSqr(payload.pos().getCenter()) > 64 * 64) return;
                    if (!(sp.level().getBlockEntity(payload.pos()) instanceof NpcMarkerBlockEntity be)) return;

                    ResourceLocation type = ResourceLocation.tryParse(payload.npcType());
                    if (type == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(type)) {
                        sp.sendSystemMessage(Component.translatable(
                                "messages.zenkai.npc_marker.bad_type", payload.npcType()));
                        return;
                    }
                    be.applyFrom(type, payload.yaw(), payload.offX(), payload.offY(), payload.offZ(), payload.respawnSeconds());
                    if (payload.respawn()) be.forceRespawn();
                }));

        registrar.playToClient(OpenPieceConnectorPayload.TYPE, OpenPieceConnectorPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.openPieceConnector(payload)));

        registrar.playToServer(SavePieceConnectorPayload.TYPE, SavePieceConnectorPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    if (!sp.canUseGameMasterBlocks()) return;                 // creativo + permiso 2
                    if (!sp.level().isLoaded(payload.pos())) return;
                    if (sp.distanceToSqr(payload.pos().getCenter()) > 64 * 64) return;
                    if (!(sp.level().getBlockEntity(payload.pos()) instanceof PieceConnectorBlockEntity be)) return;
                    BlockState state = sp.level().getBlockState(payload.pos());
                    if (!(state.getBlock() instanceof PieceConnectorBlock)) return;

                    ResourceLocation socket = ResourceLocation.tryParse(payload.socket());
                    if (socket == null) {
                        sp.sendSystemMessage(Component.translatable(
                                "messages.zenkai.piece_connector.bad_socket", payload.socket()));
                        return;
                    }
                    be.setSocket(socket);

                    Direction facing = Direction.byName(payload.facing());
                    if (facing != null && state.getValue(PieceConnectorBlock.FACING) != facing) {
                        sp.level().setBlock(payload.pos(), state.setValue(PieceConnectorBlock.FACING, facing), 3);
                    }
                }));

        registrar.playToServer(
                ForgetSkillPacket.TYPE,
                ForgetSkillPacket.STREAM_CODEC,
                ForgetSkillPacket::handle);

        registrar.playToClient(
                ScouterUpgradeSyncPacket.TYPE,
                ScouterUpgradeSyncPacket.STREAM_CODEC,
                ScouterUpgradeSyncPacket::handle);

        registrar.playToClient(OpenMasterPayload.TYPE, OpenMasterPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.openMaster(payload)));

        registrar.playToServer(MasterServicePacket.TYPE, MasterServicePacket.STREAM_CODEC,
                MasterServicePacket::handle);

        registrar.playToClient(MasterServicesUpdatePayload.TYPE, MasterServicesUpdatePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.updateMasterServices(payload)));

        registrar.playToServer(ScouterTintPacket.TYPE, ScouterTintPacket.STREAM_CODEC,
                ScouterTintPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.action.ActionStateSyncPacket.TYPE,
                com.hmc.zenkai.feature.action.ActionStateSyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.action.ActionStateSyncPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.action.ActionRejectPacket.TYPE,
                com.hmc.zenkai.feature.action.ActionRejectPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.action.ActionRejectPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.generator.GeneratorFuelSyncPacket.TYPE,
                com.hmc.zenkai.feature.generator.GeneratorFuelSyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.generator.GeneratorFuelSyncPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.party.PartySyncPacket.TYPE,
                com.hmc.zenkai.feature.party.PartySyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.party.PartySyncPacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.teleport.InstantTransmissionHoldPacket.TYPE,
                com.hmc.zenkai.feature.teleport.InstantTransmissionHoldPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.InstantTransmissionHoldPacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.teleport.InstantTransmissionConfirmPacket.TYPE,
                com.hmc.zenkai.feature.teleport.InstantTransmissionConfirmPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.InstantTransmissionConfirmPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.teleport.InstantTransmissionSyncPacket.TYPE,
                com.hmc.zenkai.feature.teleport.InstantTransmissionSyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.InstantTransmissionSyncPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.combat.DashAnimPacket.TYPE,
                com.hmc.zenkai.feature.combat.DashAnimPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.DashAnimPacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.combat.ParryPacket.TYPE,
                com.hmc.zenkai.feature.combat.ParryPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.ParryPacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.combat.ParryPrefPacket.TYPE,
                com.hmc.zenkai.feature.combat.ParryPrefPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.ParryPrefPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.combat.ParryCooldownPacket.TYPE,
                com.hmc.zenkai.feature.combat.ParryCooldownPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.ParryCooldownPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.combat.ParryAnimPacket.TYPE,
                com.hmc.zenkai.feature.combat.ParryAnimPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.combat.ParryAnimPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.technique.clash.ClashStartPacket.TYPE,
                com.hmc.zenkai.feature.technique.clash.ClashStartPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.technique.clash.ClashStartPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.technique.clash.ClashResultPacket.TYPE,
                com.hmc.zenkai.feature.technique.clash.ClashResultPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.technique.clash.ClashResultPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.technique.clash.ClashRosterPacket.TYPE,
                com.hmc.zenkai.feature.technique.clash.ClashRosterPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.technique.clash.ClashRosterPacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.technique.clash.ClashJoinPacket.TYPE,
                com.hmc.zenkai.feature.technique.clash.ClashJoinPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.technique.clash.ClashJoinPacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.technique.clash.ClashPressPacket.TYPE,
                com.hmc.zenkai.feature.technique.clash.ClashPressPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.technique.clash.ClashPressPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.technique.SwordFxPacket.TYPE,
                com.hmc.zenkai.feature.technique.SwordFxPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.technique.SwordFxPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.skills.UiDodgeAnimPacket.TYPE,
                com.hmc.zenkai.feature.skills.UiDodgeAnimPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.skills.UiDodgeAnimPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.teleport.InstantTransmissionAnimPacket.TYPE,
                com.hmc.zenkai.feature.teleport.InstantTransmissionAnimPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.InstantTransmissionAnimPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.teleport.InstantTransmissionReleaseAnimPacket.TYPE,
                com.hmc.zenkai.feature.teleport.InstantTransmissionReleaseAnimPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.InstantTransmissionReleaseAnimPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.teleport.OpenInstantTransmissionMenuPayload.TYPE,
                com.hmc.zenkai.feature.teleport.OpenInstantTransmissionMenuPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(ClientPayloadHandlers::openInstantTransmissionMenu));

        registrar.playToServer(
                com.hmc.zenkai.feature.teleport.TeleportRequestPacket.TYPE,
                com.hmc.zenkai.feature.teleport.TeleportRequestPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.TeleportRequestPacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.teleport.GenericDimensionTeleportPacket.TYPE,
                com.hmc.zenkai.feature.teleport.GenericDimensionTeleportPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.GenericDimensionTeleportPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.teleport.InstantTransmissionBlocklistSyncPacket.TYPE,
                com.hmc.zenkai.feature.teleport.InstantTransmissionBlocklistSyncPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.InstantTransmissionBlocklistSyncPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.teleport.InstantTransmissionMenuStatePacket.TYPE,
                com.hmc.zenkai.feature.teleport.InstantTransmissionMenuStatePacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.InstantTransmissionMenuStatePacket::handle);

        registrar.playToServer(
                com.hmc.zenkai.feature.teleport.PartyTeleportRequestPacket.TYPE,
                com.hmc.zenkai.feature.teleport.PartyTeleportRequestPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.teleport.PartyTeleportRequestPacket::handle);

        registrar.playToClient(
                com.hmc.zenkai.feature.spacepod.OpenGalacticMenuPayload.TYPE,
                com.hmc.zenkai.feature.spacepod.OpenGalacticMenuPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(ClientPayloadHandlers::openGalacticMenu));

        registrar.playToServer(
                com.hmc.zenkai.feature.spacepod.SpacePodLaunchPacket.TYPE,
                com.hmc.zenkai.feature.spacepod.SpacePodLaunchPacket.STREAM_CODEC,
                com.hmc.zenkai.feature.spacepod.SpacePodLaunchPacket::handle);
    }
}