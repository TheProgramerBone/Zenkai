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
 * Carga desde datapack los COSTES del scouter y los sincroniza al cliente, igual que
 * SkillManager y TechniqueManager. Dos cosas distintas, un solo listener:
 *  1. MEJORAS — data/&lt;ns&gt;/zenkai_scouter_upgrades/&lt;id&gt;.json, un fichero por mejora.
 *     El nombre del archivo DEBE coincidir con el id de un ScouterUpgrade; cualquier otro se
 *     ignora con aviso. El catálogo no se descubre por datapack: existe en código y esto solo
 *     le pone precio.
 *     {
 *       "levels": [
 *         { "energy": 1500, "materials": [ { "item": "minecraft:redstone", "count": 8 },
 *                                          { "tag":  "c:ingots/iron",     "count": 4 } ] }
 *       ]
 *     }
 *     levels[0] = coste de subir del nivel 0 al 1. Sobran entradas = se ignoran;
 *     faltan = ese nivel sale gratis.
 *  2. REPARACIÓN — data/&lt;ns&gt;/zenkai_scouter_repair.json, fichero único.
 *     {
 *       "materials": [ { "tag": "c:ingots/iron", "count": 3 },
 *                      { "item": "minecraft:redstone", "count": 1 } ],
 *       "energy": 6000,
 *       "anvil_levels": 5
 *     }
 *     Va FUERA de la carpeta de mejoras a propósito: ahí dentro, listResources lo recogería y
 *     el bucle lo rechazaría por no corresponder a ninguna mejora. Y al leerse con
 *     getResource, un datapack de encima lo sobreescribe entero, que es el comportamiento que
 *     se quiere para un fichero único.
 * Las dos cargas van juntas porque se aplican y se sincronizan juntas: separarlas abriría la
 * puerta a que un /reload actualice una y no la otra.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ScouterUpgradeManager {
    private ScouterUpgradeManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-ScouterUpgrades");
    private static final String FOLDER = "zenkai_scouter_upgrades";
    private static final String REPAIR_FILE = "zenkai_scouter_repair.json";
    private static final String TINT_FILE = "zenkai_scouter_tint.json";

    private record Loaded(Map<ScouterUpgrade, List<ScouterUpgradeCost>> upgrades,
                          ScouterRepairCost repair,
                          ScouterTintCost tint) {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ScouterUpgradeSyncPacket pkt = new ScouterUpgradeSyncPacket(
                ScouterUpgradeCost.snapshot(), ScouterRepairCost.get(), ScouterTintCost.get());
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
            return new Loaded(prepareUpgrades(rm), prepareRepair(rm), prepareTint(rm));
        }

        /** Economía del tinte. Cuatro números, no una lista de recetas: el algoritmo vive en
         *  Java y el datapack solo mueve la curva. */
        private ScouterTintCost prepareTint(ResourceManager rm) {
            ResourceLocation file =
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, TINT_FILE);
            var res = rm.getResource(file);
            if (res.isEmpty()) return ScouterTintCost.DEFAULT;
            try (BufferedReader reader = res.get().openAsReader()) {
                JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                return ScouterTintCost.clamped(
                        GsonHelper.getAsInt(o, "energy", 1000),
                        GsonHelper.getAsInt(o, "min_materials", 1),
                        GsonHelper.getAsInt(o, "max_materials", 3),
                        GsonHelper.getAsFloat(o, "material_scale", 1.0f));
            } catch (Exception ex) {
                LOGGER.error("[Zenkai] No se pudo leer {}: {}. Coste de tinte por defecto.",
                        file, ex.toString());
                return ScouterTintCost.DEFAULT;
            }
        }

        // ── Mejoras ──────────────────────────────────────────────────────────

        private Map<ScouterUpgrade, List<ScouterUpgradeCost>> prepareUpgrades(ResourceManager rm) {
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
                        levels.add(new ScouterUpgradeCost(
                                List.copyOf(readMaterials(lo)),
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

        // ── Reparación ───────────────────────────────────────────────────────

        /**
         * ⚠ VERIFICAR 1.21.1: ResourceManager#getResource(ResourceLocation) -> Optional&lt;Resource&gt;.
         */
        private ScouterRepairCost prepareRepair(ResourceManager rm) {
            ResourceLocation file =
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, REPAIR_FILE);
            var res = rm.getResource(file);
            if (res.isEmpty()) {
                LOGGER.warn("[Zenkai] No hay {}; se usa el coste de reparación por defecto.", file);
                return ScouterRepairCost.DEFAULT;
            }
            try (BufferedReader reader = res.get().openAsReader()) {
                JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                // clamped() aplica el suelo AQUÍ, una vez: lo que se guarda, lo que viaja por
                // red y lo que pinta el tooltip son el mismo objeto ya saneado, así que no hay
                // forma de que el cliente enseñe un precio y el servidor cobre otro.
                return ScouterRepairCost.clamped(
                        readMaterials(o),
                        GsonHelper.getAsInt(o, "energy", 0),
                        GsonHelper.getAsInt(o, "anvil_levels", 0));
            } catch (Exception ex) {
                LOGGER.error("[Zenkai] No se pudo leer {}: {}. Se usa el coste por defecto.",
                        file, ex.toString());
                return ScouterRepairCost.DEFAULT;
            }
        }

        // ── Común ────────────────────────────────────────────────────────────

        /** Bloque "materials" de un objeto. Lo comparten las mejoras y la reparación: el
         *  formato es idéntico y tenerlo dos veces garantizaba que acabaran divergiendo. */
        private List<ScouterUpgradeCost.Material> readMaterials(JsonObject o) {
            List<ScouterUpgradeCost.Material> mats = new ArrayList<>();
            if (!o.has("materials")) return mats;
            for (var mEl : o.getAsJsonArray("materials")) {
                JsonObject mo = mEl.getAsJsonObject();
                boolean isTag = mo.has("tag");
                String raw = isTag ? GsonHelper.getAsString(mo, "tag")
                        : GsonHelper.getAsString(mo, "item");
                mats.add(new ScouterUpgradeCost.Material(
                        ResourceLocation.parse(raw), isTag,
                        Math.max(1, GsonHelper.getAsInt(mo, "count", 1))));
            }
            return mats;
        }

        // ── Aplicación ───────────────────────────────────────────────────────

        @Override
        protected void apply(@NotNull Loaded loaded, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            ScouterTintCost.replace(loaded.tint());
            ScouterUpgradeCost.replaceAll(loaded.upgrades());
            ScouterRepairCost.replace(loaded.repair());
            LOGGER.info("[Zenkai] Mejoras de scouter con coste cargado: {}/{}. Reparación: {} FE, {} niveles.",
                    loaded.upgrades().size(), ScouterUpgrade.values().length,
                    loaded.repair().energy(), loaded.repair().anvilLevels());
        }
    }
}