package com.hmc.zenkai.feature.aura;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga las firmas de aura por aura_type desde datapack y las sincroniza al cliente (login y
 * /reload). Espeja FormManager, con una diferencia: la clave aquí es un String bare (el
 * aura_type que ya vive en FormDef), no un ResourceLocation namespaced — no hace falta id
 * completo, solo el nombre de archivo.
 *
 * Ruta: data/<ns>/zenkai_aura_signatures/<aura_type>.json -> aura_type = <aura_type>.
 * Un aura_type sin archivo (incluido "default") cae en AuraModifier.NONE por diseño del
 * propio registro; no hace falta que exista default.json.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class AuraSignatureManager {
    private AuraSignatureManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-AuraSignatures");
    private static final String FOLDER = "zenkai_aura_signatures";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        List<AuraSignatureSyncPacket.Entry> entries = new ArrayList<>();
        AuraSignatureRegistry.all().forEach((type, mod) ->
                entries.add(new AuraSignatureSyncPacket.Entry(type, mod)));
        AuraSignatureSyncPacket pkt = new AuraSignatureSyncPacket(entries);
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static float readFloat(JsonObject o, String key, float fallback) {
        return o.has(key) ? GsonHelper.getAsFloat(o, key, fallback) : fallback;
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<String, AuraModifier>> {

        @Override
        protected @NotNull Map<String, AuraModifier> prepare(@NotNull ResourceManager rm,
                                                              @NotNull ProfilerFiller profiler) {
            Map<String, AuraModifier> out = new LinkedHashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String auraType = file.getPath().substring(FOLDER.length() + 1,
                        file.getPath().length() - ".json".length());

                // Un datapack roto no puede saturar el sync: el cap real vive en
                // AuraSignatureSyncPacket.MAX_TYPE_LEN, se valida aquí antes de que llegue
                // a un writeUtf en el próximo /reload para todos los jugadores conectados.
                if (auraType.length() > AuraSignatureSyncPacket.MAX_TYPE_LEN) {
                    LOGGER.warn("[Zenkai] aura_type demasiado largo, ignorado ({} > {}): {}",
                            auraType.length(), AuraSignatureSyncPacket.MAX_TYPE_LEN, file);
                    continue;
                }

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();

                    out.put(auraType, new AuraModifier(
                            readFloat(o, "d_mass", 0f),
                            readFloat(o, "d_spike", 0f),
                            readFloat(o, "d_turb", 0f),
                            readFloat(o, "d_spread", 0f),
                            readFloat(o, "d_height", 0f),
                            readFloat(o, "d_density", 0f),
                            readFloat(o, "turb_gain", 1f),
                            readFloat(o, "spread_gain", 1f),
                            readFloat(o, "pulse_gain", 1f)));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer la firma de aura en {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<String, AuraModifier> defs, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            AuraSignatureRegistry.replaceAll(defs);
            LOGGER.info("[Zenkai] Firmas de aura cargadas: {}.", defs.size());
        }
    }
}
