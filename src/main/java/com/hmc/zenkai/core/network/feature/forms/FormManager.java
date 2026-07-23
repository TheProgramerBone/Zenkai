package com.hmc.zenkai.core.network.feature.forms;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.Race;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carga las transformaciones desde datapack y las sincroniza al cliente (login y /reload).
 * Espeja SkillManager y TechniqueManager.
 *
 * Ruta: data/&lt;ns&gt;/zenkai_forms/&lt;id&gt;.json  -> el id de la forma es &lt;ns&gt;:&lt;id&gt;.
 * La forma BASE no necesita archivo: es el estado neutro (0% y sin drenaje).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class FormManager {
    private FormManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Forms");
    private static final String FOLDER = "zenkai_forms";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        FormSyncPacket pkt = new FormSyncPacket(List.copyOf(FormDef.all()));
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static EnumSet<Race> readRaces(JsonObject o) {
        EnumSet<Race> out = EnumSet.noneOf(Race.class);
        if (!o.has("races")) return EnumSet.allOf(Race.class); // sin lista = todas
        JsonArray arr = GsonHelper.getAsJsonArray(o, "races");
        for (var el : arr) {
            String s = el.getAsString().toUpperCase(Locale.ROOT);
            try {
                out.add(Race.valueOf(s));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[Zenkai] Raza desconocida en una forma: {}", s);
            }
        }
        return out;
    }

    /** Mapa clave -> id de item. Ausente = mapa vacío (la forma no cambia ese visual). */
    private static Map<String, ResourceLocation> readItemMap(JsonObject o, String key) {
        if (!o.has(key)) return Map.of();
        Map<String, ResourceLocation> out = new LinkedHashMap<>();
        JsonObject obj = GsonHelper.getAsJsonObject(o, key);
        for (String k : obj.keySet()) {
            String v = obj.get(k).getAsString();
            if (v == null || v.isBlank()) continue;
            out.put(k.toLowerCase(Locale.ROOT), ResourceLocation.parse(v));
        }
        return out;
    }

    /** Acepta 0xFFE55C, #FFE55C o un entero. */
    private static int readRgb(JsonObject o, String key, int fallback) {
        if (!o.has(key)) return fallback;
        var el = o.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return Integer.decode(el.getAsString().trim().replace("#", "0x")) & 0xFFFFFF;
        }
        return el.getAsInt() & 0xFFFFFF;
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, FormDef>> {

        @Override
        protected @NotNull Map<ResourceLocation, FormDef> prepare(@NotNull ResourceManager rm,
                                                                  @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, FormDef> out = new LinkedHashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String name = file.getPath().substring(FOLDER.length() + 1,
                        file.getPath().length() - ".json".length());
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);

                if (FormIds.BASE.equals(id)) {
                    LOGGER.warn("[Zenkai] 'base' no se define en datapack (es el estado neutro): ignorada.");
                    continue;
                }

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();

                    String parentStr = GsonHelper.getAsString(o, "parent", "");
                    ResourceLocation parent = parentStr.isBlank()
                            ? null : ResourceLocation.parse(parentStr);

                    out.put(id, new FormDef(id,
                            readRaces(o),
                            FormDef.Kind.byName(GsonHelper.getAsString(o, "kind", "super")),
                            parent,
                            GsonHelper.getAsInt(o, "tp_cost", 0),
                            Math.max(0, GsonHelper.getAsInt(o, "hold_ticks", 100)),
                            GsonHelper.getAsDouble(o, "mastery_gain", 1.0),
                            GsonHelper.getAsDouble(o, "stat_percent_untrained", 0.0),
                            GsonHelper.getAsDouble(o, "stat_percent_mastered", 0.0),
                            GsonHelper.getAsDouble(o, "ki_drain_untrained", 0.0),
                            GsonHelper.getAsDouble(o, "ki_drain_mastered", 0.0),
                            readItemMap(o, "hair_items"),
                            readItemMap(o, "body_items"),
                            GsonHelper.getAsString(o, "aura_type", "default"),
                            readRgb(o, "aura_rgb", 0xFFFFFF),
                            readRgb(o, "hair_rgb", -1),
                            GsonHelper.getAsDouble(o, "scale", 1.0)));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer la forma en {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, FormDef> defs, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            // Un parent que no existe rompería la cadena en silencio: mejor avisar.
            for (FormDef d : defs.values()) {
                if (d.parent() != null && !defs.containsKey(d.parent())) {
                    LOGGER.error("[Zenkai] La forma '{}' apunta a un parent inexistente: {}",
                            d.id(), d.parent());
                }
            }
            FormDef.replaceAll(defs);
            FormRegistry.rebuild();
            LOGGER.info("[Zenkai] Formas cargadas: {}.", defs.size());
        }
    }
}