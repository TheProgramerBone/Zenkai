package com.hmc.zenkai.feature.race.body;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hmc.zenkai.feature.Race;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Una opción de cuerpo elegible en la pestaña correspondiente de Body &amp; Colors (apéndice de cabeza del
 * Majin hoy; cuernos, crestas o colas de otras razas mañana). Mismo patrón que {@code FacePartDef}.
 *
 * Archivo: data/&lt;ns&gt;/zenkai_body_options/&lt;categoría&gt;/&lt;nombre&gt;.json (id &lt;ns&gt;:&lt;categoría&gt;/&lt;nombre&gt;).
 *   "bones"  nombres de los huesos RAÍZ del .geo.json que esta opción muestra (sus hijos van con ellos;
 *            los de las demás opciones de la categoría se ocultan)
 *   "races"  ["majin", ...]; ausente = cualquier raza
 *   "sort"   orden en la GUI (defecto 1000)
 *   "coming_soon": true  sale en la GUI atenuada con «Próximamente» y NO se puede elegir ni guardar
 *   "default": true  la opción inicial de la categoría para esas razas (si no, la primera por orden)
 */
public record BodyOptionDef(ResourceLocation id, String category, int sort, List<String> races,
                            List<String> bones, boolean isDefault, boolean comingSoon) {

    private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> STRINGS =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final StreamCodec<FriendlyByteBuf, BodyOptionDef> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeResourceLocation(d.id());
                buf.writeUtf(d.category());
                buf.writeVarInt(d.sort());
                STRINGS.encode(buf, d.races());
                STRINGS.encode(buf, d.bones());
                buf.writeBoolean(d.isDefault());
                buf.writeBoolean(d.comingSoon());
            },
            buf -> new BodyOptionDef(buf.readResourceLocation(), buf.readUtf(), buf.readVarInt(),
                    STRINGS.decode(buf), STRINGS.decode(buf), buf.readBoolean(), buf.readBoolean()));

    /** Nombre corto (lo que se guarda en el jugador): la parte del id tras la categoría. */
    public String name() {
        String p = id.getPath();
        int slash = p.indexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    /** Clave de lang del nombre: {@code body_option.<ns>.<categoría>.<nombre>}. */
    public String langKey() { return id.toLanguageKey("body_option").replace('/', '.'); }

    public boolean allows(Race race) {
        return races.isEmpty() || (race != null && races.contains(race.name().toLowerCase(Locale.ROOT)));
    }

    public static BodyOptionDef parse(ResourceLocation id, String category, JsonObject o, Consumer<String> warn) {
        List<String> bones = strings(o, "bones", false);
        if (!o.has("bones")) warn.accept("sin \"bones\" (usa [] si la opción no añade huesos)");
        return new BodyOptionDef(id, category, o.has("sort") ? o.get("sort").getAsInt() : 1000,
                strings(o, "races", true), bones, o.has("default") && o.get("default").getAsBoolean(),
                o.has("coming_soon") && o.get("coming_soon").getAsBoolean());
    }

    private static List<String> strings(JsonObject o, String key, boolean lower) {
        List<String> out = new ArrayList<>();
        if (!o.has(key)) return out;
        JsonArray a = o.getAsJsonArray(key);
        for (JsonElement e : a) out.add(lower ? e.getAsString().toLowerCase(Locale.ROOT) : e.getAsString());
        return out;
    }
}
