package com.hmc.zenkai.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.function.Supplier;

/**
 * Formaciones de roca tipo "hoodoo"/mesa para rocky_wasteland — pedido explícito del usuario con
 * una imagen de referencia de Dragon Ball (cañón de agujas rocosas anaranjadas, silueta abultada/
 * ondulada, no un cono liso que termina en punta). Deliberadamente NO reutiliza HfilSpikeFeature
 * tal cual: esa clase modela un PINCHO (cono que afila a 0 de radio en la punta, identidad del
 * HFIL) — la referencia de esta sesión muestra formaciones más anchas, con "cintura" y remate
 * ancho (nunca llegan a una punta), así que el perfil de radio es una función distinta, no un
 * parámetro más de la misma clase. Mismo estilo ligero que el resto de worldgen/*Feature.java:
 * sin jigsaw, sondeo de suelo LOCAL vía LocalGroundProbe, bloques colocados a mano.
 *
 * Deliberadamente NO toca la forma macro del terreno (density function / erosion) — ver la
 * sesión 2026-09-04 en .claude/pendiente/rocky-wasteland-terrablender-gen.md: el relieve suave
 * (EROSION_5) se dejó así a propósito para evitar reabrir el bug de acantilados abruptos junto al
 * agua ya medido y arreglado. Esta feature logra el aspecto de cañón dramático solo con
 * decoración ENCIMA del terreno existente, igual que HfilOreBoulderFeature hace con los
 * afloramientos de mineral.
 */
