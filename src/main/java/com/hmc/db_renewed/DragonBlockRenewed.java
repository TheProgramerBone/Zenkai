package com.hmc.db_renewed;

import com.hmc.db_renewed.client.ClientHooks;
import com.hmc.db_renewed.client.ClientPalTick;
import com.hmc.db_renewed.client.CombatHooks;
import com.hmc.db_renewed.client.DbPalLayers;
import com.hmc.db_renewed.content.block.ModBlocks;
import com.hmc.db_renewed.content.blockentity.ModBlockEntities;
import com.hmc.db_renewed.client.render_and_model.blockentity.AllDragonBallsRenderer;
import com.hmc.db_renewed.client.input.KeyBindings;
import com.hmc.db_renewed.core.config.StatsConfig;
import com.hmc.db_renewed.core.config.WishConfig;
import com.hmc.db_renewed.content.effect.ModEffects;
import com.hmc.db_renewed.content.entity.ModEntities;
import com.hmc.db_renewed.client.render_and_model.entity.KiBlastRenderer;
import com.hmc.db_renewed.client.render_and_model.entity.KintounRenderer;
import com.hmc.db_renewed.client.render_and_model.entity.ShadowKintounRenderer;
import com.hmc.db_renewed.client.render_and_model.entity.NamekianRenderer;
import com.hmc.db_renewed.client.render_and_model.entity.NamekianWarriorRenderer;
import com.hmc.db_renewed.client.render_and_model.entity.SpacePodRenderer;
import com.hmc.db_renewed.client.render_and_model.entity.ShenLongRenderer;
import com.hmc.db_renewed.client.gui.ModMenuTypes;
import com.hmc.db_renewed.client.gui.screens.wishes.StackWishScreen;
import com.hmc.db_renewed.content.item.ModItems;
import com.hmc.db_renewed.core.network.ModNetworking;
import com.hmc.db_renewed.core.network.TickHandlers;
import com.hmc.db_renewed.core.network.feature.forms.FormRegistry;
import com.hmc.db_renewed.core.network.feature.ki.MouseHooks;
import com.hmc.db_renewed.core.network.feature.player.PlayerLifeCycle;
import com.hmc.db_renewed.core.network.feature.stats.*;
import com.hmc.db_renewed.content.sound.ModSounds;
import com.hmc.db_renewed.core.ModCommands;
import com.hmc.db_renewed.worldgen.ModSurfaceRules;
import com.hmc.db_renewed.worldgen.ModOverworldRegion;
import com.mojang.logging.LogUtils;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;


@Mod(DragonBlockRenewed.MOD_ID)
public class DragonBlockRenewed
{
    public static final String MOD_ID = "db_renewed";
    private static final Logger LOGGER = LogUtils.getLogger();

    public DragonBlockRenewed(IEventBus modEventBus, ModContainer modContainer)
    {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.register(this);
        ModCreativeModeTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEntities.register(modEventBus);
        ModEffects.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, WishConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, StatsConfig.SPEC);
        modEventBus.addListener(WishConfig::onConfigLoad);
        modEventBus.addListener(StatsConfig::onConfigLoad);
        DataAttachments.REGISTER.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        modEventBus.addListener(ClientModEvents::onKeyMappingRegister);
        modEventBus.addListener(DragonBlockRenewed::registerCapabilities);

        var forgeBus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        forgeBus.register(MouseHooks.class);
        forgeBus.register(ClientHooks.class);
        forgeBus.register(FlyApplier.class);
        forgeBus.register(CombatHooks.class);
        forgeBus.register(TickHandlers.class);
        forgeBus.register(PlayerLifeCycle.class);
        forgeBus.register(ModCommands.class);
        forgeBus.register(ClientPalTick.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(FormRegistry::bootstrap);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
//        event.registerEntity(
//                PlayerStatsProvider.PLAYER_STATS_CAPABILITY,
//                EntityType.PLAYER,
//                (player, ctx) -> new PlayerStats()
//        );
    }


    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenuTypes.STACK_WISH.get(),
                    StackWishScreen::new);
        }
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event){
            BlockEntityRenderers.register(ModBlockEntities.ALL_DRAGON_BALLS_ENTITY.get(), AllDragonBallsRenderer::new);
            EntityRenderers.register(ModEntities.SPACE_POD.get(), SpacePodRenderer::new);
            EntityRenderers.register(ModEntities.KINTOUN.get(), KintounRenderer::new);
            EntityRenderers.register(ModEntities.SHADOW_KINTOUN.get(), ShadowKintounRenderer::new);
            EntityRenderers.register(ModEntities.NAMEKIAN.get(), NamekianRenderer::new);
            EntityRenderers.register(ModEntities.NAMEKIAN_WARRIOR.get(), NamekianWarriorRenderer::new);
            EntityRenderers.register(ModEntities.SHENLONG.get(), ShenLongRenderer::new);
            EntityRenderers.register(ModEntities.KI_BLAST.get(), KiBlastRenderer::new);
            event.enqueueWork(() ->
            {
                Regions.register(new ModOverworldRegion());
                SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, MOD_ID, ModSurfaceRules.makeRules());
            });
            event.enqueueWork(() -> {
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        DbPalLayers.TRANSFORM_LAYER,
                        1600,
                        player -> new PlayerAnimationController(player, (controller, state, animSetter) -> PlayState.STOP)
                );
            });
        }


        @SubscribeEvent
        public static void onKeyMappingRegister(RegisterKeyMappingsEvent event) {
            KeyBindings.registerKeyMappings(event);
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            KeyBindings.handleKeyInput(event);
        }

    }
}
