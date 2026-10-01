package com.hmc.zenkai.feature.race.face;

import com.hmc.zenkai.feature.Race;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de rasgos faciales vigente. Lo rellena {@link FaceCatalogManager} en el servidor
 * (datapack) y {@link FaceCatalogSyncPacket} en el cliente — mismo patrón que FormDef.replaceAll:
 * en un solo jugador los dos lados comparten esta clase y escriben el mismo contenido.
 *
 * Mientras esté VACÍO (datapack roto, o cliente que aún no recibió el sync) nada debe tratarlo
 * como "ningún rasgo es válido": {@link com.hmc.zenkai.feature.race.FaceBans#reconcile} no toca
 * al jugador en ese caso, para no machacar caras guardadas por un fallo de carga.
 */
public final class FaceCatalog {
    private FaceCatalog() {}

    /** Orden de presentación: sort y, a igualdad, el id (estable entre recargas y clientes). */
    private static final Comparator<FacePartDef> ORDER =
            Comparator.comparingInt(FacePartDef::sort).thenComparing(d -> d.id().toString());

    private static volatile Map<ResourceLocation, FacePartDef> parts = Map.of();

    public static void replaceAll(Map<ResourceLocation, FacePartDef> newParts) {
        parts = Map.copyOf(newParts);
    }

    public static boolean isEmpty() { return parts.isEmpty(); }

    public static Collection<FacePartDef> all() { return parts.values(); }

    @Nullable
    public static FacePartDef get(@Nullable ResourceLocation id) {
        return id == null ? null : parts.get(id);
    }

    /** Rasgos de una categoría que esta raza puede elegir, en orden de presentación. */
    public static List<FacePartDef> options(FaceCategory category, Race race) {
        List<FacePartDef> out = new ArrayList<>();
        for (FacePartDef d : parts.values()) {
            if (d.category() == category && d.allows(race)) out.add(d);
        }
        out.sort(ORDER);
        return out;
    }
}
