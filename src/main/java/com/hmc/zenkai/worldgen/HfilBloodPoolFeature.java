package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

/**
 * Charco de sangre del HFIL, ABIERTO de verdad — sustituye a {@code Feature.LAKE} (ver
 * .claude/pendiente/hfil-rework-propuesta.md, secciones 1.2 y 5.2, Fase 5). El motivo del
 * cambio: {@code LakeFeature.Configuration} solo expone dos {@code BlockStateProvider} (fluido +
 * barrier) — la FORMA del hueco y la decisión de taparlo con barrier por arriba son internas de
 * {@code LakeFeature} y no configurables. En juego eso se veía como un disco maroon tapado
 * ("blops"), no como un charco abierto — no había ningún parámetro que lo arreglara, había que
 * sustituir la feature entera.
 *
 * Mismo estilo ligero que {@link HfilSpikeFeature}/{@link HfilBonePileFeature}: sin jigsaw,
 * colocación de bloques a mano, sondeo de suelo LOCAL por columna vía {@link LocalGroundProbe}
 * (nunca un heightmap global — mismo motivo que esas dos features). Cava una cubeta somera (más
 * profunda en el centro, superficial hacia el borde) y la rellena de agua hasta el nivel de
 * suelo original de cada columna — SIN ninguna capa de barrier POR ENCIMA del agua. El barrier
 * (HFIL_SCORCHED_STONE) se usa solo como orilla, a nivel de suelo, en el anillo exterior; nunca
 * como techo. El agua queda expuesta al cielo siempre, igual que la referencia real del "Blood
 * Pond" (agua/sangre a la vista, no una gruta).
 */
