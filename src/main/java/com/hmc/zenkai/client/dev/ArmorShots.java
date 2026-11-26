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
 * <p>
 * Tercera línea opcional {@code flotando} (2026-11-25, «oculta al jugador y deja la armadura flotando para ver que no se
 * rompe nada»): la armadura se viste en un SOPORTE DE ARMADURAS invisible y sin gravedad a 3 bloques del suelo, de día,
 * con el jugador invisible y en 1ª persona como cámara. El soporte gira para cada ángulo (frente, 3/4, perfil, 3/4 trasera,
 * espalda), la cámara sube y baja (desde abajo, desde arriba) y hay una pose de ANDAR (brazos y piernas a ±30°) para ver el
 * solape de las perneras en movimiento. Sin cuerpo delante se ven las caras interiores, los huecos y el z-fighting que la
 * piel tapa. (Primer intento con el propio jugador: en 3ª persona la cámara gira con él y cada foto salía de frente, y de
 * noche el cuerpo Saiyan se transformaba en Oozaru.)
 * <p>
 * Tercera línea {@code piernas}: los cuerpos de raza con SOLO pantalón y botas, para ver que la cintura del pantalón
 * ({@code armorWaist}) tapa la ropa interior y deja el pecho libre.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ArmorShots {
    private ArmorShots() {}

    private static final int WAIT_LOAD = 160, WAIT = 40;
    private static Boolean enabled;
    private static String world, only;
    private static boolean opened, floating, legsOnly;
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
                    floating = lines.size() > 2 && lines.get(2).trim().equalsIgnoreCase("flotando");
                    // "piernas": cuerpos de raza con SOLO pantalon y botas (la cintura armorWaist debe tapar la ropa
                    // interior y dejar el pecho libre; usuario 2026-11-25, SSJ4 con el pantalon de GT)
                    legsOnly = lines.size() > 2 && lines.get(2).trim().equalsIgnoreCase("piernas");
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
            if (floating) {
                String tag = set.id().getPath() + "_flotando";
                if (PLAN.isEmpty()) setupFloating(mc);
                standScene(mc, set, tag + "_frente", 180F, 0F, false);
                standScene(mc, set, tag + "_34", 145F, 0.8F, false);
                standScene(mc, set, tag + "_perfil", 90F, 0F, false);
                standScene(mc, set, tag + "_34_atras", 35F, 0.8F, false);
                standScene(mc, set, tag + "_espalda", 0F, 0F, false);
                standScene(mc, set, tag + "_abajo", 150F, -2.8F, false);
                standScene(mc, set, tag + "_arriba", 150F, 3.0F, false);
                standScene(mc, set, tag + "_andando", 140F, 0.5F, true);
                standScene(mc, set, tag + "_andando_lado", 90F, 0F, true);
                continue;
            }
            for (Body body : BODIES) {
                String tag = set.id().getPath() + "_" + body.tag();
                scene(mc, set, body, tag + "_frente", CameraType.THIRD_PERSON_FRONT, 0F, 10F, false);
                scene(mc, set, body, tag + "_espalda", CameraType.THIRD_PERSON_BACK, 0F, 10F, false);
            }
        }
    }

    private static void scene(Minecraft mc, ArmorSets.Def set, Body body, String name, CameraType cam,
                              float yaw, float pitch, boolean crouch) {
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
                sp.setItemSlot(EquipmentSlot.CHEST, legsOnly ? ItemStack.EMPTY : piece(set, ArmorItem.Type.CHESTPLATE));
                sp.setItemSlot(EquipmentSlot.LEGS, piece(set, ArmorItem.Type.LEGGINGS));
                sp.setItemSlot(EquipmentSlot.FEET, piece(set, ArmorItem.Type.BOOTS));
            });
            mc.options.setCameraType(cam);
            wait = WAIT;
        });
        PLAN.add(() -> {
            mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
            mc.player.yBodyRot = yaw; mc.player.yBodyRotO = yaw; mc.player.yHeadRot = yaw; mc.player.yHeadRotO = yaw;
            mc.options.keyShift.setDown(crouch);
            wait = 30;
        });
        PLAN.add(() -> capture(mc, name));
    }

    private static net.minecraft.world.phys.Vec3 anchor;
    private static net.minecraft.world.entity.decoration.ArmorStand stand;

    /** Día, jugador humano en espectador y 1ª persona, sin HUD; el ancla queda 3 bloques sobre él. */
    private static void setupFloating(Minecraft mc) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                // el soporte va en el nivel DEL JUGADOR, no en server.overworld(): con una copia guardada en otra
                // dimension (HFIL) el soporte aparecia en el Overworld y las 54 fotos salieron vacias (2026-11-25)
                var nivel = sp.serverLevel();
                server.overworld().setDayTime(6000);
                server.overworld().setWeatherParameters(6000, 0, false, false);
                PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
                att.setRace(Race.HUMAN);
                att.setRaceChosen(true);
                PlayerLifeCycle.sync(sp);
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET}) sp.setItemSlot(slot, ItemStack.EMPTY);
                // ESPECTADOR como camara: ni cae ni se ve. Con "volar + invisible" el vuelo de Zenkai le quitaba la
                // capacidad de volar al jugador sin la skill y caia entre el teletransporte y la foto (fotos vacias)
                sp.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                anchor = sp.position().add(0, 3, 0);
                stand = new net.minecraft.world.entity.decoration.ArmorStand(nivel, anchor.x, anchor.y, anchor.z);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                stand.setShowArms(true);
                stand.setNoBasePlate(true);
                stand.setInvulnerable(true);
                nivel.addFreshEntity(stand);
            });
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            wait = WAIT;
        });
    }

    /**
     * Soporte girado a {@code standYaw} (180 = mirando a la cámara), cámara a 3,2 bloques en -Z del ancla y {@code dy}
     * bloques por encima/debajo del centro de la armadura, mirándola.
     */
    private static void standScene(Minecraft mc, ArmorSets.Def set, String name, float standYaw, float dy, boolean walking) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null || stand == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                stand.setItemSlot(EquipmentSlot.HEAD, piece(set, ArmorItem.Type.HELMET));
                stand.setItemSlot(EquipmentSlot.CHEST, piece(set, ArmorItem.Type.CHESTPLATE));
                stand.setItemSlot(EquipmentSlot.LEGS, piece(set, ArmorItem.Type.LEGGINGS));
                stand.setItemSlot(EquipmentSlot.FEET, piece(set, ArmorItem.Type.BOOTS));
                var cero = new net.minecraft.core.Rotations(0, 0, 0);
                float a = walking ? 30F : 0F;
                stand.setHeadPose(cero);
                stand.setBodyPose(cero);
                stand.setRightArmPose(new net.minecraft.core.Rotations(-a, 0, 0));
                stand.setLeftArmPose(new net.minecraft.core.Rotations(a, 0, 0));
                stand.setRightLegPose(new net.minecraft.core.Rotations(a, 0, 0));
                stand.setLeftLegPose(new net.minecraft.core.Rotations(-a, 0, 0));
                stand.moveTo(anchor.x, anchor.y, anchor.z, standYaw, 0F);
                stand.setYBodyRot(standYaw);
                stand.setYHeadRot(standYaw);
                double dist = 3.2, centro = anchor.y + 1.0;
                float pitch = (float) Math.toDegrees(Math.atan2(dy, dist));
                // ojos a 1,62 de los pies; yaw 0 = mirando a +Z (hacia el soporte)
                sp.connection.teleport(anchor.x, centro + dy - 1.62, anchor.z - dist, 0F, pitch);
            });
            wait = WAIT;
        });
        PLAN.add(() -> capture(mc, name));
        PLAN.add(() -> wait = 5);
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
