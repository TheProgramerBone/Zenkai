package com.hmc.zenkai.feature.forms;

import com.google.gson.JsonArray;
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
    /**
     * Formas baneadas (data/zenkai/banned_forms.json): no se cargan. Sirve para tener definida una
     * forma (JSON, ítems) mientras su modelo no existe, sin que nadie pueda usarla; quien la quiera
     * borra su línea. Gana el archivo del pack de mayor prioridad, así un datapack puede
     * desbanear todo con {"banned": []} sin tocar el mod.
     */
    private static final ResourceLocation BANNED_FILE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "banned_forms.json");

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
        if (!o.has("races")) return EnumSet.allOf(Race.class); // sin lista = cualquiera
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

    /** Ids de formas baneadas. Archivo ausente o roto = ninguna (nunca bloquea la carga). */
    private static java.util.Set<ResourceLocation> readBanned(ResourceManager rm) {
        java.util.Set<ResourceLocation> out = new java.util.HashSet<>();
        var res = rm.getResource(BANNED_FILE);
        if (res.isEmpty()) return out;
        try (BufferedReader reader = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            if (o.has("banned")) {
                for (var el : GsonHelper.getAsJsonArray(o, "banned")) {
                    try {
                        out.add(ResourceLocation.parse(el.getAsString().trim()));
                    } catch (Exception bad) {
                        LOGGER.warn("[Zenkai] Id inválido en banned_forms.json: {}", el);
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.error("[Zenkai] No se pudo leer banned_forms.json (se ignora): {}", ex.toString());
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

    /** "mastery_reqs": {"zenkai:fifth_form": 100, ...}. Ausente = sin requisitos. */
    private static Map<ResourceLocation, Double> readMasteryReqs(JsonObject o) {
        if (!o.has("mastery_reqs")) return Map.of();
        Map<ResourceLocation, Double> out = new LinkedHashMap<>();
        JsonObject obj = GsonHelper.getAsJsonObject(o, "mastery_reqs");
        for (String k : obj.keySet()) out.put(ResourceLocation.parse(k), obj.get(k).getAsDouble());
        return out;
    }

    /** "layer_colors": {"0": "0xD9A21B", "2": "#F2C230"} (índice de capa -> RGB). Ausente = ninguna. */
    private static Map<Integer, Integer> readLayerColors(JsonObject o) {
        if (!o.has("layer_colors")) return Map.of();
        Map<Integer, Integer> out = new LinkedHashMap<>();
        JsonObject obj = GsonHelper.getAsJsonObject(o, "layer_colors");
        for (String k : obj.keySet()) {
            try {
                var el = obj.get(k);
                int rgb = el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()
                        ? Integer.decode(el.getAsString().trim().replace("#", "0x")) : el.getAsInt();
                out.put(Integer.parseInt(k), rgb & 0xFFFFFF);
            } catch (Exception bad) {
                LOGGER.warn("[Zenkai] layer_colors: entrada inválida '{}' ignorada", k);
            }
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
            java.util.Set<ResourceLocation> banned = readBanned(rm);

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                // <categoría opcional>/<id>.json: la categoría (0.3.0) es la subcarpeta, si la
                // hay — puramente organización de archivo + agrupación en la rueda, nunca parte
                // del id lógico de la forma (un addon sin subcarpeta sigue funcionando igual,
                // categoría "" = sin agrupar).
                String rel = file.getPath().substring(FOLDER.length() + 1,
                        file.getPath().length() - ".json".length());
                int slash = rel.lastIndexOf('/');
                String category = slash >= 0 ? rel.substring(0, slash) : "";
                String name = slash >= 0 ? rel.substring(slash + 1) : rel;
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);

                if (FormIds.BASE.equals(id)) {
                    LOGGER.warn("[Zenkai] 'base' no se define en datapack (es el estado neutro): ignorada.");
                    continue;
                }
                if (banned.contains(id)) {
                    LOGGER.info("[Zenkai] Forma baneada (banned_forms.json), no se carga: {}", id);
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
                            Math.max(0, GsonHelper.getAsInt(o, "spi_req", 0)),
                            readItemMap(o, "hair_items"),
                            readItemMap(o, "body_items"),
                            GsonHelper.getAsString(o, "aura_type", "default"),
                            readRgb(o, "aura_rgb", 0xFFFFFF),
                            readRgb(o, "hair_rgb", -1),
                            readRgb(o, "tail_rgb", -1),
                            GsonHelper.getAsDouble(o, "scale", 1.0),
                            GsonHelper.getAsBoolean(o, "descendable", false),
                            GsonHelper.getAsDouble(o, "overdrive_ceiling_bonus", 0.0),
                            GsonHelper.getAsDouble(o, "overdrive_drain_mult_untrained", 1.0),
                            GsonHelper.getAsDouble(o, "overdrive_drain_mult_mastered", 1.0),
                            GsonHelper.getAsBoolean(o, "wheel_selectable", true),
                            GsonHelper.getAsBoolean(o, "divine_tier", false),
                            readRgb(o, "aura_outer_rgb", -1),
                            readRgb(o, "eye_rgb", -1),
                            readRgb(o, "brow_rgb", -1),
                            FormDef.Tier.byName(GsonHelper.getAsString(o, "tier", "none")),
                            GsonHelper.getAsBoolean(o, "endgame_path", false),
                            FormDef.AlignBranch.byName(GsonHelper.getAsString(o, "alignment_branch", "none")),
                            readMasteryReqs(o),
                            category,
                            readLayerColors(o),
                            readRgb(o, "fur_rgb", -1),
                            GsonHelper.getAsBoolean(o, "no_brows", false),
                            GsonHelper.getAsString(o, "icon_default", ""),
                            GsonHelper.getAsString(o, "hair_style", ""),
                            longScenePose(o),
                            GsonHelper.getAsString(o, "transform_face", "").trim()));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer la forma en {}: {}", file, ex.toString());
                }
            }
            // Banear una forma banea también todo lo que cuelga de ella (su parent ya no existe):
            // sin esto quedaría una rama huérfana y un error de "parent inexistente" por cada una.
            boolean removed;
            do {
                removed = false;
                for (var it = out.values().iterator(); it.hasNext(); ) {
                    FormDef d = it.next();
                    if (d.parent() != null && banned.contains(d.parent())) {
                        LOGGER.info("[Zenkai] Forma {} descartada: su parent {} está baneado.", d.id(), d.parent());
                        banned.add(d.id());
                        it.remove();
                        removed = true;
                    }
                }
            } while (removed);
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

    /** {@code "long_scene": {"pose": "serene"}} → "serene"; sin bloque, vacío (ver FormDef#hasLongScenePose). */
    private static String longScenePose(JsonObject o) {
        if (!o.has("long_scene") || !o.get("long_scene").isJsonObject()) return "";
        return GsonHelper.getAsString(o.getAsJsonObject("long_scene"), "pose", "").trim();
    }
}