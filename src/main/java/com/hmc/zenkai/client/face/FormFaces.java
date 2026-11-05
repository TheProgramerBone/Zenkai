package com.hmc.zenkai.client.face;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.race.face.FaceCatalog;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.FacePartDef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CARA POR FORMA (fase 3 de las expresiones, 2026-11-01): qué rasgos de la cara impone una forma mientras está activa y
 * cuáles brillan (ojos emisivos). Datos SOLO DE CLIENTE en {@code assets/<ns>/zenkai_form_faces/<forma>.json} (la
 * forma {@code zenkai:ssj2} es {@code zenkai_form_faces/ssj2.json}); sin red y sin tocar {@code FormDef}.
 * <pre>
 * { "eyes": "zenkai:eyes/narrow", "mouth": "zenkai:mouth/determined",   // sustituye el rasgo elegido por el jugador
 *   "eyebrows": "...", "nose": "...",                                   // (cualquier categoría del catálogo)
 *   "emissive": ["eyes"] }                                              // categorías que se pintan a pleno brillo
 * </pre>
 * Un override solo vale si el rasgo existe en el catálogo y lo admite la raza ({@link FacePartDef#allows}); si no, se
 * respeta el del jugador. La expresión (parpadeo, daño...) se aplica DESPUÉS, sobre el rasgo ya sustituido.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class FormFaces extends SimpleJsonResourceReloadListener {

    private record Entry(Map<FaceCategory, ResourceLocation> parts, Set<FaceCategory> emissive) {}

    private static volatile Map<ResourceLocation, Entry> entries = Map.of();

    public static final FormFaces INSTANCE = new FormFaces();

    private FormFaces() { super(new Gson(), "zenkai_form_faces"); }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(INSTANCE);
    }

    /** El rasgo que se dibuja en esa categoría con esa forma: el de la forma si lo declara y la raza lo admite. */
    public static FacePartDef override(@Nullable ResourceLocation form, FaceCategory category, FacePartDef chosen, Race race) {
        Entry e = form == null ? null : entries.get(form);
        if (e == null) return chosen;
        ResourceLocation id = e.parts().get(category);
        if (id == null) return chosen;
        FacePartDef forced = FaceCatalog.get(id);
        return forced != null && forced.allows(race) ? forced : chosen;
    }

    /** ¿Esta categoría se dibuja a pleno brillo con esta forma? */
    public static boolean emissive(@Nullable ResourceLocation form, FaceCategory category) {
        Entry e = form == null ? null : entries.get(form);
        return e != null && e.emissive().contains(category);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        Map<ResourceLocation, Entry> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> f : files.entrySet()) {
            try {
                JsonObject o = f.getValue().getAsJsonObject();
                EnumMap<FaceCategory, ResourceLocation> parts = new EnumMap<>(FaceCategory.class);
                for (FaceCategory c : FaceCategory.values()) {
                    if (o.has(c.key())) parts.put(c, ResourceLocation.parse(o.get(c.key()).getAsString()));
                }
                Set<FaceCategory> glow = EnumSet.noneOf(FaceCategory.class);
                if (o.has("emissive")) {
                    for (JsonElement el : o.getAsJsonArray("emissive")) {
                        for (FaceCategory c : FaceCategory.values()) if (c.key().equals(el.getAsString())) glow.add(c);
                    }
                }
                out.put(f.getKey(), new Entry(Map.copyOf(parts), Set.copyOf(glow)));
            } catch (RuntimeException ex) {
                Zenkai.LOGGER.warn("[Zenkai] Cara de forma {}: JSON inválido ({})", f.getKey(), ex.toString());
            }
        }
        entries = Map.copyOf(out);
    }
}
