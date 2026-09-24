package com.hmc.zenkai.config;

import com.hmc.zenkai.client.overlay.HudAnchor;
import com.hmc.zenkai.client.overlay.HudOrientation;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Preferencias que solo afectan a lo que ve ESTE jugador.
 * Separada de CommonConfig y ServerConfig a propósito: aquí no va nada que altere el
 * comportamiento del juego, solo presentación. Si una opción cambia el resultado de algo,
 * pertenece a Common o Server — si no, un cliente podría cambiarla y desincronizar.
 * AUTOMATISMO DE LA PANTALLA: cada opción se declara con un helper define*(), que además de
 * crear el valor lo apunta en ENTRIES. ClientConfigScreen recorre esa lista, así que una opción
 * nueva aparece sola en la GUI sin tocar la pantalla. La alternativa —hurgar en el
 * ModConfigSpec por reflexión— funcionaría hoy y se rompería en la próxima versión.
 * TRES TIPOS DE OPCIÓN. Antes solo había booleanos y la pantalla podía asumir que cada fila era
 * un ON/OFF. Al llegar la colocación del HUD hicieron falta enumerados y números, así que Entry
 * pasa a ser una interfaz sellada: la pantalla hace un switch sobre los tres casos y el
 * compilador avisa si algún día se añade un cuarto y alguien olvida pintarlo.
 */
public final class ClientConfig {
    private ClientConfig() {}

    // ── Modelo de opciones ───────────────────────────────────────────────────

    /** Una opción configurable, con su clave de traducción para la pantalla. */
    public sealed interface Entry permits BoolEntry, EnumEntry, IntEntry {
        String titleKey();
        String tooltipKey();
    }

    public record BoolEntry(ModConfigSpec.BooleanValue value, String titleKey, String tooltipKey)
            implements Entry {}

    /** Declara sin registrar en la GUI: para valores que se editan por otra vía. */
    private static ModConfigSpec.BooleanValue defineHiddenBool(String path, String comment,
                                                               boolean def) {
        return BUILDER.comment(comment).define(path, def);
    }

    private static <T extends Enum<T>> ModConfigSpec.EnumValue<T> defineHiddenEnum(
            String path, String comment, T def) {
        return BUILDER.comment(comment).defineEnum(path, def);
    }

    /**
     * Opción de lista. Guarda la clase del enum porque ModConfigSpec.EnumValue no la expone y
     * la pantalla necesita poder recorrer los valores para ciclar entre ellos.
     */
    public record EnumEntry<T extends Enum<T>>(ModConfigSpec.EnumValue<T> value, Class<T> type,
                                               String titleKey, String tooltipKey)
            implements Entry {
        public T[] options() { return type.getEnumConstants(); }

        /** Siguiente valor en el ciclo, con vuelta. dir = -1 o +1. */
        public T cycle(T from, int dir) {
            T[] all = options();
            return all[Math.floorMod(from.ordinal() + dir, all.length)];
        }
    }