public class HfilBloodPoolFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 6;
    /** Profundidad máxima de la cubeta, en el centro del charco. */
    private static final int MAX_DEPTH = 3;
    /** Ventana de sondeo local del suelo real de cada columna — ver LocalGroundProbe para el
     *  porqué (no un heightmap global: una columna puede tener una isla flotante muy por
     *  encima del suelo real del HFIL). */
    private static final int GROUND_SEARCH_RADIUS = 4;
    /** Probabilidad de "morder" el borde (charco y orilla) — mismo espíritu que
     *  HfilSpikeFeature.EDGE_ERODE_CHANCE, para que el contorno no sea un círculo perfecto. */
    private static final float EDGE_ERODE_CHANCE = 0.3f;
    /** Probabilidad de que ESTE charco (no todos) lleve huesos hundidos en el borde — variedad
     *  "cementerio", no un adorno garantizado en cada lago. */
    private static final float SUNKEN_BONES_CHANCE = 0.35f;
    private static final int MIN_SUNKEN_BONES = 1;
    private static final int MAX_SUNKEN_BONES = 3;
    /** Ventana de sondeo local para los huesos hundidos — mismo motivo que GROUND_SEARCH_RADIUS
     *  de arriba, más pequeña porque solo hace falta encontrar el borde inmediato del charco. */
    private static final int SUNKEN_BONES_GROUND_SEARCH_RADIUS = 3;

    public HfilBloodPoolFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        boolean placed = carve(level, random, origin, radius, MAX_DEPTH, GROUND_SEARCH_RADIUS, EDGE_ERODE_CHANCE, null);
        if (placed && random.nextFloat() < SUNKEN_BONES_CHANCE) {
            placeSunkenBones(level, random, origin, radius);
        }
        return placed;
    }

    /**
     * Huesos hundidos en el borde del charco — variedad "cementerio", pieza 4 de
     * .claude/pendiente (sesión de variedad por bioma). Nota de diseño explícita: NO se apoyan en
     * HFIL_SCORCHED_STONE (el rojo de esa piedra no combina con el agua roja del charco) sino en
     * un pequeño parche de arena — encaja además con el propio nombre "Blood Shore" (orilla).
     * Solo afecta a la decoración normal ({@code place()}), no a {@code carve()} (compartido con
     * BloodPondPiece, el landmark de la Fase 6, que ya tiene sus propios marcadores de
     * hueso/calavera) — así el landmark no se ve afectado por este cambio.
     */
    private static void placeSunkenBones(WorldGenLevel level, RandomSource random, BlockPos origin, int poolRadius) {
        int boneCount = MIN_SUNKEN_BONES + random.nextInt(MAX_SUNKEN_BONES - MIN_SUNKEN_BONES + 1);
        for (int i = 0; i < boneCount; i++) {
            // Ángulo aleatorio, distancia entre medio radio y justo al filo del charco — que
            // queden cerca del agua, no dispersos por toda la orilla.
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = poolRadius * (0.5 + random.nextDouble() * 0.6);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * dist);

            int groundY = LocalGroundProbe.findGroundY(level, x, origin.getY(), z, SUNKEN_BONES_GROUND_SEARCH_RADIUS);
            if (groundY == Integer.MIN_VALUE) continue;

            BlockPos bonePos = new BlockPos(x, groundY, z);
            if (!level.getBlockState(bonePos).isAir()) continue;

            level.setBlock(bonePos.below(), Blocks.SAND.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            Direction.Axis axis = Direction.Axis.values()[random.nextInt(3)];
            BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
            level.setBlock(bonePos, bone, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    /**
     * Cava y rellena la cubeta — cuerpo compartido con {@code place()} de arriba, extraído para
     * que {@code BloodPondPiece} (el landmark de la Fase 6, ver
     * .claude/pendiente/hfil-rework-propuesta.md sección 7/8) pueda generar el MISMO tipo de
     * charco abierto a un tamaño fijo mayor, en vez de reimplementar el algoritmo. {@code
     * chunkBox}, si no es null, filtra cada bloque colocado (uso de {@code StructurePiece},
     * cuyo {@code postProcess} puede llamarse una vez por cada chunk que solape la pieza — sin
     * el filtro se repetiría trabajo, inofensivo pero innecesario). Las features normales
     * (este mismo {@code place()}) pasan {@code null}: una feature solo se coloca una vez, sin
     * necesidad de recortar por chunk.
     */
    public static boolean carve(WorldGenLevel level, RandomSource random, BlockPos origin,
                                 int radius, int maxDepth, int groundSearchRadius, float edgeErodeChance,
                                 @Nullable BoundingBox chunkBox) {
        int centerGroundY = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), groundSearchRadius);
        if (centerGroundY == Integer.MIN_VALUE) return false; // sin suelo real bajo el origen

        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState bank = ModBlocks.HFIL_SCORCHED_STONE.get().defaultBlockState();
        int minY = level.getMinBuildHeight();

        boolean placedAny = false;
        int innerSq = (radius - 1) * (radius - 1);
        int rSq = radius * radius;
        int bankSq = (radius + 1) * (radius + 1);

        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > bankSq) continue; // fuera del área total (charco + orilla)

                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (chunkBox != null && !chunkBox.isInside(new BlockPos(x, origin.getY(), z))) continue;
                // Cada columna sondea su PROPIO suelo local en vez de asumir la Y del centro —
                // el terreno del HFIL no es perfectamente plano en un radio de 7 bloques.
                int groundY = LocalGroundProbe.findGroundY(level, x, centerGroundY, z, groundSearchRadius);
                if (groundY == Integer.MIN_VALUE) continue;

                if (distSq <= rSq) {
                    // Interior del charco: erosión solo cerca del borde, para un contorno
                    // irregular en vez de un círculo perfecto (el centro nunca se erosiona,
                    // así el charco no queda con agujeros en medio).
                    if (distSq > innerSq && random.nextFloat() < edgeErodeChance) continue;

                    double t = Math.sqrt(distSq) / radius; // 0 en el centro, 1 en el borde
                    int depth = Math.max(1, (int) Math.round(maxDepth * (1.0 - t)));
                    for (int dy = 0; dy < depth; dy++) {
                        int y = groundY - 1 - dy;
                        if (y < minY) break;
                        level.setBlock(new BlockPos(x, y, z), water, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                    }
                    placedAny = true;
                } else {
                    // Anillo exterior: orilla de roca calcinada a nivel de suelo — reviste el
                    // borde del charco, nunca tapa el agua desde arriba.
                    if (random.nextFloat() < edgeErodeChance) continue;
                    int y = groundY - 1;
                    if (y < minY) continue;
                    level.setBlock(new BlockPos(x, y, z), bank, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }

        return placedAny;
    }
}
