package com.hmc.zenkai.feature.technique;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.technique.TechniqueDef.Kind;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Vuelca a JSON las técnicas que tienen override, para que lo afinado en juego no se pierda.
 *
 * DESTINO: un datapack DEL MUNDO, en {@code <mundo>/datapacks/zenkai_overrides/}. No se
 * escribe nunca sobre el jar ni sobre el árbol de desarrollo por defecto: el mod corre en
 * instalaciones normales y no tiene por qué saber dónde está mi {@code src}.
 * {@link CommonConfig#techniqueDumpDir()} añade una COPIA en una ruta suelta como comodidad
 * de desarrollo; vacío = desactivado, y su fallo no invalida el volcado principal.
 *
 * QUÉ SE ESCRIBE: el def EFECTIVO completo (todos los campos que aplican al kind), no solo
 * los campos overrideados. Un JSON parcial dependería de que el JSON de fábrica siga igual,
 * y el volcado existe justamente para congelar un estado reproducible.
 *
 * EL OVERRIDE NO SE BORRA tras volcar. Cuando el datapack volcado se cargue, ese valor pasará
 * a estar en las dos capas y {@code info} lo dirá; seguir tuneando encima sigue funcionando.
 */
public final class TechniqueDump {
    private TechniqueDump() {}

    public static final String PACK_NAME = "zenkai_overrides";

    /** ⚠ pack_format de DATOS en 1.21.1. Si el pack sale marcado como incompatible, es esto. */
    private static final int PACK_FORMAT = 48;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** {@code error != null} = no se escribió nada más allá de {@code written}. */
    public record Result(int written, Path packRoot, Path mirror, String mirrorError, String error) {}

    public static Result run(MinecraftServer server, Set<Kind> kinds) {
        TechniqueOverrides ov = TechniqueOverrides.get(server);
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_NAME);

        Path mirror = null;
        String mirrorError = null;
        String cfg = CommonConfig.techniqueDumpDir();
        if (cfg != null && !cfg.isBlank()) {
            try {
                mirror = Path.of(cfg.trim());
            } catch (Exception ex) {
                mirrorError = "ruta de copia inválida: " + ex.getMessage();
            }
        }

        int written = 0;
        try {
            Files.createDirectories(packRoot);
            Files.writeString(packRoot.resolve("pack.mcmeta"), packMeta());

            for (String k : new ArrayList<>(ov.all().keySet())) {
                int slash = k.indexOf('/');
                if (slash <= 0) continue;
                Kind kind;
                try {
                    kind = Kind.valueOf(k.substring(0, slash));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (!kinds.contains(kind)) continue;
                String id = k.substring(slash + 1);

                TechniqueDef def = TechniqueDef.get(kind, id);
                if (def == null) continue;   // override de una técnica que el enum ya no tiene

                String json = GSON.toJson(toJson(def)) + "\n";

                Path out = packRoot.resolve("data").resolve(Zenkai.MOD_ID)
                        .resolve("zenkai_techniques").resolve(kind.folder());
                Files.createDirectories(out);
                Files.writeString(out.resolve(id + ".json"), json);

                if (mirror != null && mirrorError == null) {
                    try {
                        Path m = mirror.resolve(kind.folder());
                        Files.createDirectories(m);
                        Files.writeString(m.resolve(id + ".json"), json);
                    } catch (Exception ex) {
                        mirrorError = ex.toString();
                    }
                }
                written++;
            }
        } catch (Exception ex) {
            return new Result(written, packRoot, mirror, mirrorError, ex.toString());
        }
        return new Result(written, packRoot, mirror, mirrorError, null);
    }

    private static JsonObject toJson(TechniqueDef def) {
        JsonObject o = new JsonObject();
        for (TechniqueField f : TechniqueField.values()) {
            if (f.applies(def.kind())) f.writeJson(o, f.get(def));
        }
        return o;
    }

    private static String packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "Zenkai - overrides volcados con /zenkai tech dump");
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return GSON.toJson(root) + "\n";
    }

    /** Ruta legible para el mensaje del comando: relativa a la carpeta del mundo. */
    public static String prettyPath(MinecraftServer server, Path p) {
        try {
            return server.getWorldPath(LevelResource.ROOT).relativize(p).toString();
        } catch (Exception ex) {
            return p.toString();
        }
    }

    /** Los kinds que cubre un ámbito de comando ("ki", "physical", "all"). Null = desconocido. */
    public static Set<Kind> scope(String raw) {
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "ki" -> Set.of(Kind.KI);
            case "physical" -> Set.of(Kind.PHYSICAL);
            case "all" -> Set.of(Kind.KI, Kind.PHYSICAL);
            default -> null;
        };
    }

    public static List<String> scopes() { return List.of("ki", "physical", "all"); }
}
