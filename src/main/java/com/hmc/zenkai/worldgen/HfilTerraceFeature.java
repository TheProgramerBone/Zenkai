package com.hmc.zenkai.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.HashSet;
import java.util.Set;

/**
 * Terrazas escalonadas con pozas de sangre — propuesta B de
 * .claude/docs/worldgen.md. Referencia real: Pamukkale (Turquía) y Mammoth Hot
 * Springs (Yellowstone), terrazas de travertino blanco donde cada escalón es una poza somera
 * cuyo borde contiene el agua y desborda hacia el siguiente. Aquí el travertino es el SUELO DEL
 * BIOMA (regla del usuario, 2026-10-02: una estructura se hace con los bloques del bioma donde
 * aparece; en needle_wastes es la piedra pálida) y el agua es agua normal, que en el HFIL ya se
 * tiñe de rojo por bioma — el blanco y el rojo del Blood Pond en una sola imagen.
 *
 * Forma: N escalones (3-5) en fila hacia una dirección (la de bajada del terreno si la hay),
 * cada uno una elipse ancha en perpendicular a esa dirección (como los "platos" reales), un
 * bloque más bajo que el anterior; el último, a ras del suelo. Cada escalón: borde macizo a la altura de su lámina y agua a
 * ras del borde (poza llena hasta el labio), 1 bloque de hondo. Se construye de arriba abajo y
 * cada columna pertenece al primer escalón que la reclama, así el agua de un escalón alto nunca
 * toca una columna de uno más bajo (no se derrama). Lo que falte de terreno debajo se rellena
 * con el mismo bloque de suelo; lo que sobre encima se recorta.
 */
