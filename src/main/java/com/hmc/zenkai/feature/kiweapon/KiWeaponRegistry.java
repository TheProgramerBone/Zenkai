package com.hmc.zenkai.feature.kiweapon;

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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Números de las armas de ki, recargables con /reload.
 *   data/&lt;ns&gt;/zenkai_ki_weapons/ki_blade.json
 *   { "damage_mult": 1.60, "ki_cost_mult": 1.40 }
 *
 * La clave es el nombre del archivo, que coincide con el id del interruptor y del item. No se
 * pueden AÑADIR armas desde datapack (los items se registran en Java), pero sí ajustarlas sin
 * reiniciar, que es lo que de verdad hace falta al balancear combate.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class KiWeaponRegistry {
    private KiWeaponRegistry() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-KiWeapons");
    private static final String FOLDER = "zenkai_ki_weapons";

    private static volatile Map<String, KiWeaponDef> DEFS = Map.of();

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Nunca null: sin JSON devuelve el FALLBACK, así un datapack incompleto no rompe nada. */
    public static KiWeaponDef get(String id) {
        return DEFS.getOrDefault(id, KiWeaponDef.FALLBACK);
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<String, KiWeaponDef>> {

        @Override
        protected @NotNull Map<String, KiWeaponDef> prepare(@NotNull ResourceManager rm,
                                                            @NotNull ProfilerFiller profiler) {
            Map<String, KiWeaponDef> out = new HashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    out.put(id, new KiWeaponDef(
                            GsonHelper.getAsDouble(o, "damage_mult", 1.0),
                            GsonHelper.getAsDouble(o, "ki_cost_mult", 1.0)));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<String, KiWeaponDef> data, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            DEFS = Map.copyOf(data);
            LOGGER.info("[Zenkai] Ki weapons cargadas: {}", DEFS.size());
        }
    }
}