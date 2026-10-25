package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
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
 * HERRAMIENTA DE DESARROLLO (2026-10-21): capturas de la MANO en 1ª persona según el modelo/forma
 * (Oozaru, Namekiano Naranja, Gigante Namekiano), mismo patrón que FpWeaponShots. Solo se activa si existe
 * {@code <run>/zenkai_handshots.txt} (primera línea = carpeta de una COPIA del mundo). Cada escena fija
 * raza/forma/interruptor en el servidor integrado y guarda la captura entera en {@code zenkai_handshots/}
 * (1ª y 3ª persona). Al terminar escribe done.txt y cierra el juego. BORRAR el .txt al acabar.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class HandShots {
    private HandShots() {}

    private static final int WAIT_LOAD = 160, WAIT = 40;
    private static Boolean enabled;
    private static String world;
    private static boolean opened;
    private static int ticks, step, wait;
    private static final List<Runnable> PLAN = new ArrayList<>();

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_handshots.txt");
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
            catch (Exception ex) { Zenkai.LOGGER.warn("HandShots: paso {} falló", step - 1, ex); }
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_handshots");
        d.mkdirs();
        return d;
    }

    private static void buildPlan(Minecraft mc) {
        scene(mc, "saiyan_base", Race.SAIYAN, "zenkai:base", false);
        scene(mc, "saiyan_oozaru", Race.SAIYAN, "zenkai:oozaru", false);
        scene(mc, "namek_base", Race.NAMEKIAN, "zenkai:base", false);
        scene(mc, "namek_orange", Race.NAMEKIAN, "zenkai:namek_orange", false);
        scene(mc, "namek_giant", Race.NAMEKIAN, "zenkai:base", true);
        scene(mc, "namek_giant_orange", Race.NAMEKIAN, "zenkai:namek_orange", true);
    }

    private static void scene(Minecraft mc, String tag, Race race, String form, boolean giant) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                att.setRace(race);
                att.setRaceChosen(true);
                att.skills().clear();
                att.skills().setLevel(SkillEffects.NAMEK_GIANT, giant ? 1 : 0);
                SkillToggles.set(sp, SkillEffects.NAMEK_GIANT, giant);
                att.refreshSkillTree();
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).setFormId(sp, ResourceLocation.parse(form));
                PlayerLifeCycle.sync(sp);
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
            });
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            wait = WAIT;
        });
        PLAN.add(() -> {
            mc.player.setYRot(0F); mc.player.yRotO = 0F; mc.player.setXRot(20F); mc.player.xRotO = 20F;
            wait = 20;
        });
        PLAN.add(() -> capture(mc, tag + "_fp"));
        PLAN.add(() -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private static void capture(Minecraft mc, String name) {
        if (mc.player != null) {
            Zenkai.LOGGER.info("[HANDSHOTS] {} form={} scale={}", name,
                    mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId(),
                    mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE));
        }
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(new File(out(mc), name + ".png"));
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("HandShots: no se pudo capturar {}", name, ex);
        }
    }
}
