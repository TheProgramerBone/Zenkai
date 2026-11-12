package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * HERRAMIENTA DE DESARROLLO: fotogramas de la animación de reposo de una entidad GeckoLib. {@code <run>/zenkai_idleshots.txt}:
 * línea 1 = carpeta de una COPIA del mundo, línea 2 = id de la entidad (p. ej. {@code zenkai:kaio}). La invoca a 3 bloques
 * delante de la cámara, mirándola, sin IA, y captura un fotograma cada 10 ticks durante 4 s en {@code zenkai_idleshots/}.
 * Nació para la animación que le faltaba a Kaiosama (2026-11-12).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class EntityIdleShots {
    private EntityIdleShots() {}

    private static final int WAIT_LOAD = 160, FRAMES = 8, EVERY = 10;
    private static Boolean enabled;
    private static String world, entity;
    private static boolean opened, summoned;
    private static int ticks, frame;

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_idleshots.txt");
            if (trigger.isFile()) {
                try {
                    List<String> l = Files.readAllLines(trigger.toPath());
                    world = l.get(0).trim();
                    entity = l.get(1).trim();
                    enabled = !world.isEmpty() && !entity.isEmpty();
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
        if (!summoned) {
            summoned = true;
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            if (server != null) server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                var src = sp.createCommandSourceStack().withPermission(4);
                server.getCommands().performPrefixedCommand(src, "time set noon");
                // Coordenadas ABSOLUTAS: con «^ ^ ^3» el comando usaba la orientación de antes del tp y la entidad salía fuera
                // de cámara. El jugador mira al sur (yaw 0) y la entidad, a 3 bloques, mira al norte (hacia él).
                sp.teleportTo(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ(), 0f, 10f);
                server.getCommands().performPrefixedCommand(src, String.format(java.util.Locale.ROOT,
                        "summon %s %.2f %.2f %.2f {NoAI:1b,Rotation:[180f,0f],Tags:[\"zenkai_idleshot\"]}",
                        entity, sp.getX(), sp.getY(), sp.getZ() + 3.0));
            });
            ticks = WAIT_LOAD - 40;   // 2 s para que aparezca
            return;
        }
        if ((ticks - WAIT_LOAD) % EVERY != 0) return;
        if (frame < FRAMES) {
            capture(mc, String.format("frame_%02d", frame++));
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_idleshots");
        d.mkdirs();
        return d;
    }

    private static void capture(Minecraft mc, String name) {
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(new File(out(mc), name + ".png"));
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("EntityIdleShots: no se pudo capturar {}", name, ex);
        }
    }
}
