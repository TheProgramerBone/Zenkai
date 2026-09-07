package com.hmc.zenkai.client.training;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import net.minecraft.client.Minecraft;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee los charts de Meditation modo Canción de assets/zenkai/meditation_charts/&lt;discId&gt;.json
 * vía el ResourceManager del cliente (mismo mecanismo que un resourcepack normal: sobrevive a
 * /reload sin necesitar un SimplePreparableReloadListener propio, porque MeditationScreen solo
 * pide la lista al ABRIR el selector de canciones, no la mantiene cacheada entre reloads).
 *
 * Puramente client-side a propósito: estos JSON no son datos de partida (no afectan al servidor,
 * ni deben poder venir de un datapack de otro jugador) — es contenido de UI, como un idioma.
 */
public final class MeditationChartLoader {
    private MeditationChartLoader() {}

    private static final String FOLDER = "meditation_charts";

    /** Vuelve a leer del disco cada vez que se llama (barato: son 3-4 JSON pequeños, y solo se
     *  pide al abrir el selector de canciones, no por frame) — así un /reload en dev se refleja
     *  sin reiniciar el cliente, útil mientras se afinan charts a mano. */
    public static Map<String, MeditationChart> loadAll() {
        Map<String, MeditationChart> out = new HashMap<>();
        var resources = Minecraft.getInstance().getResourceManager()
                .listResources(FOLDER, loc -> loc.getNamespace().equals(Zenkai.MOD_ID)
                        && loc.getPath().endsWith(".json"));

        for (var entry : resources.entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                String discId = GsonHelper.getAsString(o, "discId");

                List<MeditationChart.MeditationNote> notes = new ArrayList<>();
                JsonArray notesArr = o.getAsJsonArray("notes");
                if (notesArr != null) {
                    for (var el : notesArr) {
                        JsonObject n = el.getAsJsonObject();
                        notes.add(new MeditationChart.MeditationNote(
                                GsonHelper.getAsInt(n, "t"), GsonHelper.getAsInt(n, "lane")));
                    }
                }

                out.put(discId, new MeditationChart(
                        discId,
                        GsonHelper.getAsInt(o, "durationMs"),
                        GsonHelper.getAsInt(o, "lanes", 4),
                        GsonHelper.getAsString(o, "difficultyHint", "medium"),
                        notes));
            } catch (Exception ex) {
                Zenkai.LOGGER.error("[Zenkai] No se pudo leer chart de Meditation {}: {}",
                        entry.getKey(), ex.toString());
            }
        }
        return out;
    }
}
