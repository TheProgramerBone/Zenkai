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
 *   data/&lt;ns&gt;/zenkai_ki_weapons/ki_sword.json
 *   { "damage_mult": 1.60, "ki_cost_mult": 1.40, "reach": 1.0, "attack_speed": -2.4, "attack_damage": 3.0 }
 *
 * La clave es el nombre del archivo, que coincide con el id del interruptor. No se pueden AÑADIR
 * armas desde datapack (el interruptor y el modelo se registran en Java), pero sí ajustarlas sin
 * reiniciar, que es lo que de verdad hace falta al balancear combate.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class KiWeaponRegistry {
    private KiWeaponRegistry() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-KiWeapons");
    private static final String FOLDER = "zenkai_ki_weapons";

    private static volatile Map<String, KiWeaponDef> DEFS = Map.of();

    /**
     * Armas BLOQUEADAS (data/zenkai/banned_ki_weapons.json, {"banned": ["ki_axe"]}): no se pueden materializar aunque
     * el interruptor esté puesto. Mismo espíritu que banned_forms.json: gana el archivo del pack de mayor prioridad,
     * así un datapack o un servidor las bloquea o desbloquea sin tocar el mod. Los ids son los del interruptor
     * (ki_sword, ki_axe, ki_mace, ki_scythe, ki_hammer, ki_shield); un prefijo de namespace se ignora.
     */
    private static final ResourceLocation BANNED_FILE =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "banned_ki_weapons.json");
    private static volatile java.util.Set<String> BANNED = java.util.Set.of();

    /** ¿Está bloqueada esta variante? (solo servidor: los datos no se sincronizan al cliente). */
    public static boolean isBanned(String id) {
        return id != null && BANNED.contains(id);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Nunca null: sin JSON devuelve el FALLBACK, así un datapack incompleto no rompe nada. */
    public static KiWeaponDef get(String id) {
        return DEFS.getOrDefault(id, KiWeaponDef.FALLBACK);
    }

    /** Ids bloqueados. Archivo ausente o roto = ninguno (nunca impide la carga). */
    private static java.util.Set<String> readBanned(ResourceManager rm) {
        java.util.Set<String> out = new java.util.HashSet<>();
        var res = rm.getResource(BANNED_FILE);
        if (res.isEmpty()) return out;
        try (BufferedReader reader = res.get().openAsReader()) {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            if (o.has("banned")) {
                for (var el : GsonHelper.getAsJsonArray(o, "banned")) {
                    String id = el.getAsString().trim();
                    int colon = id.indexOf(':');
                    out.add(colon >= 0 ? id.substring(colon + 1) : id);
                }
            }
        } catch (Exception ex) {
            LOGGER.error("[Zenkai] No se pudo leer banned_ki_weapons.json (se ignora): {}", ex.toString());
        }
        return out;
    }

    private record Loaded(Map<String, KiWeaponDef> defs, java.util.Set<String> banned) {}

    private static final class Loader extends SimplePreparableReloadListener<Loaded> {

        @Override
        protected @NotNull Loaded prepare(@NotNull ResourceManager rm,
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
                            GsonHelper.getAsDouble(o, "ki_cost_mult", 1.0),
                            GsonHelper.getAsDouble(o, "reach", 0.0),
                            GsonHelper.getAsDouble(o, "attack_speed", 0.0),
                            GsonHelper.getAsDouble(o, "attack_damage", 2.0),
                            GsonHelper.getAsBoolean(o, "two_handed", false),
                            GsonHelper.getAsDouble(o, "knockback", 0.0),
                            Math.max(0.0, Math.min(1.0, GsonHelper.getAsDouble(o, "guard_break", 0.0))),
                            GsonHelper.getAsDouble(o, "block_bonus", 0.0),
                            GsonHelper.getAsDouble(o, "block_ki_pct", 0.0)));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer {}: {}", file, ex.toString());
                }
            }
            return new Loaded(out, readBanned(rm));
        }

        @Override
        protected void apply(@NotNull Loaded data, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            DEFS = Map.copyOf(data.defs());
            BANNED = java.util.Set.copyOf(data.banned());
            LOGGER.info("[Zenkai] Ki weapons cargadas: {} (bloqueadas: {})", DEFS.size(), BANNED);
        }
    }
}