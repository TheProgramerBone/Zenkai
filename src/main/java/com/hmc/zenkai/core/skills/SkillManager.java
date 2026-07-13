package com.hmc.zenkai.core.skills;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.skills.SkillSyncPacket;
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
import java.util.Map;

/**
 * Carga las habilidades desde datapack (data/&lt;ns&gt;/zenkai_skills/*.json) y las
 * sincroniza al cliente (login y /reload). El id es la RUTA del archivo sin carpeta ni
 * extensión ("focus", "masters/kaio_fist") — si dos namespaces definen el mismo path,
 * gana el último y se avisa por log.
 *
 * JSON: { "tp_cost": 100, "mind_req": 5, "purchasable": true }  (todos opcionales;
 * purchasable=false -> solo maestros / /zenkai skill give).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class SkillManager {
    private SkillManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Skills");
    private static final String FOLDER = "zenkai_skills";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Login de un jugador (getPlayer() != null) o /reload (null = broadcast). */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SkillSyncPacket pkt = new SkillSyncPacket(List.copyOf(SkillDef.all()));
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<String, SkillDef>> {

        @Override
        protected @NotNull Map<String, SkillDef> prepare(@NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            Map<String, SkillDef> out = new LinkedHashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String id = file.getPath().substring(
                        FOLDER.length() + 1,
                        file.getPath().length() - ".json".length());
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    SkillDef def = new SkillDef(id,
                            GsonHelper.getAsInt(o, "tp_cost", 0),
                            GsonHelper.getAsInt(o, "mind_req", 0),
                            GsonHelper.getAsBoolean(o, "purchasable", true));
                    if (out.put(id, def) != null) {
                        LOGGER.warn("[Zenkai] Skill duplicada '{}': dos namespaces definen el mismo path (gana {}).", id, file);
                    }
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer la skill en {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<String, SkillDef> defs, @NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            SkillDef.replaceAll(defs);
            LOGGER.info("[Zenkai] Skills cargadas: {} definición(es).", defs.size());
        }
    }
}