package com.hmc.zenkai;


import com.hmc.zenkai.client.gui.screens.ClientConfigScreen;
import com.hmc.zenkai.client.gui.screens.ScouterBenchScreen;
import com.hmc.zenkai.client.render_and_model_entities.blockentity.ScouterBenchRenderer;
import com.hmc.zenkai.client.sound.ScouterBenchSounds;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.event.*;
import com.hmc.zenkai.client.ClientZenkaiPalTick;
import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.client.gui.screens.wishes.StackWishScreen;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.client.particle.KiImpactParticle;
import com.hmc.zenkai.client.particle.KiSparkParticle;
import com.hmc.zenkai.client.render_and_model_entities.entity.KiProjectileRenderer;
import com.hmc.zenkai.client.render_and_model_entities.blockentity.AllDragonBallsRenderer;
import com.hmc.zenkai.client.render_and_model_entities.entity.*;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.sense.ScouterStacks;
import com.hmc.zenkai.registry.*;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.network.ModNetworking;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.mojang.logging.LogUtils;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
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
        ModWoodTypes.init();
        ModStructures.register(modEventBus);
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
        ZenkaiTriggers.register(modEventBus);
        ModStructureProcessors.register(modEventBus);

        // Configs
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
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
        event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            fire.setFlammable(ModBlocks.AJISA_LOG.get(), 5, 5);
            fire.setFlammable(ModBlocks.AJISA_WOOD.get(), 5, 5);
            fire.setFlammable(ModBlocks.STRIPPED_AJISA_LOG.get(), 5, 5);
            fire.setFlammable(ModBlocks.STRIPPED_AJISA_WOOD.get(), 5, 5);
            fire.setFlammable(ModBlocks.AJISA_PLANKS.get(), 5, 20);
            fire.setFlammable(ModBlocks.AJISA_SLAB.get(), 5, 20);
            fire.setFlammable(ModBlocks.AJISA_STAIRS.get(), 5, 20);
            fire.setFlammable(ModBlocks.AJISA_FENCE.get(), 5, 20);
            fire.setFlammable(ModBlocks.AJISA_FENCE_GATE.get(), 5, 20);
            fire.setFlammable(ModBlocks.AJISA_LEAVES.get(), 30, 60);
        });
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // El banco de scouter acepta FE por las seis caras. No hay lado privilegiado: es un
        // consumidor, y obligar a conectar por una cara concreta solo estorba al construir.
        // ⚠ Verificar en NeoForge 1.21.1: Capabilities.EnergyStorage.BLOCK y la firma
        // registerBlockEntity(cap, blockEntityType, (be, side) -> handler).
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.SCOUTER_BENCH.get(),
                (be, side) -> be.energyHandler());
    }

    // ── Setup exclusivo de cliente ────────────────────────────────────────────
    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenuTypes.STACK_WISH.get(), StackWishScreen::new);
            event.register(ModMenuTypes.SCOUTER_BENCH.get(), ScouterBenchScreen::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

            // El botón "Config" de la lista de mods es un extension point ÚNICO: solo cabe una
            // pantalla. Configured funciona ocupando ese hueco en los mods que NO lo declaran, y
            // cuando lo ocupa da acceso a los TRES specs. Al declararlo nosotros, Configured se
            // apartaba y el botón abría una pantalla que solo edita ClientConfig — una sola
            // opción — dejando Common (~130 claves) y Server (~30) sin ninguna vía gráfica.
            // Con Configured presente le cedemos el sitio. Sin él, nuestra pantalla es mejor que
            // ningún botón. En ambos casos ClientConfigScreen sigue siendo la pestaña CONFIG del
            // menú Zenkai, que es donde se usa de verdad (en partida, sin pausar).
            if (!ModList.get().isLoaded("configured")) {
                ModLoadingContext.get().getActiveContainer().registerExtensionPoint(
                        IConfigScreenFactory.class,
                        (container, parent) -> new ClientConfigScreen(parent));
            }

            ScouterBenchBlockEntity.clientTickHook = ScouterBenchSounds::tick;

            // Icono del scouter roto: propiedad 0/1 que dispara el override del modelo.
            // Un item aparte habría partido el tag de tinte, la receta y el slot de Curios.
            ItemProperties.register(ModItems.SCOUTER.get(),
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "broken"),
                    (stack, lvl, ent, seed) -> ScouterStacks.isBroken(stack) ? 1.0F : 0.0F);

            // Block entities
            BlockEntityRenderers.register(
                    ModBlockEntities.ALL_DRAGON_BALLS_ENTITY.get(),
                    AllDragonBallsRenderer::new
            );

            BlockEntityRenderers.register(
                    ModBlockEntities.SCOUTER_BENCH.get(),
                    ScouterBenchRenderer::new
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

            EntityRenderers.register(ModEntities.KI_ARROW.get(),
                    TippableArrowRenderer::new);

            EntityRenderers.register(ModEntities.ISAAC.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("isaac", true), 0.5f));

            EntityRenderers.register(ModEntities.YEMMA.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("yemma", true), 4f));

            EntityRenderers.register(ModEntities.KAMI.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("kami", true), 0.5f));

            EntityRenderers.register(ModEntities.KAIO.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("kaio", true), 0.5f));


            // Worldgen (Terrablender)
            event.enqueueWork(() -> {
                Regions.register(new ModOverworldRegion());
                SurfaceRuleManager.addSurfaceRules(
                        SurfaceRuleManager.RuleCategory.OVERWORLD,
                        MOD_ID,
                        ModSurfaceRules.makeRules()
                );
            });

            // Animaciones de jugador. La política de 1ª persona vive en ZenkaiPalAnimations,
            // NO aquí y NO en cada animación: las cinco capas comparten exactamente la misma.
            event.enqueueWork(() -> {
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.TRANSFORM_LAYER, 1000,
                        ZenkaiPalAnimations::newFirstPersonController);

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.FLY_LAYER, 800,
                        ZenkaiPalAnimations::newFirstPersonController);

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.BLOCK_LAYER, 1200,
                        ZenkaiPalAnimations::newFirstPersonController);

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.COMBAT_LAYER, 900,
                        ZenkaiPalAnimations::newFirstPersonController);

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.PHYS_LAYER, 950,
                        ZenkaiPalAnimations::newFirstPersonController);


            });
        }

        @SubscribeEvent
        public static void registerParticleProviders(RegisterParticleProvidersEvent event) { // ⚠
            event.registerSpriteSet(ModParticles.KI_IMPACT.get(),
                    KiImpactParticle.Provider::new);
            event.registerSpriteSet(ModParticles.KI_SPARK.get(),
                    KiSparkParticle.Provider::new);
            // El Black Flash no necesita clase propia: KiImpactParticle ya hace exactamente
            // lo que pide (quieto, 6 frames por edad, tinte de las opciones). Solo cambian
            // el sprite set, el color y la escala.
            event.registerSpriteSet(ModParticles.BLACK_FLASH_CORE.get(),
                    KiImpactParticle.Provider::new);
            event.registerSpriteSet(ModParticles.BLACK_FLASH_RIM.get(),
                    KiImpactParticle.Provider::new);
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