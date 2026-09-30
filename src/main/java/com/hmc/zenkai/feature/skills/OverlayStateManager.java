package com.hmc.zenkai.feature.skills;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga los {@link OverlayStateDef} desde datapack cross-namespace (mismo patrón que
 * {@code FormManager}/{@code EntityArchetypeManager}): {@code data/<ns>/zenkai_overlay_states/
 * <id>.json}, un addon puede sumar los suyos sin tocar Zenkai. Ningún JSON real existe todavía
 * en el mod (Ultra Instinct/Ultra Ego solo tienen su {@link SkillToggles.Toggle}/{@code
 * SkillDef} por ahora) — este manager arranca con el registro vacío y eso es un estado válido,
 * no un error: cualquier consumidor trata "sin definición" como "sin efecto", nunca como fallo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class OverlayStateManager {
    private OverlayStateManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-OverlayStates");
    private static final String FOLDER = "zenkai_overlay_states";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        OverlayStateSyncPacket pkt = new OverlayStateSyncPacket(List.copyOf(OverlayStateDef.all()));
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<String, OverlayStateDef>> {

        @Override
        protected @NotNull Map<String, OverlayStateDef> prepare(@NotNull ResourceManager rm,
                                                                 @NotNull ProfilerFiller profiler) {
            Map<String, OverlayStateDef> out = new HashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                String id = path.substring(FOLDER.length() + 1, path.length() - ".json".length());

                Resource resource = entry.getValue();
                try (BufferedReader reader = resource.openAsReader()) {
                    JsonElement root = JsonParser.parseReader(reader);
                    OverlayStateDef prev = out.put(id, parse(id, root.getAsJsonObject()));
                    if (prev != null) {
                        LOGGER.warn("[Zenkai] Overlay state duplicado '{}': gana {}", id, file);
                    }
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer el overlay state {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<String, OverlayStateDef> object, ResourceManager rm, ProfilerFiller profiler) {
            OverlayStateDef.replaceAll(object);
            LOGGER.info("[Zenkai] Overlay states cargados: {} definición(es).", object.size());
        }
    }

    private static OverlayStateDef parse(String id, JsonObject o) {
        int auraRgb = readRgb(o, "aura_rgb", -1);
        int priority = o.has("priority") ? o.get("priority").getAsInt() : 0;

        Map<String, Map<String, Double>> formModifiers = new LinkedHashMap<>();
        if (o.has("form_modifiers") && o.get("form_modifiers").isJsonObject()) {
            JsonObject outer = o.getAsJsonObject("form_modifiers");
            for (String outerKey : outer.keySet()) {
                if (!outer.get(outerKey).isJsonObject()) continue;
                JsonObject inner = outer.getAsJsonObject(outerKey);
                Map<String, Double> channels = new LinkedHashMap<>();
                for (String channel : inner.keySet()) {
                    channels.put(channel, inner.get(channel).getAsDouble());
                }
                formModifiers.put(outerKey, channels);
            }
        }
        return new OverlayStateDef(id, auraRgb, priority, formModifiers);
    }

    /** Acepta 0xFFAA55, #FFAA55 o un entero. */
    private static int readRgb(JsonObject o, String key, int fallback) {
        if (!o.has(key)) return fallback;
        var el = o.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return Integer.decode(el.getAsString().trim().replace("#", "0x")) & 0xFFFFFF;
        }
        return el.getAsInt() & 0xFFFFFF;
    }
}
