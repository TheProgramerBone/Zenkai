package com.hmc.zenkai.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Preferencias que solo afectan a lo que ve ESTE jugador.
 *
 * Separada de CommonConfig y ServerConfig a propósito: aquí no va nada que altere el
 * comportamiento del juego, solo presentación. Si una opción cambia el resultado de algo,
 * pertenece a Common o Server — si no, un cliente podría cambiarla y desincronizar.
 *
 * AUTOMATISMO DE LA PANTALLA: cada opción se declara con defineBool(), que además de crear
 * el valor lo apunta en ENTRIES. ClientConfigScreen recorre esa lista, así que una opción
 * nueva aparece sola en la GUI sin tocar la pantalla. La alternativa —hurgar en el
 * ModConfigSpec por reflexión— funcionaría hoy y se rompería en la próxima versión.
 */
public final class ClientConfig {
    private ClientConfig() {}

    /** Una opción booleana, con su clave de traducción para la pantalla. */
    public record BoolEntry(ModConfigSpec.BooleanValue value, String titleKey, String tooltipKey) {}

    private static final List<BoolEntry> ENTRIES = new ArrayList<>();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Declara una opción booleana Y la registra para la GUI.
     * Usa SIEMPRE este helper en vez de BUILDER.define(...) directamente, o la opción
     * existirá en el toml pero será invisible en la pantalla.
     */
    private static ModConfigSpec.BooleanValue defineBool(String path, String key,
                                                         String comment, boolean def) {
        ModConfigSpec.BooleanValue v = BUILDER.comment(comment).define(path, def);
        ENTRIES.add(new BoolEntry(v,
                "config.zenkai." + key,
                "config.zenkai." + key + ".desc"));
        return v;
    }

    // ── Opciones ─────────────────────────────────────────────────────────────

    private static final ModConfigSpec.BooleanValue SHOW_MODEL_CREDITS =
            defineBool("tooltips.show_model_credits", "show_model_credits",
                    "Show model and texture credits in item tooltips", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── Lectura ──────────────────────────────────────────────────────────────

    public static boolean showModelCredits() { return SHOW_MODEL_CREDITS.get(); }

    /** Lista inmutable para la pantalla. */
    public static List<BoolEntry> entries() { return Collections.unmodifiableList(ENTRIES); }
}