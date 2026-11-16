package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
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
 * HERRAMIENTA DE DESARROLLO (2026-11-15): capturas de la pose de derribado propia ({@code zenkai.downed}). Solo se activa si existe
 * {@code <run>/zenkai_downedshots.txt} (primera línea = carpeta de una COPIA del mundo). Deja al jugador derribado por el servidor
 * (body 0 y plazo larguísimo, reafirmado cada 10 ticks para que el sistema de derribado no lo levante ni lo mate) y fotografía
 * de frente, de perfil, de espalda, cenital y en 1ª persona, con dos fotogramas separados por medio ciclo de respiración.
 * Salida: {@code zenkai_downedshots/}. BORRAR el .txt al terminar.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class DownedShots {
    private DownedShots() {}

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
            File trigger = new File(mc.gameDirectory, "zenkai_downedshots.txt");
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
        if (mc.screen != null) mc.setScreen(null);
        if (++ticks < WAIT_LOAD) return;
        if (step > 0 && ticks % 10 == 0) keepDowned(mc);
        if (PLAN.isEmpty() && step == 0) buildPlan(mc);
        if (wait > 0) { wait--; return; }
        if (step < PLAN.size()) {
            try { PLAN.get(step++).run(); }
            catch (Exception ex) { Zenkai.LOGGER.warn("DownedShots: paso {} falló", step - 1, ex); }
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_downedshots");
        d.mkdirs();
        return d;
    }

    /** Deja (o mantiene) al jugador derribado desde el servidor. */
    private static void keepDowned(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayers().get(0);
            PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
            att.setBody(0);
            att.flags().setDowned(true);
            att.flags().setDownedUntil(sp.serverLevel().getGameTime() + 1_000_000L);
            PlayerLifeCycle.sync(sp);
        });
    }

    private static void view(Minecraft mc, CameraType cam, float yaw, float pitch, int w) {
        mc.options.setCameraType(cam);
        mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.yHeadRot = yaw; mc.player.yHeadRotO = yaw;
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
        wait = w;
    }

    private static void buildPlan(Minecraft mc) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), "time set noon");
            });
            keepDowned(mc);
            view(mc, CameraType.THIRD_PERSON_FRONT, 0F, 10F, 60);
        });
        PLAN.add(() -> capture(mc, "front_a"));
        PLAN.add(() -> wait = 20);
        PLAN.add(() -> capture(mc, "front_b"));
        PLAN.add(() -> view(mc, CameraType.THIRD_PERSON_FRONT, 90F, 10F, 20));
        PLAN.add(() -> capture(mc, "side_a"));
        PLAN.add(() -> wait = 20);
        PLAN.add(() -> capture(mc, "side_b"));
        PLAN.add(() -> view(mc, CameraType.THIRD_PERSON_BACK, 0F, 25F, 20));
        PLAN.add(() -> capture(mc, "back"));
        PLAN.add(() -> view(mc, CameraType.THIRD_PERSON_BACK, 0F, 90F, 20));
        PLAN.add(() -> capture(mc, "top"));
        PLAN.add(() -> view(mc, CameraType.FIRST_PERSON, 0F, 20F, 20));
        PLAN.add(() -> capture(mc, "first_person"));
    }

    private static void capture(Minecraft mc, String name) {
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(new File(out(mc), name + ".png"));
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("DownedShots: no se pudo capturar {}", name, ex);
        }
    }
}
