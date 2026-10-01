package com.hmc.zenkai.feature.race.hair;

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
 * Carga el catálogo de peinados desde datapack y lo sincroniza al cliente (login y /reload).
 * Espeja FaceCatalogManager / FormManager.
 *
 * Ruta: data/&lt;ns&gt;/zenkai_hair_styles/&lt;nombre&gt;.json → id &lt;ns&gt;:&lt;nombre&gt; (campos en
 * HairStyleDef.parse). Un form_override que apunta a un peinado inexistente se avisa en el log
 * y en el juego cae al peinado base (nunca crash).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class HairCatalogManager {
    private HairCatalogManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-HairCatalog");
    private static final String FOLDER = "zenkai_hair_styles";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        HairCatalogSyncPacket pkt = new HairCatalogSyncPacket(List.copyOf(HairCatalog.all()));
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, HairStyleDef>> {

        @Override
        protected @NotNull Map<ResourceLocation, HairStyleDef> prepare(@NotNull ResourceManager rm,
                                                                       @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, HairStyleDef> out = new LinkedHashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String rel = file.getPath().substring(FOLDER.length() + 1,
                        file.getPath().length() - ".json".length());
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), rel);
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    out.put(id, HairStyleDef.parse(id, o,
                            msg -> LOGGER.warn("[Zenkai] Peinado {}: {}", file, msg)));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer el peinado {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, HairStyleDef> styles, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            for (HairStyleDef d : styles.values()) {
                for (var e : d.formOverrides().entrySet()) {
                    if (!styles.containsKey(e.getValue())) {
                        LOGGER.error("[Zenkai] El peinado '{}' manda a '{}' en la forma {}, que no existe",
                                d.id(), e.getValue(), e.getKey());
                    }
                }
            }
            HairCatalog.replaceAll(styles);
            LOGGER.info("[Zenkai] Peinados cargados: {}.", styles.size());
        }
    }
}
