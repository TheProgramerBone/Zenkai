package com.hmc.zenkai.feature.technique.vfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registro data-driven de {@link ZenkaiVfxEffect}, mismo patrón que
 * {@code EntityStatsManager}/{@code TechniqueManager} (Gson a mano, no Codec, por consistencia
 * con el resto de cargadores de datapack del mod). Ruta:
 * {@code data/<ns>/zenkai_vfx_effects/<...>/<id>.json}, id = ResourceLocation derivado de la
 * ruta del archivo (namespace + subcarpeta/nombre sin extensión) — igual que una receta vainilla,
 * no un campo "id" dentro del JSON, porque el efecto no necesita conocer su propio nombre.
 *
 * Paso 1 del roadmap de {@code .claude/pendiente/cobblemon-audit-vfx-animaciones.md} (§20).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ZenkaiVfxRegistry {
    private ZenkaiVfxRegistry() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-VfxEffects");
    private static final String FOLDER = "zenkai_vfx_effects";

    private static volatile Map<ResourceLocation, ZenkaiVfxEffect> EFFECTS = Map.of();

    @Nullable
    public static ZenkaiVfxEffect get(ResourceLocation id) {
        return EFFECTS.get(id);
    }

    public static Map<ResourceLocation, ZenkaiVfxEffect> all() {
        return EFFECTS;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, ZenkaiVfxEffect>> {

        @Override
        protected @NotNull Map<ResourceLocation, ZenkaiVfxEffect> prepare(@NotNull ResourceManager rm,
                                                                          @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, ZenkaiVfxEffect> out = new HashMap<>();
            var found = rm.listResources(FOLDER, loc -> loc.getPath().endsWith(".json"));
            for (var entry : found.entrySet()) {
                ResourceLocation file = entry.getKey();
                Resource resource = entry.getValue();
                String rel = file.getPath().substring(FOLDER.length() + 1,
                        file.getPath().length() - ".json".length());
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), rel);
                try (BufferedReader reader = resource.openAsReader()) {
                    JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                    out.put(id, parse(o, id));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer el efecto VFX {}: {}", file, ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, ZenkaiVfxEffect> defs,
                             @NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            EFFECTS = Map.copyOf(defs);
            LOGGER.info("[Zenkai] Efectos VFX cargados: {} definición(es).", EFFECTS.size());
        }
    }

    // ── Parseo JSON -> ZenkaiVfxEffect ───────────────────────────────────────

    private static ZenkaiVfxEffect parse(JsonObject o, ResourceLocation id) {
        List<ZenkaiVfxEffect.ParticleSpec> particles = new ArrayList<>();
        if (o.has("particles") && o.get("particles").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("particles")) {
                if (!el.isJsonObject()) continue;
                ZenkaiVfxEffect.ParticleSpec spec = parseParticle(el.getAsJsonObject(), id);
                if (spec != null) particles.add(spec);
            }
        }
        if (particles.isEmpty()) {
            LOGGER.warn("[Zenkai] Efecto VFX '{}' sin ninguna partícula válida.", id);
        }

        ZenkaiVfxEffect.SoundSpec sound = null;
        if (o.has("sound") && o.get("sound").isJsonObject()) {
            JsonObject s = o.getAsJsonObject("sound");
            if (s.has("id")) {
                ResourceLocation soundId = ResourceLocation.parse(s.get("id").getAsString());
                float volume = s.has("volume") ? s.get("volume").getAsFloat() : 1.0f;
                float pitch = s.has("pitch") ? s.get("pitch").getAsFloat() : 1.0f;
                sound = new ZenkaiVfxEffect.SoundSpec(soundId, volume, pitch);
            }
        }

        // "tint" y "frames": reservados por §12.Y.5 del informe, ver javadoc de ZenkaiVfxEffect.
        // Se parsean ya (para no tener que rediseñar el formato el día que el primer efecto los
        // use de verdad) aunque hoy ningún JSON real los declare.
        List<ZenkaiVfxEffect.TintStop> tint = null;
        if (o.has("tint") && o.get("tint").isJsonArray()) {
            tint = new ArrayList<>();
            for (JsonElement el : o.getAsJsonArray("tint")) {
                if (!el.isJsonObject()) continue;
                JsonObject t = el.getAsJsonObject();
                float at = t.has("t") ? t.get("t").getAsFloat() : 0f;
                int rgb = t.has("rgb") ? Integer.decode(t.get("rgb").getAsString()) & 0xFFFFFF : 0xFFFFFF;
                tint.add(new ZenkaiVfxEffect.TintStop(at, rgb));
            }
        }
        String frames = o.has("frames") ? o.get("frames").getAsString() : null;

        return new ZenkaiVfxEffect(List.copyOf(particles), sound, tint, frames);
    }

    private static ZenkaiVfxEffect.ParticleSpec parseParticle(JsonObject p, ResourceLocation ownerId) {
        String typeName = p.has("type") ? p.get("type").getAsString() : "";
        ZenkaiVfxEffect.ParticleSpec.Kind kind;
        try {
            kind = ZenkaiVfxEffect.ParticleSpec.Kind.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("[Zenkai] Tipo de partícula VFX '{}' desconocido en {}", typeName, ownerId);
            return null;
        }
        int count = p.has("count") ? p.get("count").getAsInt() : 1;
        float scale = p.has("scale") ? p.get("scale").getAsFloat() : 1.0f;
        boolean spreadFromTarget = p.has("spread_from_target") && p.get("spread_from_target").getAsBoolean();
        double spreadY = p.has("spread_y") ? p.get("spread_y").getAsDouble() : 0.0;
        double speed = p.has("speed") ? p.get("speed").getAsDouble() : 0.0;
        return new ZenkaiVfxEffect.ParticleSpec(kind, count, scale, spreadFromTarget, spreadY, speed);
    }
}
