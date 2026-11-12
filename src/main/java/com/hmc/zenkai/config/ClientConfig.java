package com.hmc.zenkai.config;

import com.hmc.zenkai.client.aura.AuraClassicQuality;
import com.hmc.zenkai.client.aura.flame.AuraEngine;
import com.hmc.zenkai.client.overlay.HudAnchor;
import com.hmc.zenkai.client.overlay.HudOrientation;
import com.hmc.zenkai.client.overlay.hud.HudBarsStyle;
import com.hmc.zenkai.client.overlay.hud.HudValueFormat;
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
 * compilador avisa si algún día se añade un cuarto y alguien olvida pintarlo. Ese cuarto llegó
 * (ColorEntry, 2026-10-09) y el aviso funcionó tal cual.
 */
public final class ClientConfig {
    private ClientConfig() {}

    // ── Modelo de opciones ───────────────────────────────────────────────────

    /** Una opción configurable, con su clave de traducción para la pantalla. */
    public sealed interface Entry permits BoolEntry, EnumEntry, IntEntry, ColorEntry {
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

    /** Color 0xRRGGBB guardado como entero. Tipo propio y no IntEntry porque la pantalla lo edita
     *  con el selector de color (ColorPickerWidget), no con flechas: recorrer 16,7 millones de
     *  valores de uno en uno no es un control. */
    public record ColorEntry(ModConfigSpec.IntValue value, String titleKey, String tooltipKey)
            implements Entry {}

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

    /** Color 0xRRGGBB con fila de selector de color en la pantalla. */
    private static ModConfigSpec.IntValue defineColor(String path, String key, String comment, int def) {
        ModConfigSpec.IntValue v = BUILDER.comment(comment).defineInRange(path, def, 0x000000, 0xFFFFFF);
        ENTRIES.add(new ColorEntry(v, "config.zenkai." + key, "config.zenkai." + key + ".desc"));
        return v;
    }

    /** Declara sin registrar en la GUI: para valores que se editan por otra vía. */
    private static ModConfigSpec.IntValue defineHiddenInt(String path, String comment,
                                                          int def, int min, int max) {
        return BUILDER.comment(comment).defineInRange(path, def, min, max);
    }

    // ── Opciones ─────────────────────────────────────────────────────────────

    private static final ModConfigSpec.BooleanValue KI_FIST_AURA =
            defineBool("combat.ki_fist_aura", "ki_fist_aura",
                    "Draw ki-coloured energy around the fists while Ki Fist is on (and Ultra Ego's destruction energy)", true);

    private static final ModConfigSpec.BooleanValue KI_INFUSE_GLOW =
            defineBool("combat.ki_infuse_glow", "ki_infuse_glow",
                    "Make the weapon in hand glow with the player's aura while Ki Infuse is on", true);

    private static final ModConfigSpec.BooleanValue SWORD_TECH_FX =
            defineBool("combat.sword_technique_fx", "sword_technique_fx",
                    "Draw slash arcs, shockwaves and streaks for the sword techniques", true);

    private static final ModConfigSpec.EnumValue<com.hmc.zenkai.client.aura.compose.LongTransformMode> LONG_TRANSFORM_MODE =
            defineEnum("transformation.long_sequence", "long_transform_mode",
                    "Long sequence the FIRST time a form is used (any route): FULL = long aura plus camera shake and zoom, "
                            + "REDUCED = long aura without touching the camera, OFF = always the short one. Presentation only.",
                    com.hmc.zenkai.client.aura.compose.LongTransformMode.FULL,
                    com.hmc.zenkai.client.aura.compose.LongTransformMode.class);

    private static final ModConfigSpec.BooleanValue ALWAYS_LONG_TRANSFORM =
            defineBool("transformation.always_long_sequence", "always_long_transform",
                    "Play the long sequence on EVERY transformation of yours, not only the first time a form is used. "
                            + "Presentation only: no invulnerability and no lock, those stay exclusive to the first time", false);