public class HfilTerraceFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_TIERS = 3;
    private static final int MAX_TIERS = 5;
    /** Separación entre centros de escalón, en la dirección de bajada. */
    private static final int TIER_SPACING = 4;
    /** Semiejes de cada plato: ancho (perpendicular a la bajada) y fondo (a lo largo). Con un
     *  fondo de 2 el interior quedaba en una cruz de un bloque de ancho (captura del usuario,
     *  2026-10-02): 3 es lo mínimo para que el agua sea una lámina. */
    private static final int MIN_HALF_WIDTH = 4;
    private static final int MAX_HALF_WIDTH = 5;
    private static final int HALF_DEPTH = 3;
    private static final int GROUND_SEARCH_RADIUS = 4;
    /** Desnivel máximo tolerado bajo la huella respecto a la altura que pide cada escalón: más,
     *  y la terraza levantaría un muro o cortaría un tajo — se descarta y sale en otro sitio.
     *  MAX_TIERS - 1 para que en llano quepa la colina entera (el escalón más alto sube eso). */
    private static final int MAX_FILL = MAX_TIERS - 1;

    public HfilTerraceFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int g0 = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (g0 == Integer.MIN_VALUE) return false;
        BlockState stone = level.getBlockState(new BlockPos(origin.getX(), g0 - 1, origin.getZ()));
        if (!stone.getFluidState().isEmpty()) return false;

        Direction down = downhill(level, origin, g0, random);
        Direction side = down.getClockWise();
        int tiers = MIN_TIERS + random.nextInt(MAX_TIERS - MIN_TIERS + 1);
        // Altura del escalón más alto: nunca por debajo del suelo del origen, y lo bastante alta
        // para que el más bajo quede a ras del suelo donde termina la terraza. En cuesta abajo
        // los escalones siguen la pendiente; en llano forman una colina escalonada (como la
        // Pamukkale real), en vez de hundirse en el suelo como una escalera en un hoyo.
        BlockPos end = origin.relative(down, (tiers - 1) * TIER_SPACING);
        int gEnd = LocalGroundProbe.findGroundY(level, end.getX(), g0, end.getZ(), GROUND_SEARCH_RADIUS);
        if (gEnd == Integer.MIN_VALUE) return false;
        int topLevel = Math.max(g0 - 1, (gEnd - 1) + (tiers - 1));

        // Huella completa primero: si algún punto pide demasiado relleno/recorte, nada se coloca.
        int[] halfWidth = new int[tiers];
        for (int k = 0; k < tiers; k++) {
            halfWidth[k] = MIN_HALF_WIDTH + random.nextInt(MAX_HALF_WIDTH - MIN_HALF_WIDTH + 1);
            BlockPos c = origin.relative(down, k * TIER_SPACING);
            int lvl = topLevel - k;
            for (int u = -halfWidth[k]; u <= halfWidth[k]; u++) {
                for (int v = -HALF_DEPTH; v <= HALF_DEPTH; v++) {
                    if (!inEllipse(u, v, halfWidth[k], HALF_DEPTH)) continue;
                    BlockPos col = c.relative(side, u).relative(down, v);
                    int g = LocalGroundProbe.findGroundY(level, col.getX(), lvl + 1, col.getZ(), GROUND_SEARCH_RADIUS);
                    if (g == Integer.MIN_VALUE || Math.abs((g - 1) - lvl) > MAX_FILL) return false;
                }
            }
        }

        Set<Long> claimed = new HashSet<>();
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int k = 0; k < tiers; k++) {
            BlockPos c = origin.relative(down, k * TIER_SPACING);
            int lvl = topLevel - k;          // altura del labio y de la lámina de este escalón
            int a = halfWidth[k];
            for (int u = -a; u <= a; u++) {
                for (int v = -HALF_DEPTH; v <= HALF_DEPTH; v++) {
                    if (!inEllipse(u, v, a, HALF_DEPTH)) continue;
                    BlockPos col = c.relative(side, u).relative(down, v);
                    // Clave de columna con la Y a 0: dos escalones a distinta altura cuentan como la
                    // MISMA columna. Si ya la reclamó un escalón más alto, es suya.
                    if (!claimed.add(BlockPos.asLong(col.getX(), 0, col.getZ()))) continue;
                    // Labio = celda de la elipse con algún vecino fuera de ella; el resto es agua.
                    // Así el agua SIEMPRE linda con su propio plato (nunca con otro escalón ni
                    // con aire), sea cual sea el tamaño de la elipse.
                    boolean inner = inEllipse(u + 1, v, a, HALF_DEPTH) && inEllipse(u - 1, v, a, HALF_DEPTH)
                            && inEllipse(u, v + 1, a, HALF_DEPTH) && inEllipse(u, v - 1, a, HALF_DEPTH);
                    int g = LocalGroundProbe.findGroundY(level, col.getX(), lvl + 1, col.getZ(), GROUND_SEARCH_RADIUS);
                    int groundTop = g - 1;
                    // Base maciza del mismo bloque de suelo hasta debajo de la lámina...
                    for (int y = Math.min(groundTop + 1, lvl); y < lvl; y++) set(level, col.getX(), y, col.getZ(), stone);
                    // ...en la lámina, agua (interior) o labio (borde)...
                    set(level, col.getX(), lvl, col.getZ(), inner ? water : stone);
                    // ...y aire encima si el terreno era más alto.
                    for (int y = lvl + 1; y <= groundTop; y++) set(level, col.getX(), y, col.getZ(), Blocks.AIR.defaultBlockState());
                }
            }
        }
        return true;
    }

    /** ¿(u, v) dentro de la elipse de semiejes a (ancho) y b (fondo)? */
    private static boolean inEllipse(int u, int v, int a, int b) {
        if (a <= 0 || b <= 0) return u == 0 && v == 0 && a >= 0 && b >= 0;
        return (u * u) / (double) (a * a) + (v * v) / (double) (b * b) <= 1.0 + 1.0e-6;
    }

    /** Dirección de bajada: la horizontal cuyo suelo, a la distancia de toda la terraza, está más
     *  bajo. En llano, una al azar. */
    private static Direction downhill(WorldGenLevel level, BlockPos origin, int g0, RandomSource random) {
        Direction best = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int bestDrop = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = origin.relative(d, TIER_SPACING * MIN_TIERS);
            int g = LocalGroundProbe.findGroundY(level, p.getX(), g0, p.getZ(), GROUND_SEARCH_RADIUS);
            if (g != Integer.MIN_VALUE && g0 - g > bestDrop) {
                bestDrop = g0 - g;
                best = d;
            }
        }
        return best;
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        if (y < level.getMinBuildHeight()) return;
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
    }
}
