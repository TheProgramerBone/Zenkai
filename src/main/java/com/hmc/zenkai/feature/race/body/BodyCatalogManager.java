package com.hmc.zenkai.feature.race.body;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga las opciones de cuerpo desde datapack y las sincroniza al cliente (login y /reload).
 * Ruta: data/&lt;ns&gt;/zenkai_body_options/&lt;categoría&gt;/&lt;nombre&gt;.json (campos en BodyOptionDef).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class BodyCatalogManager {
    private BodyCatalogManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-BodyCatalog");
    private static final String FOLDER = "zenkai_body_options";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) { event.addListener(new Loader()); }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        BodyCatalogSyncPacket pkt = new BodyCatalogSyncPacket(List.copyOf(BodyCatalog.all()));
        if (event.getPlayer() != null) PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        else PacketDistributor.sendToAllPlayers(pkt);
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, BodyOptionDef>> {
        @Override
        protected @NotNull Map<ResourceLocation, BodyOptionDef> prepare(@NotNull ResourceManager rm,
                                                                        @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, BodyOptionDef> out = new LinkedHashMap<>();
            for (var entry : rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String rel = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
                int slash = rel.indexOf('/');
                if (slash <= 0) {
                    LOGGER.warn("[Zenkai] Opción de cuerpo fuera de una categoría, ignorada: {}", file);
                    continue;
                }
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), rel);
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    out.put(id, BodyOptionDef.parse(id, rel.substring(0, slash), o,
                            msg -> LOGGER.warn("[Zenkai] Opción de cuerpo {}: {}", file, msg)));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer la opción de cuerpo {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, BodyOptionDef> options, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            BodyCatalog.replaceAll(options);
            LOGGER.info("[Zenkai] Opciones de cuerpo cargadas: {}.", options.size());
        }
    }
}
