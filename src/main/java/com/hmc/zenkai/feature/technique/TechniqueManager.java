package com.hmc.zenkai.feature.technique;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Carga los números de las técnicas desde datapack, les aplica los overrides en caliente y
 * sincroniza el resultado al cliente (login, /reload y cada {@code /zenkai tech ... set}).
 *
 * Ruta: data/&lt;ns&gt;/zenkai_techniques/&lt;ki|physical&gt;/&lt;id&gt;.json
 * El id debe coincidir con el nombre del enum en minúsculas; un JSON sin enum se ignora con
 * warn, y un enum sin JSON queda DESACTIVADO salvo que tenga overrides.
 *
 * TRES CAPAS, en este orden: fábrica ({@link TechniqueField#factoryDefault}) → datapack (JSON)
 * → override ({@link TechniqueOverrides}). {@link Base} guarda las dos primeras ya fundidas
 * MÁS el conjunto de campos que el JSON declaraba de verdad, que es lo único que permite a
 * {@code info} distinguir "lo pone el datapack" de "es el default".
 *
 * QUIÉN COMPONE Y CUÁNDO: {@link #rebuild} es el único sitio que llama a
 * {@link TechniqueDef#replaceAll} con overrides aplicados. El reload listener deja el registro
 * en la capa datapack pura porque corre durante el arranque, antes de que exista el nivel del
 * que cuelga el SavedData; {@link ServerStartedEvent} lo completa antes de que entre nadie.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class TechniqueManager {
    private TechniqueManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Techniques");
    private static final String FOLDER = "zenkai_techniques";

    /** Capa datapack: el def con los defaults ya rellenados y qué campos venían del JSON. */
    public record Base(TechniqueDef def, EnumSet<TechniqueField> declared) {}

    private static volatile Map<String, Base> BASE = Map.of();

    private static String key(TechniqueDef.Kind kind, String id) { return kind.name() + "/" + id; }

    /** null = ningún datapack define esta técnica (puede seguir existiendo por overrides). */
    public static Base base(TechniqueDef.Kind kind, String id) { return BASE.get(key(kind, id)); }

    // ── Eventos ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Arranque: el reload ya dejó la capa datapack; aquí se le suman los overrides. Todavía
     *  no hay jugadores, así que no hace falta broadcast. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuild(event.getServer());
    }

    /** Login de un jugador (getPlayer() != null) o /reload (null = broadcast). En el /reload
     *  hay que recomponer ANTES de enviar: el listener acaba de pisar el registro con la capa
     *  datapack pura y los overrides estarían perdidos hasta el siguiente set. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            rebuild(event.getPlayerList().getServer());
            PacketDistributor.sendToAllPlayers(snapshot());
        } else {
            PacketDistributor.sendToPlayer(event.getPlayer(), snapshot());
        }
    }

    /** Recompone y reparte. Lo llaman los comandos tras tocar un override. */
    public static void rebuildAndSync(MinecraftServer server) {
        rebuild(server);
        PacketDistributor.sendToAllPlayers(snapshot());
    }

    private static TechniqueSyncPacket snapshot() {
        return new TechniqueSyncPacket(List.copyOf(TechniqueDef.all()));
    }

    // ── Composición ──────────────────────────────────────────────────────────

    /** Compartido con {@link TechniqueCommands}: es la MISMA pregunta ("¿este kind/id es un
     *  tipo real del enum?") y antes vivía duplicada en los dos archivos. */
    static boolean exists(TechniqueDef.Kind kind, String id) {
        return kind == TechniqueDef.Kind.KI
                ? KiTechniqueType.byName(id) != null
                : PhysicalTechnique.byName(id) != null;
    }

    private static void rebuild(MinecraftServer server) {
        Map<String, Base> base = BASE;
        TechniqueOverrides ov = TechniqueOverrides.get(server);

        Set<String> keys = new LinkedHashSet<>(base.keySet());
        keys.addAll(ov.all().keySet());

        Map<String, TechniqueDef> out = new LinkedHashMap<>(keys.size());
        for (String k : keys) {
            int slash = k.indexOf('/');
            if (slash <= 0) continue;
            TechniqueDef.Kind kind;
            try {
                kind = TechniqueDef.Kind.valueOf(k.substring(0, slash));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            final String id = k.substring(slash + 1);
            if (!exists(kind, id)) {
                LOGGER.warn("[Zenkai] Override de técnica inexistente '{}': ignorado.", k);
                continue;
            }
            final TechniqueDef.Kind fKind = kind;
            Base b = base.get(k);
            EnumMap<TechniqueField, Object> values = new EnumMap<>(TechniqueField.class);
            for (TechniqueField f : TechniqueField.values()) {
                if (!f.applies(fKind)) continue;
                values.put(f, b != null ? f.get(b.def()) : f.factoryDefault());
            }
            ov.values(fKind, id).forEach((f, v) -> { if (f.applies(fKind)) values.put(f, v); });
            out.put(k, TechniqueField.build(id, fKind, values));
        }

        TechniqueDef.replaceAll(out);

        for (KiTechniqueType t : KiTechniqueType.values()) {
            if (!t.enabled()) LOGGER.error("[Zenkai] Técnica ki '{}' SIN JSON: desactivada.", t.id());
        }
        for (PhysicalTechnique t : PhysicalTechnique.values()) {
            if (!t.enabled()) LOGGER.error("[Zenkai] Técnica física '{}' SIN JSON: desactivada.", t.id());
        }
        LOGGER.info("[Zenkai] Técnicas activas: {} ({} con override).", out.size(), ov.all().size());
    }

    // ── Carga ────────────────────────────────────────────────────────────────

    private static final class Loader extends SimplePreparableReloadListener<Map<String, Base>> {

        @Override
        protected @NotNull Map<String, Base> prepare(@NotNull ResourceManager rm,
                                                     @NotNull ProfilerFiller profiler) {
            Map<String, Base> out = new LinkedHashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                // zenkai_techniques/<folder>/<id>.json
                String rel = file.getPath().substring(FOLDER.length() + 1,
                        file.getPath().length() - ".json".length());
                int slash = rel.indexOf('/');
                if (slash <= 0) {
                    LOGGER.warn("[Zenkai] Técnica sin subcarpeta ki/ o physical/: {}", file);
                    continue;
                }
                TechniqueDef.Kind kind = TechniqueDef.Kind.byFolder(rel.substring(0, slash));
                String id = rel.substring(slash + 1).toLowerCase(Locale.ROOT);
                if (kind == null) {
                    LOGGER.warn("[Zenkai] Subcarpeta de técnica desconocida en {}", file);
                    continue;
                }
                // El enum es la identidad: un JSON sin enum no sirve para nada.
                if (!exists(kind, id)) {
                    LOGGER.warn("[Zenkai] Técnica '{}' ({}) no existe en el enum: ignorada.", id, kind);
                    continue;
                }

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    EnumMap<TechniqueField, Object> values = new EnumMap<>(TechniqueField.class);
                    EnumSet<TechniqueField> declared = EnumSet.noneOf(TechniqueField.class);
                    for (TechniqueField f : TechniqueField.values()) {
                        if (!f.applies(kind)) continue;
                        Object v = f.readJson(o);
                        if (v != null) declared.add(f);
                        values.put(f, v != null ? v : f.factoryDefault());
                    }
                    Base def = new Base(TechniqueField.build(id, kind, values), declared);
                    if (out.put(key(kind, id), def) != null) {
                        LOGGER.warn("[Zenkai] Técnica duplicada '{}/{}': gana {}.", kind.folder(), id, file);
                    }
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer la técnica en {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        /**
         * Deja el registro en la capa datapack pura. Los overrides los aplica {@link #rebuild},
         * que necesita el servidor: en el arranque este apply corre antes de que exista el
         * nivel del que cuelga el SavedData, y en /reload lo remata OnDatapackSyncEvent.
         */
        @Override
        protected void apply(@NotNull Map<String, Base> defs, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            BASE = Collections.unmodifiableMap(new LinkedHashMap<>(defs));
            Map<String, TechniqueDef> plain = new LinkedHashMap<>(defs.size());
            defs.forEach((k, b) -> plain.put(k, b.def()));
            TechniqueDef.replaceAll(plain);
            LOGGER.info("[Zenkai] Técnicas en datapack: {} definición(es).", defs.size());
        }
    }
}
