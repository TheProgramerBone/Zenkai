package com.hmc.zenkai.client.aura.compose;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.compose.AuraBodyParams.Key;
import com.hmc.zenkai.client.aura.compose.component.AuraComponentConfig;
import com.hmc.zenkai.client.aura.compose.component.AuraComponentTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Carga los ESTILOS de aura de {@code assets/<ns>/zenkai_aura_styles/<aura_type>.json}. Listener de
 * recursos de CLIENTE: F3+T (o {@code /zenkaiaura reload}) los relee sin reiniciar. En un run de
 * desarrollo, recuerda que se lee {@code build/}: {@code ./gradlew processResources} antes de
 * recargar (ver .claude/docs/build-reload-y-datapacks.md).
 * <p>
 * Formato (ver el detalle y ejemplos en .claude/docs/tecnicas-y-vfx.md, "Aura compuesta"):
 * <pre>
 * {
 *   "family": "divine",                       // flame | divine | dark_dense
 *   "body":     { "radius": 0.9 },            // multiplicadores sobre la familia
 *   "body_set": { "cloud": 0.8 },             // valores absolutos (dimensionales escalan por PL)
 *   "inner": { "mode": "always", "scale": 0.75, "amp": 0.5, "alpha": 3.5, "tip": 0.4, "emit": 0.3 },
 *   "components": [ { "type": "motes", ... } ],       // sustituye a los de la familia
 *   "extra_components": [ ... ],                      // se AÑADEN a los de la familia
 *   "ground": "clean", "transform": "radiant",
 *   "trail": { "width": 0.8, "jag": 0.35, "sway": 0.0, "alpha": 0.55, "core": 0.85, "deep": false, "points": 18 }
 * }
 * </pre>
 * Un error en un archivo se registra y ese estilo se ignora (el aura_type cae en su familia por
 * defecto, flame): una errata nunca deja a nadie sin aura. Las claves desconocidas solo avisan.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraStyleManager extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final Set<String> TOP_KEYS = Set.of("family", "body", "body_set", "inner", "components",
            "extra_components", "ground", "transform", "trail", "_comment");

    private static volatile Map<String, AuraStyle> styles = Map.of();

    public static final AuraStyleManager INSTANCE = new AuraStyleManager();

    private AuraStyleManager() { super(GSON, "zenkai_aura_styles"); }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(INSTANCE);
    }

    /** Estilo de un aura_type; sin JSON, la familia flame desnuda (el aspecto de antes). */
    public static AuraStyle get(@Nullable String auraType) {
        String t = auraType == null ? "default" : auraType;
        AuraStyle s = styles.get(t);
        return s != null ? s : AuraStyle.bare(t, AuraFamilies.FLAME);
    }

    public static Set<String> loadedTypes() { return styles.keySet(); }

    /** Relectura síncrona solo de los estilos, para {@code /zenkaiaura reload} (F3+T relee los recursos enteros). */
    public void reloadNow(ResourceManager rm, ProfilerFiller profiler) {
        apply(prepare(rm, profiler), rm, profiler);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, AuraStyle> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            String type = e.getKey().getPath();
            try {
                out.put(type, parse(type, e.getValue().getAsJsonObject()));
            } catch (RuntimeException ex) {
                Zenkai.LOGGER.error("[Zenkai] Estilo de aura {} ignorado: {}", e.getKey(), ex.getMessage());
            }
        }
        styles = Map.copyOf(out);
        Zenkai.LOGGER.info("[Zenkai] Estilos de aura cargados: {}", out.keySet());
    }

    static AuraStyle parse(String type, JsonObject o) {
        for (String k : o.keySet()) {
            if (!TOP_KEYS.contains(k)) Zenkai.LOGGER.warn("[Zenkai] Estilo de aura {}: clave desconocida \"{}\"", type, k);
        }
        String famId = o.has("family") ? o.get("family").getAsString() : "flame";
        AuraFamily family = AuraFamilies.get(famId);
        if (family == null) {
            throw new JsonParseException("familia desconocida \"" + famId + "\" (existen: " + AuraFamilies.ids() + ")");
        }
        return new AuraStyle(type, family,
                keyMap(type, o, "body"), keyMap(type, o, "body_set"),
                o.has("inner") ? parseInner(o.getAsJsonObject("inner"), family) : null,
                o.has("components") ? components(o.getAsJsonArray("components")) : null,
                o.has("extra_components") ? components(o.getAsJsonArray("extra_components")) : List.of(),
                o.has("ground") ? o.get("ground").getAsString() : null,
                o.has("transform") ? o.get("transform").getAsString() : null,
                o.has("trail") ? family.trail().withJson(o.getAsJsonObject("trail")) : null);
    }

    /** Mapa clave de cuerpo → valor de un objeto JSON (lo usan también componentes como shell). */
    public static Map<Key, Float> keyMap(String type, JsonObject o, String field) {
        if (!o.has(field)) return Map.of();
        EnumMap<Key, Float> m = new EnumMap<>(Key.class);
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(field).entrySet()) {
            Key k = Key.byName(e.getKey());
            if (k == null) {
                Zenkai.LOGGER.warn("[Zenkai] Estilo de aura {}: clave de cuerpo desconocida \"{}\" en {}", type, e.getKey(), field);
                continue;
            }
            m.put(k, e.getValue().getAsFloat());
        }
        return Map.copyOf(m);
    }

    private static AuraInnerSpec parseInner(JsonObject o, AuraFamily family) {
        AuraInnerSpec base = family.inner();
        AuraInnerSpec.Mode mode = o.has("mode")
                ? AuraInnerSpec.Mode.valueOf(o.get("mode").getAsString().toUpperCase(Locale.ROOT)) : base.mode();
        AuraBodyKind kind = o.has("kind")
                ? AuraBodyKind.valueOf(o.get("kind").getAsString().toUpperCase(Locale.ROOT)) : base.kind();
        return new AuraInnerSpec(mode, kind,
                AuraComponentTypes.f(o, "scale", base.scale()),
                AuraComponentTypes.f(o, "amp", base.amp()),
                AuraComponentTypes.f(o, "alpha", base.alpha()),
                AuraComponentTypes.f(o, "tip", base.tip()),
                AuraComponentTypes.f(o, "emit", base.emit()));
    }

    private static List<AuraComponentConfig> components(JsonArray arr) {
        List<AuraComponentConfig> list = new ArrayList<>();
        for (JsonElement el : arr) list.add(AuraComponentTypes.parse(el.getAsJsonObject()));
        return List.copyOf(list);
    }
}
