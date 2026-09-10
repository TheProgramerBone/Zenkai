package com.hmc.zenkai.feature.spacepod;

import com.hmc.zenkai.registry.ModDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Los planetas reales a los que el menú galáctico de la SpacePod puede llevar al jugador —
 * sistema TOTALMENTE separado de TeleportDestination/TeleportRealm (Instant Transmission): no
 * hay descubrimiento ni protectorKey aquí, solo una tabla estática de destinos, decisión
 * explícita del usuario tras evaluar las dos opciones descritas en
 * .claude/pendiente/nave-espacial-menu-galactico.md ("no hace falta 'descubrir' un planeta al
 * que solo se llega en nave").
 * Yardrat queda FUERA de este enum a propósito: no existe todavía ni como dimensión, así que
 * GalacticMenuScreen lo pinta como una fila deshabilitada "próximamente" sin ningún
 * SpacePodDestination real detrás — añadir su entrada aquí el día que tenga dimensión propia.
 */
public enum SpacePodDestination {
    EARTH(Level.OVERWORLD),
    NAMEK(ModDimensions.NAMEK_LEVEL);

    private final ResourceKey<Level> dimension;

    SpacePodDestination(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    public ResourceKey<Level> dimension() { return dimension; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public String nameKey() { return "screen.zenkai.galactic_menu.dest." + id(); }

    public static SpacePodDestination byId(String id) {
        for (SpacePodDestination d : values()) {
            if (d.id().equals(id)) return d;
        }
        return null;
    }
}
