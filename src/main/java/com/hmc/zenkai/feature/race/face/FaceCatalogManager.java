package com.hmc.zenkai.feature.race.face;

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
 * Carga el catálogo de rasgos faciales desde datapack y lo sincroniza al cliente (login y
 * /reload). Espeja FormManager.
 *
 * Ruta: data/&lt;ns&gt;/zenkai_face_parts/&lt;categoría&gt;/&lt;nombre&gt;.json → id &lt;ns&gt;:&lt;categoría&gt;/&lt;nombre&gt;.
 * La categoría sale de la subcarpeta (eyes, eyebrows, nose, mouth — ver FaceCategory); un
 * archivo fuera de una categoría conocida se ignora con aviso.
 *
 * Campos (parseo en FacePartDef.parse):
 *   "layers"      [{"texture": "ns:textures/....png", "color": "none|skin|eye|eye_left|eye_right|hair|#RRGGBB"}]
 *                 en orden de pintado; o el atajo "texture" de una sola capa con el color de la categoría
 *   "sort"        orden en la GUI (defecto 1000; a igualdad, por id)
 *   "races"       ["human", "majin", ...]; ausente = cualquier raza
 *   "exclude_races" se resta de "races" (cómodo para "cualquiera menos X")
 *   "default_for" razas para las que es la opción inicial al reconciliar
 *
 * Un addon añade rasgos con su propio namespace sin tocar los de Zenkai ni chocar con otros.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class FaceCatalogManager {
    private FaceCatalogManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-FaceCatalog");
    private static final String FOLDER = "zenkai_face_parts";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        FaceCatalogSyncPacket pkt = new FaceCatalogSyncPacket(List.copyOf(FaceCatalog.all()));
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, FacePartDef>> {

        @Override
        protected @NotNull Map<ResourceLocation, FacePartDef> prepare(@NotNull ResourceManager rm,
                                                                      @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, FacePartDef> out = new LinkedHashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String rel = file.getPath().substring(FOLDER.length() + 1,
                        file.getPath().length() - ".json".length());
                int slash = rel.indexOf('/');
                FaceCategory category = slash > 0 ? FaceCategory.byKey(rel.substring(0, slash)) : null;
                if (category == null) {
                    LOGGER.warn("[Zenkai] Rasgo facial fuera de una categoría conocida, ignorado: {}", file);
                    continue;
                }
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), rel);

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    out.put(id, FacePartDef.parse(id, category, o,
                            msg -> LOGGER.warn("[Zenkai] Rasgo facial {}: {}", file, msg)));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer el rasgo facial {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, FacePartDef> parts, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            FaceCatalog.replaceAll(parts);
            LOGGER.info("[Zenkai] Rasgos faciales cargados: {}.", parts.size());
        }
    }
}
