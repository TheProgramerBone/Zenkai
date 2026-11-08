package com.hmc.zenkai.feature.rage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Carga los despertares de Rage Forms desde datapack (solo servidor: el cliente no los necesita, el sistema es autoritativo).
 * Ruta: {@code data/<ns>/zenkai_rage_awakenings/<nombre>.json}. Un archivo roto se ignora con un aviso, no tumba el resto.
 * Formato: {@code form}, {@code race}, {@code triggers} (lista de {@link RageTrigger}), {@code min_intensity} (low por defecto),
 * {@code min_parent_mastery} (0, en %), {@code awakens} (true).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class RageAwakeningManager {
    private RageAwakeningManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-RageAwakenings");
    private static final String FOLDER = "zenkai_rage_awakenings";

    private static volatile List<RageAwakening> ALL = List.of();

    public static List<RageAwakening> all() { return ALL; }

    /** Despertares de esa raza. */
    public static List<RageAwakening> forRace(Race race) {
        List<RageAwakening> out = new ArrayList<>();
        for (RageAwakening a : ALL) if (a.race() == race) out.add(a);
        return out;
    }

    /** Solo para tests: sustituye el conjunto cargado. */
    public static void setForTest(List<RageAwakening> list) { ALL = List.copyOf(list); }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Lee un despertar; null (con aviso) si le falta algo esencial. */
    static RageAwakening parse(ResourceLocation file, JsonObject o) {
        ResourceLocation form = ResourceLocation.tryParse(GsonHelper.getAsString(o, "form", ""));
        if (form == null) { LOGGER.warn("[Zenkai] {}: 'form' ausente o inválido", file); return null; }
        Race race;
        try { race = Race.valueOf(GsonHelper.getAsString(o, "race", "").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { LOGGER.warn("[Zenkai] {}: 'race' desconocida", file); return null; }
        Set<RageTrigger> triggers = EnumSet.noneOf(RageTrigger.class);
        JsonArray arr = o.has("triggers") ? GsonHelper.getAsJsonArray(o, "triggers") : new JsonArray();
        for (JsonElement el : arr) {
            RageTrigger t = RageTrigger.parse(el.getAsString());
            if (t == null) LOGGER.warn("[Zenkai] {}: trigger desconocido '{}'", file, el.getAsString());
            else triggers.add(t);
        }
        if (triggers.isEmpty()) { LOGGER.warn("[Zenkai] {}: sin triggers válidos", file); return null; }
        return new RageAwakening(form, race, Collections.unmodifiableSet(triggers),
                RageIntensity.parse(GsonHelper.getAsString(o, "min_intensity", "low"), RageIntensity.LOW),
                GsonHelper.getAsDouble(o, "min_parent_mastery", 0.0),
                GsonHelper.getAsBoolean(o, "awakens", true));
    }

    private static final class Loader extends SimplePreparableReloadListener<List<RageAwakening>> {
        @Override
        protected @NotNull List<RageAwakening> prepare(@NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            List<RageAwakening> out = new ArrayList<>();
            for (var entry : rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json")).entrySet()) {
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    RageAwakening a = parse(entry.getKey(), JsonParser.parseReader(reader).getAsJsonObject());
                    if (a != null) out.add(a);
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer el despertar {}: {}", entry.getKey(), ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull List<RageAwakening> list, @NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            ALL = List.copyOf(list);
            LOGGER.info("[Zenkai] {} despertares de Rage Forms cargados", list.size());
        }
    }
}
