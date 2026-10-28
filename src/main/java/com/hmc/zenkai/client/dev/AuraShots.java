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
    /** Modo {@code humo_shaders}: lo mismo con el shaderpack de {@code run/shaderpacks} en uso
     *  ({@code enableShaders=true} en {@code run/config/iris.properties}). */
    private static boolean smokeShadersMode;
    private static Float pitchOverride;
    /** Giro del jugador (null = 0); el modo {@code humo_prueba} lo cambia fotograma a fotograma. */
    private static Float yawOverride;
    /** Modo {@code humo_prueba}: prueba completa del contorno (giro, cabeceo, aura, noche, 1ª persona). */
    private static boolean smokeTestMode;
    /** Modo {@code revision}: repaso de formas de la dirección de arte (contornos, lágrima, estela...). */
    private static boolean reviewMode;
    /** Modo {@code humo_momentaneo}: el humo de las formas divinas aparece al cargar y se va al soltar. */
    private static boolean momentaryMode;
    /** Modo {@code divinas}: hojas "mod vs referencia" del rework de las auras divinas
     *  (.claude/pendiente/aura-divinas-plan.md). Tercera línea opcional: ids de forma separados por espacios. */
    private static boolean divineMode;
    private static final List<String> DIVINE_FORMS = new ArrayList<>();
    /** Beast forzando: % de poder que se impone cada tick en el cliente (0 = no se toca). */
    private static int forcedPowerPercent;
    /** Vuelo lateral para la estela: Y fija en el aire y avance por tick en X (0 = quieto). */
    private static double moveStep, moveY;
    /** Avance por tick en Z (vuelo hacia la cámara o alejándose) y "en el aire" aunque no avance
     *  (vuelo quieto: la Y se fija cada tick para que no caiga). Modo divinas, 2026-10-26. */
    private static double moveStepZ;
    /** Subida por tick (vuelo vertical: el aura se aplastaba al subir, 2026-10-27). */
    private static double moveStepY, moveYStart;
    private static boolean airborne;
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
                    smokeShadersMode = lines.size() > 1 && lines.get(1).trim().equals("humo_shaders");
                    smokeTestMode = lines.size() > 1 && lines.get(1).trim().equals("humo_prueba");
                    reviewMode = lines.size() > 1 && lines.get(1).trim().equals("revision");
                    momentaryMode = lines.size() > 1 && lines.get(1).trim().equals("humo_momentaneo");
                    divineMode = lines.size() > 1 && lines.get(1).trim().equals("divinas");
                    if (divineMode && lines.size() > 2) {
                        for (String id : lines.get(2).trim().split("\\s+")) if (!id.isEmpty()) DIVINE_FORMS.add(id);
                    }
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

    /** Beast forzando: el % de poder también al empezar CADA fotograma. La sincronización de stats
     *  reescribe el attachment entero desde el NBT del servidor (cada vez que cambia el ki), así que
     *  impuesto solo en el tick se perdía antes de pintar y el humo rojo no salía en las fotos. */
    @SubscribeEvent
    public static void onFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (forcedPowerPercent <= 0 || mc.player == null) return;
        com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(mc.player).setPowerPercent(forcedPowerPercent, forcedPowerPercent);
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
        if (forcedPowerPercent > 0) {
            var stats = com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(p);
            if (stats.getPowerPercent() != forcedPowerPercent) stats.setPowerPercent(forcedPowerPercent, forcedPowerPercent);
        }
        if (moveStep != 0 || moveStepZ != 0 || airborne) {
            // Vuelo (estela, divinas): solo en el cliente; el servidor integrado acepta el avance.
            moveY += moveStepY;
            p.setPos(p.getX() + moveStep, moveY, p.getZ() + moveStepZ);
            p.setDeltaMovement(moveStep, moveStepY, moveStepZ);
        }
        float yaw = yawOverride != null ? yawOverride : 0F;
        p.setYRot(yaw); p.yRotO = yaw; p.setYHeadRot(yaw); p.yHeadRotO = yaw; p.yBodyRot = yaw; p.yBodyRotO = yaw;
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
        if (smokeShadersMode) { smokeShadersPlan(mc); return; }
        if (smokeTestMode) { smokeTestPlan(mc); return; }
        if (reviewMode) { reviewPlan(mc); return; }
        if (momentaryMode) { momentaryPlan(mc); return; }
        if (divineMode) { divinePlan(mc); return; }
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
        renderCases(mc, legend, "humo/render");
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

    /**
     * Fallos de RENDER del contorno (2026-10-26), cada caso con la foto "antes" (oclusión y escritura de
     * depth apagadas, como estaba) y "arreglado", en {@code dir}. Cámara de frente: el +Z del
     * jugador queda entre él y la cámara, el -Z detrás de él.
     * <ul>
     *   <li>{@code oclusion}: un bloque delante de las piernas y columnas a los lados (lenguas y línea
     *       interior encima del bloque).</li>
     *   <li>{@code cristal_detras} / {@code agua_detras} / {@code particulas_detras}: lo translúcido y las
     *       partículas se dibujan DESPUÉS de la llama; sin depth se pintaban encima de las lenguas.</li>
     *   <li>{@code cristal_delante}: lo de delante debe seguir tiñendo la llama, no taparla.</li>
     *   <li>{@code con_aura}: el aura compuesta (AFTER_PARTICLES) con la llama escribiendo depth.</li>
     *   <li>{@code fov_*}: el jugador grande y pequeño en pantalla (grosor por alto, 2-36 px).</li>
     * </ul>
     */
    private static void renderCases(Minecraft mc, Scene legend, String dir) {
        setTime(mc, DAY);
        PLAN.add(() -> {
            command(mc, "fill ~-6 ~ ~-6 ~6 ~3 ~6 minecraft:air"); // restos de una ejecución cortada
            active = legend; auraOff = true; pitchOverride = 10F;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            wait = 30;
        });
        String[][] cases = {
                {"oclusion", "fill ~1 ~ ~2 ~1 ~1 ~2 minecraft:stone", "setblock ~-1 ~ ~2 minecraft:stone", "setblock ~ ~ ~2 minecraft:stone"},
                {"cristal_detras", "fill ~-2 ~ ~-2 ~2 ~3 ~-2 minecraft:light_blue_stained_glass"},
                {"cristal_delante", "fill ~-2 ~ ~2 ~2 ~3 ~2 minecraft:light_blue_stained_glass"},
                {"agua_detras", "fill ~-2 ~ ~-3 ~2 ~3 ~-3 minecraft:glass", "fill ~-2 ~ ~-2 ~2 ~3 ~-2 minecraft:water"},
                {"particulas_detras"},
                {"con_aura"},
        };
        for (String[] c : cases) {
            String id = c[0];
            PLAN.add(() -> {
                for (int i = 1; i < c.length; i++) command(mc, c[i]);
                auraOff = !id.equals("con_aura");
                wait = id.equals("con_aura") ? 30 : 6;
            });
            for (boolean fixed : new boolean[]{false, true}) {
                PLAN.add(() -> {
                    com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugNoOcclusion = !fixed;
                    com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugNoDepthWrite = !fixed;
                    if (id.equals("particulas_detras")) command(mc, "particle minecraft:end_rod ~ ~1 ~-1.2 0.5 0.7 0.05 0 200 force");
                    wait = id.equals("particulas_detras") ? 2 : 4;
                });
                PLAN.add(() -> capture(mc, legend, dir, id + (fixed ? "_arreglado" : "_antes")));
            }
            // Limpieza ancha: el agua se extiende unos bloques mientras se hacen las fotos.
            PLAN.add(() -> {
                command(mc, "fill ~-6 ~ ~-6 ~6 ~3 ~6 minecraft:air");
                auraOff = true;
                wait = 20;
            });
        }
        for (int fov : new int[]{30, 110}) {
            PLAN.add(() -> { mc.options.fov().set(fov); wait = 10; });
            PLAN.add(() -> capture(mc, legend, dir, "fov_" + fov));
        }
        PLAN.add(() -> {
            mc.options.fov().set(100);
            com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugNoOcclusion = false;
            com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugNoDepthWrite = false;
            wait = 10;
        });
    }

    /**
     * Contorno con SHADERPACK (2026-10-26; {@code enableShaders=true} en {@code run/config/iris.properties}).
     * Ikari/Legendario sin aura y Legendario con aura, de frente y de espaldas, con el contorno
     * ({@code _contorno}) y sin él ({@code _apagado}, {@code debugDisabled}); una de control en Base. Luego
     * los casos de render ({@link #renderCases}) con el pack. Primero se descubrió así que con el pack la
     * máscara SÍ se capturaba y lo que se perdía era la composición (iba antes del compuesto del pack).
     */
    private static void smokeShadersPlan(Minecraft mc) {
        Scene ikari = new Scene("ikari", ResourceLocation.parse("zenkai:ikari"), KaiokenTier.OFF, false, false);
        Scene legend = new Scene("legendario", ResourceLocation.parse("zenkai:ssj_green"), KaiokenTier.OFF, false, false);
        Scene base = new Scene("base", FormIds.BASE, KaiokenTier.OFF, false, false);
        PLAN.add(() -> Zenkai.LOGGER.info("AuraShots humo_shaders: shaderpack en uso = {}",
                com.hmc.zenkai.compat.IrisCompat.shaderPackActive()));
        setTime(mc, DAY);
        Object[][] cases = {{base, true}, {ikari, true}, {legend, true}, {legend, false}};
        for (Object[] c : cases) {
            Scene s = (Scene) c[0];
            boolean off = (Boolean) c[1];
            for (CameraType cam : new CameraType[]{CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK}) {
                String tag = s.id() + (off ? "_sin_aura" : "_con_aura") + (cam == CameraType.THIRD_PERSON_FRONT ? "_frente" : "_atras");
                PLAN.add(() -> {
                    active = s; auraOff = off; pitchOverride = 10F;
                    mc.options.setCameraType(cam);
                    wait = 40;
                });
                for (boolean disabled : new boolean[]{true, false}) {
                    PLAN.add(() -> { com.hmc.zenkai.client.aura.silhouette.SilhouetteFlameRenderer.debugDisabled = disabled; wait = 6; });
                    PLAN.add(() -> capture(mc, s, "humo_shaders", tag + (disabled ? "_apagado" : "_contorno")));
                }
            }
        }
        renderCases(mc, legend, "humo_shaders/render");
        setTime(mc, NIGHT);
        PLAN.add(() -> { active = legend; auraOff = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = 30; });
        PLAN.add(() -> capture(mc, legend, "humo_shaders", "legendario_sin_aura_frente_noche"));
        PLAN.add(() -> {
            active = null; pitchOverride = null; auraOff = false;
            AuraClientState.localTurbo = false;
            mc.options.fov().set(savedFov);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }

    /**
     * PRUEBA COMPLETA del contorno (2026-10-26, "pruébalo tú mismo"): con o sin shaderpack según
     * {@code run/config/iris.properties}. 24 fotogramas seguidos por caso en {@code humo_prueba/<caso>/}: el
     * jugador girando 15 grados por fotograma (frente y espaldas), barrido de cabeceo de -45 a 45, Ikari, el
     * Legendario con aura girando, de noche, y una foto en 1ª persona (no debe verse contorno propio). Fuera del
     * juego se cuentan los píxeles del contorno por fotograma: un fotograma sin contorno = parpadeo.
     */
    private static void smokeTestPlan(Minecraft mc) {
        Scene ikari = new Scene("ikari", ResourceLocation.parse("zenkai:ikari"), KaiokenTier.OFF, false, false);
        Scene legend = new Scene("legendario", ResourceLocation.parse("zenkai:ssj_green"), KaiokenTier.OFF, false, false);
        Scene legend1p = new Scene("legendario_1p", ResourceLocation.parse("zenkai:ssj_green"), KaiokenTier.OFF, false, true);
        PLAN.add(() -> Zenkai.LOGGER.info("AuraShots humo_prueba: shaderpack en uso = {}",
                com.hmc.zenkai.compat.IrisCompat.shaderPackActive()));
        PLAN.add(() -> command(mc, "fill ~-6 ~ ~-6 ~6 ~3 ~6 minecraft:air"));
        setTime(mc, DAY);
        // {escena, sin aura, cámara, giro?, cabeceo?, carpeta}
        Object[][] cases = {
                {legend, true, CameraType.THIRD_PERSON_FRONT, true, false, "giro_frente"},
                {legend, true, CameraType.THIRD_PERSON_BACK, true, false, "giro_atras"},
                {legend, true, CameraType.THIRD_PERSON_FRONT, false, true, "cabeceo"},
                {ikari, true, CameraType.THIRD_PERSON_FRONT, true, false, "ikari_giro"},
                {legend, false, CameraType.THIRD_PERSON_FRONT, true, false, "con_aura_giro"},
        };
        for (Object[] c : cases) addSpin(mc, (Scene) c[0], (Boolean) c[1], (CameraType) c[2], (Boolean) c[3], (Boolean) c[4], (String) c[5]);
        addSteady(mc, legend, "estable_dia");
        setTime(mc, NIGHT);
        addSpin(mc, legend, true, CameraType.THIRD_PERSON_FRONT, true, false, "noche_giro");
        addSteady(mc, legend, "estable_noche");
        PLAN.add(() -> { active = legend1p; auraOff = true; yawOverride = 0F; pitchOverride = 30F; mc.options.setCameraType(CameraType.FIRST_PERSON); wait = 30; });
        PLAN.add(() -> capture(mc, legend1p, "humo_prueba/primera_persona", "f000"));
        PLAN.add(() -> {
            active = null; pitchOverride = null; yawOverride = null; auraOff = false;
            AuraClientState.localTurbo = false;
            mc.options.fov().set(savedFov);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }

    private static Scene form(String id, boolean flying) {
        return new Scene(id, ResourceLocation.parse("zenkai:" + id), KaiokenTier.OFF, flying, false);
    }

    /**
     * REPASO de la dirección de arte (2026-10-26, puntos 3 y 4 de los pendientes). Con o sin
     * shaderpack según {@code run/config/iris.properties}. Carpeta {@code revision/}:
     * <ul>
     *   <li>{@code contornos/}: las formas que llevaban el contorno de CAJAS y ahora el humo, sin y con
     *       aura; Beast normal y forzando (poder 150 %).</li>
     *   <li>{@code lagrima/}: SSJ2, SSJ3, Rosé y Rosé Evolución con la llama en lágrima, 12 fotogramas.</li>
     *   <li>{@code legendarios/}: el aura verde de cada raza, sin y con aura.</li>
     *   <li>{@code ultra_ego/}: 8 fotogramas.</li>
     *   <li>{@code majin_malvado/}: cámara de espaldas libre y con una pared detrás que la empuja DENTRO
     *       del volumen del aura.</li>
     *   <li>{@code estela/}: vuelo lateral a 8 bloques/s (SSJ1, Blue, Ultra Ego), 16 fotogramas.</li>
     * </ul>
     */
    private static void reviewPlan(Minecraft mc) {
        PLAN.add(() -> Zenkai.LOGGER.info("AuraShots revision: shaderpack en uso = {}",
                com.hmc.zenkai.compat.IrisCompat.shaderPackActive()));
        PLAN.add(() -> command(mc, "fill ~-6 ~ ~-6 ~6 ~3 ~6 minecraft:air"));
        setTime(mc, DAY);
        String[] outlined = {"ssj2", "ssj3", "ssj_god", "ssj_blue", "ssj_blue_evolution", "human_god", "namek_god",
                "arcosian_god", "majin_god", "majin_super", "majin_harmony", "beast"};
        for (String id : outlined) reviewPair(mc, form(id, false), "revision/contornos", id, 0);
        reviewPair(mc, form("beast", false), "revision/contornos", "beast_forzando", 150);
        for (String id : new String[]{"ssj2", "ssj3", "ssj_rose", "ssj_rose_evolution"}) {
            Scene s = form(id, false);
            PLAN.add(() -> { active = s; auraOff = false; pitchOverride = 10F; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = 40; });
            for (int f = 0; f < 12; f++) {
                final int frame = f;
                PLAN.add(() -> capture(mc, s, "revision/lagrima/" + id, String.format("f%03d", frame)));
            }
        }
        for (String id : new String[]{"ssj_green", "ssj_green_max", "human_legendary", "namek_legendary",
                "arcosian_legendary", "majin_legendary"}) reviewPair(mc, form(id, false), "revision/legendarios", id, 0);
        Scene ego = form("ultra_ego", false);
        PLAN.add(() -> { active = ego; auraOff = false; pitchOverride = 10F; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = 40; });
        for (int f = 0; f < 8; f++) {
            final int frame = f;
            PLAN.add(() -> capture(mc, ego, "revision/ultra_ego", String.format("f%03d", frame)));
        }
        // Majin malvado: cámara de espaldas (detrás del jugador = -Z) libre y empujada por una pared.
        Scene evil = form("majin_pure_evil", false);
        for (boolean wall : new boolean[]{false, true}) {
            PLAN.add(() -> {
                if (wall) command(mc, "fill ~-3 ~ ~-2 ~3 ~4 ~-2 minecraft:stone");
                active = evil; auraOff = false; pitchOverride = 10F;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                wait = 40;
            });
            for (int f = 0; f < 6; f++) {
                final int frame = f;
                PLAN.add(() -> capture(mc, evil, "revision/majin_malvado", (wall ? "pared_" : "libre_") + String.format("f%03d", frame)));
            }
        }
        PLAN.add(() -> { command(mc, "fill ~-6 ~ ~-6 ~6 ~4 ~6 minecraft:air"); wait = 10; });
        // Estela: en el aire y avanzando de lado; la cámara de frente sigue al jugador.
        for (String id : new String[]{"ssj1", "ssj_blue", "ultra_ego"}) {
            Scene s = form(id, true);
            PLAN.add(() -> {
                active = s; auraOff = false; pitchOverride = 10F;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                moveY = mc.player.getY() + 3.0;
                moveStep = 0.4;
                wait = 30;
            });
            for (int f = 0; f < 16; f++) {
                final int frame = f;
                PLAN.add(() -> capture(mc, s, "revision/estela/" + id, String.format("f%03d", frame)));
            }
            PLAN.add(() -> { moveStep = 0; wait = 40; });
        }
        PLAN.add(() -> {
            forcedPowerPercent = 0; moveStep = 0;
            active = null; pitchOverride = null; yawOverride = null; auraOff = false;
            AuraClientState.localTurbo = false;
            mc.options.fov().set(savedFov);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }

    /**
     * Humo MOMENTÁNEO de las formas divinas (2026-10-26): SSJ God y Blue sin cargar (no debe haber humo),
     * 24 fotogramas al encender el turbo (aparece poco a poco) y 24 al apagarlo (se va poco a poco); el
     * Legendario de control lo lleva siempre. Carpeta {@code humo_momentaneo/<forma>/}.
     */
    private static void momentaryPlan(Minecraft mc) {
        setTime(mc, DAY);
        // Pelo por forma (2026-10-26: Legendario y Pleno con el peinado de SSJ; Ikari con la base).
        for (String id : new String[]{"base", "ssj1", "ikari", "ssj_green", "ssj_green_max"}) {
            Scene s = form(id, false);
            PLAN.add(() -> { active = s; auraOff = true; pitchOverride = 10F; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = 40; });
            PLAN.add(() -> capture(mc, s, "humo_momentaneo/pelo", id));
        }
        for (String id : new String[]{"ssj_rose", "ssj_rose_evolution", "ssj2", "ssj3", "ssj_green"}) {
            Scene s = form(id, false);
            PLAN.add(() -> { active = s; auraOff = true; pitchOverride = 10F; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = 60; });
            PLAN.add(() -> capture(mc, s, "humo_momentaneo/" + id, "a_quieto"));
            PLAN.add(() -> auraOff = false);
            for (int f = 0; f < 24; f++) {
                final int frame = f;
                PLAN.add(() -> capture(mc, s, "humo_momentaneo/" + id, String.format("b_carga_%02d", frame)));
            }
            PLAN.add(() -> auraOff = true);
            for (int f = 0; f < 24; f++) {
                final int frame = f;
                PLAN.add(() -> capture(mc, s, "humo_momentaneo/" + id, String.format("c_suelta_%02d", frame)));
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

    /**
     * Hojas "mod vs referencia" del rework de las auras divinas (2026-10-26,
     * .claude/pendiente/aura-divinas-plan.md, fase F0 y verificación de cada fase). SIEMPRE EN VUELO
     * (pedido del usuario, 2026-10-26: "que no se vean planas o cuadradas"), a 4 bloques del suelo, con el
     * poder del mundo y después con {@code /zenkai attr maxall}:
     * <ul>
     *   <li>quieto en el aire: 8 fotogramas de frente y una foto de 3/4, espaldas, desde arriba (+50),
     *       desde abajo (-25) y con la cámara pegada (pared detrás);</li>
     *   <li>{@code lateral}: avanzando de lado a 8 bloques/s, 8 fotogramas (la inclinación de vuelo de
     *       perfil);</li>
     *   <li>{@code hacia_camara} / {@code alejandose}: avanzando a lo largo de la visual, 6 fotogramas cada
     *       uno (el caso en que una lámina se ve de punta).</li>
     * </ul>
     * Una foto de noche quieto en el aire. Carpeta {@code divinas/<poder>/<forma>/}; el montaje con las
     * referencias se hace fuera del juego.
     */
    private static void divinePlan(Minecraft mc) {
        List<String> ids = DIVINE_FORMS.isEmpty()
                ? List.of("ssj_god", "ssj_blue", "ultra_instinct", "golden_form")
                : DIVINE_FORMS;
        PLAN.add(() -> Zenkai.LOGGER.info("AuraShots divinas: shaderpack en uso = {}",
                com.hmc.zenkai.compat.IrisCompat.shaderPackActive()));
        // Al spawn y al suelo antes de empezar: los vuelos de las tomas se guardan en el mundo y cada tanda
        // empezaba más lejos, hasta acabar delante de una aldea y contra una pared (v15, 2026-10-27).
        PLAN.add(() -> { var sp = mc.level.getSharedSpawnPos();
                command(mc, "spreadplayers " + sp.getX() + " " + sp.getZ() + " 0 1 false @s"); wait = 40; });
        PLAN.add(() -> { command(mc, "fill ~-6 ~ ~-6 ~6 ~8 ~6 minecraft:air"); moveY = mc.player.getY() + 4.0; airborne = true; });
        for (String power : new String[]{"base", "max"}) {
            if (power.equals("max")) PLAN.add(() -> { command(mc, "zenkai attr maxall"); wait = WAIT_TIME; });
            setTime(mc, DAY);
            for (String id : ids) {
                Scene s = form(id, true);
                String dir = "divinas/" + power + "/" + id;
                PLAN.add(() -> { active = s; auraOff = false; yawOverride = 0F; pitchOverride = 10F; moveStep = 0; moveStepZ = 0;
                        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = 60; });
                for (int fr = 0; fr < 8; fr++) {
                    final int frame = fr;
                    PLAN.add(() -> { wait = 3; });
                    PLAN.add(() -> capture(mc, s, dir, String.format("frente_f%02d", frame)));
                }
                // {nombre, cámara, giro, cabeceo}
                Object[][] views = {
                        {"tres_cuartos", CameraType.THIRD_PERSON_FRONT, 35F, 10F},
                        {"espaldas", CameraType.THIRD_PERSON_BACK, 0F, 10F},
                        {"arriba", CameraType.THIRD_PERSON_BACK, 0F, 50F},
                        {"abajo", CameraType.THIRD_PERSON_FRONT, 0F, -25F},
                        // Cámara justo encima y justo debajo: ahí el aura desaparecía (2026-10-27).
                        {"cenit", CameraType.THIRD_PERSON_BACK, 0F, 90F},
                        {"nadir", CameraType.THIRD_PERSON_BACK, 0F, -90F},
                };
                for (Object[] v : views) {
                    // Desde debajo, a 4 bloques del suelo la cámara de 3ª persona chocaba con el terreno y
                    // se pegaba a los pies: se sube el jugador 8 bloques solo para esa toma.
                    boolean high = "nadir".equals(v[0]);
                    PLAN.add(() -> { yawOverride = (Float) v[2]; pitchOverride = (Float) v[3]; if (high) moveY += 8;
                            mc.options.setCameraType((CameraType) v[1]); wait = 20; });
                    PLAN.add(() -> capture(mc, s, dir, (String) v[0]));
                    if (high) PLAN.add(() -> { moveY -= 8; wait = 10; });
                }
                // Cámara pegada: pared de espaldas (detrás del jugador = -Z) que acerca la cámara.
                PLAN.add(() -> { command(mc, "fill ~-3 ~-1 ~-2 ~3 ~4 ~-2 minecraft:stone"); yawOverride = 0F; pitchOverride = 10F;
                        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); wait = 25; });
                PLAN.add(() -> capture(mc, s, dir, "pegada"));
                PLAN.add(() -> { command(mc, "fill ~-3 ~-1 ~-2 ~3 ~4 ~-2 minecraft:air"); wait = 10; });
                // {nombre, avance X, avance Z, cámara, fotogramas, eje de inclinación del aura}. El eje se
                // impone (AuraTiltController.debugAxis): el de verdad sale de la entrada de vuelo, que el
                // arnés no da. Unos 60 grados hacia donde avanza, como un vuelo rápido.
                Object[][] moves = {
                        {"lateral", 0.4, 0.0, CameraType.THIRD_PERSON_FRONT, 8, new org.joml.Vector3f(0.87f, 0.5f, 0f)},
                        {"hacia_camara", 0.0, 0.4, CameraType.THIRD_PERSON_FRONT, 6, new org.joml.Vector3f(0f, 0.5f, 0.87f)},
                        {"alejandose", 0.0, 0.4, CameraType.THIRD_PERSON_BACK, 6, new org.joml.Vector3f(0f, 0.5f, 0.87f)},
                        // Vuelo RÁPIDO con la cámara detrás y el cuerpo casi tumbado: el aura desaparecía
                        // (2026-10-27). Y subiendo en vertical: se aplastaba.
                        {"rapido_detras", 0.0, 1.0, CameraType.THIRD_PERSON_BACK, 6, new org.joml.Vector3f(0f, 0.17f, 0.98f)},
                        {"subiendo", 0.0, 0.0, CameraType.THIRD_PERSON_FRONT, 6, new org.joml.Vector3f(0f, 1f, 0f), 0.4},
                };
                for (Object[] mv : moves) {
                    PLAN.add(() -> { yawOverride = 0F; pitchOverride = 10F; moveStep = (Double) mv[1]; moveStepZ = (Double) mv[2];
                            moveStepY = mv.length > 6 ? (Double) mv[6] : 0; moveYStart = moveY;
                            com.hmc.zenkai.client.aura.AuraTiltController.debugAxis = (org.joml.Vector3f) mv[5];
                            mc.options.setCameraType((CameraType) mv[3]); wait = 30; });
                    for (int fr = 0; fr < (Integer) mv[4]; fr++) {
                        final int frame = fr;
                        PLAN.add(() -> { wait = 2; });
                        PLAN.add(() -> capture(mc, s, dir, String.format("%s_f%02d", mv[0], frame)));
                    }
                    // Tras subir, de vuelta a la altura de las tomas (4 sobre el suelo despejado).
                    PLAN.add(() -> { moveStep = 0; moveStepZ = 0; moveStepY = 0; moveY = moveYStart;
                            com.hmc.zenkai.client.aura.AuraTiltController.debugAxis = null; wait = 30; });
                }
            }
            if (power.equals("base")) {
                setTime(mc, NIGHT);
                for (String id : ids) {
                    Scene s = form(id, true);
                    PLAN.add(() -> { active = s; yawOverride = 0F; pitchOverride = 10F;
                            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); wait = 40; });
                    PLAN.add(() -> capture(mc, s, "divinas/base/" + id, "noche"));
                }
            }
        }
        PLAN.add(() -> {
            active = null; pitchOverride = null; yawOverride = null; auraOff = false;
            moveStep = 0; moveStepZ = 0; airborne = false;
            AuraClientState.localTurbo = false;
            mc.options.fov().set(savedFov);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }

    /** Foto sin aura y con aura de una forma (de frente), con el % de poder impuesto si {@code power} > 0. */
    private static void reviewPair(Minecraft mc, Scene s, String dir, String name, int power) {
        PLAN.add(() -> {
            forcedPowerPercent = power;
            active = s; auraOff = true; pitchOverride = 10F;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            wait = 30;
        });
        PLAN.add(() -> capture(mc, s, dir, name + "_sin_aura"));
        PLAN.add(() -> { auraOff = false; wait = 30; });
        PLAN.add(() -> capture(mc, s, dir, name + "_con_aura"));
        PLAN.add(() -> { forcedPowerPercent = 0; wait = 2; });
    }

    /** 120 fotogramas seguidos sin mover nada: si alguno sale sin contorno, es un parpadeo de verdad. */
    private static void addSteady(Minecraft mc, Scene s, String dir) {
        PLAN.add(() -> {
            active = s; auraOff = true; yawOverride = 0F; pitchOverride = 10F;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            wait = 40;
        });
        for (int f = 0; f < 120; f++) {
            final int frame = f;
            PLAN.add(() -> capture(mc, s, "humo_prueba/" + dir, String.format("f%03d", frame)));
        }
    }

    private static void addSpin(Minecraft mc, Scene s, boolean off, CameraType cam, boolean spin, boolean tilt, String dir) {
        PLAN.add(() -> {
            active = s; auraOff = off; yawOverride = 0F; pitchOverride = tilt ? -45F : 10F;
            mc.options.setCameraType(cam);
            wait = 40;
        });
        for (int f = 0; f < FRAMES; f++) {
            final int frame = f;
            PLAN.add(() -> {
                if (spin) yawOverride = frame * 15F;
                if (tilt) pitchOverride = -45F + frame * (90F / (FRAMES - 1));
                wait = 1; // el giro se aplica en el tick y se dibuja en el fotograma siguiente
            });
            PLAN.add(() -> capture(mc, s, "humo_prueba/" + dir, String.format("f%03d", frame)));
        }
    }

    /** Comando del servidor integrado como el jugador (posición relativa a él), con permiso 4. */
    private static void command(Minecraft mc, String cmd) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayers().get(0);
            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), cmd);
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
            boolean wide = cameraMode || smokeMode || smokeShadersMode || smokeTestMode || reviewMode || momentaryMode || divineMode;
            boolean full = s.firstPerson() || wide;
            int x0 = full ? (wide ? (int) (w * 0.15F) : 0) : (int) (w * 0.30F);
            int y0 = full ? 0 : (int) (h * 0.04F);
            int cw = full ? (wide ? (int) (w * 0.70F) : w) : (int) (w * 0.40F);
            int ch = full ? h : (int) (h * 0.92F);
            int tw = s.firstPerson() ? 640 : wide ? 560 : 400;
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