    /** Formas que el jugador oculta de la rueda (clic derecho en el árbol). Preferencia de este cliente, no de la partida. */
    private static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_WHEEL_FORMS =
            BUILDER.comment("Forms hidden from the power wheel (right click on the form in the transformation tree)")
                    .defineListAllowEmpty("transformation.hidden_wheel_forms", List.of(), () -> "", o -> o instanceof String);

    private static final ModConfigSpec.BooleanValue KI_SENSE_CAMERA_SHAKE =
            defineBool("sense.ki_sense_camera_shake", "ki_sense_camera_shake",
                    "Shake the camera when your ki sense warns you of a nearby threat", true);

    private static final ModConfigSpec.BooleanValue FLIGHT_FX_DUST =
            defineBool("flight_fx.ground_dust", "flight_fx_dust",
                    "Kick up dust and debris from the ground while flying fast and low", true);

    private static final ModConfigSpec.BooleanValue FLIGHT_FX_SONIC_BOOM =
            defineBool("flight_fx.sonic_boom", "flight_fx_sonic_boom",
                    "Play a sonic boom (ring and sound) once each time you break the speed threshold", true);

    private static final ModConfigSpec.BooleanValue FLIGHT_FX_SPEED_LINES =
            defineBool("flight_fx.speed_lines", "flight_fx_speed_lines",
                    "Draw thin speed lines streaking past you while flying at high speed", true);

    private static final ModConfigSpec.BooleanValue FLIGHT_FX_LANDING =
            defineBool("flight_fx.landing_shockwave", "flight_fx_landing",
                    "Draw a dust shockwave ring when you land at high speed", true);

    /** 0 = igual que el comportamiento de siempre: el aura de uno mismo no se dibuja en
     *  primera persona (ver AuraRenderer). Por encima de 0 deja de ser un simple on/off — el
     *  jugador que quiera verla aunque sea tenue puede subirla sin llegar al 100%. */
    private static final ModConfigSpec.IntValue AURA_FP_OPACITY =
            defineInt("aura.first_person_opacity", "aura_first_person_opacity",
                    "Opacity, in percent, of your OWN aura as seen in first person. 0 hides it "
                            + "completely (previous behaviour), 100 shows it at full strength",
                    30, 0, 100, 10);

    /** Cuánto crece el aura COMPUESTA al subir el poder, en % del crecimiento de siempre. 100 = el actual
     *  (es el mínimo: no se puede hacer crecer MENOS que el diseño aprobado); por encima, el tramo
     *  por encima del poder base crece proporcionalmente más. Solo visual, por eso va en el cliente. */
    private static final ModConfigSpec.IntValue AURA_POWER_GROWTH =
            defineInt("aura.power_growth_percent", "aura_power_growth",
                    "How much your aura grows as your power rises, in percent of the default growth. "
                            + "100 = default (minimum); 200 = the part of the size above base power grows twice as fast",
                    100, 100, 300, 10);

    /** Cómo prefiere ESTE jugador activar el parry: KEY (por defecto, solo la tecla de parry), BLOCK
     *  (al pulsar la guardia: la guardia real se aplaza hasta que acaba la ventana) o BOTH. Es una
     *  preferencia del jugador: el cliente la manda al servidor (ParryPrefPacket) al entrar y cada
     *  vez que cambia. */
    private static final ModConfigSpec.EnumValue<com.hmc.zenkai.feature.combat.ParryTrigger> PARRY_TRIGGER =
            defineEnum("parry.trigger", "parry_trigger",
                    "How you trigger the parry. KEY: only the dedicated parry key (default). BLOCK: pressing "
                            + "the guard button opens the parry window and the real guard starts a moment later "
                            + "(tap = parry, hold = block). BOTH: either",
                    com.hmc.zenkai.feature.combat.ParryTrigger.KEY,
                    com.hmc.zenkai.feature.combat.ParryTrigger.class);

    /** Orientación de la barra del choque de haces: 0 horizontal (bajo la mirilla), 1 vertical (a
     *  su derecha). Solo cambia el giro con que ClashBarOverlay dibuja las MISMAS piezas, no la
     *  lógica. La clave se renombró (antes `bar_style`, 0 = vertical, con una diagonal que ya no
     *  existe) para que un toml viejo no cambie de orientación sin que nadie lo note: vuelve al
     *  valor por defecto en vez de reinterpretar su 0. */
    private static final ModConfigSpec.IntValue CLASH_BAR_STYLE =
            defineInt("clash.bar_orientation", "clash_bar_orientation",
                    "Orientation of the beam-clash timing bar: 0 = horizontal, 1 = vertical",
                    0, 0, 1, 1);

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

