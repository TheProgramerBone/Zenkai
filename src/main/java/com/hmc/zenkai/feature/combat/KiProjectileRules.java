package com.hmc.zenkai.feature.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Qué proyectiles admiten Ki Infuse y con qué trato. Datapack recargable:
 *   data/&lt;ns&gt;/zenkai_ki_projectiles/*.json
 *   {
 *     "replace": { "minecraft:arrow": "zenkai:ki_arrow" },
 *     "scaled":  [ "minecraft:trident", "minecraft:snowball" ],
 *     "blocked": [ "otromod:proyectil_raro" ]
 *   }
 *  - replace: la entidad se sustituye por la nuestra. Control total (la flecha de ki no se
 *    recoge y se desvanece al aterrizar).
 *  - scaled: la entidad original vuela tal cual y solo se le pega el KiInfusedShot. Es la vía
 *    para lo que no podemos ni queremos reemplazar, incluidos proyectiles de otros mods.
 *  - blocked: nunca se infusiona, pase lo que pase. Es la válvula de compatibilidad: un mod
 *    cuyo proyectil se rompa al llevar attachments se desactiva desde el JSON sin tocar Java.
 * blocked gana sobre lo demás, y replace gana sobre scaled: las tres listas se
 * consultan en ese orden, así que un archivo que meta la misma entidad en dos sitios tiene un
 * resultado definido en vez de depender del orden de carga.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class KiProjectileRules {
    private KiProjectileRules() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-KiProjectiles");
    private static final String FOLDER = "zenkai_ki_projectiles";

    public enum Mode { NONE, REPLACE, SCALED }

    private static volatile Map<ResourceLocation, ResourceLocation> REPLACE = Map.of();
    private static volatile Set<ResourceLocation> SCALED  = Set.of();
    private static volatile Set<ResourceLocation> BLOCKED = Set.of();

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Trato que le toca a este tipo de proyectil. */
    public static Mode modeFor(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (key == null || BLOCKED.contains(key)) return Mode.NONE;
        if (REPLACE.containsKey(key)) return Mode.REPLACE;
        return SCALED.contains(key) ? Mode.SCALED : Mode.NONE;
    }

    /**
     * Construye el sustituto de un proyectil, copiando su estado por NBT: posición, vector de
     * vuelo, daño base, crítico, perforación, efectos de la flecha con punta y de qué arma
     * salió. Copiar por NBT y no campo a campo es lo que hace que una flecha de Poder V con
     * efecto de veneno siga siendo eso al infusionarse.
     * Se le quita el UUID antes de cargar: si no, las dos entidades comparten identidad y el
     * nivel se lía al eliminar la original.
     * @return null si el tipo destino no existe o no es un proyectil (JSON mal puesto).
     */
    public static Projectile buildReplacement(Projectile original) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(original.getType());
        ResourceLocation targetId = key == null ? null : REPLACE.get(key);
        if (targetId == null) return null;

        EntityType<?> target = BuiltInRegistries.ENTITY_TYPE.get(targetId);
        if (target == null) return null;

        Entity created = target.create(original.level());
        if (!(created instanceof Projectile p)) return null;

        // ⚠ API a verificar al compilar: Entity#saveWithoutId y Entity#load (públicos en 1.21.1).
        CompoundTag tag = original.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        p.load(tag);
        p.setOwner(original.getOwner());   // explícito: no dependemos de cómo serialice el dueño
        return p;
    }

    private static final class Loader extends SimplePreparableReloadListener<Loader.Data> {

        private record Data(Map<ResourceLocation, ResourceLocation> replace,
                            Set<ResourceLocation> scaled,
                            Set<ResourceLocation> blocked) {}

        @Override
        protected @NotNull Data prepare(@NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, ResourceLocation> replace = new LinkedHashMap<>();
            Set<ResourceLocation> scaled  = new LinkedHashSet<>();
            Set<ResourceLocation> blocked = new LinkedHashSet<>();

            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();

                    if (o.has("replace") && o.get("replace").isJsonObject()) {
                        for (var e : o.getAsJsonObject("replace").entrySet()) {
                            ResourceLocation from = ResourceLocation.tryParse(e.getKey());
                            ResourceLocation to = ResourceLocation.tryParse(e.getValue().getAsString());
                            if (from == null || to == null) {
                                LOGGER.warn("[Zenkai] Entrada 'replace' inválida en {}: {}", file, e.getKey());
                                continue;
                            }
                            replace.put(from, to);
                        }
                    }
                    readList(o, "scaled",  scaled,  file);
                    readList(o, "blocked", blocked, file);
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer {}: {}", file, ex.toString());
                }
            }
            return new Data(replace, scaled, blocked);
        }

        private static void readList(JsonObject o, String key, Set<ResourceLocation> out,
                                     ResourceLocation file) {
            if (!o.has(key) || !o.get(key).isJsonArray()) return;
            JsonArray arr = o.getAsJsonArray(key);
            for (int i = 0; i < arr.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(arr.get(i).getAsString());
                if (id == null) {
                    LOGGER.warn("[Zenkai] Id inválido en '{}' de {}: {}", key, file, arr.get(i));
                    continue;
                }
                out.add(id);
            }
        }

        @Override
        protected void apply(@NotNull Data data, @NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            REPLACE = Map.copyOf(data.replace());
            SCALED  = Set.copyOf(data.scaled());
            BLOCKED = Set.copyOf(data.blocked());
            LOGGER.info("[Zenkai] Ki projectiles: {} reemplazos, {} escalados, {} bloqueados.",
                    REPLACE.size(), SCALED.size(), BLOCKED.size());
        }
    }
}