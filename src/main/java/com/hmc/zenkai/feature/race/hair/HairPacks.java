package com.hmc.zenkai.feature.race.hair;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hmc.zenkai.feature.race.hair.strand.HairCodeException;
import com.hmc.zenkai.feature.race.hair.strand.HairCodes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manifiesto de pack de peinados: UN json que declara una carpeta de la biblioteca de pelo y los
 * peinados que lleva, para que quien hace datapacks no escriba un json por peinado.
 *
 * <pre>
 * data/&lt;ns&gt;/zenkai_hair_packs/&lt;pack&gt;.json          →  carpeta &lt;ns&gt;:&lt;pack&gt;
 * {
 *   "name":   "Mi pack",                              nombre literal (sin él: clave hair_folder.&lt;ns&gt;.&lt;pack&gt;, o el id)
 *   "icon":   "&lt;ns&gt;:textures/gui/hair_folders/x.png",  opcional; por defecto &lt;ns&gt;:textures/gui/hair_folders/&lt;pack&gt;.png
 *   "format": "auto" | "dmz" | "dbc",                 formato por defecto de los códigos (defecto auto)
 *   "races":  ["human", "saiyan"],                    por defecto de los peinados (ausente = cualquiera)
 *   "sort":   100,                                    orden de la carpeta
 *   "styles": [
 *     {"id": "goku", "name": "Goku", "format": "dbc", "code": "5050...", "sort": 10,
 *      "races": [...], "exclude_races": [...], "default_for": [...],
 *      "form_overrides": {"zenkai:ssj1": "goku_ssj"}}   (un id sin ":" es de este mismo pack)
 *     {"id": "meme", "model": "<ns>:geo/hair/x.geo.json"}  peinado de cubos en vez de código (sin "format")
 *   ]
 * }
 * </pre>
 * El id de cada peinado es &lt;ns&gt;:&lt;pack&gt;/&lt;id&gt;. "format" declarado y código de OTRO formato = el
 * peinado se descarta con un aviso (un pack con DMZ y DBC mezclados declara el formato en cada
 * peinado y se detecta el error de pegar el código equivocado). El ícono es un asset de cliente: va
 * en el mismo pack/zip (o en hair_packs/, que sirve a los dos lados).
 */
public final class HairPacks {
    private HairPacks() {}

    public record Parsed(HairFolderDef folder, List<HairStyleDef> styles) {}

    public static Parsed parse(ResourceLocation packId, JsonObject o, Consumer<String> warn) {
        String packFormat = GsonHelper.getAsString(o, "format", "auto");
        checkFormatName(packFormat);
        ResourceLocation icon = o.has("icon")
                ? ResourceLocation.parse(GsonHelper.getAsString(o, "icon")) : null;
        HairFolderDef folder = new HairFolderDef(packId,
                o.has("name") ? GsonHelper.getAsString(o, "name") : null, icon,
                GsonHelper.getAsInt(o, "sort", 1000));

        List<HairStyleDef> out = new ArrayList<>();
        if (!o.has("styles")) return new Parsed(folder, out);
        int index = 0;
        for (JsonElement el : GsonHelper.getAsJsonArray(o, "styles")) {
            index++;
            try {
                JsonObject s = GsonHelper.convertToJsonObject(el, "styles[" + index + "]");
                out.add(parseStyle(packId, o, s, packFormat));
            } catch (Exception ex) {
                warn.accept("peinado #" + index + ": " + ex.getMessage());
            }
        }
        return new Parsed(folder, out);
    }

    private static HairStyleDef parseStyle(ResourceLocation packId, JsonObject pack, JsonObject s,
                                           String packFormat) {
        String local = GsonHelper.getAsString(s, "id");
        ResourceLocation id = ResourceLocation.tryBuild(packId.getNamespace(), packId.getPath() + "/" + local);
        if (id == null) throw new IllegalArgumentException("id inválido '" + local + "'");
        if (s.has("model")) return parseGeoStyle(packId, pack, s, id);
        String code = GsonHelper.getAsString(s, "code");

        String declared = GsonHelper.getAsString(s, "format", packFormat);
        checkFormatName(declared);
        HairCodes.Format real;
        try {
            real = HairCodes.detect(code);
        } catch (HairCodeException e) {
            throw new IllegalArgumentException("'" + local + "': el código no es de ningún formato conocido");
        }
        if (!"auto".equals(declared) && !real.name().equalsIgnoreCase(declared)) {
            throw new IllegalArgumentException("'" + local + "' declara " + declared
                    + " pero su código es " + real.name().toLowerCase(Locale.ROOT));
        }

        JsonObject d = new JsonObject();
        d.addProperty("representation", "code");
        d.addProperty("folder", packId.toString());
        for (String key : new String[]{"races", "exclude_races", "default_for"}) {
            if (s.has(key)) d.add(key, s.get(key));
            else if (pack.has(key)) d.add(key, pack.get(key));
        }
        for (String key : new String[]{"code", "sort", "name", "selectable", "color"}) {
            if (s.has(key)) d.add(key, s.get(key));
        }
        if (s.has("form_overrides")) {
            JsonObject fo = new JsonObject();
            for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(s, "form_overrides").entrySet()) {
                fo.addProperty(e.getKey(), resolveLocal(packId, e.getValue().getAsString()).toString());
            }
            d.add("form_overrides", fo);
        }
        return HairStyleDef.parse(id, d, w -> { throw new IllegalArgumentException(w); });
    }


    /**
     * Peinado de cubos de Blockbench dentro del pack: "model" (un .geo.json de assets) en lugar de "code".
     * Sin "texture" usa la blanca plana compartida, que se tiñe con el color de pelo. No lleva "format".
     */
    private static HairStyleDef parseGeoStyle(ResourceLocation packId, JsonObject pack, JsonObject s, ResourceLocation id) {
        JsonObject d = new JsonObject();
        d.addProperty("representation", "geo");
        d.addProperty("folder", packId.toString());
        d.addProperty("texture", GsonHelper.getAsString(s, "texture",
                "zenkai:textures/customization/hair/flat_white.png"));
        for (String key : new String[]{"races", "exclude_races", "default_for"}) {
            if (s.has(key)) d.add(key, s.get(key));
            else if (pack.has(key)) d.add(key, pack.get(key));
        }
        for (String key : new String[]{"model", "sort", "name", "selectable", "color"}) {
            if (s.has(key)) d.add(key, s.get(key));
        }
        if (s.has("form_overrides")) {
            JsonObject fo = new JsonObject();
            for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(s, "form_overrides").entrySet()) {
                fo.addProperty(e.getKey(), resolveLocal(packId, e.getValue().getAsString()).toString());
            }
            d.add("form_overrides", fo);
        }
        return HairStyleDef.parse(id, d, w -> { throw new IllegalArgumentException(w); });
    }

    /** Un id de override sin ":" pertenece al mismo pack. */
    private static ResourceLocation resolveLocal(ResourceLocation packId, String ref) {
        if (ref.indexOf(':') >= 0) return ResourceLocation.parse(ref);
        return ResourceLocation.fromNamespaceAndPath(packId.getNamespace(), packId.getPath() + "/" + ref);
    }

    private static void checkFormatName(@Nullable String f) {
        if (!"auto".equals(f) && !"dmz".equals(f) && !"dbc".equals(f)) {
            throw new IllegalArgumentException("format '" + f + "': usa auto, dmz o dbc");
        }
    }
}
