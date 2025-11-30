package com.hmc.db_renewed;

import com.hmc.db_renewed.block.ModBlocks;
import com.hmc.db_renewed.block.ModBlockEntities;
import com.hmc.db_renewed.block.entity.AllDragonBalls.AllDragonBallsRenderer;
import com.hmc.db_renewed.client.input.KeyBindings;
import com.hmc.db_renewed.config.StatsConfig;
import com.hmc.db_renewed.config.WishConfig;
import com.hmc.db_renewed.effect.ModEffects;
import com.hmc.db_renewed.entity.ModEntities;
import com.hmc.db_renewed.entity.ki_attacks.ki_blast.KiBlastRenderer;
import com.hmc.db_renewed.entity.namekian.NamekianRenderer;
import com.hmc.db_renewed.entity.namekian.NamekianWarriorRenderer;
import com.hmc.db_renewed.entity.saiyan_pod.SpacePodRenderer;
import com.hmc.db_renewed.entity.shenlong.ShenLongRenderer;
import com.hmc.db_renewed.gui.ModMenuTypes;
import com.hmc.db_renewed.gui.wishes.StackWishScreen;
import com.hmc.db_renewed.item.ModItems;
import com.hmc.db_renewed.network.ModNetworking;
import com.hmc.db_renewed.network.ki.MouseHooks;
import com.hmc.db_renewed.network.stats.*;
import com.hmc.db_renewed.sound.ModSounds;
import com.hmc.db_renewed.network.stats.CommandsInit;
import com.hmc.db_renewed.worldgen.ModSurfaceRules;
import com.hmc.db_renewed.worldgen.ModOverworldRegion;
import com.mojang.logging.LogUtils;
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
        forgeBus.register(CommandsInit.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        //ModRaceCommand.register(event.getServer().getCommands().getDispatcher());
        //ModStatCommand.register(event.getServer().getCommands().getDispatcher());
        //ModStyleCommand.register(event.getServer().getCommands().getDispatcher());
        //ModResetCharacterCommand.register((event.getServer().getCommands().getDispatcher()));
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
            EntityRenderers.register(ModEntities.NAMEKIAN.get(), NamekianRenderer::new);
            EntityRenderers.register(ModEntities.NAMEKIAN_WARRIOR.get(), NamekianWarriorRenderer::new);
            EntityRenderers.register(ModEntities.SHENLONG.get(), ShenLongRenderer::new);
            EntityRenderers.register(ModEntities.KI_BLAST.get(), KiBlastRenderer::new);
            event.enqueueWork(() ->
            {
                Regions.register(new ModOverworldRegion());
                SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, MOD_ID, ModSurfaceRules.makeRules());
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
