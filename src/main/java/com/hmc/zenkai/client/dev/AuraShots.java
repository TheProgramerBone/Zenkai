package com.hmc.zenkai.client.dev;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.AuraClientState;
import com.hmc.zenkai.client.aura.compose.AuraBodyParams;
import com.hmc.zenkai.client.aura.compose.AuraDebug;
import com.hmc.zenkai.client.aura.compose.AuraDynamics;
import com.hmc.zenkai.client.aura.flame.AuraFlameTuning;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HERRAMIENTA DE DESARROLLO: capturas automáticas del aura para la prueba lado a lado de la DIRECCIÓN DE
 * ARTE (Fase 0.3 de .claude/pendiente/aura-direccion-de-arte.md, 2026-10-26). Mismo patrón que
 * FpWeaponShots: solo se activa si existe {@code <run>/zenkai_aurashots.txt}, cuya primera línea es la
 * CARPETA del mundo (usar una COPIA: cambia la hora). Al terminar escribe {@code zenkai_aurashots/done.txt}
 * y cierra el juego.
 *
 * Qué hace: fija la forma, el Kaioken y el turbo SOLO en el cliente (el servidor sigue en Base y no manda
 * sincronización mientras no cambie nada, así que no la revierte) y los reaplica en cada tick. Por escena
 * (Base, SSJ1, SSJ Blue, Kaioken, SSJ1 volando, SSJ1 en 1ª persona) y variante de {@code /zenkaiaura look}
 * guarda {@link #FRAMES} fotogramas SEGUIDOS (uno por tick) en {@code frames/<escena>_<variante>/}: con
 * una imagen fija no se ve si el movimiento va a saltos. Además una foto de noche de la variante actual y
 * de la combinada. El montaje (hojas y GIF) lo hace fuera del juego quien lo lance.
 * <p>
 * Modo {@code tune} (segunda línea del archivo): calibración contra las referencias de
 * .claude/imagenes_videos_referencias/aura vfx/. Cada línea siguiente es una variante
 * {@code id | fps bandas3(0/1) tope relleno_arriba | param=valor b.clave=valor familia=id...}, con los parámetros de
 * {@code /zenkaiauraflame} ({@link AuraFlameTuning}); se reinician antes de cada variante. Solo
 * escenas SSJ1 y Kaioken de día y una foto de SSJ1 de noche.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraShots {
    private AuraShots() {}

    /** Variante = id + (fps a saltos, tres bandas, tope de blanco). */
    private record Look(String id, float fps, boolean bands3, float whiteCap, float topFill, Map<String, Float> tuning,
                        String family) {
        Look(String id, float fps, boolean bands3, float whiteCap) {
            this(id, fps, bands3, whiteCap, 0f, Map.of(), null);
        }
    }

    private record Scene(String id, ResourceLocation form, KaiokenTier kaioken, boolean flying, boolean firstPerson, boolean bodyOnly) {
        Scene(String id, ResourceLocation form, KaiokenTier kaioken, boolean flying, boolean firstPerson) {
            this(id, form, kaioken, flying, firstPerson, false);
        }
    }

    private static final Look CURRENT = new Look("actual", 0f, false, 0f);
    private static final Look ALL8 = new Look("todo8", 8f, true, 0.10f);
    private static final Look[] LOOKS = {
            CURRENT,
            new Look("saltos8", 8f, false, 0f),
            new Look("bandas3", 0f, true, 0f),
            new Look("tope", 0f, false, 0.10f),
            ALL8,
    };
    /** Solo en SSJ1: el mismo combinado a otros ritmos, para elegir los fps. */
    private static final Look[] FPS_EXTRA = {
            new Look("todo6", 6f, true, 0.10f),
            new Look("todo12", 12f, true, 0.10f),
    };
    private static final ResourceLocation SSJ1 = ResourceLocation.parse("zenkai:ssj1");
    private static final Scene[] SCENES = {
            new Scene("base", FormIds.BASE, KaiokenTier.OFF, false, false),
            new Scene("ssj1", SSJ1, KaiokenTier.OFF, false, false),
            new Scene("blue", ResourceLocation.parse("zenkai:ssj_blue"), KaiokenTier.OFF, false, false),
            new Scene("kaioken", FormIds.BASE, KaiokenTier.X10, false, false),
            new Scene("ssj1_vuelo", SSJ1, KaiokenTier.OFF, true, false),
            new Scene("ssj1_1p", SSJ1, KaiokenTier.OFF, false, true),
            // Solo el cuerpo (sin agujas ni partículas): para MEDIR que el shader salta de verdad.
            new Scene("ssj1_cuerpo", SSJ1, KaiokenTier.OFF, false, false, true),
    };
    private static final int FRAMES = 24;
    private static final long DAY = 6000L, NIGHT = 18000L;
    /** Ticks de espera: carga del mundo, cambio de hora (el cliente la recibe cada segundo), cambio de escena. */
    private static final int WAIT_LOAD = 160, WAIT_TIME = 40, WAIT_SCENE = 30;

    private static Boolean enabled;
    private static String world;
    private static boolean opened;
    private static int ticks, step, wait;
    private static final List<Runnable> PLAN = new ArrayList<>();
    private static Scene active;
    private static int savedFov = 70;
    private static final List<Look> TUNE = new ArrayList<>();
    /** Modo {@code camara}: barrido de cabeceo (null = el de la escena) y aura apagada para la foto base. */
    private static boolean cameraMode;
    /** Modo {@code humo}: contorno de llama de la silueta (SilhouetteFlameRenderer) en Ikari/Legendario. */
    private static boolean smokeMode;
    private static Float pitchOverride;
    private static boolean auraOff;

    /** {@code id | fps bandas3 tope | param=valor ...} */
    private static Look parseLook(String line) {
        String[] parts = line.split("\\|");
        String[] look = parts.length > 1 ? parts[1].trim().split("\\s+") : new String[0];
        Map<String, Float> tuning = new LinkedHashMap<>();
        String family = null;
        if (parts.length > 2) {
            for (String kv : parts[2].trim().split("\\s+")) {
                String[] t = kv.split("=");
                if (t.length == 2 && t[0].equals("familia")) family = t[1];
                else if (t.length == 2) tuning.put(t[0], Float.parseFloat(t[1]));
            }
        }
        return new Look(parts[0].trim(),
                look.length > 0 ? Float.parseFloat(look[0]) : 0f,
                look.length > 1 && look[1].equals("1"),
                look.length > 2 ? Float.parseFloat(look[2]) : 0f,
                look.length > 3 ? Float.parseFloat(look[3]) : 0f, tuning, family);
    }

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) {
            enabled = false;
            if (FMLEnvironment.production) return false;
            File trigger = new File(mc.gameDirectory, "zenkai_aurashots.txt");
            if (trigger.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(trigger.toPath());
                    world = lines.get(0).trim();
                    cameraMode = lines.size() > 1 && lines.get(1).trim().equals("camara");
                    smokeMode = lines.size() > 1 && lines.get(1).trim().equals("humo");
                    if (lines.size() > 1 && lines.get(1).trim().equals("tune")) {
                        for (String l : lines.subList(2, lines.size())) if (!l.isBlank()) TUNE.add(parseLook(l));
                    }
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
        if (active != null) hold(mc.player, active);
        if (++ticks < WAIT_LOAD) return;
        if (PLAN.isEmpty() && step == 0) buildPlan(mc);
        if (wait > 0) { wait--; return; }
        if (step < PLAN.size()) {
            try { PLAN.get(step++).run(); }
            catch (Exception ex) { Zenkai.LOGGER.warn("AuraShots: paso {} falló", step - 1, ex); }
            return;
        }
        setLook(CURRENT);
        try { Files.writeString(out(mc).toPath().resolve("done.txt"), "ok"); } catch (Exception ignored) {}
        enabled = false;
        mc.stop();
    }

    /** Reaplica la escena en el cliente: forma, Kaioken, aura encendida, vuelo y pose fija. */
    private static void hold(Player p, Scene s) {
        var form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        form.setFormId(s.form());
        form.setKaioken(s.kaioken());
        AuraClientState.localTurbo = !auraOff;
        if (s.flying()) AuraDebug.FORCED.put(AuraDynamics.State.FLYING, true);
        else AuraDebug.FORCED.remove(AuraDynamics.State.FLYING);
        AuraDebug.solo = s.bodyOnly() ? "body" : null;
        float pitch = pitchOverride != null ? pitchOverride : s.firstPerson() ? -10F : 0F;
        p.setYRot(0F); p.yRotO = 0F; p.setYHeadRot(0F); p.yHeadRotO = 0F; p.yBodyRot = 0F; p.yBodyRotO = 0F;
        p.setXRot(pitch); p.xRotO = pitch;
    }

    private static void setLook(Look l) {
        AuraFlameTuning.reset();
        AuraDebug.BODY_OVERRIDES.clear();
        l.tuning().forEach((k, v) -> {
            // "b.<clave>" = valor del cuerpo tras el estilo (AuraBodyParams.Key), p. ej. b.crown_soft=1
            if (k.startsWith("b.")) {
                for (AuraBodyParams.Key bk : AuraBodyParams.Key.values()) {
                    if (bk.key.equals(k.substring(2))) { AuraDebug.BODY_OVERRIDES.put(bk, v); return; }
                }
                Zenkai.LOGGER.warn("AuraShots: clave de cuerpo desconocida {}", k);
                return;
            }
            AuraFlameTuning.Param p = AuraFlameTuning.Param.byKey(k);
            if (p != null) AuraFlameTuning.set(p, v);
            else Zenkai.LOGGER.warn("AuraShots: parámetro de llama desconocido {}", k);
        });
        AuraDebug.lookFps = l.fps();
        AuraDebug.lookBands3 = l.bands3();
        AuraDebug.lookWhiteCap = l.whiteCap();
        AuraDebug.lookTopFill = l.topFill();
        AuraDebug.familyOverride = l.family();
    }

    private static File out(Minecraft mc) {
        File d = new File(mc.gameDirectory, "zenkai_aurashots");
        d.mkdirs();
        return d;
    }

    private static void setTime(Minecraft mc, long time) {
        PLAN.add(() -> {
            var server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> server.overworld().setDayTime(time));
            wait = WAIT_TIME;
        });
    }

    private static void buildPlan(Minecraft mc) {
        mc.options.hideGui = true;
        // FOV amplio: las variantes altas (2-3 veces el jugador, como las referencias) no caben con 70.
        savedFov = mc.options.fov().get();
        mc.options.fov().set(100);
        if (!TUNE.isEmpty()) { tunePlan(mc); return; }
        if (cameraMode) { cameraPlan(mc); return; }
        if (smokeMode) { smokePlan(mc); return; }
        setTime(mc, DAY);
        for (Scene s : SCENES) {
            PLAN.add(() -> {
                active = s;
                mc.options.setCameraType(s.firstPerson() ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
                wait = WAIT_SCENE;
            });
            List<Look> looks = new ArrayList<>(List.of(LOOKS));
            if (s.id().equals("ssj1")) looks.addAll(List.of(FPS_EXTRA));
            for (Look l : looks) {
                PLAN.add(() -> { setLook(l); wait = 10; });
                for (int f = 0; f < FRAMES; f++) {
                    final int frame = f;
                    PLAN.add(() -> capture(mc, s, "frames/" + s.id() + "_" + l.id(), String.format("f%03d", frame)));
                }
            }
        }
        setTime(mc, NIGHT);
        for (Scene s : SCENES) {
            PLAN.add(() -> {
                active = s;
                mc.options.setCameraType(s.firstPerson() ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
                wait = WAIT_SCENE;
            });
            for (Look l : new Look[]{CURRENT, ALL8}) {
                PLAN.add(() -> { setLook(l); wait = 10; });
                PLAN.add(() -> capture(mc, s, "noche", s.id() + "_" + l.id()));
            }
        }
        PLAN.add(() -> {
            active = null;
            AuraClientState.localTurbo = false;
            AuraDebug.FORCED.remove(AuraDynamics.State.FLYING);
            AuraDebug.solo = null;
            mc.options.fov().set(savedFov);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }

    /**
     * Sensibilidad a la CÁMARA en 3ª persona (2026-10-26, "el aura se hace más pequeña, se malforma
     * según la cámara"): por escena, cámara de espaldas y de frente, cabeceo de -50 a 50; en cada
     * ángulo una foto CON aura y otra SIN ella ({@code base_}), para medir fuera del juego cuántos
     * píxeles ocupa el aura en cada ángulo. Variantes de familia por escena: la de la forma y la vela
     * antigua ({@code candle}) para comparar.
     */
    private static void cameraPlan(Minecraft mc) {
        // Familias de cuerpo SOFT (Blue, God, Majin) y DENSE (Majin malvado): normal corregida 2026-10-26.
        Scene[] scenes = {SCENES[2],
                new Scene("god", ResourceLocation.parse("zenkai:ssj_god"), KaiokenTier.OFF, false, false),
                new Scene("majin", ResourceLocation.parse("zenkai:majin_super"), KaiokenTier.OFF, false, false),
                new Scene("majin_malvado", ResourceLocation.parse("zenkai:majin_pure_evil"), KaiokenTier.OFF, false, false)};
        String[] families = {null};
        float[] pitches = {-50F, -25F, 0F, 25F, 50F};
        setTime(mc, DAY);
        for (Scene s : scenes) {
            for (String fam : families) {
                String tag = s.id() + (fam == null ? "" : "_" + fam);
                for (CameraType cam : new CameraType[]{CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT}) {
                    String camTag = cam == CameraType.THIRD_PERSON_BACK ? "atras" : "frente";
                    for (float pitch : pitches) {
                        String name = camTag + "_" + (int) pitch;
                        PLAN.add(() -> {
                            active = s; pitchOverride = pitch; auraOff = true;
                            AuraDebug.familyOverride = fam;
                            mc.options.setCameraType(cam);
                            wait = 12;
                        });
                        PLAN.add(() -> capture(mc, s, "camara/" + tag, "base_" + name));
                        PLAN.add(() -> { auraOff = false; wait = 30; });
                        PLAN.add(() -> capture(mc, s, "camara/" + tag, name));
                    }
                }
            }
        }
        PLAN.add(() -> {
            active = null; pitchOverride = null; auraOff = false;
            AuraClientState.localTurbo = false;
            AuraDebug.familyOverride = null;
            mc.options.fov().set(savedFov);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }

    /**
     * Contorno de llama de la silueta (2026-10-26): Ikari y Legendario SIN aura (sale siempre en la
     * forma) y Legendario CON aura, de frente y de espaldas, 16 fotogramas seguidos cada uno; de día
     * y una foto de noche.
     */
    private static void smokePlan(Minecraft mc) {
        Scene ikari = new Scene("ikari", ResourceLocation.parse("zenkai:ikari"), KaiokenTier.OFF, false, false);
        Scene legend = new Scene("legendario", ResourceLocation.parse("zenkai:ssj_green"), KaiokenTier.OFF, false, false);
        Scene control = new Scene("control_ssj1", SSJ1, KaiokenTier.OFF, false, false);
        Object[][] cases = {{ikari, true}, {legend, true}, {legend, false}, {control, true}};
        // Diagnóstico: la misma forma con el contorno APAGADO, para separar fallos del contorno de
        // fallos de la forma.
        PLAN.add(() -> { com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugDisabled = true; active = legend; auraOff = true; pitchOverride = 10F; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = 40; });
        PLAN.add(() -> capture(mc, legend, "humo/diag_contorno_apagado", "f000"));
        PLAN.add(() -> { com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugDisabled = false; wait = 20; });
        PLAN.add(() -> capture(mc, legend, "humo/diag_contorno_encendido", "f000"));
        PLAN.add(() -> { com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugDumpDir = new File(out(mc), "humo/diag_texturas"); wait = 5; });
        PLAN.add(() -> { com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugNoCompose = true; wait = 20; });
        PLAN.add(() -> capture(mc, legend, "humo/diag_solo_mascara", "f000"));
        PLAN.add(() -> { com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugNoCompose = false;
                com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugNoCapture = true; wait = 20; });
        PLAN.add(() -> capture(mc, legend, "humo/diag_solo_composicion", "f000"));
        PLAN.add(() -> { com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugNoCapture = false; wait = 5; });
        for (long time : new long[]{DAY, NIGHT}) {
            setTime(mc, time);
            for (Object[] c : cases) {
                Scene s = (Scene) c[0];
                boolean off = (Boolean) c[1];
                for (CameraType cam : new CameraType[]{CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK}) {
                    String tag = s.id() + (off ? "_sin_aura" : "_con_aura") + (cam == CameraType.THIRD_PERSON_FRONT ? "_frente" : "_atras")
                            + (time == DAY ? "" : "_noche");
                    PLAN.add(() -> {
                        active = s; auraOff = off; pitchOverride = 10F;
                        mc.options.setCameraType(cam);
                        wait = 30;
                    });
                    int n = time == DAY ? 16 : 1;
                    for (int f = 0; f < n; f++) {
                        final int frame = f;
                        PLAN.add(() -> capture(mc, s, "humo/" + tag, String.format("f%03d", frame)));
                    }
                }
            }
        }
        PLAN.add(() -> {
            active = null; pitchOverride = null; auraOff = false;
            AuraClientState.localTurbo = false;
            mc.options.fov().set(savedFov);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }

    private static void tunePlan(Minecraft mc) {
        // El tamaño del aura sale SOLO del poder: con el personaje del mundo de prueba (débil) no se puede
        // comparar contra referencias de poder máximo. Primero el poder del mundo; luego atributos al máximo.
        Scene[] scenes = {SCENES[1], SCENES[3],
                new Scene("max_ssj1", SSJ1, KaiokenTier.OFF, false, false),
                new Scene("max_kaioken", FormIds.BASE, KaiokenTier.X10, false, false),
                new Scene("max_ssj1_cuerpo", SSJ1, KaiokenTier.OFF, false, false, true)};
        setTime(mc, DAY);
        for (Scene s : scenes) {
            if (s.id().equals("max_ssj1")) {
                PLAN.add(() -> {
                    var server = mc.getSingleplayerServer();
                    if (server != null) server.execute(() -> {
                        var sp = server.getPlayerList().getPlayers().get(0);
                        server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), "zenkai attr maxall");
                    });
                    wait = WAIT_TIME;
                });
            }
            PLAN.add(() -> { active = s; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = WAIT_SCENE; });
            for (Look l : TUNE) {
                PLAN.add(() -> { setLook(l); wait = 10; });
                for (int f = 0; f < FRAMES; f++) {
                    final int frame = f;
                    PLAN.add(() -> capture(mc, s, "frames/" + s.id() + "_" + l.id(), String.format("f%03d", frame)));
                }
            }
        }
        setTime(mc, NIGHT);
        PLAN.add(() -> { active = scenes[2]; wait = WAIT_SCENE; });
        for (Look l : TUNE) {
            PLAN.add(() -> { setLook(l); wait = 10; });
            PLAN.add(() -> capture(mc, scenes[2], "noche", "max_ssj1_" + l.id()));
        }
        PLAN.add(() -> {
            active = null;
            AuraClientState.localTurbo = false;
            AuraFlameTuning.reset();
            AuraDebug.BODY_OVERRIDES.clear();
            mc.options.fov().set(savedFov);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }

    /** En 3ª persona recorta la franja central (el jugador y su aura); en 1ª persona, la pantalla entera. */
    private static void capture(Minecraft mc, Scene s, String dir, String name) {
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int w = shot.getWidth(), h = shot.getHeight();
            boolean full = s.firstPerson() || cameraMode || smokeMode;
            int x0 = full ? (cameraMode || smokeMode ? (int) (w * 0.15F) : 0) : (int) (w * 0.30F);
            int y0 = full ? 0 : (int) (h * 0.04F);
            int cw = full ? (cameraMode || smokeMode ? (int) (w * 0.70F) : w) : (int) (w * 0.40F);
            int ch = full ? h : (int) (h * 0.92F);
            int tw = s.firstPerson() ? 640 : cameraMode || smokeMode ? 560 : 400;
            int th = Math.round(tw * (float) ch / cw);
            File d = new File(out(mc), dir);
            d.mkdirs();
            try (NativeImage tile = new NativeImage(tw, th, true)) {
                shot.resizeSubRectTo(x0, y0, cw, ch, tile);
                tile.writeToFile(new File(d, name + ".png"));
            }
        } catch (Exception ex) {
            Zenkai.LOGGER.warn("AuraShots: no se pudo capturar {}/{}", dir, name, ex);
        }
    }
}
