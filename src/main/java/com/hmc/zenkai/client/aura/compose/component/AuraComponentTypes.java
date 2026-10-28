package com.hmc.zenkai.client.aura.compose.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Registro de TIPOS de componente: el valor de {@code "type"} en la lista {@code components} de un
 * estilo JSON. Añadir un componente nuevo = una clase {@link AuraComponent}, su configuración y una
 * línea en {@link #TYPES}. Se acepta el id con o sin el prefijo {@code zenkai:}.
 */
public final class AuraComponentTypes {

    private AuraComponentTypes() {}

    private static final Map<String, Function<JsonObject, AuraComponentConfig>> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put(MoteComponent.TYPE, MoteComponent.Config::parse);
        TYPES.put(ElectricityComponent.TYPE, ElectricityComponent.Config::parse);
        TYPES.put(WispComponent.TYPE, WispComponent.Config::parse);
        TYPES.put(ShellComponent.TYPE, ShellComponent.Config::parse);
        TYPES.put(StrandComponent.TYPE, StrandComponent.Config::parse);
        TYPES.put(FlameSheetComponent.TYPE, FlameSheetComponent.Config::parse);
    }

    public static Set<String> ids() { return TYPES.keySet(); }

    /** @throws JsonParseException si el tipo no existe o sus parámetros no valen. */
    public static AuraComponentConfig parse(JsonObject json) {
        if (!json.has("type")) throw new JsonParseException("componente sin \"type\"");
        String type = strip(json.get("type").getAsString());
        Function<JsonObject, AuraComponentConfig> parser = TYPES.get(type);
        if (parser == null) {
            throw new JsonParseException("tipo de componente desconocido: " + type + " (existen: " + TYPES.keySet() + ")");
        }
        return parser.apply(json);
    }

    public static String strip(String id) {
        return id.startsWith("zenkai:") ? id.substring("zenkai:".length()) : id;
    }

    // ── Ayudas de lectura compartidas por las configuraciones ──

    public static float f(JsonObject o, String key, float def) {
        return o.has(key) ? o.get(key).getAsFloat() : def;
    }

    /** Rango {@code [min, max]} o un número suelto (min = max). */
    public static float[] range(JsonObject o, String key, float min, float max) {
        if (!o.has(key)) return new float[]{min, max};
        JsonElement e = o.get(key);
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            if (a.size() != 2) throw new JsonParseException(key + " debe ser [min, max]");
            float lo = a.get(0).getAsFloat(), hi = a.get(1).getAsFloat();
            return new float[]{Math.min(lo, hi), Math.max(lo, hi)};
        }
        float v = e.getAsFloat();
        return new float[]{v, v};
    }

    public static String s(JsonObject o, String key, String def) {
        return o.has(key) ? o.get(key).getAsString() : def;
    }

    /** Avisa (no falla) de las claves que el componente no reconoce: una errata en un JSON de
     *  estilo no debe romper el aura, pero sí verse en el log. */
    public static @Nullable String unknownKeys(JsonObject o, Set<String> known) {
        StringBuilder sb = null;
        for (String k : o.keySet()) {
            if (k.equals("type") || known.contains(k)) continue;
            if (sb == null) sb = new StringBuilder();
            else sb.append(", ");
            sb.append(k);
        }
        return sb == null ? null : sb.toString();
    }
}
