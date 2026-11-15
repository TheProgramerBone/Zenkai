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
    /** Segunda línea del .txt: "item" = objetos en la mano en 3ª persona (FormHandAnchor) en vez de la mano en 1ª. */
    private static String mode = "";
    /** Modo "scene": tercera línea opcional con las poses a capturar separadas por comas (vacía = todas). */
    private static String sceneOnly = "";
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
                    var lines = Files.readAllLines(trigger.toPath());
                    world = lines.get(0).trim();
                    if (lines.size() > 1) mode = lines.get(1).trim();
                    if (lines.size() > 2) sceneOnly = lines.get(2).trim();
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
        if (mode.equals("scene")) {
            scenePoses(mc);
            return;
        }
        if (mode.equals("scenereal")) {
            sceneReal(mc);
            return;
        }
        if (mode.equals("oozarugrow")) {
            oozaruGrow(mc);
            return;
        }
        if (mode.equals("item")) {
            itemScene(mc, "saiyan_base", "zenkai:base");
            itemScene(mc, "saiyan_oozaru", "zenkai:oozaru");
            itemScene(mc, "saiyan_super_oozaru", "zenkai:super_oozaru");
            return;
        }
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

    /**
     * Modo "item" (2026-11-13): espada en la mano principal y antorcha en la secundaria, en 3ª persona de frente y de
     * espaldas, quieto y a mitad de golpe. Sirve para ver si el objeto sale del puño del cuerpo de la forma.
     */
    private static void itemScene(Minecraft mc, String tag, String form) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                att.setRace(Race.SAIYAN);
                att.setRaceChosen(true);
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH));
                sp.level().getServer().overworld().setDayTime(6000);
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).setFormId(sp, ResourceLocation.parse(form));
                PlayerLifeCycle.sync(sp);
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
            });
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            wait = WAIT;
        });
        // Escena de despertar (brazos en cruz tras el estallido): el objeto tiene que seguir al puño también con PAL.
        // Un fotograma cada 15 ticks; la escena larga dura 126, así que las capturas limpias van después.
        for (int f = 0; f < 11; f++) {
            int n = f;
            PLAN.add(() -> { capture(mc, tag + "_scene" + n); wait = 15; });
        }
        for (CameraType cam : new CameraType[]{CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK}) {
            String side = cam == CameraType.THIRD_PERSON_FRONT ? "front" : "back";
            PLAN.add(() -> {
                mc.options.setCameraType(cam);
                mc.player.setYRot(0F); mc.player.yRotO = 0F; mc.player.setXRot(10F); mc.player.xRotO = 10F;
                mc.player.yBodyRot = 0F; mc.player.yBodyRotO = 0F; mc.player.yHeadRot = 0F; mc.player.yHeadRotO = 0F;
                wait = 20;
            });
            PLAN.add(() -> capture(mc, tag + "_" + side));
            // Golpe: a los 3 ticks el brazo va por la mitad del barrido.
            PLAN.add(() -> { mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND); wait = 2; });
            PLAN.add(() -> capture(mc, tag + "_" + side + "_swing"));
            PLAN.add(() -> wait = 10);
        }
        PLAN.add(() -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; });
    }

    /**
     * Modo "scene" (2026-11-13): cada pose de la secuencia larga (rage_scene.animation.json) puesta a mano en el jugador, de frente
     * y de perfil, capturada en el quiebre, la escalada, el estallido y el remate. Solo la pose: sin aura, viñeta ni cambio de forma.
     */
    private static final String[] SCENE_POSES = {"kneel", "roar", "clutch", "resolve", "ssj3_long", "serene", "guard_charge",
            "pride", "skyward", "flex", "crossed", "ultra_ego", "ui_sign", "ui_mastered", "potential", "beast", "oozaru",
            "super_oozaru", "ssj4", "ikari", "rose", "cocoon", "black", "kata"};
    /** Poses que se ven sobre el cuerpo de su forma (el Oozaru acaba su escena siendo mono): se pone la forma antes. */
    private static final java.util.Map<String, String> SCENE_FORM = java.util.Map.of(
            "oozaru", "zenkai:oozaru", "super_oozaru", "zenkai:super_oozaru", "ssj4", "zenkai:super_oozaru");
    /** Ticks de la escena estirada (estallido en 80): quiebre, escalada, estallido, remate. */
    private static final int[] SCENE_TICKS = {24, 64, 84, 100};

    private static void scenePoses(Minecraft mc) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                att.setRace(Race.SAIYAN);
                att.setRaceChosen(true);
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                sp.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                sp.level().getServer().overworld().setDayTime(6000);
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).setFormId(sp, ResourceLocation.parse("zenkai:base"));
                PlayerLifeCycle.sync(sp);
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
            });
            mc.options.hideGui = true;
            wait = WAIT;
        });
        String[] poses = sceneOnly.isEmpty() ? SCENE_POSES : sceneOnly.split(",");
        for (String view : new String[]{"front", "side"}) {
            for (String pose : poses) {
                String form = SCENE_FORM.getOrDefault(pose, "zenkai:base");
                PLAN.add(() -> {
                    var server = mc.getSingleplayerServer();
                    if (server != null) server.execute(() -> {
                        var sp = server.getPlayerList().getPlayers().get(0);
                        var f = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
                        if (f.getFormId().toString().equals(form)) return;
                        f.setFormId(sp, ResourceLocation.parse(form));
                        PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
                    });
                    // Un cambio de forma puede disparar su propia secuencia larga (primera vez): se espera a que acabe.
                    boolean change = !mc.player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId().toString().equals(form);
                    wait = change ? 170 : 0;
                });
                PLAN.add(() -> {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    mc.player.setYRot(0F); mc.player.yRotO = 0F; mc.player.setXRot(5F); mc.player.xRotO = 5F;
                    // Perfil: el cuerpo gira 90° y la cabeza se queda mirando a la cámara (el giro de cabeza no pasa de 50°,
                    // así que el cuerpo queda en diagonal; basta para leer la profundidad de la pose).
                    float body = view.equals("side") ? 75F : 0F;
                    mc.player.yBodyRot = body; mc.player.yBodyRotO = body; mc.player.yHeadRot = 0F; mc.player.yHeadRotO = 0F;
                    com.hmc.zenkai.event.ZenkaiPalAnimations.playRageScene(mc.player, pose);
                    wait = SCENE_TICKS[0] - 1;
                });
                for (int i = 0; i < SCENE_TICKS.length; i++) {
                    final int k = i;
                    PLAN.add(() -> {
                        if (view.equals("side")) { mc.player.yBodyRot = 75F; mc.player.yBodyRotO = 75F; }
                        capture(mc, "scene_" + pose + "_" + view + "_" + k);
                        wait = k + 1 < SCENE_TICKS.length ? SCENE_TICKS[k + 1] - SCENE_TICKS[k] - 1 : 0;
                    });
                }
                PLAN.add(() -> { com.hmc.zenkai.event.ZenkaiPalAnimations.stopRageScene(mc.player); wait = 30; });
            }
        }
        PLAN.add(() -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; });
    }

    /**
     * Modo "scenereal" (2026-11-13): arranca la ESCENA de verdad (AwakeningScene.start, como al recibir el aviso) para cada forma de
     * la 3.ª línea (ids separados por comas): la pose la elige pick() por los datos de la forma y la cara lee la forma de la escena
     * (transform_face). Primer plano de frente para ver ojos y boca. No cambia la forma ni el aura.
     */
    private static void sceneReal(Minecraft mc) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                att.setRace(Race.SAIYAN);
                att.setRaceChosen(true);
                sp.level().getServer().overworld().setDayTime(6000);
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).setFormId(sp, ResourceLocation.parse("zenkai:base"));
                PlayerLifeCycle.sync(sp);
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
            });
            mc.options.hideGui = true;
            wait = WAIT;
        });
        for (String form : sceneOnly.split(",")) {
            String tag = form.substring(form.indexOf(':') + 1);
            PLAN.add(() -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setYRot(0F); mc.player.yRotO = 0F; mc.player.setXRot(5F); mc.player.xRotO = 5F;
                mc.player.yBodyRot = 0F; mc.player.yBodyRotO = 0F; mc.player.yHeadRot = 0F; mc.player.yHeadRotO = 0F;
                var info = new com.hmc.zenkai.feature.rage.LongTransformState.Pending(
                        mc.level.getGameTime() + 120, ResourceLocation.parse(form), "");
                com.hmc.zenkai.client.rage.AwakeningScene.start(mc.player, true, info, new float[]{0.8f, 0.85f, 1f},
                        com.hmc.zenkai.client.aura.compose.LongTransformMode.REDUCED);
                wait = SCENE_TICKS[0] - 1;
            });
            for (int i = 0; i < SCENE_TICKS.length; i++) {
                final int k = i;
                PLAN.add(() -> {
                    capture(mc, "real_" + tag + "_" + k);
                    wait = k + 1 < SCENE_TICKS.length ? SCENE_TICKS[k + 1] - SCENE_TICKS[k] - 1 : 0;
                });
            }
            PLAN.add(() -> { com.hmc.zenkai.client.rage.AwakeningScene.cancel(mc.player); wait = 50; });
        }
        PLAN.add(() -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; });
    }

    /**
     * Modo "oozarugrow" (2026-11-13): pone al jugador en Oozaru por el servidor (primera vez: dispara su secuencia larga) y saca un
     * fotograma cada 10 ticks durante 130, con la escala del jugador en el log. Sirve para ver que crece poco a poco.
     */
    private static void oozaruGrow(Minecraft mc) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                att.setRace(Race.SAIYAN);
                att.setRaceChosen(true);
                att.flags().forgetLongCinematicSeen("zenkai:oozaru");
                sp.level().getServer().overworld().setDayTime(6000);
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).setFormId(sp, ResourceLocation.parse("zenkai:base"));
                PlayerLifeCycle.sync(sp);
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
            });
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            wait = WAIT + 40;
        });
        PLAN.add(() -> {
            mc.player.setYRot(0F); mc.player.yRotO = 0F; mc.player.setXRot(5F); mc.player.xRotO = 5F;
            var server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).setFormId(sp, ResourceLocation.parse("zenkai:oozaru"));
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
            });
            wait = 2;
        });
        for (int i = 0; i < 14; i++) {
            final int k = i;
            PLAN.add(() -> { capture(mc, String.format("grow_%02d", k)); wait = 9; });
        }
        PLAN.add(() -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; });
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
