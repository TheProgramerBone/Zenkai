package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.registry.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
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
 * HERRAMIENTA DE DESARROLLO (2026-11-25, skill armor-from-reference): viste al jugador con cada set de
 * {@link ArmorSets#ALL} y lo fotografía en 3ª persona de frente y de espalda sobre varios cuerpos (Saiyan ♂, Humano ♀,
 * Namekiano), para ver la armadura GeckoLib PUESTA sobre el cuerpo de raza real y no solo en el render de la skill.
 * Mismo patrón que FurShots: solo se activa si existe {@code <run>/zenkai_armorshots.txt} (primera línea = carpeta de una
 * COPIA del mundo; segunda línea opcional = id de un solo set, p. ej. {@code piccolo_gi}). Capturas en
 * {@code zenkai_armorshots/<set>_<cuerpo>_<frente|espalda>.png}; al terminar escribe done.txt y cierra. BORRAR el .txt.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ArmorShots {
    private ArmorShots() {}

    private static final int WAIT_LOAD = 160, WAIT = 40;
    private static Boolean enabled;
    private static String world, only;
    private static boolean opened;
    private static int ticks, step, wait;
    private static final List<Runnable> PLAN = new ArrayList<>();

    private record Body(String tag, Race race, boolean female) {}

    private static final Body[] BODIES = {
            new Body("saiyan_m", Race.SAIYAN, false),
            new Body("humano_f", Race.HUMAN, true),
            new Body("namek", Race.NAMEKIAN, false)};

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_armorshots.txt");
            if (trigger.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(trigger.toPath());
                    world = lines.get(0).trim();
                    only = lines.size() > 1 && !lines.get(1).isBlank() ? lines.get(1).trim() : null;
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
            catch (Exception ex) { Zenkai.LOGGER.warn("ArmorShots: paso {} falló", step - 1, ex); }
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_armorshots");
        d.mkdirs();
        return d;
    }

    private static void buildPlan(Minecraft mc) {
        for (ArmorSets.Def set : ArmorSets.ALL) {
            if (only != null && !set.id().getPath().equals(only)) continue;
            for (Body body : BODIES) {
                String tag = set.id().getPath() + "_" + body.tag();
                scene(mc, set, body, tag + "_frente", CameraType.THIRD_PERSON_FRONT);
                scene(mc, set, body, tag + "_espalda", CameraType.THIRD_PERSON_BACK);
            }
        }
    }

    private static void scene(Minecraft mc, ArmorSets.Def set, Body body, String name, CameraType cam) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                att.setRace(body.race());
                att.setRaceChosen(true);
                var vis = PlayerVisualAttachment.get(sp);
                vis.setGender(body.female() ? PlayerVisualAttachment.Gender.FEMALE : PlayerVisualAttachment.Gender.MALE);
                PlayerLifeCycle.syncVisual(sp);
                PlayerLifeCycle.sync(sp);
                // casco fuera salvo que el set lo tenga: un casco de un set anterior taparía la cabeza
                sp.setItemSlot(EquipmentSlot.HEAD, piece(set, ArmorItem.Type.HELMET));
                sp.setItemSlot(EquipmentSlot.CHEST, piece(set, ArmorItem.Type.CHESTPLATE));
                sp.setItemSlot(EquipmentSlot.LEGS, piece(set, ArmorItem.Type.LEGGINGS));
                sp.setItemSlot(EquipmentSlot.FEET, piece(set, ArmorItem.Type.BOOTS));
            });
            mc.options.setCameraType(cam);
            wait = WAIT;
        });
        PLAN.add(() -> {
            mc.player.setYRot(0F); mc.player.yRotO = 0F; mc.player.setXRot(10F); mc.player.xRotO = 10F;
            wait = 30;
        });
        PLAN.add(() -> capture(mc, name));
    }

    private static ItemStack piece(ArmorSets.Def set, ArmorItem.Type type) {
        var item = ModItems.dbArmor(set, type);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static void capture(Minecraft mc, String name) {
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(new File(out(mc), name + ".png"));
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("ArmorShots: no se pudo capturar {}", name, ex);
        }
    }
}