    /** Resplandor (bloom) del AURA — mismo pipeline que el del ki (KiVfxBloomPipeline) y se apaga
     *  solo con shaderpack, pero desde el 2026-09-30 NO depende de ki.bloom_enabled (cada tarea
     *  de la cola respeta su propio interruptor, ver KiVfxFrameQueue.replay). Separado porque
     *  un aura grande cuesta más (se redibuja parte de su geometría) y puede querer apagarse sin
     *  perder el del ki. */
    private static final ModConfigSpec.BooleanValue AURA_BLOOM_ENABLED =
            defineBool("aura.bloom_enabled", "aura_bloom_enabled",
                    "Screen-space glow around auras (core, sparks, lightning). Uses the same bloom pass as "
                            + "ki techniques but has its own switch. Disabled with a shader pack",
                    true);

    // ── Cara y cuerpo (2026-11-01) ──
    private static final ModConfigSpec.BooleanValue FACE_EXPRESSIONS =
            defineBool("face.expressions", "face_expressions",
                    "Facial expressions: blinking, pain, tiredness, sleeping, dying and ki charging", true);
    private static final ModConfigSpec.IntValue FACE_PAIN_THRESHOLD =
            defineInt("face.pain_threshold_percent", "face_pain_threshold",
                    "A single hit makes a body show the pain expression when it takes at least this percent of its "
                            + "max health. 0 = any damage",
                    15, 0, 100, 5);
    private static final ModConfigSpec.BooleanValue FACE_EMISSIVE =
            defineBool("face.emissive", "face_emissive",
                    "Glowing eyes on forms that declare them (Ultra Instinct, gods...): drawn at full brightness", true);
    private static final ModConfigSpec.BooleanValue BLOOD_EFFECTS =
            defineBool("face.blood_effects", "blood_effects",
                    "Blood stains on the body as health drops (client only). Off by default: turn it on to see blood", false);

    /** Qué aura ve el jugador (ver AuraSystemDispatcher): la NUEVA compuesta por familias
     *  (client/aura/compose, también con shaderpack) o la CLÁSICA (faldones legacy, más ligera).
     *  Solo dos opciones a propósito (pedido del usuario, 2026-09-30): si la nueva da lag en un
     *  equipo, se cambia a la clásica y ya está. Los modos de comparación viven solo en
     *  /zenkaiaura system. Una config vieja con AUTO/FORCE_NEW/... no es un valor válido del enum
     *  nuevo y NeoForge la corrige al valor por defecto (NEW). Los valores se traducen con
     *  config.zenkai.value.new/legacy. */
    private static final ModConfigSpec.EnumValue<AuraEngine> AURA_SYSTEM =
            defineEnum("aura.system", "aura_system",
                    "Which aura the game draws. NEW: the composed aura (each form has its own visual family). "
                            + "LEGACY: the classic aura, lighter; use it if the new one costs you too many FPS",
                    AuraEngine.NEW, AuraEngine.class);

    /** Solo afecta al aura CLÁSICA (AuraRenderer). FULL = comportamiento de siempre: el detalle
     *  baja solo con la distancia. Ver AuraClassicQuality. */
    private static final ModConfigSpec.EnumValue<AuraClassicQuality> AURA_CLASSIC_QUALITY =
            defineEnum("aura.classic_quality", "aura_classic_quality",
                    "Detail of the CLASSIC aura (only when aura.system = LEGACY). FULL: detail drops "
                            + "only with distance. MEDIUM/LOW/MINIMAL: lighter even up close, for more FPS",
                    AuraClassicQuality.FULL, AuraClassicQuality.class);

