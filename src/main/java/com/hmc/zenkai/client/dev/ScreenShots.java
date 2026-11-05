package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.*;
import com.hmc.zenkai.client.gui.screens.skilltree.SkillTreeScreen;
import com.hmc.zenkai.client.gui.screens.skilltree.SkillsTreeScreen;
import com.hmc.zenkai.client.gui.screens.wishes.*;
import com.hmc.zenkai.client.gui.wheel.WheelMenu;
import com.hmc.zenkai.client.gui.wheel.WheelScreen;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.network.OpenMasterPayload;
import com.hmc.zenkai.registry.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * HERRAMIENTA DE DESARROLLO: una captura de CADA pantalla del mod (auditoría de diseño/paleta), mismo patrón que
 * SkillTreeShots. Solo se activa si existe {@code <run>/zenkai_screenshots.txt}; su primera línea es la CARPETA del
 * mundo que se abre (usar una COPIA: cambia raza y TP del jugador). Salida en {@code zenkai_screenshots/}; al
 * terminar escribe done.txt y cierra el juego.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ScreenShots {
    private ScreenShots() {}

    private static final int WAIT_LOAD = 160, WAIT = 25;

    private static Boolean enabled;
    private static String world;
    /** 2ª línea del trigger: «wishes» = solo las pantallas de deseos de Shenlong. */
    private static String mode = "";
    private static boolean opened;
    private static int ticks, step, wait;
    private static final List<Runnable> PLAN = new ArrayList<>();

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_screenshots.txt");
            if (trigger.isFile()) {
                try {
                    world = Files.readAllLines(trigger.toPath()).get(0).trim();
                    var lines = Files.readAllLines(trigger.toPath());
                    if (lines.size() > 1) mode = lines.get(1).trim();
                    enabled = !world.isEmpty();
                } catch (Exception ignored) {}
            }
        }
        return enabled;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled(mc)) return;
        if (mc.level == null || mc.player == null) {
            if (!opened && mc.screen instanceof TitleScreen) {
                capture(mc, "00_vanilla_title");
                opened = true;
                mc.getWindow().setWindowed(1600, 900);
                if (world.startsWith("new:")) createFreshWorld(mc, world.substring(4));
                else mc.createWorldOpenFlows().openWorld(world, () -> {});
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (++ticks < WAIT_LOAD) return;
        if (PLAN.isEmpty() && step == 0) buildPlan(mc);
        if (wait > 0) { wait--; return; }
        if (step < PLAN.size()) {
            try { PLAN.get(step++).run(); }
            catch (Throwable ex) { Zenkai.LOGGER.warn("ScreenShots: paso {} falló", step - 1, ex); }
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_screenshots");
        d.mkdirs();
        return d;
    }

    private static void capture(Minecraft mc, String name) {
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(new File(out(mc), name + ".png"));
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("ScreenShots: no se pudo capturar {}", name, ex);
        }
    }

    /** Abre la pantalla, espera y captura; si construirla revienta lo deja en el log y sigue con la siguiente. */
    private static void shot(Minecraft mc, String name, Supplier<Screen> s) { shot(mc, name, s, WAIT); }

    /** Igual, con espera propia: las pantallas con fundido de entrada (skill trees) necesitan más de 25 ticks. */
    private static void shot(Minecraft mc, String name, Supplier<Screen> s, int settle) {
        PLAN.add(() -> {
            try { mc.setScreen(s.get()); }
            catch (Throwable ex) { Zenkai.LOGGER.warn("ScreenShots: {} no se pudo abrir", name, ex); mc.setScreen(null); }
            wait = settle;
        });
        PLAN.add(() -> { capture(mc, name); mc.setScreen(null); wait = 5; });
    }

    private static void setup(Minecraft mc, Race race) {
        mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayers().get(0);
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.setRace(race);
            att.setRaceChosen(true);
            att.skills().clear();
            att.skills().setLevel("super_forms", 3);
            att.skills().setLevel("ki_sense", 3);
            att.skills().setLevel("fly", 2);
            att.skills().setLevel("run", 4);
            att.skills().setLevel("meditation", 5);
            att.markMasterMet("kami");
            att.markMasterMet("korin");
            att.addTP(60000 - att.getTP());
            att.refreshSkillTree();
            PlayerLifeCycle.sync(sp);
        });
    }

    /** Mundo superplano nuevo en creativo: limpio, sin entidades de pruebas anteriores y con sitio para colocar bloques. */
    private static void createFreshWorld(Minecraft mc, String name) {
        var settings = new net.minecraft.world.level.LevelSettings(name, net.minecraft.world.level.GameType.CREATIVE, false,
                net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        var options = new net.minecraft.world.level.levelgen.WorldOptions(1234L, false, false);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, options, access -> access
                .registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)
                .value().createWorldDimensions(), mc.screen);
    }

    /** Pantallas de contenedor: se colocan los bloques delante del jugador y se abren como lo haría el clic derecho. */
    private static void containersPlan(Minecraft mc) {
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        containerShot(mc, "80_scouter_bench", com.hmc.zenkai.registry.ModBlocks.SCOUTER_BENCH.get());
        containerShot(mc, "81_energy_generator", com.hmc.zenkai.registry.ModBlocks.ENERGY_GENERATOR.get());
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, ply) -> new com.hmc.zenkai.client.gui.menu.StackWishMenu(id, inv),
                        net.minecraft.network.chat.Component.translatable("screen.zenkai.option.stack")));
            });
            wait = 40;
        });
        PLAN.add(() -> { capture(mc, "82_wish_stack"); mc.setScreen(null); wait = 5; });
    }

    private static void containerShot(Minecraft mc, String name, net.minecraft.world.level.block.Block block) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                var level = sp.serverLevel();
                BlockPos pos = sp.blockPosition().offset(2, 0, 0);
                level.setBlock(pos, block.defaultBlockState(), 3);
                block.defaultBlockState().useWithoutItem(level, sp, new net.minecraft.world.phys.BlockHitResult(
                        pos.getCenter(), net.minecraft.core.Direction.UP, pos, false));
            });
            wait = 40;
        });
        PLAN.add(() -> { capture(mc, name); mc.setScreen(null); wait = 5; });
    }

    private static void wishesPlan(Minecraft mc) {
        shot(mc, "70_wish_main", ShenlongWishScreen::new);
        shot(mc, "71_wish_immortal", () -> new ImmortalWishScreen(null));
        shot(mc, "72_wish_legendary", () -> new LegendaryWishScreen(null));
        shot(mc, "73_wish_revive_player", () -> new RevivePlayerWishScreen(null));
        shot(mc, "74_wish_revive_pet", () -> new RevivePetWishScreen(null));
        shot(mc, "75_wish_enchant_villager", () -> new EnchantVillagerWishScreen(null));
        shot(mc, "76_wish_training_points", () -> new TrainingPointsWishScreen(null));
    }

    /** Solo el hub y los minijuegos de Training (rediseño de la plantilla de minijuegos). */
    private static void trainingPlan(Minecraft mc) {
        shot(mc, "12_tab_training", TrainingHubScreen::new);
        shot(mc, "30_training_shadow", ShadowTrainingScreen::new);
        shot(mc, "31_training_meditation", MeditationScreen::new);
        shot(mc, "32_training_target", TargetPracticeScreen::new);
        shot(mc, "33_training_shadow_result", () -> new ShadowResultScreen(120, 340));
        shot(mc, "34_training_shadow_result_record", () -> new ShadowResultScreen(120, 120));
    }

    private static void buildPlan(Minecraft mc) {
        if (world.startsWith("new:")) { containersPlan(mc); return; }
        PLAN.add(() -> { setup(mc, Race.SAIYAN); wait = WAIT * 2; });
        if ("wishes".equals(mode)) { wishesPlan(mc); return; }
        if ("training".equals(mode)) { trainingPlan(mc); return; }
        // HUD en juego, sin pantalla.
        PLAN.add(() -> { mc.setScreen(null); wait = 20; });
        PLAN.add(() -> { capture(mc, "01_hud_ingame"); });
        shot(mc, "02_pause", () -> new PauseScreen(true));
        // Pestañas del menú Zenkai.
        shot(mc, "10_tab_stats", StatsScreen::new);
        shot(mc, "11_tab_techniques", TechniquesScreen::new);
        shot(mc, "12_tab_training", TrainingHubScreen::new);
        shot(mc, "13_tab_party", PartyScreen::new);
        shot(mc, "14_tab_config", ClientConfigScreen::new);
        shot(mc, "15_credits", CreditsScreen::new);
        shot(mc, "16_tab_story_comingsoon", () -> new ComingSoonScreen(ZenkaiTab.STORY));
        // Skill trees (solo captura, no se tocan).
        shot(mc, "20_skilltree_masters", SkillsTreeScreen::new, 120);
        shot(mc, "21_skilltree_transformations", SkillTreeScreen::new, 120);
        // Training.
        shot(mc, "30_training_shadow", ShadowTrainingScreen::new);
        shot(mc, "31_training_meditation", MeditationScreen::new);
        shot(mc, "32_training_target", TargetPracticeScreen::new);
        shot(mc, "33_training_shadow_result", () -> new ShadowResultScreen(120, 340));
        // Creación de personaje.
        shot(mc, "40_race_selection", RaceSelectionScreen::new);
        shot(mc, "41_appearance", () -> new AppearanceScreen(null, null, null));
        shot(mc, "42_head_appearance", () -> new HeadAppearanceScreen(null, null, null, false));
        shot(mc, "43_body_colors", () -> new BodyColorsScreen(new AppearanceScreen(null, null, null), null, null));
        shot(mc, "44_style_selection", () -> new StyleSelectionScreen(null, null, null));
        // Técnicas, HUD, ruedas.
        shot(mc, "50_technique_edit", () -> new TechniqueEditScreen(0));
        shot(mc, "51_hud_placement", () -> new HudPlacementScreen(null));
        shot(mc, "52_wheel", () -> new WheelScreen(WheelMenu.build(mc.player)));
        shot(mc, "53_instant_transmission", InstantTransmissionMenuScreen::new);
        shot(mc, "54_galactic_menu", GalacticMenuScreen::new);
        // Maestros y NPC.
        for (String m : new String[]{"kami", "korin", "kaio", "toribot"}) {
            shot(mc, "60_master_" + m, () -> new MasterScreen(m, -1, List.of(
                    new OpenMasterPayload.ServiceEntry("train", "Entrenar", true, "Tooltip de ejemplo"),
                    new OpenMasterPayload.ServiceEntry("other", "Bloqueado", false, "No disponible"))));
        }
        shot(mc, "61_npc_marker", () -> new NpcMarkerScreen(BlockPos.ZERO, "saibaman", 0f, 0, 0, 0, 60));
        shot(mc, "62_piece_connector", () -> new PieceConnectorScreen(BlockPos.ZERO, "default", "north"));
        shot(mc, "63_weight", () -> {
            WeightScreen.open(InteractionHand.MAIN_HAND, ModItems.WEIGHTED_STRAPS.get(), 0.5);
            Screen s = mc.screen;
            mc.screen = null;
            return s;
        });
        // Deseos de Shenlong.
        shot(mc, "70_wish_main", ShenlongWishScreen::new);
        shot(mc, "71_wish_immortal", () -> new ImmortalWishScreen(null));
        shot(mc, "72_wish_legendary", () -> new LegendaryWishScreen(null));
        shot(mc, "73_wish_revive_player", () -> new RevivePlayerWishScreen(null));
        shot(mc, "74_wish_revive_pet", () -> new RevivePetWishScreen(null));
        shot(mc, "75_wish_enchant_villager", () -> new EnchantVillagerWishScreen(null));
        shot(mc, "76_wish_training_points", () -> new TrainingPointsWishScreen(null));
    }
}
