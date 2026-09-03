package com.hmc.zenkai.feature.teleport;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Carga la lista de dimensiones bloqueadas para Transmisión Instantánea desde datapack y la
 * sincroniza al cliente (login y /reload). Espeja AuraSignatureManager, con una diferencia: no
 * hay una clave por archivo — cualquier número de archivos puede aportar dimensiones, TODAS se
 * unen en un solo Set (a diferencia de las firmas de aura, donde cada archivo es una entrada
 * propia por aura_type), así que dos datapacks distintos pueden bloquear dimensiones distintas
 * sin pisarse entre ellos.
 *
 * Ruta: data/&lt;ns&gt;/zenkai_instant_transmission_blocklist/*.json, formato
 * {@code {"dimensions": ["some_mod:some_dimension", ...]}} — un array de ResourceLocation en
 * formato texto ("namespace:path"), no necesita coincidir con el propio namespace del archivo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class InstantTransmissionBlocklistManager {
    private InstantTransmissionBlocklistManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-ITBlocklist");
    private static final String FOLDER = "zenkai_instant_transmission_blocklist";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        List<String> blocked = InstantTransmissionBlocklist.all().stream()
                .map(ResourceLocation::toString).toList();
        InstantTransmissionBlocklistSyncPacket pkt = new InstantTransmissionBlocklistSyncPacket(blocked);
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static final class Loader extends SimplePreparableReloadListener<Set<ResourceLocation>> {

        @Override
        protected @NotNull Set<ResourceLocation> prepare(@NotNull ResourceManager rm,
                                                           @NotNull ProfilerFiller profiler) {
            Set<ResourceLocation> out = new HashSet<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!o.has("dimensions")) continue;

                    List<String> raw = new ArrayList<>();
                    o.getAsJsonArray("dimensions").forEach(el -> raw.add(el.getAsString()));

                    for (String id : raw) {
                        ResourceLocation loc = ResourceLocation.tryParse(id);
                        if (loc == null) {
                            LOGGER.warn("[Zenkai] id de dimensión inválido en {}: {}", file, id);
                            continue;
                        }
                        out.add(loc);
                    }
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer la lista de bloqueo en {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Set<ResourceLocation> blocked, @NotNull ResourceManager rm,
                              @NotNull ProfilerFiller profiler) {
            InstantTransmissionBlocklist.replaceAll(blocked);
            LOGGER.info("[Zenkai] Dimensiones bloqueadas para Transmisión Instantánea: {}.", blocked.size());
        }
    }
}