    /** true = comportamiento de siempre: ves tu propio cuerpo animado al cargar/disparar una
     *  técnica de ki en primera persona. false = manos vanilla en 1ª persona (los demás te siguen
     *  viendo animado). Ver ZenkaiPalAnimations.applyKiFirstPersonMode. */
    private static final ModConfigSpec.BooleanValue KI_FP_ANIMATIONS =
            defineBool("ki.first_person_animations", "ki_first_person_animations",
                    "Show your own ki attack animations (charge/release poses) in first person. "
                            + "When off you see vanilla hands; other players still see the animation",
                    true);

    /** Skill tree de transformaciones (F6): apaga la deriva de los fondos, el titileo de estrellas
     *  y el pulso/destellos de los marcos de las formas poderosas. Para quien se maree o quiera
     *  ahorrar FPS; lo estático se ve igual. */
    private static final ModConfigSpec.BooleanValue SKILL_TREE_REDUCED_FX =
            defineBool("skill_tree.reduced_effects", "skill_tree_reduced_effects",
                    "Transformation tree: turn off the animated backgrounds, twinkling stars and the "
                            + "pulsing frames of powerful forms",
                    false);

    /** Tutoriales de pantalla ya vistos (TutorialOverlay): se enseñan UNA vez por instalación; el
     *  botón «?» los repite. Oculta: se escribe sola. */
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SEEN_TUTORIALS =
            BUILDER.comment("Screen tutorials already shown (the ? button replays them). Clear to see them again")
                    .defineListAllowEmpty("ui.seen_tutorials", List.of(), () -> "", o -> o instanceof String);

    /** Nodos secretos del árbol cuyo REVELADO ya se animó (F3): la animación sale una sola vez. */
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SEEN_TREE_REVEALS =
            BUILDER.comment("Secret transformation-tree nodes whose reveal animation was already played")
                    .defineListAllowEmpty("skill_tree.seen_reveals", List.of(), () -> "", o -> o instanceof String);

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
                    60, 20, 150, 5);

    /** Qué HUD de Body/Stamina/Ki se dibuja (ver HudBarsStyle). MODERN por defecto a petición
     *  del usuario (2026-10-09); MINIMALIST conserva exactamente las barras de antes. El tamaño
     *  (hud.bars_scale) vale para los dos. */
    private static final ModConfigSpec.EnumValue<HudBarsStyle> HUD_BARS_STYLE =
            defineEnum("hud.bars_style", "hud_bars_style",
                    "Style of the Body/Stamina/Ki HUD. MODERN: pixel-art frame tinted by your form, portrait, "
                            + "damage trail and ki segments. MINIMALIST: the simple bars",
                    HudBarsStyle.MODERN, HudBarsStyle.class);

    /** Texto dentro de las barras: "cur / max" o porcentaje. Vale para los dos estilos (ver
     *  ClientZenkaiHooks.barValueText). */
    private static final ModConfigSpec.EnumValue<HudValueFormat> HUD_BARS_VALUES =
            defineEnum("hud.bars_values", "hud_bars_values",
                    "What the Body/Stamina/Ki bars show inside. NUMBERS: current / max. PERCENT: a percentage",
                    HudValueFormat.NUMBERS, HudValueFormat.class);

    /** Color del marco del HUD moderno en forma base (y siempre, si hud.frame_follows_form está
     *  apagado). Negro por defecto, a petición del usuario (2026-10-09). */
    private static final ModConfigSpec.IntValue HUD_FRAME_COLOR =
            defineColor("hud.frame_color", "hud_frame_color",
                    "Frame colour of the Modern HUD in base form, as 0xRRGGBB (default black)",
                    0x000000);

    /** true = el marco toma el color de la transformación (y va cambiando mientras te
     *  transformas); false = siempre hud.frame_color. Los avisos (vida crítica, strain, stamina
     *  agotada) se ven en los dos casos: son avisos, no decoración. */
    private static final ModConfigSpec.BooleanValue HUD_FRAME_FOLLOWS_FORM =
            defineBool("hud.frame_follows_form", "hud_frame_follows_form",
                    "The Modern HUD frame takes the colour of your transformation. Off: it always uses "
                            + "hud.frame_color",
                    true);

    // ── Lock-on (solo presentación y comodidad de entrada; el balance vive en ServerConfig) ──

