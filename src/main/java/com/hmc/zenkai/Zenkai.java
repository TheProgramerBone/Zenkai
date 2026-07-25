package com.hmc.zenkai;


import com.hmc.zenkai.event.ClientZenkaiHooks;
import com.hmc.zenkai.client.ClientZenkaiPalTick;
import com.hmc.zenkai.event.CombatZenkaiHooks;
import com.hmc.zenkai.event.ZenkaiPalLayers;
import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.client.gui.screens.wishes.StackWishScreen;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.client.particle.KiImpactParticle;
import com.hmc.zenkai.client.particle.KiSparkParticle;
import com.hmc.zenkai.client.render_and_model_entities.KiProjectileRenderer;
import com.hmc.zenkai.client.render_and_model_entities.blockentity.AllDragonBallsRenderer;
import com.hmc.zenkai.client.render_and_model_entities.entity.*;
import com.hmc.zenkai.event.ZenkaiTickHandlers;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModEffects;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.registry.ModParticles;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.registry.ModCommands;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.network.ModNetworking;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.registry.ModCreativeModeTabs;
import com.hmc.zenkai.registry.ModFeatures;
import com.hmc.zenkai.registry.ModOverworldRegion;
import com.hmc.zenkai.registry.ModSurfaceRules;
import com.mojang.logging.LogUtils;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

@Mod(Zenkai.MOD_ID)
public class Zenkai {

    public static final String MOD_ID = "zenkai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Zenkai(IEventBus modEventBus, ModContainer modContainer) {

        // Gamerules
        ModGameRules.init();

        // Registros en el mod bus
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetworking::register);
        modEventBus.addListener(Zenkai::registerCapabilities);
        modEventBus.addListener(ClientModEvents::onKeyMappingRegister);

        // Contenido
        ModCreativeModeTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEntities.register(modEventBus);
        ModEffects.register(modEventBus);
        ZenkaiDataAttachments.REGISTER.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModParticles.register(modEventBus);

        // Configs
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        modEventBus.addListener(ServerConfig::onConfigLoad);
        modEventBus.addListener(CommonConfig::onConfigLoad);

        // Registros en el forge bus (eventos del juego)
        IEventBus forgeBus = NeoForge.EVENT_BUS;
        forgeBus.register(PlayerLifeCycle.class);
        forgeBus.register(CombatZenkaiHooks.class);
        forgeBus.register(ZenkaiTickHandlers.class);
        forgeBus.register(ModCommands.class);

        // Cliente
        forgeBus.register(ClientZenkaiHooks.class);
        forgeBus.register(ClientZenkaiPalTick.class);
    }

    // ── Setup común (servidor + cliente) ─────────────────────────────────────
    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Reservado para futuros capabilities
    }

    // ── Setup exclusivo de cliente ────────────────────────────────────────────
    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenuTypes.STACK_WISH.get(), StackWishScreen::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Block entities
            BlockEntityRenderers.register(
                    ModBlockEntities.ALL_DRAGON_BALLS_ENTITY.get(),
                    AllDragonBallsRenderer::new
            );

            // Entidades
            EntityRenderers.register(ModEntities.SPACE_POD.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("space_pod",
                                    false, true), 1f));

            EntityRenderers.register(ModEntities.KINTOUN.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("kintoun"),
                            1f));

            EntityRenderers.register(ModEntities.SHADOW_KINTOUN.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("kintoun",
                                    "kintoun_shadow", "kintoun", false, false), 1f));

            EntityRenderers.register(ModEntities.NAMEKIAN.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("namekian",
                                    "namekian", "zenkai_animations", true, false), 0.5f));

            EntityRenderers.register(ModEntities.NAMEKIAN_WARRIOR.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("namekian_warrior",
                                    "namekian_warrior", "zenkai_animations", true, false), 0.5f));

            EntityRenderers.register(ModEntities.SAIBAMAN.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("saibaman",
                                    "saibaman", "zenkai_animations", true, false), 0.5f));

            EntityRenderers.register(ModEntities.SHENLONG.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("shenlong", true), 0.5f));

            EntityRenderers.register(ModEntities.KI_PROJECTILE.get(),
                    KiProjectileRenderer::new);

            EntityRenderers.register(ModEntities.ISAAC.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("isaac", true), 0.5f));

            EntityRenderers.register(ModEntities.YEMMA.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("yemma", true), 4f));


            // Worldgen (Terrablender)
            event.enqueueWork(() -> {
                Regions.register(new ModOverworldRegion());
                SurfaceRuleManager.addSurfaceRules(
                        SurfaceRuleManager.RuleCategory.OVERWORLD,
                        MOD_ID,
                        ModSurfaceRules.makeRules()
                );
            });

            // Animaciones de jugador
            event.enqueueWork(() -> {
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.TRANSFORM_LAYER,
                        1000,
                        player -> {
                            PlayerAnimationController c = new PlayerAnimationController(
                                    player, (controller, state, animSetter) -> PlayState.STOP);
                            c.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
                            c.setFirstPersonConfiguration(new FirstPersonConfiguration()
                                    .setShowLeftArm(true)
                                    .setShowRightArm(true));
                            return c;
                        }
                );
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.FLY_LAYER,
                        800,
                        player -> new PlayerAnimationController(
                                player,
                                (controller, state, animSetter) -> PlayState.STOP
                        )
                );

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.BLOCK_LAYER,
                        1200,
                        player -> new PlayerAnimationController(
                                player, (controller, state, animSetter) -> PlayState.STOP)
                );

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.COMBAT_LAYER,
                        900,
                        player -> new PlayerAnimationController(
                                player,(controller, state, animSetter) -> PlayState.STOP)

                );

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.PHYS_LAYER,
                        950, // sobre la pose de combate (900), bajo transform/fly/block
                        player -> new PlayerAnimationController(
                                player, (controller, state, animSetter) -> PlayState.STOP)
                );



            });
        }

        @SubscribeEvent
        public static void registerParticleProviders(RegisterParticleProvidersEvent event) { // ⚠
            event.registerSpriteSet(ModParticles.KI_IMPACT.get(),
                    KiImpactParticle.Provider::new);
            event.registerSpriteSet(ModParticles.KI_SPARK.get(),
                    KiSparkParticle.Provider::new);
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