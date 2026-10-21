package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.kiweapon.KiWeaponState;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.player.Player;
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
 * HERRAMIENTA DE DESARROLLO: capturas automáticas de las armas de ki en 1ª persona, para revisar el render
 * sin jugar (creada 2026-10-15 para iterar sobre los defectos de la mano). Nunca corre en producción y solo
 * se activa si existe {@code <run>/zenkai_fpshots.txt}; su primera línea es la CARPETA del mundo que se abre
 * (usar una COPIA: cambia la hora del mundo). Al terminar escribe {@code zenkai_fpshots/done.txt} y cierra el juego.
 *
 * Qué hace: en la pantalla de título abre el mundo; en él pone la cámara en 1ª persona, deja la mano principal
 * libre y, para cada arma, de día y de noche y con dos cabeceos, fija la variante SOLO en el cliente
 * ({@link KiWeaponState#clientSet}: el servidor solo reenvía si su estado cambia) y guarda el recorte de la zona
 * del arma. Por arma sale una hoja {@code <arma>.png} de 2x2 (fila = día/noche, columna = cabeceo) y los recortes
 * sueltos en {@code tiles/}.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class FpWeaponShots {
    private FpWeaponShots() {}

    private static final String[] VARIANTS = {"ki_sword", "ki_axe", "ki_mace", "ki_scythe", "ki_hammer"};
    private static final long[] TIMES = {6000L, 18000L};
    private static final float[] PITCHES = {-35F, 15F};
    /** Ticks de espera tras cambiar la hora (el cliente la recibe cada segundo) y tras cambiar arma/cabeceo. */
    private static final int WAIT_TIME = 40, WAIT_SHOT = 10, WAIT_LOAD = 160;

    private static Boolean enabled;
    private static String world;
    private static boolean opened;
    private static int ticks, step, wait;
    private static final List<Runnable> PLAN = new ArrayList<>();
    private static NativeImage sheet;

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_fpshots.txt");
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
        // Sin foco de ventana el juego abre el menú de pausa y tapa la captura: se cierra cada tick.
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (++ticks < WAIT_LOAD) return;
        if (PLAN.isEmpty() && step == 0) buildPlan(mc);
        if (wait > 0) { wait--; return; }
        if (step < PLAN.size()) {
            PLAN.get(step++).run();
            return;
        }
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_fpshots");
        new File(d, "tiles").mkdirs();
        return d;
    }

    /** Segunda línea de zenkai_fpshots.txt = "infuse": en vez de las armas de ki, un ítem corriente con Ki Infuse. */
    private static boolean infuseScene() {
        try {
            var lines = Files.readAllLines(new File(Minecraft.getInstance().gameDirectory, "zenkai_fpshots.txt").toPath());
            return lines.size() > 1 && lines.get(1).trim().equals("infuse");
        } catch (Exception e) { return false; }
    }

    /** Escena Ki Infuse: concede la habilidad, enciende el interruptor, pone una espada de diamante y captura en 1ª y 3ª
     *  persona de día y de noche, registrando cada condición de {@code infuseGlowActive}. */
    private static void buildInfusePlan(Minecraft mc) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(sp).skills()
                        .grant(com.hmc.zenkai.feature.skills.SkillEffects.KI_INFUSE, 1);
                com.hmc.zenkai.feature.skills.SkillToggles.set(sp, com.hmc.zenkai.feature.skills.SkillEffects.KI_INFUSE, true);
                sp.getInventory().selected = 0;
                sp.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            });
            wait = WAIT_TIME;
        });
        for (int ti = 0; ti < TIMES.length; ti++) {
            final long time = TIMES[ti];
            final int row = ti;
            PLAN.add(() -> {
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> server.overworld().setDayTime(time));
                wait = WAIT_TIME;
            });
            for (CameraType cam : new CameraType[]{CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_FRONT}) {
                PLAN.add(() -> {
                    mc.options.setCameraType(cam);
                    Player pl = mc.player;
                    pl.getInventory().selected = 0;
                    pl.setYRot(0F); pl.yRotO = 0F; float pitchI = cam == CameraType.FIRST_PERSON ? -30F : 15F; pl.setXRot(pitchI); pl.xRotO = pitchI;
                    Zenkai.LOGGER.info("[DIAG-INFUSE] cam={} skillLevel={} toggleOn={} cfgGlow={} attackDmg={} hasWeapon={} active={} item={}",
                            cam, com.hmc.zenkai.feature.skills.SkillEffects.level(pl, com.hmc.zenkai.feature.skills.SkillEffects.KI_INFUSE),
                            com.hmc.zenkai.feature.skills.SkillToggles.isOn(pl, com.hmc.zenkai.feature.skills.SkillEffects.KI_INFUSE),
                            com.hmc.zenkai.config.ClientConfig.kiInfuseGlow(),
                            com.hmc.zenkai.feature.combat.KiInfusion.attackDamageOf(pl),
                            com.hmc.zenkai.feature.combat.KiInfusion.hasWeapon(pl),
                            com.hmc.zenkai.client.kiweapon.KiWeaponVfx.infuseGlowActive(pl), pl.getMainHandItem());
                    var srv = mc.getSingleplayerServer();
                    if (srv != null) srv.execute(() -> { var sp = srv.getPlayerList().getPlayers().get(0);
                        Zenkai.LOGGER.info("[DIAG-INFUSE] SERVIDOR attackDmg={} hasWeapon={} item={} mods={}",
                                com.hmc.zenkai.feature.combat.KiInfusion.attackDamageOf(sp),
                                com.hmc.zenkai.feature.combat.KiInfusion.hasWeapon(sp), sp.getMainHandItem(),
                                sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getModifiers()); });
                    wait = WAIT_SHOT * 2;
                });
                PLAN.add(() -> capture(mc, "infuse_" + (cam == CameraType.FIRST_PERSON ? "fp" : "tp"), row, 0));
            }
        }
    }

    private static void buildPlan(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (infuseScene()) { buildInfusePlan(mc); return; }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        Player p = mc.player;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getItem(i).isEmpty()) { p.getInventory().selected = i; break; }
        }
        p.getInventory().setItem(p.getInventory().selected, net.minecraft.world.item.ItemStack.EMPTY);
        for (String v : VARIANTS) {
            PLAN.add(() -> sheet = new NativeImage(1600, 1292, true));
            for (int ti = 0; ti < TIMES.length; ti++) {
                final int row = ti;
                final long time = TIMES[ti];
                PLAN.add(() -> {
                    var server = mc.getSingleplayerServer();
                    if (server != null) server.execute(() -> server.overworld().setDayTime(time));
                    wait = WAIT_TIME;
                });
                for (int pi = 0; pi < PITCHES.length; pi++) {
                    final int col = pi;
                    final float pitch = PITCHES[pi];
                    PLAN.add(() -> {
                        Player pl = mc.player;
                        KiWeaponState.clientSet(pl.getId(), v, "", pl.level().getGameTime() - 200L);
                        pl.setYRot(0F); pl.yRotO = 0F;
                        pl.setXRot(pitch); pl.xRotO = pitch;
                        wait = WAIT_SHOT;
                    });
                    PLAN.add(() -> capture(mc, v, row, col));
                }
            }
            // Extra: la última combinación (noche) SIN el halo de mano, para aislar qué pinta cada capa.
            PLAN.add(() -> { com.hmc.zenkai.client.kiweapon.KiWeaponVfx.handHaloEnabled = false; wait = WAIT_SHOT; });
            PLAN.add(() -> capture(mc, v + "_nohalo", -1, -1));
            PLAN.add(() -> com.hmc.zenkai.client.kiweapon.KiWeaponVfx.handHaloEnabled = true);
            // Extra: 3ª persona de frente (noche), para comprobar que lo tocado para la mano no rompe la ruta del mundo.
            PLAN.add(() -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = WAIT_SHOT * 2; });
            PLAN.add(() -> capture(mc, v + "_tp", -1, -1));
            PLAN.add(() -> { mc.options.setCameraType(CameraType.FIRST_PERSON); wait = WAIT_SHOT; });
            PLAN.add(() -> {
                try { sheet.writeToFile(new File(out(mc), v + ".png")); } catch (Exception ignored) {}
                sheet.close();
                sheet = null;
            });
        }
    }

    /** Recorta la zona del arma (mitad derecha, de 0,22 de alto hacia abajo) y la pega en la hoja. */
    private static void capture(Minecraft mc, String variant, int row, int col) {
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = shot.getWidth(), h = shot.getHeight();
            int x0 = (int) (w * (variant.startsWith("infuse_fp") ? 0.50F : 0.36F)), y0 = (int) (h * 0.22F);
            int cw = (int) (w * 0.50F), ch = h - y0;
            try (NativeImage tile = new NativeImage(800, 646, true)) {
                shot.resizeSubRectTo(x0, y0, cw, ch, tile);
                tile.writeToFile(new File(out(mc), "tiles/" + variant + "_" + row + "_" + col + ".png"));
                if (sheet != null && row >= 0) tile.copyRect(sheet, 0, 0, col * 800, row * 646, 800, 646, false, false);
            }
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("FpWeaponShots: no se pudo capturar {}", variant, ex);
        }
    }
}
