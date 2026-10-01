package com.hmc.zenkai.feature.race.face;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hmc.zenkai.feature.Race;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Un rasgo facial del catálogo (ojos, cejas, nariz, boca). Definido en datapack
 * (data/&lt;ns&gt;/zenkai_face_parts/&lt;categoría&gt;/&lt;nombre&gt;.json) y sincronizado al cliente.
 *
 * El id es &lt;ns&gt;:&lt;categoría&gt;/&lt;nombre&gt; y es la ÚNICA identidad del rasgo: lo que se guarda
 * en el jugador, lo que viaja por red y lo que usan las reglas por raza. Ni la posición en una
 * lista ni el nombre del PNG significan nada (antes eyeIndex=3 era "el 3er eyes_N.png que
 * encuentre cada cliente": borrar un PNG cambiaba la cara de cualquier jugador guardado, y un
 * resource pack hacía que el mismo número fuera distinto en cada cliente).
 *
 * @param races      razas que pueden elegirlo (el JSON sin "races" = cualquiera)
 * @param defaultFor razas para las que es la opción inicial al reconciliar
 * @param layers     capas en orden de pintado (la última queda encima), ver {@link FaceLayer}
 * @param raceLayers capas ALTERNATIVAS por raza: el mismo rasgo dibujado distinto donde la
 *                   cabeza lo pide. Caso real (2026-10-01): el casco arcosiano tapa las filas
 *                   ly 0-7 de la cara, así que los ojos se redibujan bajo el casco. Es el MISMO
 *                   rasgo (mismo id y nombre: el jugador no pierde su elección al cambiar de
 *                   raza ni ve duplicados en la lista). Raza ausente = {@link #layers}.
 *                   Limitación conocida: va por raza, no por modelo; si una forma arcosiana
 *                   tiene algún día una cabeza sin casco, habrá que pasar a clave por modelo.
 */
public record FacePartDef(ResourceLocation id, FaceCategory category, int sort,
                          Set<Race> races, Set<Race> defaultFor, List<FaceLayer> layers,
                          Map<Race, List<FaceLayer>> raceLayers) {

    /** Capas con las que se dibuja este rasgo en esta raza. */
    public List<FaceLayer> layersFor(Race race) {
        return raceLayers.getOrDefault(race, layers);
    }

    /** Cada capa es una pasada de render por jugador: tope para que un JSON no dispare el coste. */
    public static final int MAX_LAYERS = 8;

    public boolean allows(Race race) {
        return races.contains(race);
    }

    public boolean isDefaultFor(Race race) {
        return defaultFor.contains(race);
    }

    /** face_part.&lt;ns&gt;.&lt;categoría&gt;.&lt;nombre&gt; — un addon trae sus propias traducciones. */
    public String translationKey() {
        return "face_part." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** Nombre para la GUI. Sin traducción, el nombre del archivo en vez de la clave cruda. */
    public Component displayName() {
        String path = id.getPath();
        String fallback = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return Component.translatableWithFallback(translationKey(), fallback);
    }

    // ── JSON ─────────────────────────────────────────────────────────────────

    /**
     * Lee la definición. Formato:
     * <pre>
     * "layers": [ {"texture": "ns:textures/....png", "color": "none|skin|eye|eye_left|eye_right|hair|#RRGGBB"}, ... ]
     *   o el atajo de una sola capa:  "texture": "ns:textures/....png"  (color = el de la categoría)
     * "sort", "races", "exclude_races", "default_for"  — ver FaceCatalogManager
     * </pre>
     * Lanza excepción si el JSON no es válido (el cargador la registra y salta el archivo).
     */
    public static FacePartDef parse(ResourceLocation id, FaceCategory category, JsonObject o,
                                    Consumer<String> warn) {
        Set<Race> races = readRaceList(o, "races", true, warn);
        races.removeAll(readRaceList(o, "exclude_races", false, warn));
        Set<Race> defaultFor = readRaceList(o, "default_for", false, warn);

        List<FaceLayer> layers;
        if (o.has("layers")) {
            layers = readLayers(GsonHelper.getAsJsonArray(o, "layers"), category, id, warn);
        } else if (o.has("texture")) {
            layers = List.of(new FaceLayer(ResourceLocation.parse(GsonHelper.getAsString(o, "texture")),
                    category.defaultColor(), 0));
        } else {
            layers = List.of();
        }
        if (layers.isEmpty()) throw new IllegalArgumentException("sin \"layers\" ni \"texture\"");

        // "race_layers": {"arcosian": [ ...capas... ]} — ver el javadoc del record.
        Map<Race, List<FaceLayer>> raceLayers = new EnumMap<>(Race.class);
        if (o.has("race_layers")) {
            JsonObject rl = GsonHelper.getAsJsonObject(o, "race_layers");
            for (String key : rl.keySet()) {
                Race race;
                try {
                    race = Race.valueOf(key.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    warn.accept("raza desconocida '" + key + "' en \"race_layers\"");
                    continue;
                }
                List<FaceLayer> alt = readLayers(GsonHelper.getAsJsonArray(rl, key), category, id, warn);
                if (alt.isEmpty()) throw new IllegalArgumentException("race_layers." + key + " vacío");
                raceLayers.put(race, alt);
            }
        }
        return new FacePartDef(id, category, GsonHelper.getAsInt(o, "sort", 1000),
                races, defaultFor, layers, Map.copyOf(raceLayers));
    }

    private static List<FaceLayer> readLayers(JsonArray arr, FaceCategory category, ResourceLocation id,
                                              Consumer<String> warn) {
        List<FaceLayer> layers = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject l = el.getAsJsonObject();
            ResourceLocation tex = ResourceLocation.parse(GsonHelper.getAsString(l, "texture"));
            layers.add(FaceLayer.Color.parse(tex, GsonHelper.getAsString(l, "color", null),
                    category.defaultColor()));
        }
        if (layers.size() > MAX_LAYERS) {
            warn.accept(id + ": " + layers.size() + " capas, se usan las primeras " + MAX_LAYERS);
            layers = layers.subList(0, MAX_LAYERS);
        }
        return List.copyOf(layers);
    }

    private static Set<Race> readRaceList(JsonObject o, String key, boolean absentMeansAll,
                                          Consumer<String> warn) {
        if (!o.has(key)) return absentMeansAll ? EnumSet.allOf(Race.class) : EnumSet.noneOf(Race.class);
        EnumSet<Race> out = EnumSet.noneOf(Race.class);
        JsonArray arr = GsonHelper.getAsJsonArray(o, key);
        for (var el : arr) {
            String s = el.getAsString().toUpperCase(Locale.ROOT);
            try {
                out.add(Race.valueOf(s));
            } catch (IllegalArgumentException e) {
                warn.accept("raza desconocida '" + s + "' en \"" + key + "\"");
            }
        }
        return out;
    }

    // ── Red ──────────────────────────────────────────────────────────────────

    public static final StreamCodec<FriendlyByteBuf, FacePartDef> STREAM_CODEC =
            StreamCodec.of(FacePartDef::write, FacePartDef::read);

    private static final StreamCodec<FriendlyByteBuf, List<FaceLayer>> LAYERS_CODEC =
            FaceLayer.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LAYERS));

    private static void write(FriendlyByteBuf buf, FacePartDef d) {
        buf.writeResourceLocation(d.id);
        buf.writeEnum(d.category);
        buf.writeVarInt(d.sort);
        writeRaces(buf, d.races);
        writeRaces(buf, d.defaultFor);
        LAYERS_CODEC.encode(buf, d.layers);
        buf.writeVarInt(d.raceLayers.size());
        for (var e : d.raceLayers.entrySet()) {
            buf.writeEnum(e.getKey());
            LAYERS_CODEC.encode(buf, e.getValue());
        }
    }

    private static FacePartDef read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        FaceCategory category = buf.readEnum(FaceCategory.class);
        int sort = buf.readVarInt();
        Set<Race> races = readRaces(buf);
        Set<Race> defaultFor = readRaces(buf);
        List<FaceLayer> layers = List.copyOf(LAYERS_CODEC.decode(buf));
        int n = buf.readVarInt();
        if (n > Race.values().length) throw new IllegalArgumentException("race_layers: " + n + " entradas");
        Map<Race, List<FaceLayer>> raceLayers = new EnumMap<>(Race.class);
        for (int i = 0; i < n; i++) {
            Race race = buf.readEnum(Race.class);
            raceLayers.put(race, List.copyOf(LAYERS_CODEC.decode(buf)));
        }
        return new FacePartDef(id, category, sort, races, defaultFor, layers, Map.copyOf(raceLayers));
    }

    /** Bitmask por ordinal: 5 razas caben de sobra en un varint. */
    private static void writeRaces(FriendlyByteBuf buf, Set<Race> set) {
        int mask = 0;
        for (Race r : set) mask |= 1 << r.ordinal();
        buf.writeVarInt(mask);
    }

    private static Set<Race> readRaces(FriendlyByteBuf buf) {
        int mask = buf.readVarInt();
        EnumSet<Race> out = EnumSet.noneOf(Race.class);
        for (Race r : Race.values()) if ((mask & (1 << r.ordinal())) != 0) out.add(r);
        return out;
    }
}
