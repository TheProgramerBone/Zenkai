package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.screens.RaceSelectionScreen;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * HERRAMIENTA DE DESARROLLO: reproduce la creación de personaje tras {@code /zenkai reset full} pulsando los botones REALES de
 * cada pantalla (raza → apariencia → cabeza → cuerpo → estilo → Confirmar) y vuelca al log, en cada paso, raza y flags del
 * cliente y del servidor integrado ({@code [RACEFLOW]}). Solo se activa con {@code <run>/zenkai_raceflow.txt} (primera línea =
 * carpeta de una COPIA del mundo). Capturas en {@code zenkai_raceflow/}; al terminar escribe done.txt y cierra el juego.
 * Nació para el fallo «confirmaba pero no quedaba» del mundo Estructuras (2026-11-12).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class RaceFlowShots {
    private RaceFlowShots() {}

    private static final int WAIT_LOAD = 160;

    private static Boolean enabled;
    private static String world;
    private static boolean opened;
    private static int ticks, step, wait;
    private static final List<Runnable> PLAN = new ArrayList<>();

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_raceflow.txt");
            if (trigger.isFile()) {
                try {
                    world = Files.readAllLines(trigger.toPath()).get(0).trim();
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
                opened = true;
                mc.getWindow().setWindowed(1600, 900);
                mc.createWorldOpenFlows().openWorld(world, () -> {});
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (++ticks < WAIT_LOAD) return;
        if (PLAN.isEmpty() && step == 0) buildPlan(mc);
        if (wait > 0) { wait--; return; }
        if (step < PLAN.size()) {
            try { PLAN.get(step++).run(); }
            catch (Exception ex) { Zenkai.LOGGER.warn("RaceFlowShots: paso {} falló", step - 1, ex); }
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static void buildPlan(Minecraft mc) {
        PLAN.add(() -> { log(mc, "inicio"); command(mc, "zenkai reset full @s"); command(mc, "zenkai tp add @s 1000000000"); wait = 40; });
        PLAN.add(() -> { log(mc, "tras reset"); mc.setScreen(new RaceSelectionScreen()); wait = 20; });
        // Flecha derecha de raza dos veces (la 2.ª ArrowIconButton de la pantalla) para no quedarse en Humano.
        PLAN.add(() -> { pressArrow(mc, 1); pressArrow(mc, 1); wait = 10; });
        PLAN.add(() -> { capture(mc, "01_raza"); log(mc, "raza elegida en pantalla"); wait = 5; });
        for (int i = 0; i < 25; i++) {
            final int n = i;
            PLAN.add(() -> {
                if (mc.screen == null) return;
                String name = mc.screen.getClass().getSimpleName();
                capture(mc, String.format("%02d_%s", n + 2, name));
                boolean ok = press(mc, "screen.zenkai.next") || press(mc, "screen.zenkai.confirm");
                log(mc, "pulsado en " + name + (ok ? "" : " (SIN botón)"));
                wait = 10;
            });
        }
        PLAN.add(() -> { log(mc, "tras confirmar"); wait = 60; });
        PLAN.add(() -> { log(mc, "3 s después"); capture(mc, "99_final"); wait = 5; });
    }

    private static boolean press(Minecraft mc, String key) {
        if (mc.screen == null) return false;
        for (var w : mc.screen.children()) {
            if (w instanceof AbstractButton b && b.visible && b.active
                    && b.getMessage().getContents() instanceof TranslatableContents tc && tc.getKey().equals(key)) {
                b.onPress();
                return true;
            }
        }
        return false;
    }

    private static void pressArrow(Minecraft mc, int index) {
        if (mc.screen == null) return;
        int k = 0;
        for (var w : mc.screen.children()) {
            if (w instanceof com.hmc.zenkai.client.gui.buttons.ArrowIconButton b) {
                if (k++ == index) { b.onPress(); return; }
            }
        }
    }

    private static void command(Minecraft mc, String cmd) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        var id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), cmd);
        });
    }

    private static void log(Minecraft mc, String what) {
        var c = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        String srv = "?";
        var server = mc.getSingleplayerServer();
        if (server != null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                var s = PlayerStatsAttachment.get(sp);
                srv = s.getRace() + " raceChosen=" + s.isRaceChosen() + " styleChosen=" + s.isStyleChosen()
                        + " reincarnating=" + s.isReincarnating() + " inOtherworld=" + s.isInOtherworld();
            }
        }
        Zenkai.LOGGER.info("[RACEFLOW] {} | pantalla={} | cliente: {} raceChosen={} styleChosen={} | servidor: {}", what,
                mc.screen == null ? "-" : mc.screen.getClass().getSimpleName(),
                c.getRace(), c.isRaceChosen(), c.isStyleChosen(), srv);
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_raceflow");
        d.mkdirs();
        return d;
    }

    private static void capture(Minecraft mc, String name) {
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(new File(out(mc), name + ".png"));
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("RaceFlowShots: no se pudo capturar {}", name, ex);
        }
    }
}
