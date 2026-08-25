package com.hmc.zenkai.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.aura.AuraCeiling;
import com.hmc.zenkai.feature.stats.RaceStatSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carga los coeficientes de stat por raza desde datapack
 * (data/&lt;ns&gt;/zenkai_race_stats/&lt;raza&gt;.json) y los sincroniza al cliente en el login
 * y en cada /reload.
 *
 * UN ARCHIVO POR RAZA, con los atributos de salida y las tres filas de estilo dentro:
 * {
 *   "base_attributes": { "str": 7, "con": 5, "dex": 6, "wil": 4, "spi": 3, "mnd": 10 },
 *   "warrior":        { "melee": 11.0, "defense": 4.6, "health": 28, "stamina": 13.0,
 *                       "ki_damage": 4.2, "ki_reserves": 40,
 *                       "ki_cost_mult": 0.75, "stamina_cost_mult": 0.55 },
 *   "martial_artist": { ... },
 *   "spiritualist":   { ... }
 * }
 * El nombre del archivo ES la raza (saiyan.json).
 *
 * ═══ DE DÓNDE SALEN LOS VALORES QUE UN JSON NO DEFINE ═══
 *
 * Antes: de una tabla compilada dentro de RaceStatTable. Esa tabla se había separado de los
 * JSON hasta ser casi el doble en los atributos base, y como cubría cualquier hueco, un
 * datapack roto no daba error: daba un personaje inflado que parecía legítimo. La tabla ya no
 * existe (ver RaceStatTable), así que aquí no hay a qué caer.
 *
 * Ahora: de la CAPA INFERIOR del propio datapack. Se usa listResourceStacks, que devuelve
 * el conjunto de versiones del mismo archivo ordenadas de menor a mayor prioridad, y se fusionan en
 * ese orden. Los JSON que viajan dentro del jar son la capa de abajo y hacen de referencia; un
 * datapack que solo quiera retocar la vida de los majin escribe únicamente esa clave y el
 * resto sigue viniendo de la capa base.
 *
 * Esto además arregla un fallo que los defaults compilados tapaban: listResources (sin Stacks)
 * devuelve SOLO el recurso de mayor prioridad, así que un datapack que sobrescribiera
 * saiyan.json con solo el bloque "warrior" borraba martial_artist y spiritualist por completo,
 * y el jugador se quedaba con los valores de Java sin enterarse.
 *
 * ═══ LOS FALLOS SON RUIDOSOS ═══
 *
 * Un JSON ilegible o una raza incompleta ya no se registran como aviso y se siguen: la
 * excepción sube y hace fallar el /reload o el arranque del mundo, con el archivo y la clave
 * concretos en el mensaje. Es preferible enterarse al recargar que descubrirlo veinte horas
 * después comparando números a mano.
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

            // ⚠ API: listResourceStacks devuelve el conjunto de capas del mismo path, de menor a
            // mayor prioridad. listResources (sin Stacks) devolvería solo la de arriba.
            Map<ResourceLocation, List<Resource>> found =
                    rm.listResourceStacks(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String name = file.getPath().substring(
                        FOLDER.length() + 1, file.getPath().length() - ".json".length());

                Race race;
                try {
                    race = Race.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    // Un archivo con nombre desconocido SÍ se ignora con aviso: es un datapack
                    // ajeno añadiendo una raza que este mod no tiene, no un dato corrupto.
                    LOGGER.warn("[Zenkai] {} no corresponde a ninguna raza: ignorado.", file);
                    continue;
                }

                // Acumuladores por raza: cada capa escribe encima de la anterior.
                int[] base = null;
                Map<Style, double[]> byStyle = new EnumMap<>(Style.class);

                for (Resource resource : entry.getValue()) {
                    try (BufferedReader reader = resource.openAsReader()) {
                        JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();

                        // Claves NOMBRADAS y no una lista posicional: el formato viejo de config
                        // documentaba [STR, DEX, CON, ...] y el consumidor leía
                        // [STR, CON, DEX, ...], así que CON y DEX salían intercambiadas.
                        if (o.has("base_attributes") && o.get("base_attributes").isJsonObject()) {
                            base = readBase(o.getAsJsonObject("base_attributes"), base);
                        }

                        for (Style style : Style.values()) {
                            String key = style.name().toLowerCase(Locale.ROOT);
                            if (!o.has(key) || !o.get(key).isJsonObject()) continue;
                            byStyle.put(style, readRow(o.getAsJsonObject(key), byStyle.get(style)));
                        }
                    } catch (Exception ex) {
                        // Se lanza en vez de loguear y seguir: si este archivo era el único que
                        // definía una raza, seguir significaba arrancar con esa raza a cero.
                        throw new RaceStatTable.MissingRaceStatsException(
                                "zenkai_race_stats: no se pudo leer " + file
                                        + " (pack: " + resource.sourcePackId() + "): " + ex);
                    }
                }

                if (base != null) bases.put(race, base);
                if (!byStyle.isEmpty()) {
                    // merge y no put: dos archivos distintos pueden aportar estilos distintos
                    // de la misma raza, y el segundo no debe borrar lo del primero.
                    out.computeIfAbsent(race, k -> new EnumMap<>(Style.class)).putAll(byStyle);
                }
            }
            return new Loaded(out, bases);
        }

        /**
         * Atributos de salida, indexados por ZenkaiAttributes.ordinal().
         * @param prev lo que dejó la capa inferior, o null si esta es la primera.
         */
        private static int[] readBase(JsonObject o, int[] prev) {
            int[] d = (prev != null) ? prev : new int[RaceStatTable.BASE_ATTRS];
            // Sin capa previa el default es 0, no un número inventado: un atributo base a cero
            // canta a la primera en la pantalla de stats; un 10 plausible no lo haría.
            return new int[]{
                    GsonHelper.getAsInt(o, "str", d[0]),
                    GsonHelper.getAsInt(o, "con", d[1]),
                    GsonHelper.getAsInt(o, "dex", d[2]),
                    GsonHelper.getAsInt(o, "wil", d[3]),
                    GsonHelper.getAsInt(o, "spi", d[4]),
                    GsonHelper.getAsInt(o, "mnd", d[5])
            };
        }

        /**
         * Una fila de estilo. Los campos que falten vienen de la capa inferior.
         * @param prev fila de la capa inferior, o null si esta es la primera.
         */
        private static double[] readRow(JsonObject o, double[] prev) {
            double[] d = new double[RaceStatTable.COLS];
            if (prev != null) {
                System.arraycopy(prev, 0, d, 0, Math.min(prev.length, d.length));
            } else {
                // Los dos multiplicadores de coste arrancan en 1.0 (neutro) y el resto en 0.
                // No es simetría rota por descuido: un coeficiente de rendimiento a cero da un
                // stat a cero, que se ve; un multiplicador de COSTE a cero daría técnicas
                // gratis, que se ve tarde y encima parece una ventaja.
                d[RaceStatTable.Col.KI_COST.ordinal()] = 1.0;
                d[RaceStatTable.Col.STAM_COST.ordinal()] = 1.0;
            }
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
            // Sin try/catch a propósito: replaceAll y replaceBases validan que estén las quince
            // combinaciones y las cinco razas, y su excepción tiene que llegar hasta el /reload
            // o el arranque del mundo con la lista de lo que falta. Capturarla aquí devolvería
            // el mod al comportamiento anterior: seguir jugando con datos incompletos.
            RaceStatTable.replaceAll(loaded.rows());
            AuraCeiling.invalidate();
            RaceStatTable.replaceBases(loaded.bases());
            LOGGER.info("[Zenkai] Stats de raza cargadas: {} raza(s), {} con atributos base.",
                    loaded.rows().size(), loaded.bases().size());
        }
    }
}