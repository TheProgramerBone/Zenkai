package com.hmc.zenkai.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Favoritos de la apariencia (ojos, cejas, nariz, boca, pelo): clic derecho en una casilla pone o
 * quita la estrella, y los favoritos salen siempre primero, en orden alfabético (petición del
 * usuario, 2026-10-12).
 *
 * Se guardan en la INSTALACIÓN ({@code config/zenkai/favorites.json}), no en el personaje: te
 * siguen por cualquier mundo o servidor y nadie más los necesita, así que no viajan por red.
 * Clave = "&lt;categoría&gt;|&lt;id&gt;" (p. ej. "eyes|zenkai:minimal", "hair|custom:0a1b2c3d4e5f").
 */
public final class Favorites {
    private Favorites() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Favorites");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX = 512;
    private static Set<String> keys;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("zenkai").resolve("favorites.json");
    }

    private static Set<String> keys() {
        if (keys == null) {
            keys = new LinkedHashSet<>();
            try {
                Path f = file();
                if (Files.isRegularFile(f)) {
                    JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
                    if (e.isJsonArray()) {
                        for (JsonElement k : e.getAsJsonArray()) {
                            if (keys.size() < MAX && k.isJsonPrimitive()) keys.add(k.getAsString());
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("[Zenkai] favoritos ilegibles, se empieza de cero: {}", ex.toString());
            }
        }
        return keys;
    }

    public static String key(String category, String id) {
        return category + "|" + id;
    }

    public static boolean is(String category, String id) {
        return id != null && keys().contains(key(category, id));
    }

    /** Pone o quita la estrella y guarda al momento. */
    public static void toggle(String category, String id) {
        if (id == null) return;
        String k = key(category, id);
        if (!keys().remove(k)) {
            if (keys().size() >= MAX) return;
            keys().add(k);
        }
        save();
    }

    private static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            JsonArray arr = new JsonArray();
            for (String k : keys()) arr.add(k);
            Files.writeString(f, GSON.toJson(arr), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOGGER.warn("[Zenkai] no se pudieron guardar los favoritos: {}", ex.toString());
        }
    }

    /**
     * Ordena en el sitio: primero los favoritos y después el resto, cada grupo por el nombre que
     * ve el jugador (Collator en fuerza PRIMARY: sin mayúsculas ni acentos).
     */
    public static <T> void sort(List<T> items, String category, Function<T, String> id, Function<T, String> name) {
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.ROOT);
        collator.setStrength(java.text.Collator.PRIMARY);
        items.sort(Comparator.<T, Boolean>comparing(t -> !is(category, id.apply(t)))
                .thenComparing(name, collator));
    }
}
