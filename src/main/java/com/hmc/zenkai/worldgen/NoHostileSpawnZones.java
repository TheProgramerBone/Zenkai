package com.hmc.zenkai.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Zonas protegidas (por dimensión), cada una con un NOMBRE de "protector"
 * (p. ej. "Kami", "Yemma"). Se usan para: bloquear hostiles, impedir romper/poner
 * bloques y avisar al jugador al entrar. Solo afectan al spawn NATURAL y a la
 * edición de jugadores no-creativos.
 */
public final class NoHostileSpawnZones {
    private NoHostileSpawnZones() {}

    public record Zone(ResourceKey<Level> dimension, AABB box, String protector) {}

    private static final List<Zone> ZONES = new ArrayList<>();

    public static void add(ResourceKey<Level> dimension, AABB box, String protector) {
        ZONES.add(new Zone(dimension, box, protector));
    }

    /** Registra una caja desde su esquina mínima + tamaño (se infla 1 de margen). */
    public static void addFromBase(ResourceKey<Level> dimension, BlockPos min,
                                   int sizeX, int sizeY, int sizeZ, String protector) {
        AABB box = new AABB(
                min.getX(), min.getY(), min.getZ(),
                min.getX() + sizeX, min.getY() + sizeY, min.getZ() + sizeZ
        ).inflate(1.0);
        add(dimension, box, protector);
    }

    /** Nombre del protector si la posición cae en una zona; null si no. */
    public static String getProtector(ResourceKey<Level> dimension, double x, double y, double z) {
        for (Zone zone : ZONES) {
            if (zone.dimension() == dimension && zone.box().contains(x, y, z)) return zone.protector();
        }
        return null;
    }

    public static boolean isProtected(ResourceKey<Level> dimension, double x, double y, double z) {
        return getProtector(dimension, x, y, z) != null;
    }

    /** Lista de solo lectura de las zonas (para el comando de localización). */
    public static List<Zone> getZones() { return Collections.unmodifiableList(ZONES); }

    public static void clear() { ZONES.clear(); }
}