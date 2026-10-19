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
 * HairStyleDef.parse), y data/&lt;ns&gt;/zenkai_hair_packs/&lt;pack&gt;.json = carpeta con sus peinados en un
 * solo manifiesto (HairPacks). Un form_override que apunta a un peinado inexistente se avisa en el log
 * y en el juego cae al peinado base (nunca crash).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class HairCatalogManager {
    private HairCatalogManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-HairCatalog");
    private static final String FOLDER = "zenkai_hair_styles";
    private static final String PACKS = "zenkai_hair_packs";

    /** Lo leído de los datapacks: peinados (sueltos y de manifiestos) y carpetas de los manifiestos. */
    private record Loaded(Map<ResourceLocation, HairStyleDef> styles, Map<ResourceLocation, HairFolderDef> folders) {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        HairCatalogSyncPacket pkt = new HairCatalogSyncPacket(List.copyOf(HairCatalog.all()),
                List.copyOf(HairCatalog.allFolders()));
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static final class Loader extends SimplePreparableReloadListener<Loaded> {

        @Override
        protected @NotNull Loaded prepare(@NotNull ResourceManager rm,
                                                                       @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, HairStyleDef> out = new LinkedHashMap<>();
            Map<ResourceLocation, HairFolderDef> folders = new LinkedHashMap<>();
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
            readPacks(rm, out, folders);
            return new Loaded(out, folders);
        }

        /**
         * Manifiestos de pack (HairPacks). Un peinado de un manifiesto que repite el id de uno
         * suelto se descarta con aviso: gana el suelto, como el resto de datapacks por orden.
         */
        private static void readPacks(ResourceManager rm, Map<ResourceLocation, HairStyleDef> out,
                                      Map<ResourceLocation, HairFolderDef> folders) {
            var packs = rm.listResources(PACKS, loc -> loc.getPath().endsWith(".json"));
            for (var entry : packs.entrySet()) {
                ResourceLocation file = entry.getKey();
                String rel = file.getPath().substring(PACKS.length() + 1,
                        file.getPath().length() - ".json".length());
                ResourceLocation packId = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), rel);
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    HairPacks.Parsed parsed = HairPacks.parse(packId, o,
                            msg -> LOGGER.warn("[Zenkai] Pack de peinados {}: {}", file, msg));
                    folders.put(packId, parsed.folder());
                    for (HairStyleDef d : parsed.styles()) {
                        if (out.putIfAbsent(d.id(), d) != null) {
                            LOGGER.warn("[Zenkai] Pack de peinados {}: el id {} ya existe, se ignora", file, d.id());
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer el pack de peinados {}: {}", file, ex.toString());
                }
            }
        }

        @Override
        protected void apply(@NotNull Loaded loaded, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, HairStyleDef> styles = loaded.styles();
            for (HairStyleDef d : styles.values()) {
                for (var e : d.formOverrides().entrySet()) {
                    if (!styles.containsKey(e.getValue())) {
                        LOGGER.error("[Zenkai] El peinado '{}' manda a '{}' en la forma {}, que no existe",
                                d.id(), e.getValue(), e.getKey());
                    }
                }
            }
            HairCatalog.replaceAll(styles, loaded.folders());
            LOGGER.info("[Zenkai] Peinados cargados: {} ({} carpetas de pack).", styles.size(), loaded.folders().size());
        }
    }
}
