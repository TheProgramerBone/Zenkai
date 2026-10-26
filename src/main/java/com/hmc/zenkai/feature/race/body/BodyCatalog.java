package com.hmc.zenkai.feature.race.body;

import com.hmc.zenkai.feature.Race;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de opciones de cuerpo vigente. Lo rellena {@link BodyCatalogManager} en el servidor y
 * {@link BodyCatalogSyncPacket} en el cliente (mismo patrón que FaceCatalog).
 */
public final class BodyCatalog {
    private BodyCatalog() {}

    /** Categoría de los apéndices de cabeza (pestaña «Cabeza»). */
    public static final String HEAD = "head";

    private static final Comparator<BodyOptionDef> ORDER =
            Comparator.comparingInt(BodyOptionDef::sort).thenComparing(d -> d.id().toString());

    private static volatile Map<ResourceLocation, BodyOptionDef> options = Map.of();

    public static void replaceAll(Map<ResourceLocation, BodyOptionDef> newOptions) { options = Map.copyOf(newOptions); }

    public static boolean isEmpty() { return options.isEmpty(); }

    public static Collection<BodyOptionDef> all() { return options.values(); }

    /** Opciones de una categoría que esa raza puede elegir, en orden de presentación. */
    public static List<BodyOptionDef> options(String category, Race race) {
        List<BodyOptionDef> out = new ArrayList<>();
        for (BodyOptionDef d : options.values()) if (d.category().equals(category) && d.allows(race)) out.add(d);
        out.sort(ORDER);
        return out;
    }

    @Nullable
    public static BodyOptionDef get(String category, @Nullable String name) {
        if (name == null) return null;
        for (BodyOptionDef d : options.values()) if (d.category().equals(category) && d.name().equals(name)) return d;
        return null;
    }

    /** ¿Existe esa opción en el catálogo? Lista blanca del servidor para lo que mande el cliente. */
    public static boolean exists(String category, String name) {
        BodyOptionDef d = get(category, name);
        return d != null && !d.comingSoon();
    }

    /**
     * Nombre efectivo de la opción de esa categoría: la elegida si existe y la admite la raza; si no, la marcada
     * {@code default} para la raza o la primera por orden. {@code null} si la raza no tiene opciones ahí.
     */
    @Nullable
    public static String resolve(Race race, String category, @Nullable String chosen) {
        List<BodyOptionDef> opts = options(category, race);
        if (opts.isEmpty()) return null;
        // Las «próximamente» no se pueden elegir: una guardada (o un cliente modificado) cae a la por defecto.
        for (BodyOptionDef d : opts) if (!d.comingSoon() && d.name().equals(chosen)) return d.name();
        for (BodyOptionDef d : opts) if (!d.comingSoon() && d.isDefault()) return d.name();
        for (BodyOptionDef d : opts) if (!d.comingSoon()) return d.name();
        return null;
    }

    /** Opciones efectivas de todas las categorías que tenga la raza (categoría → nombre). */
    public static Map<String, String> resolveAll(Race race, Map<String, String> chosen) {
        Map<String, String> out = new LinkedHashMap<>();
        for (BodyOptionDef d : options.values()) {
            if (!d.allows(race) || out.containsKey(d.category())) continue;
            out.put(d.category(), resolve(race, d.category(), chosen.get(d.category())));
        }
        return out;
    }
}
