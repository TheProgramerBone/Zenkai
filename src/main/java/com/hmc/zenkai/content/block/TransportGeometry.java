package com.hmc.zenkai.content.block;

import com.hmc.zenkai.feature.transport.Look;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geometría de los brazos de cable y tubería, en UN solo sitio para que el modelo (datagen), la
 * forma de colisión/selección y la detección del clic coincidan al píxel.
 *
 * Todo se mide en 16avos de bloque. Un brazo sale del núcleo (de 5 a 11 en cada eje) hacia el borde
 * del bloque: «along» cuenta desde ese borde (0 = borde, 5 = pegado al núcleo) y «cross» es la
 * sección transversal. Forma de cada modo:
 *   NORMAL  tubo liso de 6x6 (along 0 a 5).
 *   PUSH    tubo de 6x6 en along 2 a 5 y una punta estrecha de 4x4 en along 0 a 2.
 *   PULL    tubo de 6x6 en along 2 a 5 y un collar ancho de 8x8 en along 0 a 2.
 */
public final class TransportGeometry {
    private TransportGeometry() {}

    public static final double CORE_MIN = 5, CORE_MAX = 11;

    /** Una caja {x0, y0, z0, x1, y1, z1} (en 16avos) del brazo hacia {@code d}. */
    public static double[] box(Direction d, double alongLo, double alongHi, double crossLo, double crossHi) {
        boolean positive = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double lo = positive ? 16 - alongHi : alongLo;
        double hi = positive ? 16 - alongLo : alongHi;
        return switch (d.getAxis()) {
            case X -> new double[] {lo, crossLo, crossLo, hi, crossHi, crossHi};
            case Y -> new double[] {crossLo, lo, crossLo, crossHi, hi, crossHi};
            case Z -> new double[] {crossLo, crossLo, lo, crossHi, crossHi, hi};
        };
    }

    /** Las cajas VISIBLES de un brazo (cada una con su región de textura la elige el datagen). */
    public static List<double[]> armBoxes(Direction d, Look look) {
        return switch (look) {
            case PUSH -> List.of(box(d, 2, 5, 5, 11), box(d, 0, 2, 6, 10));
            case PULL -> List.of(box(d, 2, 5, 5, 11), box(d, 0, 2, 4, 12));
            default -> List.of(box(d, 0, 5, 5, 11));
        };
    }

    private static VoxelShape toShape(double[] b) {
        return Block.box(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /** Forma de selección/colisión de un brazo. El de empujar es el tubo entero (la punta es detalle). */
    public static VoxelShape armShape(Direction d, Look look) {
        VoxelShape shape = toShape(box(d, 0, 5, 5, 11));
        if (look == Look.PULL) shape = Shapes.or(shape, toShape(box(d, 0, 2, 4, 12)));
        return shape;
    }

    private static final VoxelShape CORE = Block.box(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX);

    /** Caché por combinación de aspectos (el mismo truco que Mekanism con sus formas). Se llama desde varios hilos. */
    private static final Map<Integer, VoxelShape> CABLE_SHAPES = new ConcurrentHashMap<>();

    /** Forma de un cable o tubería con los aspectos empaquetados {@code bits} (ver {@link Look#pack}). */
    public static VoxelShape cableShape(int bits) {
        return CABLE_SHAPES.computeIfAbsent(bits, key -> {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                Look look = Look.unpack(key, d.get3DDataValue());
                if (look == Look.NORMAL || look == Look.PUSH || look == Look.PULL) {
                    shape = Shapes.or(shape, armShape(d, look));
                }
            }
            return shape.optimize();
        });
    }
}
