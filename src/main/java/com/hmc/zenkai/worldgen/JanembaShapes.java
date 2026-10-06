package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Piezas comunes de la geometría del Janemba Rift (features y la torre de la estructura).
 *
 * Las formas escriben a través de un {@link Sink} y no directamente al nivel: la misma torre la
 * dibuja una feature (que escribe sin más) y una StructurePiece (que filtra por el chunk que se
 * está generando y no puede usar el RandomSource del chunk, ver JanembaTowerPiece).
 */
public final class JanembaShapes {
    private JanembaShapes() {}

    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    /** Destino de los bloques de una forma. */
    @FunctionalInterface
    public interface Sink {
        void set(int x, int y, int z, BlockState state);
    }

    /** Sink de una feature: escribe directamente, dentro de la altura del mundo. */
    public static Sink of(WorldGenLevel level) {
        int min = level.getMinBuildHeight();
        int max = level.getMaxBuildHeight() - 1;
        return (x, y, z, s) -> {
            if (y >= min && y <= max) level.setBlock(new BlockPos(x, y, z), s, FLAGS);
        };
    }

    /** ¿Es el suelo propio del bioma (carne o piedra del vacío)? Las features solo construyen
     *  sobre él: así no se montan encima de otra formación ya colocada. */
    public static boolean isBiomeGround(BlockState s) {
        return s.is(ModBlocks.HFIL_CORRUPTED_FLESH.get()) || s.is(ModBlocks.HFIL_VOID_STONE.get());
    }

    /**
     * Origen recentrado en el centro de su chunk, con un poco de holgura. Una feature solo puede
     * escribir en su chunk y en los 8 vecinos: desde un borde del chunk (InSquarePlacement da
     * 0..15) le quedan 16 bloques hacia fuera, desde el centro 24. Las formas grandes (crestas,
     * olas, agujas) lo necesitan o pierden sus bordes ("setBlock in a far chunk").
     */
    public static BlockPos centered(BlockPos origin, RandomSource random, int jitter) {
        int cx = (origin.getX() & ~15) + 8 + random.nextInt(2 * jitter + 1) - jitter;
        int cz = (origin.getZ() & ~15) + 8 + random.nextInt(2 * jitter + 1) - jitter;
        return new BlockPos(cx, origin.getY(), cz);
    }

    /**
     * Pincho cónico: de (x0,y0,z0) en la dirección dada (no hace falta normalizarla), con radio
     * {@code r0} en la base que se afila hasta la punta. El primer bloque siempre se pone, para
     * que un pincho fino no desaparezca.
     */
    public static void spike(Sink sink, double x0, double y0, double z0,
                             double dx, double dy, double dz, double length, double r0, BlockState state) {
        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (n < 1e-6) return;
        dx /= n;
        dy /= n;
        dz /= n;
        for (double s = 0; s <= length; s += 0.5) {
            double r = r0 * (1.0 - s / length);
            double cx = x0 + dx * s, cy = y0 + dy * s, cz = z0 + dz * s;
            ball(sink, cx, cy, cz, r, state);
        }
    }

    /** Bola de radio r (r < 0.5 → solo el bloque del centro). */
    public static void ball(Sink sink, double cx, double cy, double cz, double r, BlockState state) {
        int bx = (int) Math.floor(cx), by = (int) Math.floor(cy), bz = (int) Math.floor(cz);
        if (r < 0.5) {
            sink.set(bx, by, bz, state);
            return;
        }
        int ir = (int) Math.ceil(r);
        double r2 = r * r;
        for (int ox = -ir; ox <= ir; ox++) {
            for (int oy = -ir; oy <= ir; oy++) {
                for (int oz = -ir; oz <= ir; oz++) {
                    double ddx = bx + ox + 0.5 - cx, ddy = by + oy + 0.5 - cy, ddz = bz + oz + 0.5 - cz;
                    if (ddx * ddx + ddy * ddy + ddz * ddz <= r2) sink.set(bx + ox, by + oy, bz + oz, state);
                }
            }
        }
    }

    /** Hash determinista 0..1 de una posición y una semilla: la misma respuesta en cada chunk. */
    public static double hash01(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }
}