    public record IntEntry(ModConfigSpec.IntValue value, int min, int max, int step,
                           String titleKey, String tooltipKey) implements Entry {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Declara una opción Y la registra para la GUI.
     * Usa SIEMPRE estos helpers en vez de BUILDER.define(...) directamente, o la opción
     * existirá en el toml pero será invisible en la pantalla.
     */
    private static ModConfigSpec.BooleanValue defineBool(String path, String key,
                                                         String comment, boolean def) {
        ModConfigSpec.BooleanValue v = BUILDER.comment(comment).define(path, def);
        ENTRIES.add(new BoolEntry(v, "config.zenkai." + key, "config.zenkai." + key + ".desc"));
        return v;
    }

    private static <T extends Enum<T>> ModConfigSpec.EnumValue<T> defineEnum(
            String path, String key, String comment, T def, Class<T> type) {
        ModConfigSpec.EnumValue<T> v = BUILDER.comment(comment).defineEnum(path, def);
        ENTRIES.add(new EnumEntry<>(v, type,
                "config.zenkai." + key, "config.zenkai." + key + ".desc"));
        return v;
    }

    /**
     * Entero con rango. El {@code step} es solo para la GUI (cuánto salta cada pulsación); el
     * rango sí lo impone el spec, así que un toml editado a mano tampoco puede salirse.
     */
    private static ModConfigSpec.IntValue defineInt(String path, String key, String comment,
                                                    int def, int min, int max, int step) {
        ModConfigSpec.IntValue v = BUILDER.comment(comment).defineInRange(path, def, min, max);
        ENTRIES.add(new IntEntry(v, min, max, step,
                "config.zenkai." + key, "config.zenkai." + key + ".desc"));
        return v;
    }

    /** Declara sin registrar en la GUI: para valores que se editan por otra vía. */
    private static ModConfigSpec.IntValue defineHiddenInt(String path, String comment,
                                                          int def, int min, int max) {
        return BUILDER.comment(comment).defineInRange(path, def, min, max);
    }

    // ── Opciones ─────────────────────────────────────────────────────────────

    private static final ModConfigSpec.BooleanValue KI_SENSE_CAMERA_SHAKE =
            defineBool("sense.ki_sense_camera_shake", "ki_sense_camera_shake",
                    "Shake the camera when your ki sense warns you of a nearby threat", true);

    /** 0 = igual que el comportamiento de siempre: el aura de uno mismo no se dibuja en
     *  primera persona (ver AuraRenderer). Por encima de 0 deja de ser un simple on/off — el
     *  jugador que quiera verla aunque sea tenue puede subirla sin llegar al 100%. */
    private static final ModConfigSpec.IntValue AURA_FP_OPACITY =
            defineInt("aura.first_person_opacity", "aura_first_person_opacity",
                    "Opacity, in percent, of your OWN aura as seen in first person. 0 hides it "
                            + "completely (previous behaviour), 100 shows it at full strength",
                    30, 0, 100, 10);

    /** 100 = igual que el comportamiento de siempre: la bola de ki que cargas/sueltas se ve a
     *  máxima fuerza en primera persona. Por debajo de eso es para quien la encuentra
     *  demasiado grande pegada a la cámara pero no quiere perderla. */
    private static final ModConfigSpec.IntValue KI_FP_OPACITY =
            defineInt("ki.first_person_opacity", "ki_first_person_opacity",
                    "Opacity, in percent, of your OWN charging/releasing ki ball as seen in "
                            + "first person. 0 hides it completely, 100 shows it at full strength "
                            + "(previous behaviour)",
                    100, 0, 100, 10);

    /** Recuperado 2026-09-23 tras haberse eliminado sin confirmar (ver KiVfxBloomPipeline) — esta
     *  vez a petición EXPLÍCITA del usuario ("permiso de usar bloom... sin temor a romper algo"),
     *  así que arranca en TRUE, no en false como el intento original. Sigue rindiéndose solo si
     *  hay un shaderpack cargado (IrisCompat.shaderPackActive()): eso no es timidez, es evitar
     *  que dos pipelines de post-proceso independientes compitan por el mismo framebuffer. */
    private static final ModConfigSpec.BooleanValue KI_BLOOM_ENABLED =
            defineBool("ki.bloom_enabled", "ki_bloom_enabled",
                    "Real screen-space bloom/glow pass on top of the ki VFX (halo, core, trail). "
                            + "On by default. Automatically disabled if a shader pack (Iris/Oculus) is loaded",
                    true);

    /** 100 = tamaño nativo de bars_empty.png/bars_full.png (256x64 el bloque de las 3 barras).
     *  Pedido para que el HUD de Body/Stamina/Ki no se salga de pantalla con un GUI Scale alto
     *  o una ventana pequeña — el arte se pensó a un GUI Scale concreto y el resto de jugadores
     *  necesitan poder achicarlo. Tope subido de 100 a 150 (y por defecto de 80 a 100) a petición
     *  del usuario para poder ver el bloque más grande; el texto de cada fila escala con este
     *  mismo valor (ver ClientZenkaiHooks.drawStatBar/TEXT_SCALE_MULT) así que no se queda
     *  pequeño al subir el tamaño. */
    private static final ModConfigSpec.IntValue HUD_BARS_SCALE =
            defineInt("hud.bars_scale", "hud_bars_scale",
                    "Size, in percent of the native texture, of the Body/Stamina/Ki bars in the HUD",
                    65, 20, 150, 5);

    // ── HUD de técnicas ──────────────────────────────────────────────────────

    // NINGUNA de las tres aparece como fila. La colocación entera se decide en
    // HudPlacementScreen, que es donde se VE lo que se está eligiendo, y se guarda de una vez
    // por setHudPlacement(). Tenerlas además en la lista daba dos vías de escritura para el
    // mismo valor: el buffer staged de ClientConfigScreen se construye al abrir la pantalla, y
    // al volver de colocar el HUD ese buffer es viejo — pulsar Save revertía la colocación
    // recién hecha.
    private static final ModConfigSpec.EnumValue<HudAnchor> HUD_ANCHOR =
            defineHiddenEnum("hud.technique_anchor",
                    "Screen corner or edge the technique bar hangs from", HudAnchor.MIDDLE_RIGHT);

    private static final ModConfigSpec.EnumValue<HudOrientation> HUD_ORIENTATION =
            defineHiddenEnum("hud.technique_orientation",
                    "Whether the technique bar stacks vertically or horizontally",
                    HudOrientation.VERTICAL);

    private static final ModConfigSpec.BooleanValue HUD_AVOID_HOTBAR =
            defineHiddenBool("hud.technique_avoid_hotbar",
                    "Push the technique bar above the vanilla hotbar instead of overlapping it",
                    true);

    // Los desplazamientos NO aparecen como filas: se ajustan arrastrando el bloque en la
    // pantalla de colocación. Dos campos numéricos para algo que se decide a ojo serían el
    // peor de los dos mundos — tedioso de ajustar e imposible de previsualizar.
    private static final ModConfigSpec.IntValue HUD_OFFSET_X =
            defineHiddenInt("hud.technique_offset_x",
                    "Horizontal offset from the anchor, in GUI pixels", -4, -4096, 4096);

    private static final ModConfigSpec.IntValue HUD_OFFSET_Y =
            defineHiddenInt("hud.technique_offset_y",
                    "Vertical offset from the anchor, in GUI pixels", 0, -4096, 4096);

    // El último color elegido en el banco de scouter. Oculto: no es una opción que se toque
    // en una lista, es memoria de lo que hiciste la última vez. Y va en config de cliente y
    // no en el banco porque lo que el jugador quiere recordar es SU color, no el del bloque.
    private static final ModConfigSpec.IntValue SCOUTER_TINT =
            defineHiddenInt("scouter.last_tint",
                    "Last colour picked in the scouter bench, as 0xRRGGBB",
                    0xd82624, 0x000000, 0xFFFFFF);

    // ── Teclas de carril de Meditation (A/S/D/F) ────────────────────────────────

    /** Ocultas de ClientConfigScreen: se editan desde un popup propio dentro de
     *  MeditationScreen (engranaje, icons.png 80,60), no de la lista genérica de opciones —
     *  pedido explícito del usuario ("no quiero que sea por keybinding de minecraft como tal").
     *  Rango 0..348 = GLFW_KEY_SPACE..GLFW_KEY_LAST, generoso a propósito: MeditationScreen ya
     *  valida que las 4 no se pisen entre sí antes de guardar, así que aquí solo hace falta un
     *  código de tecla GLFW válido, no una lista concreta. */
    private static final ModConfigSpec.IntValue MEDITATION_LANE_KEY_0 =
            defineHiddenInt("meditation.lane_key_0",
                    "GLFW key code for lane 1 (leftmost) of the Meditation rhythm minigame",
                    GLFW.GLFW_KEY_A, 0, GLFW.GLFW_KEY_LAST);
    private static final ModConfigSpec.IntValue MEDITATION_LANE_KEY_1 =
            defineHiddenInt("meditation.lane_key_1",
                    "GLFW key code for lane 2 of the Meditation rhythm minigame",
                    GLFW.GLFW_KEY_S, 0, GLFW.GLFW_KEY_LAST);
    private static final ModConfigSpec.IntValue MEDITATION_LANE_KEY_2 =
            defineHiddenInt("meditation.lane_key_2",
                    "GLFW key code for lane 3 of the Meditation rhythm minigame",
                    GLFW.GLFW_KEY_D, 0, GLFW.GLFW_KEY_LAST);
    private static final ModConfigSpec.IntValue MEDITATION_LANE_KEY_3 =
            defineHiddenInt("meditation.lane_key_3",
                    "GLFW key code for lane 4 (rightmost) of the Meditation rhythm minigame",
                    GLFW.GLFW_KEY_F, 0, GLFW.GLFW_KEY_LAST);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── Lectura ──────────────────────────────────────────────────────────────

    public static boolean kiSenseCameraShake() { return KI_SENSE_CAMERA_SHAKE.get(); }

    /** Fracción 0f..1f, lista para multiplicar directamente sobre un alpha. */
    public static float auraFirstPersonOpacityFrac() { return AURA_FP_OPACITY.get() / 100f; }
    public static float kiFirstPersonOpacityFrac() { return KI_FP_OPACITY.get() / 100f; }
    public static boolean kiBloomEnabled() { return KI_BLOOM_ENABLED.get(); }
    public static float hudBarsScaleFrac() { return HUD_BARS_SCALE.get() / 100f; }

    public static HudAnchor hudAnchor() { return HUD_ANCHOR.get(); }
    public static HudOrientation hudOrientation() { return HUD_ORIENTATION.get(); }
    public static boolean hudAvoidHotbar() { return HUD_AVOID_HOTBAR.get(); }
    public static int hudOffsetX() { return HUD_OFFSET_X.get(); }
    public static int hudOffsetY() { return HUD_OFFSET_Y.get(); }
    public static int scouterLastTint() { return SCOUTER_TINT.get(); }

    public static void setScouterLastTint(int rgb) {
        SCOUTER_TINT.set(rgb & 0xFFFFFF);
        SPEC.save();   // ⚠ API
    }

    /** Guarda la colocación COMPLETA de una vez: es la única vía de escritura de estos cuatro
     *  valores, así que no puede quedar ninguno fuera o volvería la doble autoridad. */
    public static void setHudPlacement(HudAnchor anchor, HudOrientation orientation,
                                       int offsetX, int offsetY, boolean avoidHotbar) {
        HUD_ANCHOR.set(anchor);
        HUD_ORIENTATION.set(orientation);
        HUD_OFFSET_X.set(offsetX);
        HUD_OFFSET_Y.set(offsetY);
        HUD_AVOID_HOTBAR.set(avoidHotbar);
        SPEC.save();   // ⚠ API
    }

    /** Lista inmutable para la pantalla. */
    public static List<Entry> entries() { return Collections.unmodifiableList(ENTRIES); }

    /** Código de tecla GLFW del carril `lane` (0..3, mismo índice que MeditationScreen.KEYS). */
    public static int meditationLaneKey(int lane) {
        return switch (lane) {
            case 0 -> MEDITATION_LANE_KEY_0.get();
            case 1 -> MEDITATION_LANE_KEY_1.get();
            case 2 -> MEDITATION_LANE_KEY_2.get();
            default -> MEDITATION_LANE_KEY_3.get();
        };
    }

    /** Guarda las 4 teclas de carril DE UNA VEZ — mismo criterio que setHudPlacement(): un solo
     *  punto de escritura para un conjunto de valores relacionados, para que no pueda quedar
     *  uno desincronizado de los otros tres. El llamador (MeditationScreen) ya valida que las 4
     *  sean distintas entre sí antes de llegar aquí. */
    public static void setMeditationLaneKeys(int lane0, int lane1, int lane2, int lane3) {
        MEDITATION_LANE_KEY_0.set(lane0);
        MEDITATION_LANE_KEY_1.set(lane1);
        MEDITATION_LANE_KEY_2.set(lane2);
        MEDITATION_LANE_KEY_3.set(lane3);
        SPEC.save();   // ⚠ API
    }
}