public class RockyWastelandSpireFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_HEIGHT = 16;
    private static final int MAX_HEIGHT = 42;
    private static final int MIN_BASE_RADIUS = 3;
    private static final int MAX_BASE_RADIUS = 6;
    private static final int MIN_SPIRES = 2;
    private static final int MAX_SPIRES = 5;
    /** Cuánto se puede alejar cada aguja del origen del clúster, en bloques (radio del área) —
     *  más ancho que HfilSpikeFeature (5): la referencia muestra un cañón, no un solo cúmulo. */
    private static final int CLUSTER_SPREAD = 9;
    /** Ventana de sondeo local para encontrar el suelo real de CADA aguja — ver LocalGroundProbe
     *  para el porqué (no un heightmap global). */
    private static final int GROUND_SEARCH_RADIUS = 4;
    /** Profundidad sólida mínima exigida DEBAJO del punto que LocalGroundProbe cree que es suelo,
     *  antes de aceptarlo — ver el porqué en {@link #hasSolidFooting}. BUG real reportado en
     *  juego (sesión 2026-09-04): agujas apareciendo flotando en el aire. LocalGroundProbe solo
     *  encuentra el PRIMER bloque sólido escaneando hacia abajo desde el origen del clúster — en
     *  el terreno ondulado de rocky_wasteland (EROSION_5, con cornisas/voladizos reales) ese
     *  primer bloque puede ser el borde de una cornisa delgada con aire debajo, no el suelo de
     *  verdad varios bloques más abajo. Sin verificar profundidad, la aguja se plantaba ENCIMA de
     *  esa cornisa — sólidamente apoyada en ELLA, pero visualmente "flotando" sobre el terreno
     *  real que queda más abajo. */
    private static final int MIN_SOLID_DEPTH = 4;
    /** Probabilidad de "erosionar" un bloque del borde de cada capa — silueta fracturada/angulosa,
     *  no un cilindro perfectamente liso. */
    private static final float EDGE_ERODE_CHANCE = 0.3f;
    /** Radio mínimo como fracción del radio base — nunca llega a 0: a diferencia de un pincho, un
     *  hoodoo/mesa siempre remata con algo de volumen arriba, nunca en una punta afilada. */
    private static final double MIN_RADIUS_FRACTION = 0.5;

    private final Supplier<Block> rockBlock;

    public RockyWastelandSpireFeature(Codec<NoneFeatureConfiguration> codec, Supplier<Block> rockBlock) {
        super(codec);
        this.rockBlock = rockBlock;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int spireCount = MIN_SPIRES + random.nextInt(MAX_SPIRES - MIN_SPIRES + 1);
        boolean placedAny = false;

        for (int i = 0; i < spireCount; i++) {
            int dx = random.nextInt(CLUSTER_SPREAD * 2 + 1) - CLUSTER_SPREAD;
            int dz = random.nextInt(CLUSTER_SPREAD * 2 + 1) - CLUSTER_SPREAD;
            int x = origin.getX() + dx;
            int z = origin.getZ() + dz;

            int groundY = LocalGroundProbe.findGroundY(level, x, origin.getY(), z, GROUND_SEARCH_RADIUS);
            if (groundY == Integer.MIN_VALUE) continue; // sin suelo real cerca (incluye océano abierto): no se coloca
            if (!hasSolidFooting(level, x, groundY, z)) continue; // cornisa/voladizo delgado: no es suelo real, saltar

            placeSpire(level, random, x, groundY, z, rockBlock.get());
            placedAny = true;
        }
        return placedAny;
    }

    /** {@code groundY} es el primer bloque de AIRE por encima de lo que LocalGroundProbe cree que
     *  es suelo — comprueba que los {@link #MIN_SOLID_DEPTH} bloques justo debajo son TODOS
     *  sólidos y sin fluido, no solo el primero. Descarta cornisas/voladizos delgados (una capa de
     *  roca con aire o una cueva debajo) que de otro modo se leerían como "suelo" válido y dejarían
     *  la aguja apoyada en el aire desde el punto de vista del jugador parado en el terreno real,
     *  más abajo. */
    private static boolean hasSolidFooting(WorldGenLevel level, int x, int groundY, int z) {
        for (int i = 1; i <= MIN_SOLID_DEPTH; i++) {
            BlockState state = level.getBlockState(new BlockPos(x, groundY - i, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private static void placeSpire(WorldGenLevel level, RandomSource random, int baseX, int baseY, int baseZ, Block rockBlock) {
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        int baseRadius = MIN_BASE_RADIUS + random.nextInt(MAX_BASE_RADIUS - MIN_BASE_RADIUS + 1);
        int maxY = level.getMaxBuildHeight() - 1;
        BlockState rock = rockBlock.defaultBlockState();

        // Perfil de silueta: 1-3 "cinturas" (bulges) por aguja, en vez del taper lineal a 0 de un
        // pincho — |sin(...)| nunca llega a 0 gracias al piso MIN_RADIUS_FRACTION, así que la
        // aguja siempre remata con volumen arriba, como un hoodoo/mesa real. Sección elíptica (rx
        // distinto de rz) en vez de circular en algunas: lee como pared/aleta de cañón, no solo
        // como columna redonda — variedad que ya pide la referencia (formas mezcladas).
        int bulges = 1 + random.nextInt(3);
        double phase = random.nextDouble() * Math.PI * 2;
        double aspectX = 0.7 + random.nextDouble() * 0.6;   // 0.7..1.3
        double aspectZ = 2.0 - aspectX;                     // mantiene el área ~constante

        for (int dy = 0; dy < height; dy++) {
            int y = baseY + dy;
            if (y > maxY) break;

            double t = dy / (double) height;
            double wave = Math.abs(Math.sin(t * Math.PI * bulges + phase));
            double radiusFactor = MIN_RADIUS_FRACTION + (1.0 - MIN_RADIUS_FRACTION) * wave;
            double radius = baseRadius * radiusFactor;

            int rx = Math.max(1, (int) Math.round(radius * aspectX));
            int rz = Math.max(1, (int) Math.round(radius * aspectZ));
            int rxSq = rx * rx, rzSq = rz * rz;
            int innerRxSq = Math.max(0, rx - 1) * Math.max(0, rx - 1);
            int innerRzSq = Math.max(0, rz - 1) * Math.max(0, rz - 1);

            for (int ddx = -rx; ddx <= rx; ddx++) {
                for (int ddz = -rz; ddz <= rz; ddz++) {
                    // Ecuación de elipse normalizada (ddx²/rx² + ddz²/rz² <= 1), en enteros para
                    // no repetir la división por celda: multiplicar cruzado en vez de normalizar.
                    long lhs = (long) ddx * ddx * rzSq + (long) ddz * ddz * rxSq;
                    long rhsOuter = (long) rxSq * rzSq;
                    if (lhs > rhsOuter) continue; // fuera de la elipse de esta capa
                    long rhsInner = (long) innerRxSq * innerRzSq;
                    boolean edge = innerRxSq == 0 || innerRzSq == 0 || lhs > rhsInner;
                    if (edge && random.nextFloat() < EDGE_ERODE_CHANCE) continue; // erosión del borde
                    level.setBlock(new BlockPos(baseX + ddx, y, baseZ + ddz), rock,
                            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }
}
