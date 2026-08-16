package com.hmc.zenkai.feature.generator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
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
import java.util.List;

/**
 * Carga los combustibles del generador y resuelve, en un solo sitio, cuánto rinde un ítem.
energy_generator * ESTE ES EL EMBUDO. Lo consultan el block entity (para quemar), el menú (para decidir si un
 * ítem entra en un hueco) y la pantalla (para el tooltip). Con la tabla replicada, un ítem
 * podría dejarse meter y luego no arder, que es el peor fallo posible aquí: el jugador ve el
 * hueco lleno y la máquina parada.
energy_generator * EL ORDEN DE RESOLUCIÓN IMPORTA: primero coincidencia por ÍTEM y solo después por TAG. Así
 * un datapack puede darle al carbón vegetal un valor distinto del resto de c:coals sin tener
 * que sacarlo del tag. Al revés, el tag ganaría siempre y la entrada específica sería
 * inalcanzable.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class GeneratorFuels {
    private GeneratorFuels() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-GeneratorFuels");
    private static final String FOLDER = "zenkai_generator_fuels";

    private static volatile List<GeneratorFuel.Entry> ENTRIES = List.of();

    /** Reemplaza el snapshot completo (reload del server o sync al cliente). */
    public static void replaceAll(List<GeneratorFuel.Entry> entries) {
        ENTRIES = List.copyOf(entries);
    }

    public static List<GeneratorFuel.Entry> all() { return ENTRIES; }

    /** null si este ítem no es combustible del generador. */
    public static GeneratorFuel of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (GeneratorFuel.Entry e : ENTRIES) {
            if (!e.isTag() && e.key().equals(itemId)) return e.fuel();
        }
        for (GeneratorFuel.Entry e : ENTRIES) {
            // ItemTags.create YA devuelve el TagKey<Item>: no hay que reconstruirlo a mano.
            if (e.isTag() && stack.is(ItemTags.create(e.key()))) return e.fuel();
        }
        return null;
    }

    public static boolean isFuel(ItemStack stack) { return of(stack) != null; }

    // ── Carga ────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    /** Login de un jugador o /reload. Mismo patrón que SkillManager. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        GeneratorFuelSyncPacket pkt = new GeneratorFuelSyncPacket(ENTRIES);
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static final class Loader
            extends SimplePreparableReloadListener<List<GeneratorFuel.Entry>> {

        @Override
        protected @NotNull List<GeneratorFuel.Entry> prepare(@NotNull ResourceManager rm,
                                                             @NotNull ProfilerFiller profiler) {
            List<GeneratorFuel.Entry> out = new ArrayList<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                    boolean hasItem = json.has("item");
                    boolean hasTag  = json.has("tag");
                    if (hasItem == hasTag) {
                        LOGGER.warn("Combustible {}: hay que dar 'item' O 'tag', no los dos ni ninguno.", file);
                        continue;
                    }

                    String raw = GsonHelper.getAsString(json, hasTag ? "tag" : "item");
                    ResourceLocation key = ResourceLocation.tryParse(raw);
                    if (key == null) {
                        LOGGER.warn("Combustible {}: id no válido '{}'.", file, raw);
                        continue;
                    }

                    int fePerTick = GsonHelper.getAsInt(json, "fe_per_tick", 0);
                    int ticks     = GsonHelper.getAsInt(json, "ticks", 0);
                    if (fePerTick <= 0 || ticks <= 0) {
                        // Un combustible de 0 se dejaría meter y no ardería nunca: la máquina
                        // se quedaría con el hueco ocupado y parada, sin decir por qué.
                        LOGGER.warn("Combustible {}: fe_per_tick y ticks deben ser > 0.", file);
                        continue;
                    }

                    // Ítem inexistente = mod ausente. No es un error: un datapack puede traer
                    // entradas para mods opcionales y debe seguir cargando sin ellos.
                    if (!hasTag && !BuiltInRegistries.ITEM.containsKey(key)) continue;

                    out.add(new GeneratorFuel.Entry(key, hasTag, new GeneratorFuel(fePerTick, ticks)));

                } catch (Exception e) {
                    LOGGER.error("No se pudo leer el combustible {}", file, e);
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull List<GeneratorFuel.Entry> defs, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            replaceAll(defs);
            LOGGER.info("Combustibles del generador cargados: {}", defs.size());
        }
    }
}