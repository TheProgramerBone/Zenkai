package com.hmc.zenkai.client.rage;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.compose.LongTransformMode;
import com.hmc.zenkai.event.ZenkaiPalAnimations;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.rage.LongTransformState;
import com.hmc.zenkai.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ESCENA de la primera transformación (Rage Forms y cualquier otra vía): lo que rodea al aura larga y la cámara. Cada cliente que
 * recibe el aviso ({@code LongTransformPacket}) arranca su copia de la escena en el mismo instante que el compositor de auras dispara
 * la secuencia larga (al PULSAR la tecla, o en el acto en un despertar por muerte), así que todo comparte reloj con {@code TransformFx}:
 * <pre>
 *   QUIEBRE   0 - 1,9 s   latido grave; el jugador cae / se encorva (clip PAL); el mundo se oscurece y la viñeta de color sube.
 *   ESCALADA  1,9 - 4 s   sube el viento y el zumbido; el temblor aumenta; pelo y ojos viran hacia la forma (buildup).
 *   ESTALLIDO 4 s         golpe + cola reverberada, la nota del acontecimiento, destello, (rugido / Beast) un rayo solo visual;
 *                         ClientFormDelay aplica aquí la forma nueva y el servidor completa la carga en el mismo tick.
 *   REMATE    4,3 - 6,9 s el nombre de la forma aparece grande y se desvanece.
 * </pre>
 * La POSE la declara la FORMA (campo {@code long_scene.pose} de su JSON, 0.6.1: familias como {@code serene} para las divinas,
 * {@code skyward} para los Legendarios, {@code flex} para el Arcosiano…), salvo el rescate de un aliado, que siempre es levantarse en
 * calma. Sin pose declarada: de rodillas por duelo y rugido por cualquier otra vía (entrenamiento, ritual…). La NOTA de sonido, igual: pérdida de un
 * aliado, mascota o rescate. No hay grito (decidido: se hará aparte). Todo es presentación: nada de esto toca el estado.
 * <p>
 * MODOS ({@link LongTransformMode}): FULL todo; REDUCED sin destello, oscurecimiento, rayo ni cámara (apto para quien teme los
 * fogonazos). Abrir cualquier pantalla corta solo las capas VISUALES propias (viñeta, destello, título): la animación y el sonido
 * siguen, como la cámara de {@code TransformCameraFx}.
 * <p>
 * Lo que NO hay (a propósito): bajar la música y el ambiente (el motor de sonido no deja escalar una categoría sin tocar las
 * opciones del jugador) y cámara lenta real (retrasar el reloj del cliente rompe el resto de sistemas). El oscurecimiento se pinta
 * en pantalla; no es el brillo del cielo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AwakeningScene {
    private AwakeningScene() {}

    /** Poses de Zenkai con rayo vanilla solo visual en el estallido (FULL): las violentas. */
    private static final java.util.Set<String> LIGHTNING_POSES = java.util.Set.of("roar", "clutch", "ssj3_long", "skyward",
            "ultra_ego", "beast", "oozaru", "super_oozaru", "ikari");

    public static final int LENGTH_TICKS = 126;
    /** Cuánto vive el título después del estallido (el overlay dura más que la secuencia del aura). */
    private static final int OVERLAY_TICKS = 138;
    private static final int T_RISE = 38, T_BURST = 80;
    /** Tick del estallido: ClientFormDelay aplica ahí la forma nueva. */
    public static final int BURST_TICKS = T_BURST;

    private static final class Scene {
        int entityId;
        boolean self;
        /** Pose elegida: nombre corto de Zenkai ("roar") o id con namespace de un clip de addon. */
        String pose;
        String trigger;
        /** Primera vez de esa forma: solo entonces sale el título con su nombre. */
        boolean first;
        ResourceLocation form;
        float[] rgb;
        LongTransformMode mode;
        long start;
        boolean heartbeat, rise, burst;
        /** Ya se avisó al servidor de que el jugador cortó la presentación (crónica «interrumpida»): una vez por escena. */
        boolean interruptedSent;
        double x, y, z;
    }

    private static final List<Scene> ACTIVE = new ArrayList<>();

    /**
     * Elige la pose según la forma y lo que la despertó. Público para las pruebas. Orden (decidido por el usuario, 2026-11-13): el
     * rescate de un aliado manda siempre (es una escena con significado propio); después, la pose que declare la forma; sin ella,
     * de rodillas por duelo (SSJ1 y Humano super por pérdida) y rugido por cualquier otra vía.
     */
    public static String pick(ResourceLocation form, String trigger) {
        if ("ALLY_RESCUED".equals(trigger)) return "resolve";
        FormDef def = FormDef.get(form);
        if (def != null && def.hasLongScenePose()) return def.longScenePose();
        if (form != null && FormIds.BEAST.equals(form)) return "clutch";   // respaldo si un datapack quita el campo
        if (trigger != null && !trigger.isEmpty()) return "kneel";
        return "roar";
    }

    /** Lo llama el compositor de auras al disparar la secuencia larga. {@code rgb} es el color exterior del aura. */
    public static void start(AbstractClientPlayer p, boolean self, LongTransformState.Pending info, float[] rgb, LongTransformMode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mode == LongTransformMode.OFF) return;
        // Una escena por jugador: una nueva sustituye a la anterior.
        ACTIVE.removeIf(s -> s.entityId == p.getId());
        Scene s = new Scene();
        s.entityId = p.getId();
        s.self = self;
        s.form = info.form();
        s.trigger = info.trigger();
        s.first = info.first();
        s.pose = pick(info.form(), info.trigger());
        s.rgb = new float[]{rgb[0], rgb[1], rgb[2]};
        s.mode = mode;
        s.start = mc.level.getGameTime();
        s.x = p.getX();
        s.y = p.getY();
        s.z = p.getZ();
        ACTIVE.add(s);
        ZenkaiPalAnimations.playRageScene(p, s.pose);
    }

    /** Corta la escena de ese jugador (carga cancelada antes de completarse): pose, viñeta, título y sonidos aún sin disparar. */
    public static void cancel(AbstractClientPlayer p) {
        ACTIVE.removeIf(s -> s.entityId == p.getId());
        ZenkaiPalAnimations.stopRageScene(p);
    }

    /** Forma de la escena en curso de ese jugador (de principio a fin, también tras el estallido), o null si no tiene escena. */
    public static ResourceLocation activeForm(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Scene s : ACTIVE) {
            if (s.entityId == entityId && mc.level.getGameTime() - s.start < LENGTH_TICKS) return s.form;
        }
        return null;
    }

    /** Hacia qué forma y cuánto (0..1) va la ESCALADA de la escena de este jugador, o null si no hay escena o ya estalló. */
    public record Buildup(ResourceLocation form, float progress) {}

    /**
     * Pelo, ojos, cejas y cola cambian de color conforme avanza la escena (de 0 al estallido), y no de golpe en el estallido: lo lee
     * {@code HairTransition}. Solo hay escalada mientras el cliente aún muestra la forma anterior (ClientFormDelay).
     */
    public static Buildup buildup(net.minecraft.world.entity.player.Player p, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Scene s : ACTIVE) {
            if (s.entityId != p.getId() || s.form == null) continue;
            float age = (mc.level.getGameTime() - s.start) + partialTick;
            if (age >= T_BURST) return null;
            return new Buildup(s.form, Mth.clamp(age / T_BURST, 0f, 1f));
        }
        return null;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { ACTIVE.clear(); return; }
        Iterator<Scene> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Scene s = it.next();
            long age = mc.level.getGameTime() - s.start;
            if (age >= OVERLAY_TICKS) { it.remove(); continue; }
            if (mc.level.getEntity(s.entityId) instanceof AbstractClientPlayer p) {
                s.x = p.getX();
                s.y = p.getY();
                s.z = p.getZ();
            } else if (age < LENGTH_TICKS) {
                it.remove();   // el jugador salió del rango: sin él no hay escena
                continue;
            }
            // Abrir una pantalla durante la escena propia de una primera vez corta las capas visuales: la crónica lo anota.
            if (s.self && s.first && !s.interruptedSent && mc.screen != null && age < LENGTH_TICKS) {
                s.interruptedSent = true;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.hmc.zenkai.feature.rage.LongTransformInterruptedPacket());
            }
            if (!s.heartbeat) { s.heartbeat = true; sound(mc, s, ModSounds.RAGE_HEARTBEAT.get(), s.self ? 1.0f : 1.4f, 1.0f); }
            if (!s.rise && age >= T_RISE) { s.rise = true; sound(mc, s, ModSounds.RAGE_RISE.get(), s.self ? 1.0f : 1.4f, 1.0f); }
            if (!s.burst && age >= T_BURST) { s.burst = true; burst(mc, s); }
        }
    }

    private static void burst(Minecraft mc, Scene s) {
        // Volumen 3: el sonido llega a unos 48 bloques (16 × volumen), un aviso de que alguien se ha transformado cerca.
        sound(mc, s, ModSounds.RAGE_BURST.get(), 3.0f, 1.0f);
        SoundEvent note = noteFor(s);
        if (note != null) sound(mc, s, note, s.self ? 1.0f : 1.6f, 1.0f);
        if (s.mode == LongTransformMode.FULL && LIGHTNING_POSES.contains(s.pose)) lightning(mc, s);
    }

    private static SoundEvent noteFor(Scene s) {
        return switch (s.trigger == null ? "" : s.trigger) {
            case "ALLY_RESCUED" -> ModSounds.RAGE_NOTE_RESCUE.get();
            case "PET_DEATH" -> ModSounds.RAGE_NOTE_PET.get();
            case "ALLY_DEATH", "ALLY_DIES_WHILE_DOWNED", "CRITICAL_SURVIVAL", "ALLY_DOWNED", "BODY_GESTURE" -> ModSounds.RAGE_NOTE_LOSS.get();
            default -> null;
        };
    }

    private static void sound(Minecraft mc, Scene s, SoundEvent ev, float volume, float pitch) {
        mc.level.playLocalSound(s.x, s.y, s.z, ev, SoundSource.PLAYERS, volume, pitch, false);
    }

    /** Descarga cosmética: un rayo vanilla SOLO visual (no quema, no hace daño; el servidor no sabe de él). */
    private static void lightning(Minecraft mc, Scene s) {
        try {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(mc.level);
            if (bolt == null) return;
            bolt.moveTo(s.x + 3.0, s.y, s.z - 2.0);
            bolt.setVisualOnly(true);
            mc.level.addEntity(bolt);
        } catch (RuntimeException ignored) {
            // es solo adorno: un fallo no debe cortar la escena
        }
    }

    // ── Cámara: elevación suave (contrapicado) durante el estallido. La usa TransformCameraFx. ──

    /** Segundos desde el inicio de la escena propia en ticks (con parcial), o -1 si no hay. */
    public static float selfElapsed(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1f;
        for (Scene s : ACTIVE) {
            if (!s.self) continue;
            return (mc.level.getGameTime() - s.start) + partialTick;
        }
        return -1f;
    }

    // ── Capas de pantalla ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || ACTIVE.isEmpty()) return;
        GuiGraphics g = e.getGuiGraphics();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        for (Scene s : ACTIVE) {
            float age = (mc.level.getGameTime() - s.start) + pt;
            if (s.self) {
                if (age < LENGTH_TICKS) selfLayers(g, s, age, w, h);
                title(g, mc, s, age, w, h);
            } else if (s.mode == LongTransformMode.FULL) {
                observerFlash(g, mc, s, age, w, h);
            }
        }
    }

    private static void selfLayers(GuiGraphics g, Scene s, float age, int w, int h) {
        boolean full = s.mode == LongTransformMode.FULL;
        // Oscurecimiento: baja entre 0 y 25 ticks, se mantiene hasta el estallido y se va de golpe.
        if (full) {
            float dim = age < 52f ? ease(age / 52f) : age < T_BURST ? 1f : Math.max(0f, 1f - (age - T_BURST) / 3f);
            fill(g, 0, 0, w, h, 0x000000, 0.42f * dim);
        }
        // Viñeta del color de la forma: sube con el quiebre, se mantiene y se apaga hacia el final.
        float vig = age < 63f ? ease(age / 63f) : age < T_BURST + 6 ? 1f : Math.max(0f, 1f - (age - (T_BURST + 6)) / 30f);
        vignette(g, w, h, rgb(s.rgb), (full ? 0.55f : 0.35f) * vig);
        // Destello del estallido (solo FULL): blanco teñido que cae en 10 ticks.
        if (full && age >= T_BURST && age < T_BURST + 10) {
            float f = 1f - (age - T_BURST) / 10f;
            fill(g, 0, 0, w, h, mix(0xFFFFFF, rgb(s.rgb), 0.25f), 0.65f * f * f);
        }
    }

    /** Un espectador cerca ve un destello corto en el estallido, más fuerte cuanto más cerca esté. */
    private static void observerFlash(GuiGraphics g, Minecraft mc, Scene s, float age, int w, int h) {
        if (age < T_BURST || age >= T_BURST + 7 || mc.player == null) return;
        double d = Math.sqrt(mc.player.distanceToSqr(new Vec3(s.x, s.y, s.z)));
        float near = Mth.clamp(1f - (float) d / 32f, 0f, 1f);
        if (near <= 0f) return;
        float f = 1f - (age - T_BURST) / 7f;
        fill(g, 0, 0, w, h, mix(0xFFFFFF, rgb(s.rgb), 0.3f), 0.32f * near * f);
    }

    /** Nombre de la forma, grande, con el color del aura; entra a los 86 ticks y se va hacia el 136. */
    private static void title(GuiGraphics g, Minecraft mc, Scene s, float age, int w, int h) {
        if (!s.first || s.form == null || age < 86f || age > OVERLAY_TICKS - 2) return;
        float a = age < 96f ? (age - 86f) / 10f : age < 122f ? 1f : Math.max(0f, 1f - (age - 122f) / 14f);
        if (a <= 0.02f) return;
        int alpha = (int) (255 * a);
        int color = (alpha << 24) | mix(0xFFFFFF, rgb(s.rgb), 0.55f);
        Component name = Component.translatable(FormDef.nameKey(s.form));
        Component sub = Component.translatable("zenkai.rage_scene.title.subtitle");
        var font = mc.font;
        var pose = g.pose();
        pose.pushPose();
        float scale = 3.0f + 0.15f * Math.min(1f, (age - 86f) / 40f);   // un leve avance mientras se lee
        pose.translate(w / 2f, h * 0.30f, 0f);
        pose.scale(scale, scale, 1f);
        // Contorno oscuro: el color del aura es el del fondo (aura amarilla sobre texto amarillo no se lee).
        int outline = ((int) (alpha * 0.85f) << 24) | 0x101010;
        int nx = -font.width(name) / 2, ny = -font.lineHeight / 2;
        for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
            g.drawString(font, name, nx + d[0], ny + d[1], outline, false);
        }
        g.drawString(font, name, nx, ny, color, false);
        pose.popPose();
        pose.pushPose();
        pose.translate(w / 2f, h * 0.30f + 20f, 0f);
        pose.scale(1.2f, 1.2f, 1f);
        int sx = -font.width(sub) / 2;
        for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) g.drawString(font, sub, sx + d[0], d[1], outline, false);
        g.drawString(font, sub, sx, 0, (alpha << 24) | 0xF0F0F0, false);
        pose.popPose();
    }

    // ── Utilidades de dibujo ─────────────────────────────────────────────────

    private static float ease(float x) { x = Mth.clamp(x, 0f, 1f); return x * x * (3f - 2f * x); }

    private static int rgb(float[] c) {
        return (Mth.clamp((int) (c[0] * 255), 0, 255) << 16) | (Mth.clamp((int) (c[1] * 255), 0, 255) << 8) | Mth.clamp((int) (c[2] * 255), 0, 255);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int gg = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (gg << 8) | bl;
    }

    private static void fill(GuiGraphics g, int x0, int y0, int x1, int y1, int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        if (a <= 0) return;
        g.fill(x0, y0, x1, y1, (a << 24) | (rgb & 0xFFFFFF));
    }

    /** Viñeta de bordes: bandas cuya opacidad cae hacia el centro (8 escalones por lado; sin textura). */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        int steps = 10;
        int bandX = Math.max(8, w / 7), bandY = Math.max(8, h / 6);
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            float a = alpha * k * k * 0.5f;
            int dx = bandX * i / steps, dy = bandY * i / steps;
            int dx2 = bandX * (i + 1) / steps, dy2 = bandY * (i + 1) / steps;
            fill(g, 0, dy, w, dy2, rgb, a);              // arriba
            fill(g, 0, h - dy2, w, h - dy, rgb, a);      // abajo
            fill(g, dx, 0, dx2, h, rgb, a);              // izquierda
            fill(g, w - dx2, 0, w - dx, h, rgb, a);      // derecha
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { ACTIVE.clear(); }
}
