package com.hmc.zenkai.feature.master;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carga los maestros desde datapack (data/&lt;ns&gt;/zenkai_masters/*.json) y decide, en un
 * solo sitio, si un jugador es admitido.
 *
 * ESTE ES EL EMBUDO. Tanto el clic derecho sobre la entidad como la compra de una habilidad
 * pasan por {@link #check}: si el rechazo se duplicara, un jugador podría abrir el menú y
 * comprar desde lejos, o al revés, y las dos comprobaciones se separarían en cuanto alguien
 * tocara una de las dos.
 *
 * Los maestros SIN JSON existen igualmente con {@link MasterDef#open}: enseñan a cualquiera.
 * Así una entidad nueva funciona antes de que exista su archivo, en vez de quedar muda.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class MasterManager {
    private MasterManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Masters");
    private static final String FOLDER = "zenkai_masters";

    /** Distancia máxima para hablar y para comprar. Al cuadrado en las comparaciones. */
    public static final double INTERACT_RANGE = 8.0;

    private static volatile Map<String, MasterDef> REGISTRY = Map.of();

    public static MasterDef get(String id) {
        MasterDef d = REGISTRY.get(id);
        return d != null ? d : MasterDef.open(id);
    }

    // ── Admisión ─────────────────────────────────────────────────────────────

    /** Por qué te rechaza, o OK. Cada motivo lleva su propia clave de lang. */
    public enum Result {
        OK(null),
        TOO_WEAK("messages.zenkai.master.too_weak"),
        BAD_ALIGNMENT("messages.zenkai.master.alignment"),
        TOO_FAR(null),          // silencioso: es anti-trampa, no un mensaje de juego
        NO_RACE(null);

        public final String key;
        Result(String key) { this.key = key; }
        public boolean ok() { return this == OK; }
    }

    /**
     * ¿Puede este jugador tratar con este maestro AHORA MISMO?
     *
     * @param master entidad del maestro; si es null se salta la comprobación de distancia
     *               (no la hay que hacer cuando el propio maestro es quien pregunta)
     */
    public static Result check(ServerPlayer sp, String masterId, Entity master) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return Result.NO_RACE;

        if (master != null) {
            if (master.level() != sp.level()) return Result.TOO_FAR;
            if (master.distanceToSqr(sp) > INTERACT_RANGE * INTERACT_RANGE) return Result.TOO_FAR;
        }

        MasterDef def = get(masterId);

        // PL LIBERABLE, no el crudo. Un jugador con 1600 de potencial que solo puede sacar el
        // 50% vale 800 delante de un maestro: lo que no puedes usar no impresiona a nadie.
        if (att.getReleasablePowerLevel() < def.plReq()) return Result.TOO_WEAK;

        if (!def.alignmentOk(att.getAlignment())) return Result.BAD_ALIGNMENT;
        return Result.OK;
    }

    /** Entidad de ESE maestro cerca del jugador, o null si no está delante de él. Compartido
     *  por cualquier flujo "solo se consigue en persona": antes vivía como bucle duplicado
     *  dentro de SkillBuyPacket; TechniquePacket/PhysicalTechniquePacket lo reusan igual para
     *  el nivel 1 de una técnica firma (ver TechniqueDef, "TÉCNICA FIRMA"). */
    public static Entity findNearby(ServerPlayer sp, String masterId) {
        for (Entity e : sp.level().getEntities(sp, sp.getBoundingBox().inflate(INTERACT_RANGE))) {
            if (e instanceof ZenkaiMasterEntity m && masterId.equals(m.masterId())) return e;
        }
        return null;
    }

    /** Manda el mensaje de rechazo al CHAT (no a la action bar) con el nombre del maestro
     *  delante, para que se lea como si hablara él. Los motivos sin clave no dicen nada. */
    public static void tell(ServerPlayer sp, String masterId, Result result) {
        if (result.ok() || result.key == null) return;
        sp.sendSystemMessage(Component.translatable(result.key,
                Component.translatable(get(masterId).nameKey())).withStyle(ChatFormatting.GRAY));
    }

    // ── Carga ────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<String, MasterDef>> {

        @Override
        protected @NotNull Map<String, MasterDef> prepare(@NotNull ResourceManager rm,
                                                          @NotNull ProfilerFiller profiler) {
            Map<String, MasterDef> out = new LinkedHashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));

            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                String id = path.substring(FOLDER.length() + 1, path.length() - ".json".length());

                try (BufferedReader reader = entry.getValue().openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                    long plReq = GsonHelper.getAsLong(json, "pl_req", 0L);
                    int min = GsonHelper.getAsInt(json, "alignment_min", -100);
                    int max = GsonHelper.getAsInt(json, "alignment_max", 100);

                    if (min > max) {
                        LOGGER.warn("Maestro {}: alignment_min ({}) > alignment_max ({}). " +
                                "No admitiría a nadie; se ignora el rango.", id, min, max);
                        min = -100; max = 100;
                    }

                    MasterDef prev = out.put(id, new MasterDef(id, plReq, min, max));
                    if (prev != null) LOGGER.warn("Maestro duplicado '{}': gana {}", id, file);

                } catch (Exception e) {
                    LOGGER.error("No se pudo leer el maestro {}", file, e);
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<String, MasterDef> defs, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            REGISTRY = Map.copyOf(defs);
            LOGGER.info("Maestros cargados: {}", REGISTRY.size());
        }
    }
}