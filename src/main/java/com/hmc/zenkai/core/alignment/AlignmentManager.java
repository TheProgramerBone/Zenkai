package com.hmc.zenkai.core.alignment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alineamiento (-100..+100). Los deltas por kill vienen de datapack (recargable con /reload):
 *   data/&lt;ns&gt;/zenkai_alignment/*.json
 * JSON:
 *   { "kill_deltas": { "minecraft:villager": -5, "minecraft:pillager": 3, "player": -10 } }
 * - Clave = id de EntityType, o la clave especial "player" (matar a otro jugador).
 * - Positivo sube el alineamiento del asesino, negativo lo baja.
 * - Varios archivos se fusionan; si dos definen la misma clave gana el último y se loguea.
 * El campo alignment vive en PlayerStatsAttachment (viaja con el sync de stats normal).
 * TODO: delta pasivo por estar bajo el efecto majin, cuando ese efecto exista.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class AlignmentManager {
    private AlignmentManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Alignment");
    private static final String FOLDER = "zenkai_alignment";
    public  static final String PLAYER_KEY = "player";

    /** clave ("ns:entity" o "player") -> delta. Volátil: se reemplaza entera en cada reload. */
    private static volatile Map<String, Integer> KILL_DELTAS = Map.of();

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        LivingEntity victim = event.getEntity();
        if (victim == killer) return;

        String key = (victim instanceof Player)
                ? PLAYER_KEY
                : BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();

        Integer delta = KILL_DELTAS.get(key);
        if (delta == null || delta == 0) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(killer);
        att.addAlignment(delta);
        PlayerLifeCycle.sync(killer);
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<String, Integer>> {

        @Override
        protected @NotNull Map<String, Integer> prepare(@NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            Map<String, Integer> out = new LinkedHashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!o.has("kill_deltas")) continue;
                    JsonObject deltas = o.getAsJsonObject("kill_deltas");
                    for (var e : deltas.entrySet()) {
                        Integer prev = out.put(e.getKey(), e.getValue().getAsInt());
                        if (prev != null) {
                            LOGGER.warn("[Zenkai] Delta de alineamiento duplicado '{}' (gana {}).", e.getKey(), file);
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer alineamiento en {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<String, Integer> data, @NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            KILL_DELTAS = Map.copyOf(data);
            LOGGER.info("[Zenkai] Alineamiento: {} entradas de kill_deltas cargadas.", data.size());
        }
    }
}