    private static final ModConfigSpec.BooleanValue LOCK_ON_HOLD =
            defineBool("lock_on.hold", "lock_on_hold",
                    "Lock-on key mode. Off: press once to lock, press again to release. "
                            + "On: the target is locked only while you hold the key",
                    false);

    private static final ModConfigSpec.BooleanValue LOCK_ON_MOUSE_WHEEL =
            defineBool("lock_on.mouse_wheel", "lock_on_mouse_wheel",
                    "While locked on, the mouse wheel switches to the next/previous target "
                            + "instead of scrolling the hotbar",
                    false);

    private static final ModConfigSpec.IntValue LOCK_ON_SMOOTHING =
            defineInt("lock_on.smoothing", "lock_on_smoothing",
                    "How softly the camera follows the locked target, in percent. 0 = snappy, "
                            + "100 = very floaty. Never moves the camera when there is nothing to follow",
                    30, 0, 100, 10);

    private static final ModConfigSpec.IntValue LOCK_ON_MARKER_OPACITY =
            defineInt("lock_on.marker_opacity", "lock_on_marker_opacity",
                    "Opacity, in percent, of the ring drawn over the locked target and of the "
                            + "off-screen arrow. 0 hides the marker completely",
                    75, 0, 100, 5);

    // ── Ki Sense: números de daño e icono de jefe (presentación) ──

    private static final ModConfigSpec.BooleanValue SENSE_DAMAGE_NUMBERS =
            defineBool("sense_ki.damage_numbers", "sense_damage_numbers",
                    "Show floating damage numbers over the enemies you hit. Only players who own the "
                            + "Ki Sense skill receive them, whether the sense is switched on or not",
                    true);

    private static final ModConfigSpec.IntValue SENSE_OPACITY =
            defineInt("sense_ki.opacity", "sense_opacity",
                    "Opacity, in percent, of everything the Ki Sense draws in the world (cores, waves, "
                            + "rings and bars). Lower it if it covers too much, raise it if it gets lost",
                    100, 20, 100, 5);

