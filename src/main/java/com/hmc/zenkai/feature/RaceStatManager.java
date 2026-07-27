package com.hmc.zenkai.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.stats.RaceStatSyncPacket;
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
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Carga los coeficientes de stat por raza desde datapack
 * (data/&lt;ns&gt;/zenkai_race_stats/&lt;raza&gt;.json) y los sincroniza al cliente en el login
 * y en cada /reload. Espeja SkillManager y FormManager.
 *
 * UN ARCHIVO POR RAZA, con los atributos de salida y las tres filas de estilo dentro:
 * {
 *   "base_attributes": { "str": 14, "con": 10, "dex": 12, "wil": 8, "spi": 6, "mnd": 10 },
 *   "warrior":        { "melee": 11.0, "defense": 4.6, "health": 28, "stamina": 13.0,
 *                       "ki_damage": 4.2, "ki_reserves": 40 },
 *   "martial_artist": { ... },
 *   "spiritualist":   { ... }
 * }
 * El nombre del archivo ES la raza (saiyan.json). Las claves que falten caen al default
 * compilado de RaceStatTable, así que un JSON parcial es válido: se puede publicar un
 * datapack que solo retoque la vida de los majin.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class RaceStatManager {
    private RaceStatManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-RaceStats");
    private static final String FOLDER = "zenkai_race_stats";

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Login de un jugador (getPlayer() != null) o /reload (null = broadcast). */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        RaceStatSyncPacket pkt = RaceStatSyncPacket.snapshot();
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    /** Lo que sale de una recarga: coeficientes por estilo + atributos de salida. */
    public record Loaded(Map<Race, Map<Style, double[]>> rows, Map<Race, int[]> bases) {}

    private static final class Loader extends SimplePreparableReloadListener<Loaded> {

        @Override
        protected @NotNull Loaded prepare(
                @NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            Map<Race, Map<Style, double[]>> out = new EnumMap<>(Race.class);
            Map<Race, int[]> bases = new EnumMap<>(Race.class);
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String name = file.getPath().substring(
                        FOLDER.length() + 1, file.getPath().length() - ".json".length());

                Race race;
                try {
                    race = Race.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    LOGGER.warn("[Zenkai] {} no corresponde a ninguna raza: ignorado.", file);
                    continue;
                }

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();

                    // Claves NOMBRADAS y no una lista posicional: el formato viejo de config
                    // documentaba [STR, DEX, CON, ...] y el consumidor leía [STR, CON, DEX, ...],
                    // así que CON y DEX salían intercambiadas en las cinco razas.
                    if (o.has("base_attributes") && o.get("base_attributes").isJsonObject()) {
                        bases.put(race, readBase(o.getAsJsonObject("base_attributes"), race));
                    }

                    Map<Style, double[]> byStyle = new EnumMap<>(Style.class);

                    for (Style style : Style.values()) {
                        String key = style.name().toLowerCase(Locale.ROOT);
                        if (!o.has(key) || !o.get(key).isJsonObject()) continue;
                        byStyle.put(style, readRow(o.getAsJsonObject(key), race, style));
                    }

                    if (byStyle.isEmpty()) {
                        // Puede traer solo base_attributes: es un datapack válido.
                        continue;
                    }
                    // merge y no put: dos datapacks pueden aportar estilos distintos de la
                    // misma raza, y el segundo no debe borrar lo del primero.
                    out.computeIfAbsent(race, k -> new EnumMap<>(Style.class)).putAll(byStyle);
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer {}: {}", file, ex.toString());
                }
            }
            return new Loaded(out, bases);
        }

        /** Atributos de salida, indexados por ZenkaiAttributes.ordinal(). */
        private static int[] readBase(JsonObject o, Race race) {
            int[] d = RaceStatTable.baseAttributes(race);
            return new int[]{
                    GsonHelper.getAsInt(o, "str", d[0]),
                    GsonHelper.getAsInt(o, "con", d[1]),
                    GsonHelper.getAsInt(o, "dex", d[2]),
                    GsonHelper.getAsInt(o, "wil", d[3]),
                    GsonHelper.getAsInt(o, "spi", d[4]),
                    GsonHelper.getAsInt(o, "mnd", d[5])
            };
        }

        /** Cada campo cae a su default compilado si falta: los JSON parciales son válidos. */
        private static double[] readRow(JsonObject o, Race race, Style style) {
            double[] def = RaceStatTable.row(race, style);
            double[] d = new double[RaceStatTable.COLS];
            java.util.Arrays.fill(d, 1.0);
            if (def != null) System.arraycopy(def, 0, d, 0, Math.min(def.length, d.length));
            return new double[]{
                    GsonHelper.getAsDouble(o, "melee",             d[0]),
                    GsonHelper.getAsDouble(o, "defense",           d[1]),
                    GsonHelper.getAsDouble(o, "health",            d[2]),
                    GsonHelper.getAsDouble(o, "stamina",           d[3]),
                    GsonHelper.getAsDouble(o, "ki_damage",         d[4]),
                    GsonHelper.getAsDouble(o, "ki_reserves",       d[5]),
                    GsonHelper.getAsDouble(o, "ki_cost_mult",      d[6]),
                    GsonHelper.getAsDouble(o, "stamina_cost_mult", d[7])
            };
        }

        @Override
        protected void apply(@NotNull Loaded loaded,
                             @NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            RaceStatTable.replaceAll(loaded.rows());
            RaceStatTable.replaceBases(loaded.bases());
            LOGGER.info("[Zenkai] Stats de raza cargadas: {} raza(s), {} con bases propias.",
                    loaded.rows().size(), loaded.bases().size());
        }
    }
}