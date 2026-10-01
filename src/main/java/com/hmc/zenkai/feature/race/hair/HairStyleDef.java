package com.hmc.zenkai.feature.race.hair;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.race.face.FaceLayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Un peinado del catálogo (S5, 2026-10-01). Definido en datapack
 * (data/&lt;ns&gt;/zenkai_hair_styles/&lt;nombre&gt;.json, id &lt;ns&gt;:&lt;nombre&gt;) y sincronizado al cliente.
 *
 * Antes cada peinado era un ITEM registrado en Java (hair_1, ssj1_hair1) y el selector contaba
 * PNG de otra carpeta: un peinado nuevo exigía código, y soltar un PNG lo hacía elegible pero se
 * veía calvo. Ahora: geo + PNG + este JSON, y un único item virtual (zenkai:hair) que elige
 * modelo y textura por el componente del stack.
 *
 * La representación es AGNÓSTICA (decisión C6 del usuario): {@link Representation#GEO} = cubos
 * de Blockbench (el estándar artístico de Zenkai); {@link Representation#TEXTURE} = pintado plano
 * sobre la cabeza del modelo de raza, como el pelo de una skin vanilla (un PNG de 256 con la
 * UV de la cabeza). El resto del sistema no distingue entre las dos.
 *
 * @param color         fuente del tinte (como FaceLayer.Color): HAIR por defecto (color del
 *                      jugador, o el hair_rgb de la forma activa); NONE = textura tal cual; FIXED.
 * @param formOverrides peinado que sustituye a ESTE cuando la forma activa es la clave (SSJ3 de
 *                      pelo largo, Ultra Instinct...). Sustituye a la vieja cadena por convención
 *                      de nombres "&lt;grupo&gt;_hair&lt;N&gt;" de HairResolver.
 * @param selectable    false = solo existe como destino de un form_override (no sale en la lista).
 */
public record HairStyleDef(ResourceLocation id, int sort, Set<Race> races, Set<Race> defaultFor,
                           Representation representation, @Nullable ResourceLocation model,
                           ResourceLocation texture, FaceLayer.Color color, int fixedRgb,
                           Map<ResourceLocation, ResourceLocation> formOverrides, boolean selectable) {

    public enum Representation { GEO, TEXTURE }

    public boolean allows(Race race) { return races.contains(race); }

    public boolean isDefaultFor(Race race) { return defaultFor.contains(race); }

    /** Peinado efectivo con esta forma activa: el override de la forma, o este mismo. */
    public ResourceLocation styleForForm(@Nullable ResourceLocation formId) {
        if (formId == null) return id;
        return formOverrides.getOrDefault(formId, id);
    }

    public String translationKey() {
        return "hair_style." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public Component displayName() {
        String path = id.getPath();
        String fallback = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return Component.translatableWithFallback(translationKey(), fallback);
    }

    // ── JSON ─────────────────────────────────────────────────────────────────

    /**
     * <pre>
     * "representation": "geo" | "texture"          (defecto geo)
     * "model":   "zenkai:geo/hair/x.geo.json"       (obligatorio si geo)
     * "texture": "zenkai:textures/....png"          (obligatorio)
     * "color":   "hair" | "none" | "#RRGGBB"        (defecto hair)
     * "races", "exclude_races", "default_for", "sort"  — como los rasgos faciales
     * "form_overrides": {"zenkai:ssj1": "zenkai:otro_peinado", ...}
     * "selectable": true | false                    (defecto true)
     * </pre>
     */
    public static HairStyleDef parse(ResourceLocation id, JsonObject o, Consumer<String> warn) {
        Set<Race> races = readRaceList(o, "races", true, warn);
        races.removeAll(readRaceList(o, "exclude_races", false, warn));
        Set<Race> defaultFor = readRaceList(o, "default_for", false, warn);

        Representation rep = Representation.valueOf(
                GsonHelper.getAsString(o, "representation", "geo").toUpperCase(Locale.ROOT));
        ResourceLocation model = o.has("model") ? ResourceLocation.parse(GsonHelper.getAsString(o, "model")) : null;
        if (rep == Representation.GEO && model == null) throw new IllegalArgumentException("geo sin \"model\"");
        ResourceLocation texture = ResourceLocation.parse(GsonHelper.getAsString(o, "texture"));

        FaceLayer colorLayer = FaceLayer.Color.parse(texture, GsonHelper.getAsString(o, "color", null),
                FaceLayer.Color.HAIR);

        Map<ResourceLocation, ResourceLocation> overrides = new LinkedHashMap<>();
        if (o.has("form_overrides")) {
            JsonObject fo = GsonHelper.getAsJsonObject(o, "form_overrides");
            for (String form : fo.keySet()) {
                overrides.put(ResourceLocation.parse(form), ResourceLocation.parse(fo.get(form).getAsString()));
            }
        }
        return new HairStyleDef(id, GsonHelper.getAsInt(o, "sort", 1000), races, defaultFor, rep, model,
                texture, colorLayer.color(), colorLayer.fixedRgb(), Map.copyOf(overrides),
                GsonHelper.getAsBoolean(o, "selectable", true));
    }

    private static Set<Race> readRaceList(JsonObject o, String key, boolean absentMeansAll, Consumer<String> warn) {
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

    public static final StreamCodec<FriendlyByteBuf, HairStyleDef> STREAM_CODEC =
            StreamCodec.of(HairStyleDef::write, HairStyleDef::read);

    private static void write(FriendlyByteBuf buf, HairStyleDef d) {
        buf.writeResourceLocation(d.id);
        buf.writeVarInt(d.sort);
        writeRaces(buf, d.races);
        writeRaces(buf, d.defaultFor);
        buf.writeEnum(d.representation);
        buf.writeBoolean(d.model != null);
        if (d.model != null) buf.writeResourceLocation(d.model);
        buf.writeResourceLocation(d.texture);
        buf.writeEnum(d.color);
        buf.writeInt(d.fixedRgb);
        buf.writeVarInt(d.formOverrides.size());
        for (var e : d.formOverrides.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            buf.writeResourceLocation(e.getValue());
        }
        buf.writeBoolean(d.selectable);
    }

    private static HairStyleDef read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int sort = buf.readVarInt();
        Set<Race> races = readRaces(buf);
        Set<Race> defaultFor = readRaces(buf);
        Representation rep = buf.readEnum(Representation.class);
        ResourceLocation model = buf.readBoolean() ? buf.readResourceLocation() : null;
        ResourceLocation texture = buf.readResourceLocation();
        FaceLayer.Color color = buf.readEnum(FaceLayer.Color.class);
        int fixed = buf.readInt();
        int n = buf.readVarInt();
        if (n > 256) throw new IllegalArgumentException("form_overrides: " + n + " entradas");
        Map<ResourceLocation, ResourceLocation> overrides = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) overrides.put(buf.readResourceLocation(), buf.readResourceLocation());
        boolean selectable = buf.readBoolean();
        return new HairStyleDef(id, sort, races, defaultFor, rep, model, texture, color, fixed,
                Map.copyOf(overrides), selectable);
    }

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
