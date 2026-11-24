package com.hmc.zenkai.feature.capsule;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Geometría de la zona de una cápsula. Un solo sitio para que captura, pegado, holograma y pantalla no se desvíen un bloque.
 *
 * CAPTURA: la cápsula está apoyada en el suelo en P; la capa de abajo del cubo es el bloque sobre el que se apoya (P.y − 1),
 * así que la zona va de {@code P + (−r, −1, −r)} a {@code P + (r, 2r − 1, r)}.
 *
 * PEGADO: la capa de abajo de lo guardado (el suelo de la casa) va al nivel de la cápsula que se suelta en Q, es decir,
 * ENCIMA del terreno: {@code Q + (−r, 0, −r)}. Si se pegara a Q.y − 1, el suelo guardado chocaría siempre con el terreno.
 */
public final class CapsuleZone {
    private CapsuleZone() {}

    public static BlockPos captureMin(BlockPos capsule, int radius) {
        return capsule.offset(-radius, -1, -radius);
    }

    public static BlockPos pasteMin(BlockPos anchor, int radius) {
        return anchor.offset(-radius, 0, -radius);
    }

    public static int side(int radius) { return radius * 2 + 1; }

    /** Caja de bloques completa (para buscar entidades y para el contorno del cliente). */
    public static AABB box(BlockPos min, int radius) {
        int s = side(radius);
        return new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + s, min.getY() + s, min.getZ() + s);
    }

    /** Empaqueta una posición relativa (0..63 por eje) en un int. Radios de hasta 31 caben de sobra. */
    public static int pack(int x, int y, int z) { return (x << 12) | (y << 6) | z; }
    public static int unpackX(int p) { return (p >> 12) & 63; }
    public static int unpackY(int p) { return (p >> 6) & 63; }
    public static int unpackZ(int p) { return p & 63; }
}
