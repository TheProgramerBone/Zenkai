package com.hmc.zenkai.feature.sense;

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
import java.util.*;

/**
 * Carga los COSTES de las mejoras del scouter desde datapack
 * (data/&lt;ns&gt;/zenkai_scouter_upgrades/&lt;id&gt;.json) y los sincroniza al cliente, igual
 * que SkillManager y TechniqueManager.
 *
 * El nombre del archivo DEBE coincidir con el id de un ScouterUpgrade; cualquier otro se
 * ignora con aviso. El catálogo no se descubre por datapack: existe en código y esto solo
 * le pone precio.
 *
 * JSON:
 * {
 *   "levels": [
 *     { "energy": 0, "materials": [ { "item": "minecraft:redstone", "count": 8 },
 *                                   { "tag":  "c:ingots/iron",     "count": 4 } ] }
 *   ]
 * }
 * levels[0] = coste de subir del nivel 0 al 1. Sobran entradas = se ignoran; faltan = gratis.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ScouterUpgradeManager {
    private ScouterUpgradeManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-ScouterUpgrades");
    private static final String FOLDER = "zenkai_scouter_upgrades";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ScouterUpgradeSyncPacket pkt = new ScouterUpgradeSyncPacket(ScouterUpgradeCost.snapshot());
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static final class Loader
            extends SimplePreparableReloadListener<Map<ScouterUpgrade, List<ScouterUpgradeCost>>> {

        @Override
        protected @NotNull Map<ScouterUpgrade, List<ScouterUpgradeCost>> prepare(
                @NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {

            Map<ScouterUpgrade, List<ScouterUpgradeCost>> out = new EnumMap<>(ScouterUpgrade.class);
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String id = file.getPath().substring(
                        FOLDER.length() + 1, file.getPath().length() - ".json".length());

                ScouterUpgrade upgrade = ScouterUpgrade.byId(id);
                if (upgrade == null) {
                    LOGGER.warn("[Zenkai] {} no corresponde a ninguna mejora de scouter conocida; ignorado.", file);
                    continue;
                }

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    List<ScouterUpgradeCost> levels = new ArrayList<>();

                    for (var el : GsonHelper.getAsJsonArray(o, "levels")) {
                        JsonObject lo = el.getAsJsonObject();
                        List<ScouterUpgradeCost.Material> mats = new ArrayList<>();

                        if (lo.has("materials")) {
                            for (var mEl : lo.getAsJsonArray("materials")) {
                                JsonObject mo = mEl.getAsJsonObject();
                                boolean isTag = mo.has("tag");
                                String raw = isTag
                                        ? GsonHelper.getAsString(mo, "tag")
                                        : GsonHelper.getAsString(mo, "item");
                                ResourceLocation rl = ResourceLocation.parse(raw);
                                mats.add(new ScouterUpgradeCost.Material(
                                        rl, isTag, Math.max(1, GsonHelper.getAsInt(mo, "count", 1))));
                            }
                        }

                        levels.add(new ScouterUpgradeCost(List.copyOf(mats),
                                Math.max(0, GsonHelper.getAsInt(lo, "energy", 0))));
                    }

                    if (levels.size() < upgrade.maxLevel()) {
                        LOGGER.warn("[Zenkai] '{}' define {} nivel(es) pero la mejora tiene {}: el resto sale gratis.",
                                id, levels.size(), upgrade.maxLevel());
                    }
                    out.put(upgrade, List.copyOf(levels));

                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer la mejora de scouter en {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<ScouterUpgrade, List<ScouterUpgradeCost>> costs,
                             @NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            ScouterUpgradeCost.replaceAll(costs);
            LOGGER.info("[Zenkai] Mejoras de scouter con coste cargado: {}/{}.",
                    costs.size(), ScouterUpgrade.values().length);
        }
    }
}