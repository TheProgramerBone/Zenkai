package com.hmc.zenkai;


import com.hmc.zenkai.client.gui.screens.ClientConfigScreen;
import com.hmc.zenkai.compat.ponder.PonderCompat;
import com.hmc.zenkai.client.gui.screens.EnergyGeneratorScreen;
import com.hmc.zenkai.client.gui.screens.ScouterBenchScreen;
import com.hmc.zenkai.client.render_and_model_entities.blockentity.ScouterBenchRenderer;
import com.hmc.zenkai.client.sound.ScouterBenchSounds;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.hmc.zenkai.event.*;
import com.hmc.zenkai.client.ClientZenkaiPalTick;
import com.hmc.zenkai.client.fluid.HealingWaterFluidClientExtensions;
import com.hmc.zenkai.client.gui.ModMenuTypes;
import com.hmc.zenkai.client.gui.screens.wishes.StackWishScreen;
import com.hmc.zenkai.client.input.KeyBindings;
import com.hmc.zenkai.client.particle.AuraDustParticle;
import com.hmc.zenkai.client.particle.AuraRockParticle;
import com.hmc.zenkai.client.particle.KiArcParticle;
import com.hmc.zenkai.client.particle.KiImpactParticle;
import com.hmc.zenkai.client.particle.KiSparkParticle;
import com.hmc.zenkai.client.render_and_model_entities.entity.KiVfxProjectileRenderer;
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
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.loading.FMLEnvironment;
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
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
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
        // NO añadir aquí un modEventBus.addListener(ClientModEvents::onKeyMappingRegister) (ni
        // ninguna otra referencia manual a ClientModEvents): esa clase ya se registra sola vía
        // @EventBusSubscriber(value = Dist.CLIENT), que SÍ es dist-aware y no la toca en un
        // dedicated server. Una referencia manual como esa es incondicional en cada dist:
        // tomarla obliga a cargar la clase entera, y cargarla verifica también onClientSetup(),
        // que registra EntityRenderers de cliente — el RuntimeDistCleaner de NeoForge aborta ahí
        // mismo con BootstrapMethodError en DEDICATED_SERVER. Esto (junto con un lambda inline
        // equivalente que había en ModNetworking, ver su historial) dejaba el mod incapaz de
        // arrancar en NINGÚN server sin cliente — nadie lo había notado porque nadie había
        // arrancado nunca uno hasta el gametest de esta sesión.

        // Contenido
        ModCreativeModeTabs.register(modEventBus);
        ModWoodTypes.init();
        ModStructures.register(modEventBus);
        ModItems.register(modEventBus);
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEntities.register(modEventBus);
        ModEffects.register(modEventBus);
        ZenkaiDataAttachments.REGISTER.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModPlacementModifierTypes.register(modEventBus);
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

        // Cliente. Ninguna de las dos tiene @EventBusSubscriber(Dist.CLIENT) — a diferencia de
        // ClientModEvents, dependen de este forgeBus.register(Class) manual, así que hay que
        // gatearlo a mano: EventBus.register(Class) escanea con reflexión cada una de las
        // funciones de la clase para encontrar los @SubscribeEvent, y esa reflexión resuelve
        // también los parámetros de funciones que reciben eventos solo-cliente (ver historial de este
        // constructor) — sin el guard, un dedicated server abortaba aquí mismo.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            forgeBus.register(ClientZenkaiHooks.class);
            forgeBus.register(ClientZenkaiPalTick.class);
        }
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

        // Worldgen (TerraBlender) — BUG real encontrado y arreglado el 2026-09-04: esto vivía
        // dentro de onClientSetup (FMLClientSetupEvent, Dist.CLIENT-only), así que en un servidor
        // dedicado real ModOverworldRegion NUNCA se registraba con TerraBlender y rocky_wasteland
        // (y sus reglas de superficie) jamás habría aparecido en generación — solo "funcionaba"
        // en singleplayer porque el cliente integrado comparte JVM con el servidor integrado y los
        // dos eventos de setup corren igual antes de cargar el mundo. Registrar regiones/reglas de
        // superficie de worldgen es trabajo del SERVIDOR (es quien genera terreno de verdad, cliente
        // integrado incluido) — pertenece a FMLCommonSetupEvent, no a FMLClientSetupEvent. Detectado
        // al intentar reproducir el bug de "rocky_wasteland en océano" con runGameTestServer (un
        // entorno solo-servidor, sin Dist.CLIENT): la región nunca se registraba ahí, 0 de ~21000
        // celdas muestreadas en un barrido amplio le pertenecían a zenkai:overworld pese al weight
        // ya arreglado — el síntoma llevó directo a este bug de scope, no al mecanismo de clima que
        // se sospechaba al principio.
        event.enqueueWork(() -> {
            Regions.register(new ModOverworldRegion());
            SurfaceRuleManager.addSurfaceRules(
                    SurfaceRuleManager.RuleCategory.OVERWORLD,
                    MOD_ID,
                    ModSurfaceRules.makeRules()
            );
        });
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // El banco de scouter acepta FE por las seis caras. No hay lado privilegiado: es un
        // consumidor, y obligar a conectar por una cara concreta solo estorba al construir.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.SCOUTER_BENCH.get(),
                (be, side) -> be.energyHandler());

        // El generador expone su búfer por las seis caras para que un cable de Mekanism
        // pueda tirar de él. El reparto a bloques pegados NO va por aquí: eso lo empuja el
        // propio BE en su tick, porque el banco de scouter no sabe pedir energía.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ENERGY_GENERATOR.get(),
                (be, side) -> be.energyHandler());

        // Cara de automatización. El handler filtra: entra combustible, sale solo lo que ya
        // no arde. Ver AutomationHandler en el block entity.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ENERGY_GENERATOR.get(),
                (be, side) -> be.automationHandler());

        // Cables, tuberías y tanques: la capability por cara solo existe si la cara está en modo NORMAL.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ENERGY_TRANSPORT.get(), (be, side) -> be.energyAt(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.FLUID_TRANSPORT.get(), (be, side) -> be.fluidAt(side));

        // Batería: capability ESTÁNDAR de energía de ítem, para que la cargue cualquier mod.
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> com.hmc.zenkai.content.item.EnergyBatteryItem.storage(stack),
                com.hmc.zenkai.registry.ModItems.ENERGY_BATTERY.get());

        // Cámara de contención de ki: fluido (solo extracción) y FE por las seis caras de las DOS
        // mitades. El block entity vive en la inferior; la superior no tiene, así que se registra
        // por BLOQUE y cada mitad busca el block entity de abajo.
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> level.getBlockEntity(
                        com.hmc.zenkai.content.block.KiContainmentChamberBlock.lowerPos(state, pos))
                        instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity chamber
                        ? chamber.fluidHandler() : null,
                ModBlocks.KI_CONTAINMENT_CHAMBER.get());
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> level.getBlockEntity(
                        com.hmc.zenkai.content.block.KiContainmentChamberBlock.lowerPos(state, pos))
                        instanceof com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity chamber
                        ? chamber.energyHandler() : null,
                ModBlocks.KI_CONTAINMENT_CHAMBER.get());
    }

    // ── Setup exclusivo de cliente ────────────────────────────────────────────
    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenuTypes.STACK_WISH.get(), StackWishScreen::new);
            event.register(ModMenuTypes.SCOUTER_BENCH.get(), ScouterBenchScreen::new);
            event.register(ModMenuTypes.ENERGY_GENERATOR.get(), EnergyGeneratorScreen::new);
            event.register(ModMenuTypes.KI_CHAMBER.get(), com.hmc.zenkai.client.gui.screens.KiChamberScreen::new);
            event.register(ModMenuTypes.PLAYER_BODY.get(), com.hmc.zenkai.client.gui.screens.PlayerBodyScreen::new);
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

            // Tutoriales 3D de Ponder (compat opcional, ver compat/ponder/PonderCompat.java).
            // No hace nada si el jugador no tiene Ponder instalado.
            PonderCompat.register();

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

            BlockEntityRenderers.register(
                    ModBlockEntities.KI_CONTAINMENT_CHAMBER.get(),
                    com.hmc.zenkai.client.render_and_model_entities.blockentity.KiChamberRenderer::new
            );

            // Entidades
            EntityRenderers.register(ModEntities.SPACE_POD.get(), SpacePodRenderer::new);

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

            // Ogros del HFIL: un solo modelo con rig y UV de jugador (geo/ogre), una textura por tipo.
            EntityRenderers.register(ModEntities.RED_OGRE.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("ogre", "red_ogre", "zenkai_animations", true, false), 0.6f));
            EntityRenderers.register(ModEntities.BLUE_OGRE.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("ogre", "blue_ogre", "zenkai_animations", true, false), 0.6f));
            // Ángel: rig de jugador con trompeta (geo/angel), animación propia de tocar.
            EntityRenderers.register(ModEntities.ANGEL.get(),
                    ctx -> {
                        GenericGeoRenderer<com.hmc.zenkai.content.entity.otherworld.AngelEntity> r =
                                new GenericGeoRenderer<>(ctx,
                                        new GenericGeoModel<>("angel", "angel", "angel", false, false), 0.25f);
                        // El casco del ángel (el halo del mod) con la capa de armadura de GeckoLib.
                        r.addRenderLayer(new com.hmc.zenkai.client.render_and_model_entities.entity.AngelArmorLayer(r));
                        return r;
                    });

            EntityRenderers.register(ModEntities.SHENLONG.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new GenericGeoModel<>("shenlong", true), 0.5f));

            EntityRenderers.register(ModEntities.KI_PROJECTILE.get(),
                    KiVfxProjectileRenderer::new);

            EntityRenderers.register(ModEntities.CLASH_ORB.get(),
                    com.hmc.zenkai.client.render_and_model_entities.entity.ClashOrbRenderer::new);

            EntityRenderers.register(ModEntities.PLAYER_BODY.get(),
                    com.hmc.zenkai.client.render_and_model_entities.entity.PlayerBodyRenderer::new);

            EntityRenderers.register(ModEntities.SWORD_WAVE.get(),
                    com.hmc.zenkai.client.render_and_model_entities.entity.SwordWaveRenderer::new);

            EntityRenderers.register(ModEntities.ABSORB_MASS.get(),
                    com.hmc.zenkai.client.render_and_model_entities.entity.AbsorbMassRenderer::new);

            EntityRenderers.register(ModEntities.KI_ARROW.get(),
                    TippableArrowRenderer::new);

            EntityRenderers.register(ModEntities.ISAAC.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("isaac", true), 0.5f));

            EntityRenderers.register(ModEntities.YEMMA.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("yemma", true), 4f));

            EntityRenderers.register(ModEntities.URANAI_BABA.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("uranai_baba", true), 0.5f));

            EntityRenderers.register(ModEntities.KAMI.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("kami", true), 0.5f));

            EntityRenderers.register(ModEntities.KAIO.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("kaio", true), 0.5f));

            EntityRenderers.register(ModEntities.KORIN.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("korin", true), 0.5f));

            EntityRenderers.register(ModEntities.TORIBOT.get(),
                    ctx -> new GenericGeoRenderer<>(ctx, new GenericGeoModel<>("toribot", true), 0.5f));

            // Ya NO PlayerModel vainilla — ver el javadoc de PosedHumanoidGeoModel/
            // ShadowCloneEntity: rig humanoide GeckoLib idéntico a geo/saibaman.geo.json (sin el
            // 0.75 de escala), reusa attack.strike/move.walk de zenkai_animations.animation.json
            // de verdad (triggerAnim ya no es inerte como con PlayerModel).
            EntityRenderers.register(ModEntities.SHADOW_CLONE.get(),
                    ctx -> new GenericGeoRenderer<>(ctx,
                            new PosedHumanoidGeoModel<>("shadow_clone",
                                    "shadow_clone", "zenkai_animations", true, false), 0.5f));

            // Animaciones de jugador. La política de 1ª persona vive en ZenkaiPalAnimations,
            // NO aquí y NO en cada animación: las cinco capas comparten exactamente la misma.
            event.enqueueWork(() -> {
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.TRANSFORM_LAYER, 1000,
                        ZenkaiPalAnimations::newFirstPersonController);

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.FLY_LAYER, 800,
                        ZenkaiPalAnimations::newFlightController);

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.BLOCK_LAYER, 1200,
                        ZenkaiPalAnimations::newLegReleaseController);

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.COMBAT_LAYER, 900,
                        ZenkaiPalAnimations::newLegReleaseController);

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.PHYS_LAYER, 950,
                        ZenkaiPalAnimations::newFirstPersonController);

                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.KI_LAYER, 960,
                        ZenkaiPalAnimations::newLegReleaseController);

                // Prioridad 1250, por encima de BLOCK_LAYER (1200) — pedido explícito del
                // usuario: quiere que la pose de carga se vea SIEMPRE, incluso volando (FLY_LAYER,
                // 800). AnimationStack.get3DTransform aplica las capas en orden ascendente de
                // prioridad y cada una transforma el hueso que dejó la anterior ("Highest index =
                // it can override everything else", PAL AnimationStack.java) — con 970 ya le
                // ganaba a FLY_LAYER numéricamente, pero se sube más para que sea inequívocamente
                // la capa dominante de gameplay (justo debajo de PREVIEW, que es la única que
                // debe estar siempre por encima de lo demás).
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.TRANSMISSION_LAYER, 1250,
                        ZenkaiPalAnimations::newFirstPersonController);

                // Prioridad 1300: pose de derribado propia, por encima de bloqueo (1200), transmisión (1250) y vuelo (800).
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.DOWNED_LAYER, 1300,
                        ZenkaiPalAnimations::newFirstPersonController);

                // Prioridad 1600: brazos de quien lleva un cuerpo en brazos; la más alta de gameplay (pedido del usuario).
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.CARRY_LAYER, 1600,
                        ZenkaiPalAnimations::newFirstPersonController);

                // Prioridad 1500: por encima de TRANSMISSION_LAYER (1250), ahora la más alta de
                // gameplay. El preview del editor tiene que verse pase lo que pase; no compite
                // con gameplay porque solo está viva mientras hay una pantalla de edición abierta.
                PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                        ZenkaiPalLayers.PREVIEW_LAYER, 1500,
                        ZenkaiPalAnimations::newPreviewController);
            });
        }

        @SubscribeEvent
        public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.KI_IMPACT.get(),
                    KiImpactParticle.Provider::new);
            event.registerSpriteSet(ModParticles.KI_SPARK.get(),
                    KiSparkParticle.Provider::new);
            event.registerSpriteSet(ModParticles.STUN_STAR.get(),
                    com.hmc.zenkai.client.particle.StunStarParticle.Provider::new);
            event.registerSpriteSet(ModParticles.ENERGY_MOTE.get(),
                    com.hmc.zenkai.client.particle.EnergyMoteParticle.Provider::new);
            // El Black Flash no necesita clase propia: KiImpactParticle ya hace exactamente
            // lo que pide (quieto, 6 frames por edad, tinte de las opciones). Solo cambian
            // el sprite set, el color y la escala.
            event.registerSpriteSet(ModParticles.BLACK_FLASH_CORE.get(),
                    KiImpactParticle.Provider::new);
            event.registerSpriteSet(ModParticles.BLACK_FLASH_RIM.get(),
                    KiImpactParticle.Provider::new);
            // El "arco" (filo crepitante de Death Ball, arcos de carga de Final Flash, efecto ARC de
            // los datapacks) es un RAYO fractal como los del aura, no un sprite: no tiene sprite set.
            event.registerSpecial(ModParticles.KI_ARC.get(), new KiArcParticle.Provider());
            // Efectos de suelo al cargar ki (AuraChargeGroundFx). La roca no tiene sprite set:
            // dibuja un recorte del sprite del bloque pisado, del atlas de bloques.
            event.registerSpriteSet(ModParticles.AURA_DUST.get(),
                    AuraDustParticle.Provider::new);
            event.registerSpecial(ModParticles.AURA_ROCK.get(), new AuraRockParticle.Provider(false));
            event.registerSpecial(ModParticles.AURA_ROCK_FLOAT.get(), new AuraRockParticle.Provider(true));
        }

        @SubscribeEvent
        public static void onKeyMappingRegister(RegisterKeyMappingsEvent event) {
            KeyBindings.registerKeyMappings(event);
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            KeyBindings.handleKeyInput(event);
        }

        /** Clic derecho mientras TAB está pulsado = confirma el blink de Transmisión Instantánea
         *  (ver KeyBindings.handleMouseButton) — MouseButton.Pre, no Post, porque hace falta poder
         *  cancelar el clic para que no dispare además una interacción normal. */
        @SubscribeEvent
        public static void onMouseButton(InputEvent.MouseButton.Pre event) {
            KeyBindings.handleMouseButton(event);
        }

        /** Textura (agua vanilla) + tinte del agua curativa. Ver HealingWaterFluidClientExtensions. */
        @SubscribeEvent
        public static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
            event.registerFluidType(HealingWaterFluidClientExtensions.INSTANCE, ModFluids.HEALING_WATER_TYPE.get());
            event.registerFluidType(com.hmc.zenkai.client.fluid.HfilBoilingWaterFluidClientExtensions.INSTANCE,
                    ModFluids.HFIL_BOILING_WATER_TYPE.get());
            // Fluidos de Ki: sprite propio por tipo, sin tinte (ver KiFluidClientExtensions).
            for (com.hmc.zenkai.feature.kichamber.KiEnergyType t : com.hmc.zenkai.feature.kichamber.KiEnergyType.values()) {
                event.registerFluidType(new com.hmc.zenkai.client.fluid.KiFluidClientExtensions(t.fluidName()),
                        t.fluid().type().get());
            }
        }
    }
}