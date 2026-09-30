package com.hmc.zenkai.feature.combat.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.ZenkaiAttributes;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Carga los arquetipos de entidad desde datapack (data/&lt;ns&gt;/zenkai_archetypes/*.json,
 * escaneado cross-namespace — cualquier addon puede sumar los suyos) y los guarda por nombre.
 * Mismo patrón exacto que {@link EntityStatsManager}/{@code MasterManager}. Reemplaza el
 * bloque {@code static{}} fijo que tenía {@link EntityArchetype} — los 6 arquetipos originales
 * ahora son JSON base bajo {@code data/zenkai/zenkai_archetypes/}, no código.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class EntityArchetypeManager {
    private EntityArchetypeManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-EntityArchetypes");
    private static final String FOLDER = "zenkai_archetypes";
    private static final String FALLBACK_NAME = "balanced";

    /** nombre (en minúsculas) -> arquetipo. Inmutable tras cada recarga. */
    private static volatile Map<String, EntityArchetype> DEFS = Map.of();

    /** Arquetipo por nombre (case-insensitive). Cae a "balanced" si no existe (o si "balanced"
     *  mismo falta, un mapa vacío por defecto en vez de un null que reviente EntityStats). */
    public static EntityArchetype get(String name) {
        Map<String, EntityArchetype> defs = DEFS;
        EntityArchetype a = name == null ? null : defs.get(name.toLowerCase(Locale.ROOT));
        if (a != null) return a;
        EntityArchetype fallback = defs.get(FALLBACK_NAME);
        return fallback != null ? fallback : EMPTY;
    }

    /** Arquetipo neutro si ni siquiera "balanced" tiene JSON cargado (datapack roto/vacío) —
     *  mejor esto que un NullPointerException en cualquier sitio que llame a get(). */
    private static final EntityArchetype EMPTY =
            new EntityArchetype("balanced", new EnumMap<>(ZenkaiAttributes.class), 1.0, 1.0);

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<String, EntityArchetype>> {

        @Override
        protected @NotNull Map<String, EntityArchetype> prepare(@NotNull ResourceManager rm,
                                                                 @NotNull ProfilerFiller profiler) {
            Map<String, EntityArchetype> out = new HashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                String name = path.substring(FOLDER.length() + 1, path.length() - ".json".length());

                Resource resource = entry.getValue();
                try (BufferedReader reader = resource.openAsReader()) {
                    JsonElement root = JsonParser.parseReader(reader);
                    EntityArchetype prev = out.put(name, parse(name, root.getAsJsonObject()));
                    if (prev != null) LOGGER.warn("[Zenkai] Arquetipo duplicado '{}': gana {}", name, file);
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer el arquetipo {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<String, EntityArchetype> object, ResourceManager rm, ProfilerFiller profiler) {
            DEFS = Map.copyOf(object);
            LOGGER.info("[Zenkai] Arquetipos de entidad cargados: {} definición(es).", DEFS.size());
        }
    }

    private static EntityArchetype parse(String name, JsonObject o) {
        EnumMap<ZenkaiAttributes, Double> shape = new EnumMap<>(ZenkaiAttributes.class);
        if (o.has("shape") && o.get("shape").isJsonObject()) {
            JsonObject s = o.getAsJsonObject("shape");
            put(shape, ZenkaiAttributes.STRENGTH, s, "str");
            put(shape, ZenkaiAttributes.CONSTITUTION, s, "con");
            put(shape, ZenkaiAttributes.DEXTERITY, s, "dex");
            put(shape, ZenkaiAttributes.WILLPOWER, s, "wil");
            put(shape, ZenkaiAttributes.SPIRIT, s, "spi");
            // MIND no participa del reparto de un arquetipo (ver javadoc de EntityArchetype):
            // se deja fuera a propósito, igual que el bloque static{} original nunca lo ponía.
        }
        double bodyMult = o.has("body_mult") ? o.get("body_mult").getAsDouble() : 1.0;
        double kiMult = o.has("ki_mult") ? o.get("ki_mult").getAsDouble() : 1.0;
        return new EntityArchetype(name, shape, bodyMult, kiMult);
    }

    private static void put(EnumMap<ZenkaiAttributes, Double> shape, ZenkaiAttributes attr,
                            JsonObject s, String key) {
        shape.put(attr, s.has(key) ? s.get(key).getAsDouble() : 0.0);
    }
}
