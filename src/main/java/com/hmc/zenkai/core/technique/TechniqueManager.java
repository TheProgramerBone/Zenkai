package com.hmc.zenkai.core.technique;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.technique.TechniqueSyncPacket;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carga los números de las técnicas desde datapack y los sincroniza al cliente
 * (login y /reload). Espeja SkillManager.
 *
 * Ruta: data/&lt;ns&gt;/zenkai_techniques/&lt;ki|physical&gt;/&lt;id&gt;.json
 * El id debe coincidir con el nombre del enum en minúsculas; un JSON sin enum se
 * ignora con warn, y un enum sin JSON queda DESACTIVADO (error en log).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class TechniqueManager {
    private TechniqueManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Techniques");
    private static final String FOLDER = "zenkai_techniques";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Login de un jugador (getPlayer() != null) o /reload (null = broadcast). */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        TechniqueSyncPacket pkt = new TechniqueSyncPacket(List.copyOf(TechniqueDef.all()));
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    /** Acepta 12345 o "0x55AAFF" / "#55AAFF". */
    private static int readRgb(JsonObject o, String key) {
        if (!o.has(key)) return 0xFFFFFF;
        JsonElement el = o.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString().trim().replace("#", "0x");
            return Integer.decode(s) & 0xFFFFFF;
        }
        return el.getAsInt() & 0xFFFFFF;
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<String, TechniqueDef>> {

        @Override
        protected @NotNull Map<String, TechniqueDef> prepare(@NotNull ResourceManager rm,
                                                             @NotNull ProfilerFiller profiler) {
            Map<String, TechniqueDef> out = new LinkedHashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                // zenkai_techniques/<folder>/<id>.json
                String rel = file.getPath().substring(FOLDER.length() + 1,
                        file.getPath().length() - ".json".length());
                int slash = rel.indexOf('/');
                if (slash <= 0) {
                    LOGGER.warn("[Zenkai] Técnica sin subcarpeta ki/ o physical/: {}", file);
                    continue;
                }
                TechniqueDef.Kind kind = TechniqueDef.Kind.byFolder(rel.substring(0, slash));
                String id = rel.substring(slash + 1).toLowerCase(Locale.ROOT);
                if (kind == null) {
                    LOGGER.warn("[Zenkai] Subcarpeta de técnica desconocida en {}", file);
                    continue;
                }
                // El enum es la identidad: un JSON sin enum no sirve para nada.
                boolean known = kind == TechniqueDef.Kind.KI
                        ? KiTechniqueType.byName(id) != null
                        : PhysicalTechnique.byName(id.toUpperCase(Locale.ROOT)) != null;
                if (!known) {
                    LOGGER.warn("[Zenkai] Técnica '{}' ({}) no existe en el enum: ignorada.", id, kind);
                    continue;
                }

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    TechniqueDef def = new TechniqueDef(id, kind,
                            GsonHelper.getAsInt(o, "tp_cost", 0),
                            GsonHelper.getAsInt(o, "mind_req", 0),
                            GsonHelper.getAsDouble(o, "damage_mult", 1.0),
                            GsonHelper.getAsDouble(o, "ki_cost_mult", 1.0),
                            GsonHelper.getAsDouble(o, "stamina_pct", 0.0),
                            GsonHelper.getAsInt(o, "charge_ticks", 20),
                            GsonHelper.getAsInt(o, "cooldown_ticks", 20),
                            GsonHelper.getAsDouble(o, "speed", 1.0),
                            Math.max(0, GsonHelper.getAsInt(o, "count", 1)),
                            GsonHelper.getAsBoolean(o, "defensive", false),
                            readRgb(o, "default_rgb"),
                            GsonHelper.getAsDouble(o, "range", 3.0));
                    if (out.put(kind.name() + "/" + id, def) != null) {
                        LOGGER.warn("[Zenkai] Técnica duplicada '{}/{}': gana {}.", kind.folder(), id, file);
                    }
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer la técnica en {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<String, TechniqueDef> defs, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            TechniqueDef.replaceAll(defs);
            for (KiTechniqueType t : KiTechniqueType.values()) {
                if (!t.enabled()) {
                    LOGGER.error("[Zenkai] Técnica ki '{}' SIN JSON: desactivada.", t.id());
                }
            }
            for (PhysicalTechnique t : PhysicalTechnique.values()) {
                if (!t.enabled()) {
                    LOGGER.error("[Zenkai] Técnica física '{}' SIN JSON: desactivada.", t.id());
                }
            }
            LOGGER.info("[Zenkai] Técnicas cargadas: {} definición(es).", defs.size());
        }
    }
}