    private static final ModConfigSpec.BooleanValue BOSS_ICON =
            defineBool("sense_ki.boss_icon", "boss_icon",
                    "Draw a small demon icon over bosses (entities in the zenkai:bosses tag), "
                            + "so you know at a glance that they cannot be grabbed or stunned",
                    true);

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
                    "Horizontal offset from the anchor, in GUI pixels", 0, -4096, 4096);

    private static final ModConfigSpec.IntValue HUD_OFFSET_Y =
            defineHiddenInt("hud.technique_offset_y",
                    "Vertical offset from the anchor, in GUI pixels", 0, -4096, 4096);

    // ── HUD de la party (izquierda de la pantalla) ──

    private static final ModConfigSpec.BooleanValue PARTY_HUD_ENABLED =
            defineBool("party_hud.enabled", "party_hud_enabled",
                    "Show the party members you picked (up to 4) on the HUD: face, health, stamina and ki bars",
                    true);

    // Como el HUD de técnicas: la colocación se decide arrastrando en HudPlacementScreen y se
    // guarda de una vez, así que ninguna de estas tres es una fila de la lista.
    private static final ModConfigSpec.EnumValue<HudAnchor> PARTY_HUD_ANCHOR =
            defineHiddenEnum("party_hud.anchor",
                    "Screen corner or edge the party panel hangs from", HudAnchor.MIDDLE_LEFT);
    private static final ModConfigSpec.IntValue PARTY_HUD_OFFSET_X =
            defineHiddenInt("party_hud.offset_x",
                    "Horizontal offset from the anchor, in GUI pixels", 0, -4096, 4096);
    private static final ModConfigSpec.IntValue PARTY_HUD_OFFSET_Y =
            defineHiddenInt("party_hud.offset_y",
                    "Vertical offset from the anchor, in GUI pixels", -77, -4096, 4096);

    // Panel de lectura del Ki Sense (nivel 5): misma mecánica de colocación que el panel de party.
    // Por defecto, pegado a la izquierda bajo el panel de party (que sube 77 px del centro).
    private static final ModConfigSpec.EnumValue<HudAnchor> READING_HUD_ANCHOR =
            defineHiddenEnum("sense_ki.reading_hud.anchor",
                    "Screen corner or edge the Ki Sense reading panel hangs from", HudAnchor.MIDDLE_LEFT);
    private static final ModConfigSpec.IntValue READING_HUD_OFFSET_X =
            defineHiddenInt("sense_ki.reading_hud.offset_x",
                    "Horizontal offset from the anchor, in GUI pixels", 0, -4096, 4096);
    private static final ModConfigSpec.IntValue READING_HUD_OFFSET_Y =
            defineHiddenInt("sense_ki.reading_hud.offset_y",
                    "Vertical offset from the anchor, in GUI pixels", 27, -4096, 4096);

    /** UUID de los miembros elegidos para el panel, separados por comas. Vacío = nunca se ha
     *  elegido (el panel completa solo con los primeros miembros). */
    private static final ModConfigSpec.ConfigValue<String> PARTY_HUD_SELECTED =
            BUILDER.comment("Party members shown on the party HUD, comma-separated UUIDs (max 4). Edited from the Party screen")
                    .define("party_hud.selected", "");

    // El último color elegido en el banco de scouter. Oculto: no es una opción que se toque
    // en una lista, es memoria de lo que hiciste la última vez. Y va en config de cliente y
    // no en el banco porque lo que el jugador quiere recordar es SU color, no el del bloque.
    private static final ModConfigSpec.IntValue SCOUTER_TINT =
            defineHiddenInt("scouter.last_tint",
                    "Last colour picked in the scouter bench, as 0xRRGGBB",
                    0xd82624, 0x000000, 0xFFFFFF);

    // ── Teclas de carril de Meditation (flechas: izquierda, abajo, arriba, derecha) ────────────────────────────────

    /** Ocultas de ClientConfigScreen: se editan desde un popup propio dentro de
     *  MeditationScreen (engranaje, icons.png 80,60), no de la lista genérica de opciones —
     *  pedido explícito del usuario ("no quiero que sea por keybinding de minecraft como tal").
     *  Rango 0..348 = GLFW_KEY_SPACE..GLFW_KEY_LAST, generoso a propósito: MeditationScreen ya
     *  valida que las 4 no se pisen entre sí antes de guardar, así que aquí solo hace falta un
     *  código de tecla GLFW válido, no una lista concreta. */
    private static final ModConfigSpec.IntValue MEDITATION_LANE_KEY_0 =
            defineHiddenInt("meditation.lane_key_0",
                    "GLFW key code for lane 1 (leftmost) of the Meditation rhythm minigame",
                    GLFW.GLFW_KEY_LEFT, 0, GLFW.GLFW_KEY_LAST);
    private static final ModConfigSpec.IntValue MEDITATION_LANE_KEY_1 =
            defineHiddenInt("meditation.lane_key_1",
                    "GLFW key code for lane 2 of the Meditation rhythm minigame",
                    GLFW.GLFW_KEY_DOWN, 0, GLFW.GLFW_KEY_LAST);
    private static final ModConfigSpec.IntValue MEDITATION_LANE_KEY_2 =
            defineHiddenInt("meditation.lane_key_2",
                    "GLFW key code for lane 3 of the Meditation rhythm minigame",
                    GLFW.GLFW_KEY_UP, 0, GLFW.GLFW_KEY_LAST);
    private static final ModConfigSpec.IntValue MEDITATION_LANE_KEY_3 =
            defineHiddenInt("meditation.lane_key_3",
                    "GLFW key code for lane 4 (rightmost) of the Meditation rhythm minigame",
                    GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_KEY_LAST);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── Lectura ──────────────────────────────────────────────────────────────

    public static boolean kiFistAura() { return KI_FIST_AURA.get(); }
    public static boolean kiInfuseGlow() { return KI_INFUSE_GLOW.get(); }
    public static boolean swordTechniqueFx() { return SWORD_TECH_FX.get(); }
    public static boolean kiSenseCameraShake() { return KI_SENSE_CAMERA_SHAKE.get(); }
    public static com.hmc.zenkai.client.aura.compose.LongTransformMode longTransformMode() { return LONG_TRANSFORM_MODE.get(); }

    public static boolean alwaysLongTransform() { return ALWAYS_LONG_TRANSFORM.get(); }

    public static void setAlwaysLongTransform(boolean on) {
        ALWAYS_LONG_TRANSFORM.set(on);
        SPEC.save();
    }

    public static boolean wheelFormHidden(String formId) { return HIDDEN_WHEEL_FORMS.get().contains(formId); }

    /** @return true si la forma queda OCULTA tras la llamada. */
    public static boolean toggleWheelFormHidden(String formId) {
        List<String> l = new ArrayList<>(HIDDEN_WHEEL_FORMS.get());
        boolean hidden = !l.remove(formId);
        if (hidden) l.add(formId);
        HIDDEN_WHEEL_FORMS.set(l);
        SPEC.save();
        return hidden;
    }
    public static boolean flightFxDust() { return FLIGHT_FX_DUST.get(); }
    public static boolean flightFxSonicBoom() { return FLIGHT_FX_SONIC_BOOM.get(); }
    public static boolean flightFxLanding() { return FLIGHT_FX_LANDING.get(); }
    public static boolean flightFxSpeedLines() { return FLIGHT_FX_SPEED_LINES.get(); }
    public static boolean lockOnHold() { return LOCK_ON_HOLD.get(); }
    public static boolean lockOnMouseWheel() { return LOCK_ON_MOUSE_WHEEL.get(); }
    /** 0..1, ya normalizado. */
    public static float lockOnSmoothing() { return LOCK_ON_SMOOTHING.get() / 100f; }
    /** 0f..1f, listo para multiplicar sobre un alpha. */
    public static float lockOnMarkerOpacity() { return LOCK_ON_MARKER_OPACITY.get() / 100f; }
    public static boolean senseDamageNumbers() { return SENSE_DAMAGE_NUMBERS.get(); }
    public static boolean bossIcon() { return BOSS_ICON.get(); }
    /** 0.2f..1f, listo para multiplicar sobre un alpha. */
    public static float senseOpacity() { return SENSE_OPACITY.get() / 100f; }

    /** Fracción 0f..1f, lista para multiplicar directamente sobre un alpha. */
    public static float auraFirstPersonOpacityFrac() { return AURA_FP_OPACITY.get() / 100f; }
    /** 1f = crecimiento por poder de siempre; hasta 3f. */
    public static float auraPowerGrowthFactor() { return AURA_POWER_GROWTH.get() / 100f; }
    public static int clashBarStyle() { return CLASH_BAR_STYLE.get(); }
    public static com.hmc.zenkai.feature.combat.ParryTrigger parryTrigger() { return PARRY_TRIGGER.get(); }
    public static float kiFirstPersonOpacityFrac() { return KI_FP_OPACITY.get() / 100f; }
    public static boolean kiBloomEnabled() { return KI_BLOOM_ENABLED.get(); }
    public static boolean kiFirstPersonAnimations() { return KI_FP_ANIMATIONS.get(); }
    public static boolean auraBloomEnabled() { return AURA_BLOOM_ENABLED.get(); }
    public static boolean faceExpressions() { return FACE_EXPRESSIONS.get(); }
    public static boolean faceEmissive()    { return FACE_EMISSIVE.get(); }
    public static int facePainThresholdPercent() { return FACE_PAIN_THRESHOLD.get(); }
    public static boolean bloodEffects()    { return BLOOD_EFFECTS.get(); }
    public static AuraEngine auraEngine() { return AURA_SYSTEM.get(); }
    public static AuraClassicQuality auraClassicQuality() { return AURA_CLASSIC_QUALITY.get(); }
    public static float hudBarsScaleFrac() { return HUD_BARS_SCALE.get() / 100f; }
    public static HudBarsStyle hudBarsStyle() { return HUD_BARS_STYLE.get(); }
    public static HudValueFormat hudBarsValues() { return HUD_BARS_VALUES.get(); }
    public static int hudFrameColor() { return HUD_FRAME_COLOR.get() & 0xFFFFFF; }
    public static boolean hudFrameFollowsForm() { return HUD_FRAME_FOLLOWS_FORM.get(); }

    public static HudAnchor hudAnchor() { return HUD_ANCHOR.get(); }
    public static HudOrientation hudOrientation() { return HUD_ORIENTATION.get(); }
    public static boolean hudAvoidHotbar() { return HUD_AVOID_HOTBAR.get(); }
    public static int hudOffsetX() { return HUD_OFFSET_X.get(); }
    public static int hudOffsetY() { return HUD_OFFSET_Y.get(); }
    public static int scouterLastTint() { return SCOUTER_TINT.get(); }

    public static boolean skillTreeReducedFx() { return SKILL_TREE_REDUCED_FX.get(); }

    public static boolean tutorialSeen(String id) { return SEEN_TUTORIALS.get().contains(id); }

    public static void markTutorialSeen(String id) {
        if (tutorialSeen(id)) return;
        List<String> l = new ArrayList<>(SEEN_TUTORIALS.get());
        l.add(id);
        SEEN_TUTORIALS.set(l);
        SPEC.save();
    }

    /** Olvida tutoriales y revelados vistos. Solo la herramienta de capturas SkillTreeShots. */
    public static void resetSeenForDev() {
        SEEN_TUTORIALS.set(new ArrayList<>());
        SEEN_TREE_REVEALS.set(new ArrayList<>());
        SPEC.save();
    }

    public static boolean treeRevealSeen(String nodeId) { return SEEN_TREE_REVEALS.get().contains(nodeId); }

    public static void markTreeRevealSeen(String nodeId) {
        if (treeRevealSeen(nodeId)) return;
        List<String> l = new ArrayList<>(SEEN_TREE_REVEALS.get());
        l.add(nodeId);
        SEEN_TREE_REVEALS.set(l);
        SPEC.save();
    }

    public static void setScouterLastTint(int rgb) {
        SCOUTER_TINT.set(rgb & 0xFFFFFF);
        SPEC.save();
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
        SPEC.save();
    }

    public static HudAnchor readingHudAnchor() { return READING_HUD_ANCHOR.get(); }
    public static int readingHudOffsetX() { return READING_HUD_OFFSET_X.get(); }
    public static int readingHudOffsetY() { return READING_HUD_OFFSET_Y.get(); }

    /** Guarda la colocación completa del panel de lectura del Ki Sense de una vez. */
    public static void setReadingHudPlacement(HudAnchor anchor, int offsetX, int offsetY) {
        READING_HUD_ANCHOR.set(anchor);
        READING_HUD_OFFSET_X.set(offsetX);
        READING_HUD_OFFSET_Y.set(offsetY);
        SPEC.save();
    }

    public static boolean partyHudEnabled() { return PARTY_HUD_ENABLED.get(); }
    public static HudAnchor partyHudAnchor() { return PARTY_HUD_ANCHOR.get(); }
    public static int partyHudOffsetX() { return PARTY_HUD_OFFSET_X.get(); }
    public static int partyHudOffsetY() { return PARTY_HUD_OFFSET_Y.get(); }

    /** Guarda la colocación completa del panel de party de una vez (misma razón que el HUD de técnicas). */
    public static void setPartyHudPlacement(HudAnchor anchor, int offsetX, int offsetY) {
        PARTY_HUD_ANCHOR.set(anchor);
        PARTY_HUD_OFFSET_X.set(offsetX);
        PARTY_HUD_OFFSET_Y.set(offsetY);
        SPEC.save();
    }

    /** Miembros elegidos para el panel, en el orden elegido; vacía si nunca se ha elegido. */
    public static List<java.util.UUID> partyHudSelected() {
        List<java.util.UUID> out = new ArrayList<>();
        for (String s : PARTY_HUD_SELECTED.get().split(",")) {
            try {
                if (!s.isBlank()) out.add(java.util.UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) {
                // Un toml editado a mano con basura: se salta esa entrada.
            }
        }
        return out;
    }

    public static void setPartyHudSelected(List<java.util.UUID> ids) {
        StringBuilder sb = new StringBuilder();
        for (java.util.UUID id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        PARTY_HUD_SELECTED.set(sb.toString());
        SPEC.save();
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
        SPEC.save();
    }
}