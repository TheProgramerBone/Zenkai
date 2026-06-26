package com.hmc.zenkai;


import com.hmc.zenkai.client.ClientZenkaiHooks;
import com.hmc.zenkai.client.ClientZenkaiPalTick;
import com.hmc.zenkai.client.CombatZenkaiHooks;
import com.hmc.zenkai.client.DbPalLayers;
import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.client.gui.screens.wishes.StackWishScreen;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.client.render_and_model.blockentity.AllDragonBallsRenderer;
import com.hmc.zenkai.client.render_and_model.entity.*;
import com.hmc.zenkai.content.block.ModBlocks;
import com.hmc.zenkai.content.blockentity.ModBlockEntities;
import com.hmc.zenkai.content.effect.ModEffects;
import com.hmc.zenkai.content.entity.ModEntities;
import com.hmc.zenkai.content.item.ModItems;
import com.hmc.zenkai.content.sound.ModSounds;
import com.hmc.zenkai.core.ModCommands;
import com.hmc.zenkai.core.ModGameRules;
import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.config.WishConfig;
import com.hmc.zenkai.core.network.ModNetworking;
import com.hmc.zenkai.core.network.TickHandlers;
import com.hmc.zenkai.core.network.feature.forms.FormRegistry;
import com.hmc.zenkai.core.network.feature.ki.MouseZenkaiHooks;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import com.hmc.zenkai.core.network.feature.stats.FlyApplier;
import com.hmc.zenkai.worldgen.ModOverworldRegion;
import com.hmc.zenkai.worldgen.ModSurfaceRules;
import com.mojang.logging.LogUtils;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
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
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

@Mod(Zenkai.MOD_ID)
public class Zenkai {

    public static final String MOD_ID = "zenkai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Zenkai(IEventBus modEventBus, ModContainer modContainer) {

        // ── Gamerules (inicialización estática anticipada) ────────────────────
        ModGameRules.init();

        // ── Registros en el mod bus ───────────────────────────────────────────
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
        DataAttachments.REGISTER.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);

        // Configs
        modContainer.registerConfig(ModConfig.Type.SERVER, WishConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, StatsConfig.SPEC);
        modEventBus.addListener(WishConfig::onConfigLoad);
        modEventBus.addListener(StatsConfig::onConfigLoad);

        // ── Registros en el forge bus (eventos del juego) ─────────────────────
        IEventBus forgeBus = NeoForge.EVENT_BUS;
        forgeBus.register(PlayerLifeCycle.class);
        forgeBus.register(CombatZenkaiHooks.class);
        forgeBus.register(FlyApplier.class);
        forgeBus.register(TickHandlers.class);
        forgeBus.register(ModCommands.class);

        // Cliente
        forgeBus.register(MouseZenkaiHooks.class);
        forgeBus.register(ClientZenkaiHooks.class);
        forgeBus.register(ClientZenkaiPalTick.class);
    }

    // ── Setup común (servidor + cliente) ─────────────────────────────────────
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(FormRegistry::bootstrap);
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
                                    "namekian", "namekian_default", true, true), 0.5f));

            EntityRenderers.register(ModEntities.NAMEKIAN_WARRIOR.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("namekian_warrior",
                                    "namekian_warrior", "namekian_default", true, true), 0.5f));

            EntityRenderers.register(ModEntities.SHENLONG.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("shenlong", true), 0.5f));

            EntityRenderers.register(ModEntities.KI_BLAST.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("ki_blast")));

            EntityRenderers.register(ModEntities.ISAAC.get(), ctx -> new GenericGeoRenderer<>(ctx,new GenericGeoModel<>("isaac")));

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
            event.enqueueWork(() ->
                    PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                            DbPalLayers.TRANSFORM_LAYER,
                            1600,
                            player -> new PlayerAnimationController(
                                    player,
                                    (controller, state, animSetter) -> PlayState.STOP
                            )
                    )
            );
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