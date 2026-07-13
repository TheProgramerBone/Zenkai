package com.hmc.zenkai.core.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.ZenkaiAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga los planos de stats de entidad desde datapack (data/&lt;ns&gt;/zenkai_entities/*.json)
 * y los guarda por EntityType id. Se auto-registra como reload listener (recarga con /reload).
 * FASE 1: solo carga y almacena. Nadie los consume aún (eso es Fase 2: spawn + attachment).
 * Acceso: {@link #get(ResourceLocation)} con el id del EntityType (p. ej. zenkai:saibaman).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class EntityStatsManager {
    private EntityStatsManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-EntityStats");
    private static final String FOLDER = "zenkai_entities";

    /** entityType id -> definición. Inmutable tras cada recarga. */
    private static volatile Map<ResourceLocation, EntityStatDef> DEFS = Map.of();

    public static EntityStatDef get(ResourceLocation entityTypeId) {
        return DEFS.get(entityTypeId);
    }

    public static boolean has(ResourceLocation entityTypeId) {
        return DEFS.containsKey(entityTypeId);
    }

    public static Map<ResourceLocation, EntityStatDef> all() {
        return DEFS;
    }

    // ── Registro del reload listener (game bus) ──────────────────────────────
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    // ── Listener: prepara (off-thread) y aplica (main thread) ────────────────
    private static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, EntityStatDef>> {

        @Override
        protected @NotNull Map<ResourceLocation, EntityStatDef> prepare(ResourceManager rm, @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, EntityStatDef> out = new HashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                Resource resource = entry.getValue();
                try (BufferedReader reader = resource.openAsReader()) {
                    JsonElement root = JsonParser.parseReader(reader);
                    EntityStatDef def = parse(root.getAsJsonObject());
                    out.put(def.entity(), def);
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer stats de entidad en {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, EntityStatDef> object, ResourceManager rm, ProfilerFiller profiler) {
            DEFS = Map.copyOf(object);
            LOGGER.info("[Zenkai] Stats de entidad cargados: {} definición(es).", DEFS.size());
        }
    }

    // ── Parseo JSON -> EntityStatDef ─────────────────────────────────────────
    private static EntityStatDef parse(JsonObject o) {
        ResourceLocation entity = ResourceLocation.parse(o.get("entity").getAsString());
        long powerLevel = o.get("power_level").getAsLong();
        boolean displayOnly = o.has("display_only") && o.get("display_only").getAsBoolean();
        String archetype = o.has("archetype") ? o.get("archetype").getAsString() : "balanced";

        EnumMap<ZenkaiAttributes, EntityStatDef.AttrOverride> attrOv = new EnumMap<>(ZenkaiAttributes.class);
        double bodyMult = 1.0, kiMult = 1.0;

        if (o.has("overrides") && o.get("overrides").isJsonObject()) {
            JsonObject ov = o.getAsJsonObject("overrides");
            if (ov.has("body_mult")) bodyMult = ov.get("body_mult").getAsDouble();
            if (ov.has("ki_mult"))   kiMult   = ov.get("ki_mult").getAsDouble();

            if (ov.has("attributes") && ov.get("attributes").isJsonObject()) {
                JsonObject attrs = ov.getAsJsonObject("attributes");
                for (var e : attrs.entrySet()) {
                    ZenkaiAttributes key;
                    try {
                        key = ZenkaiAttributes.fromString(e.getKey());
                    } catch (Exception ex) {
                        LOGGER.warn("[Zenkai] Atributo desconocido '{}' en {}", e.getKey(), entity);
                        continue;
                    }
                    attrOv.put(key, parseOverride(e.getValue().getAsString()));
                }
            }
        }

        // moveset (placeholder Fase futura)
        List<ResourceLocation> kiAttacks = new ArrayList<>();
        boolean melee = true;
        if (o.has("moveset") && o.get("moveset").isJsonObject()) {
            JsonObject ms = o.getAsJsonObject("moveset");
            if (ms.has("melee")) melee = ms.get("melee").getAsBoolean();
            if (ms.has("ki_attacks") && ms.get("ki_attacks").isJsonArray()) {
                for (JsonElement el : ms.getAsJsonArray("ki_attacks")) {
                    ResourceLocation id = ResourceLocation.tryParse(el.getAsString());
                    if (id != null) kiAttacks.add(id);
                }
            }
        }

        // rewards
        String rewardTp = "auto";
        if (o.has("rewards") && o.get("rewards").isJsonObject()) {
            JsonObject rw = o.getAsJsonObject("rewards");
            if (rw.has("tp")) rewardTp = rw.get("tp").getAsString();
        }

        return new EntityStatDef(entity, powerLevel, displayOnly, archetype, attrOv, bodyMult, kiMult,
                kiAttacks, melee, rewardTp);
    }

    /** "+20%" / "-10%" -> relativo; "250" -> absoluto. */
    private static EntityStatDef.AttrOverride parseOverride(String raw) {
        String s = raw.trim();
        if (s.endsWith("%")) {
            double v = Double.parseDouble(s.substring(0, s.length() - 1).replace("+", "").trim());
            return new EntityStatDef.AttrOverride(true, v);
        }
        return new EntityStatDef.AttrOverride(false, Double.parseDouble(s.replace("+", "").trim()));
    }
}