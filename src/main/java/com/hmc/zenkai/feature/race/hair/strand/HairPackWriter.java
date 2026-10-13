package com.hmc.zenkai.feature.race.hair.strand;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Escribe un peinado importado como una carpeta de {@code hair_packs/}: un pack que es a la vez de
 * recursos (geo + nombre) y de datos (entrada del catálogo), y que HairPackSource carga solo. Lo
 * usa el botón "Exportar" de la pestaña Pelo, pensado para autores de modpacks que quieran meter
 * un peinado en el CATÁLOGO (los jugadores normales no lo necesitan: su biblioteca ya lo comparte).
 *
 * <pre>
 * &lt;nombre&gt;/pack.mcmeta
 * &lt;nombre&gt;/assets/zenkai_hair/geo/hair/&lt;nombre&gt;[_ssj|_ssj2|_ssj3].geo.json
 * &lt;nombre&gt;/assets/zenkai_hair/lang/en_us.json
 * &lt;nombre&gt;/data/zenkai_hair/zenkai_hair_styles/&lt;nombre&gt;[...].json
 * </pre>
 * Namespace propio ({@value #NAMESPACE}) para no mezclarse con el contenido del mod. La textura es
 * la blanca del mod (el color sale del tinte), así que el pack no lleva PNG.
 *
 * Seguridad: escribe en la máquina de quien exporta y solo dentro de la raíz recibida; el nombre
 * se limpia a [a-z0-9_] y la ruta final se comprueba tras normalizarla.
 */
public final class HairPackWriter {
    private HairPackWriter() {}

    public static final String NAMESPACE = "zenkai_hair";
    public static final String TEXTURE = "zenkai:textures/customization/hair/flat_white.png";
    private static final int MAX_NAME = 48;
    private static final int DATA_FORMAT = 48;     // 1.21.1
    private static final int RESOURCE_FORMAT = 34; // 1.21.1
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();


    public record Report(String name, Path folder, List<String> styles, int cubes, int approximated) {}

    /** Nombre seguro para carpeta e id, o null si no queda nada utilizable. */
    @Nullable
    public static String sanitize(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        s = s.replaceAll("^_+|_+$", "");
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME).replaceAll("_+$", "");
        return s.isEmpty() ? null : s;
    }

    /** Primer import_&lt;n&gt; libre en la raíz. */
    public static String nextDefaultName(Path root) {
        for (int n = 1; ; n++) {
            String name = "import_" + n;
            if (!Files.exists(root.resolve(name))) return name;
        }
    }

    public static Report write(Path root, String rawName, HairCodes.Decoded decoded, boolean overwrite)
            throws IOException, HairCodeException {
        String name = sanitize(rawName);
        if (name == null) throw new HairCodeException("bad_name");
        Path base = root.toAbsolutePath().normalize();
        Path folder = base.resolve(name).normalize();
        if (!folder.startsWith(base) || folder.equals(base)) throw new HairCodeException("bad_name");
        if (Files.exists(folder)) {
            if (!overwrite) throw new HairCodeException("exists", name);
            deleteTree(folder, base);
        }

        StrandSet.Variants v = decoded.variants();
        Map<String, StrandSet> sets = new LinkedHashMap<>();
        sets.put(name, v.base());
        // Variantes que de verdad cambian algo respecto a la anterior en la cadena base -> ssj -> ssj2 -> ssj3.
        StrandSet prev = v.base();
        String[] keys = {"ssj", "ssj2", "ssj3"};
        StrandSet[] vars = {v.ssj(), v.ssj2(), v.ssj3()};
        Map<String, String> variantId = new LinkedHashMap<>();
        String last = name;
        for (int i = 0; i < 3; i++) {
            if (vars[i] != null && !vars[i].strands().equals(prev.strands())) {
                last = name + "_" + keys[i];
                sets.put(last, vars[i]);
                prev = vars[i];
            }
            variantId.put(keys[i], last);
        }
        // SSJ4 (formato v2.2 de DMZ) no sigue la cadena: sin variante propia usa la de SSJ.
        String ssjId = variantId.get("ssj");
        StrandSet ssjSet = sets.getOrDefault(ssjId, v.base());
        if (v.ssj4() != null && !v.ssj4().strands().equals(ssjSet.strands())) {
            sets.put(name + "_ssj4", v.ssj4());
            variantId.put("ssj4", name + "_ssj4");
        } else {
            variantId.put("ssj4", ssjId);
        }

        Path geoDir = folder.resolve("assets").resolve(NAMESPACE).resolve("geo").resolve("hair");
        Path dataDir = folder.resolve("data").resolve(NAMESPACE).resolve("zenkai_hair_styles");
        Files.createDirectories(geoDir);
        Files.createDirectories(dataDir);

        int cubes = 0, approximated = 0;
        for (var e : sets.entrySet()) {
            StrandGeoWriter.Result r = StrandGeoWriter.write(NAMESPACE + "_" + e.getKey(), e.getValue());
            cubes = Math.max(cubes, r.cubes());
            approximated += r.approximated();
            writeJson(geoDir.resolve(e.getKey() + ".geo.json"), r.geo());
            boolean isBase = e.getKey().equals(name);
            writeJson(dataDir.resolve(e.getKey() + ".json"), catalogEntry(e.getKey(), isBase,
                    isBase && sets.size() > 1 ? variantId : null));
        }

        JsonObject lang = new JsonObject();
        String display = displayName(v.base().name(), name);
        for (String id : sets.keySet()) {
            lang.addProperty("hair_style." + NAMESPACE + "." + id,
                    id.equals(name) ? display : display + " (" + id.substring(name.length() + 1).toUpperCase(Locale.ROOT) + ")");
        }
        Path langDir = folder.resolve("assets").resolve(NAMESPACE).resolve("lang");
        Files.createDirectories(langDir);
        writeJson(langDir.resolve("en_us.json"), lang);
        writeJson(folder.resolve("pack.mcmeta"), packMeta(name, decoded.format()));

        return new Report(name, folder, List.copyOf(sets.keySet()), cubes, approximated);
    }

    private static JsonObject catalogEntry(String id, boolean selectable, @Nullable Map<String, String> variantId) {
        JsonObject o = new JsonObject();
        JsonArray races = new JsonArray();
        races.add("human");
        races.add("saiyan");
        o.add("races", races);
        o.addProperty("sort", 500);
        o.addProperty("representation", "geo");
        o.addProperty("model", NAMESPACE + ":geo/hair/" + id + ".geo.json");
        o.addProperty("texture", TEXTURE);
        o.addProperty("color", "hair");
        if (!selectable) o.addProperty("selectable", false);
        // Carpeta "Exportados" de la biblioteca de pelo: sin ella el peinado salía suelto en la raíz
        // con el nombre del código (a menudo "Custom") y no se sabía de dónde venía.
        else o.addProperty("folder", NAMESPACE + ":exported");
        if (variantId != null) {
            JsonObject fo = new JsonObject();
            // Misma tabla que el render de importados (HairFormVariants, guía customizacion-cara-y-pelo.md).
            for (var row : new java.util.TreeMap<>(HairFormVariants.FORMS).entrySet()) {
                if (row.getValue() == HairFormVariants.Variant.BASE) continue;
                String target = variantId.get(row.getValue().name().toLowerCase(Locale.ROOT));
                if (!target.equals(id)) fo.addProperty(row.getKey(), NAMESPACE + ":" + target);
            }
            if (fo.size() > 0) o.add("form_overrides", fo);
        }
        return o;
    }

    private static JsonObject packMeta(String name, HairCodes.Format format) {
        JsonObject range = new JsonObject();
        range.addProperty("min_inclusive", RESOURCE_FORMAT);
        range.addProperty("max_inclusive", DATA_FORMAT);
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Zenkai hair '" + name + "' (imported from a " + format.display + " code)");
        pack.addProperty("pack_format", DATA_FORMAT);
        pack.add("supported_formats", range);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private static String displayName(@Nullable String fromCode, String name) {
        String s = fromCode == null ? "" : fromCode.strip();
        if (s.isEmpty() || s.length() > 40) s = name.replace('_', ' ');
        return s;
    }

    private static void writeJson(Path file, JsonElement json) throws IOException {
        Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    /** Borra una carpeta de peinado, nunca nada fuera de la raíz. */
    private static void deleteTree(Path folder, Path base) throws IOException {
        if (!folder.startsWith(base) || folder.equals(base)) throw new IOException("ruta fuera de hair_packs");
        try (Stream<Path> walk = Files.walk(folder)) {
            List<Path> all = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList());
            for (Path p : all) Files.delete(p);
        }
    }
}
