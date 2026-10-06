package com.hmc.zenkai.worldgen.structure;

import com.hmc.zenkai.content.entity.otherworld.AngelEntity;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ModStructures;
import com.hmc.zenkai.worldgen.LocalGroundProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.List;

/**
 * Pieza única de {@link FriezaCocoonStructure}: el castigo de Freezer en el infierno de Dragon
 * Ball Super ("La resurrección de F") — envuelto en un capullo que cuelga de la rama de un árbol en
 * flor, en un prado, mientras unos angelitos le cantan alrededor sin parar.
 * <ul>
 *   <li>Árbol grande de cerezo (tronco 2x2, copa de varias masas, hojas que cuelgan). Rosa a
 *   propósito aunque needle_wastes sea nevado: el árbol ES el concepto (el "paraíso" que tortura a
 *   Freezer), y la regla del usuario de usar los bloques del bioma es para lo que no lo es. La
 *   primera versión (una horca de piedra) no era lo que el usuario esperaba (2026-10-02).</li>
 *   <li>Rama horizontal que sale del tronco y, en su punta, el hilo de seda (varas del End) del
 *   que cuelga el capullo: huevo de lana de 5 de ancho y 7 de alto con vueltas de hormigón blanco
 *   como pliegues, y la cara de Freezer asomando por delante (blanco con morado alrededor).</li>
 *   <li>Prado bajo la copa (césped, flores y pétalos rosas), con borde irregular.</li>
 *   <li>Dos {@link AngelEntity} dando vueltas al capullo, en fases opuestas y a dos voces.</li>
 * </ul>
 *
 * NADA de API de block entity aquí (ni tocadiscos con disco, ni carteles): en worldgen el block
 * entity vive en un ProtoChunk SIN nivel (level == null) y crashea — ver .claude/docs/worldgen.md.
 * La música la ponen los ángeles.
 *
 * Cada chunk que solapa la pieza llama a postProcess: los bloques se filtran por {@code chunkBox},
 * cada entidad solo se crea en el chunk que contiene su posición, y cualquier variación sale de un
 * hash de la posición (no del RandomSource del chunk) para que cada chunk dibuje el mismo árbol.
 */
public class FriezaCocoonPiece extends StructurePiece {

    /** Tronco a esta distancia del capullo, por detrás de la rama: deja hueco a la vuelta de los
     *  ángeles (radio AngelEntity.ORBIT_RADIUS). */
    private static final int TRUNK_OFFSET = 6;
    private static final int TRUNK_TOP = 14;
    private static final int BRANCH_Y = 12;
    /** Capullo: capas de abajo arriba y su radio. Huevo con el ancho máximo un poco por encima
     *  de la mitad. */
    private static final int COCOON_BOTTOM = 3;
    private static final double[] COCOON_RADII = {1.0, 1.8, 2.3, 2.5, 2.4, 2.0, 1.2};
    private static final int MEADOW_RADIUS = 7;
    private static final int GROUND_SEARCH_RADIUS = 4;
    /** Mitad del lado de la caja (cubre copa y prado con margen). */
    private static final int HALF = 13;
    private static final int HEIGHT = 20;

    /** Masas de la copa: {a lo largo, lateral, y, radio horizontal, radio vertical}. */
    private static final double[][] CANOPY = {
            {-TRUNK_OFFSET, 0.5, 15.5, 5.2, 2.6},
            {-1, 0, 14.5, 4.2, 2.0},
            {-10.5, 0.5, 14, 3.2, 2.0},
            {-6, 4.5, 14, 3.2, 2.0},
            {-6, -3.5, 14, 3.2, 2.0},
    };
    /** Por debajo de esto no hay copa: deja libres la rama, el hilo y el capullo. */
    private static final int CANOPY_MIN_Y = BRANCH_Y + 1;

    private static final List<Block> FLOWERS = List.of(Blocks.PINK_TULIP, Blocks.ALLIUM, Blocks.OXEYE_DAISY,
            Blocks.LILY_OF_THE_VALLEY, Blocks.AZURE_BLUET, Blocks.CORNFLOWER);

    private final BlockPos origin;
    private final boolean alongX;

    public FriezaCocoonPiece(BlockPos origin, boolean alongX) {
        super(ModStructures.FRIEZA_COCOON_PIECE.get(), 0, new BoundingBox(
                origin.getX() - HALF, origin.getY() - 3, origin.getZ() - HALF,
                origin.getX() + HALF, origin.getY() + HEIGHT, origin.getZ() + HALF));
        this.origin = origin;
        this.alongX = alongX;
    }

    public FriezaCocoonPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.FRIEZA_COCOON_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
        this.alongX = tag.getBoolean("AlongX");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("OX", origin.getX());
        tag.putInt("OY", origin.getY());
        tag.putInt("OZ", origin.getZ());
        tag.putBoolean("AlongX", alongX);
    }

    /** Posición en el sistema de la pieza: a lo largo de la rama, lateral y altura sobre el suelo. */
    private BlockPos at(BlockPos base, int a, int l, int y) {
        Direction along = alongX ? Direction.EAST : Direction.SOUTH;
        return base.relative(along, a).relative(along.getClockWise(), l).above(y);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        int g0 = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (g0 == Integer.MIN_VALUE) g0 = origin.getY();
        BlockPos base = new BlockPos(origin.getX(), g0, origin.getZ());
        Direction along = alongX ? Direction.EAST : Direction.SOUTH;
        Direction.Axis alongAxis = along.getAxis();
        Direction.Axis sideAxis = along.getClockWise().getAxis();

        meadow(level, chunkBox, base, g0);

        // Tronco 2x2, hundido 3 en el suelo, con raíces asomando en la base.
        BlockState trunk = Blocks.CHERRY_LOG.defaultBlockState();
        BlockState wood = Blocks.CHERRY_WOOD.defaultBlockState();
        for (int a = -TRUNK_OFFSET - 1; a <= -TRUNK_OFFSET; a++) {
            for (int l = 0; l <= 1; l++) {
                for (int y = -3; y <= TRUNK_TOP; y++) set(level, chunkBox, at(base, a, l, y), trunk);
            }
        }
        int[][] roots = {{-TRUNK_OFFSET + 1, 0}, {-TRUNK_OFFSET - 2, 1}, {-TRUNK_OFFSET, -1}, {-TRUNK_OFFSET - 1, 2}};
        for (int[] r : roots) {
            set(level, chunkBox, at(base, r[0], r[1], 0), wood);
            set(level, chunkBox, at(base, r[0], r[1], -1), wood);
        }

        // Rama del capullo: sube en escalón desde el tronco y sigue recta hasta pasar el capullo.
        BlockState branchLog = Blocks.CHERRY_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongAxis);
        set(level, chunkBox, at(base, -TRUNK_OFFSET + 1, 0, BRANCH_Y - 2), wood);
        set(level, chunkBox, at(base, -TRUNK_OFFSET + 2, 0, BRANCH_Y - 1), wood);
        set(level, chunkBox, at(base, -TRUNK_OFFSET + 2, 0, BRANCH_Y - 2), wood);
        for (int a = -TRUNK_OFFSET + 3; a <= 2; a++) set(level, chunkBox, at(base, a, 0, BRANCH_Y), branchLog);
        set(level, chunkBox, at(base, -TRUNK_OFFSET + 3, 0, BRANCH_Y - 1), wood);

        // Ramas cortas hacia atrás y a los lados, que sostienen las otras masas de la copa.
        for (int a = -TRUNK_OFFSET - 2; a >= -TRUNK_OFFSET - 4; a--) {
            set(level, chunkBox, at(base, a, 0, BRANCH_Y - 1 + (a <= -TRUNK_OFFSET - 4 ? 1 : 0)), branchLog);
        }
        BlockState sideLog = Blocks.CHERRY_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, sideAxis);
        for (int l = 2; l <= 4; l++) set(level, chunkBox, at(base, -TRUNK_OFFSET, l, BRANCH_Y - 2 + (l == 4 ? 1 : 0)), sideLog);
        for (int l = -1; l >= -3; l--) set(level, chunkBox, at(base, -TRUNK_OFFSET - 1, l, BRANCH_Y - 2 + (l == -3 ? 1 : 0)), sideLog);

        canopy(level, chunkBox, base);

        // Hilo de seda de la rama al capullo.
        int cocoonTop = COCOON_BOTTOM + COCOON_RADII.length - 1;
        BlockState thread = Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN);
        for (int y = cocoonTop + 1; y < BRANCH_Y; y++) set(level, chunkBox, at(base, 0, 0, y), thread);

        cocoon(level, chunkBox, base);

        // Ángeles: uno a cada lado de la vuelta, a dos voces. Centro de la vuelta a media altura
        // del capullo menos ORBIT_HEIGHT (la vuelta se hace ORBIT_HEIGHT por encima del centro).
        BlockPos orbitCenter = at(base, 0, 0, COCOON_BOTTOM + 3 - (int) AngelEntity.ORBIT_HEIGHT);
        angel(level, chunkBox, orbitCenter, 0.0, 0);
        angel(level, chunkBox, orbitCenter, Math.PI, 1);
    }

    private void cocoon(WorldGenLevel level, BoundingBox chunkBox, BlockPos base) {
        BlockState silk = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState fold = Blocks.WHITE_CONCRETE.defaultBlockState();
        // Cuarzo liso y no hormigón: el hormigón blanco ya son los pliegues.
        BlockState face = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState head = Blocks.PURPLE_CONCRETE.defaultBlockState();
        for (int i = 0; i < COCOON_RADII.length; i++) {
            double r = COCOON_RADII[i];
            int y = COCOON_BOTTOM + i;
            int ri = (int) Math.ceil(r);
            for (int a = -ri; a <= ri; a++) {
                for (int l = -ri; l <= ri; l++) {
                    if (a * a + l * l > r * r + 0.3) continue;
                    // Pliegues: vueltas en diagonal alrededor del huevo (ángulo + altura).
                    double ang = Math.atan2(l, a);
                    boolean isFold = Math.floorMod((int) Math.floor(ang * 2.5 + y * 0.9), 3) == 0;
                    set(level, chunkBox, at(base, a, l, y), isFold ? fold : silk);
                }
            }
        }
        // Cara de Freezer asomando por un costado (lateral +2, la superficie del huevo a esa
        // altura): mira de lado a la rama, como en la referencia de DBS. Cara blanca, morado de la
        // cabeza a los lados y encima.
        int faceY = COCOON_BOTTOM + 4;
        set(level, chunkBox, at(base, 0, 2, faceY), face);
        set(level, chunkBox, at(base, -1, 2, faceY), head);
        set(level, chunkBox, at(base, 1, 2, faceY), head);
        set(level, chunkBox, at(base, 0, 2, faceY + 1), head);
    }

    private void canopy(WorldGenLevel level, BoundingBox chunkBox, BlockPos base) {
        BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int a = -HALF; a <= HALF; a++) {
            for (int l = -HALF; l <= HALF; l++) {
                int lowest = Integer.MAX_VALUE;
                for (int y = CANOPY_MIN_Y; y <= HEIGHT; y++) {
                    BlockPos p = at(base, a, l, y);
                    if (!inCanopy(a, l, y, p)) continue;
                    if (level.getBlockState(p).isAir()) set(level, chunkBox, p, leaves);
                    lowest = Math.min(lowest, y);
                }
                // Hojas que cuelgan bajo el borde de la copa, lejos del hilo del capullo.
                if (lowest != Integer.MAX_VALUE && Math.abs(a) + Math.abs(l) > 3) {
                    int h = Math.floorMod(at(base, a, l, 0).hashCode() * 31, 7);
                    int drop = h == 0 ? 2 : h <= 2 ? 1 : 0;
                    for (int d = 1; d <= drop; d++) {
                        BlockPos p = at(base, a, l, lowest - d);
                        if (level.getBlockState(p).isAir()) set(level, chunkBox, p, leaves);
                    }
                }
            }
        }
    }

    private static boolean inCanopy(int a, int l, int y, BlockPos p) {
        // Borde con algo de ruido para que no sean esferas perfectas.
        double noise = (Math.floorMod(p.hashCode() * 17, 100) / 100.0) * 0.22;
        for (double[] c : CANOPY) {
            double dx = (a - c[0]) / c[3], dz = (l - c[1]) / c[3], dy = (y - c[2]) / c[4];
            if (dx * dx + dz * dz + dy * dy <= 1.0 - noise) return true;
        }
        return false;
    }

    /** Prado bajo la copa: césped con borde irregular, flores y pétalos (todo por hash). */
    private void meadow(WorldGenLevel level, BoundingBox chunkBox, BlockPos base, int g0) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int a = -MEADOW_RADIUS - 6; a <= MEADOW_RADIUS; a++) {
            for (int l = -MEADOW_RADIUS; l <= MEADOW_RADIUS; l++) {
                // Elipse estirada hacia el tronco: el prado sigue a la copa, no al capullo.
                double da = (a + 3) / (MEADOW_RADIUS + 2.0), dl = l / (double) MEADOW_RADIUS;
                int h = Math.floorMod(at(base, a, l, 0).hashCode() * 131, 100);
                if (da * da + dl * dl > 1.0 - h / 400.0) continue;
                BlockPos col = at(base, a, l, 0);
                int g = LocalGroundProbe.findGroundY(level, col.getX(), g0, col.getZ(), GROUND_SEARCH_RADIUS);
                if (g == Integer.MIN_VALUE) continue;
                BlockPos top = new BlockPos(col.getX(), g, col.getZ());
                set(level, chunkBox, top.below(), grass);
                if (!level.getBlockState(top).isAir()) continue;
                if (h < 22) {
                    set(level, chunkBox, top, FLOWERS.get(h % FLOWERS.size()).defaultBlockState());
                } else if (h < 40) {
                    set(level, chunkBox, top, Blocks.PINK_PETALS.defaultBlockState()
                            .setValue(PinkPetalsBlock.AMOUNT, 1 + h % 4)
                            .setValue(PinkPetalsBlock.FACING, Direction.from2DDataValue(h % 4)));
                } else if (h < 55) {
                    set(level, chunkBox, top, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
        }
    }

    private static void angel(WorldGenLevel level, BoundingBox chunkBox, BlockPos center, double phase, int voice) {
        double x = center.getX() + 0.5 + Math.cos(phase) * AngelEntity.ORBIT_RADIUS;
        double y = center.getY() + AngelEntity.ORBIT_HEIGHT;
        double z = center.getZ() + 0.5 + Math.sin(phase) * AngelEntity.ORBIT_RADIUS;
        if (!chunkBox.isInside(BlockPos.containing(x, y, z))) return; // solo el chunk que lo contiene
        AngelEntity angel = ModEntities.ANGEL.get().create(level.getLevel());
        if (angel == null) return;
        angel.moveTo(x, y, z, 0f, 0f);
        angel.orbit(center, phase, voice);
        angel.setPersistenceRequired();
        angel.finalizeSpawn(level, level.getCurrentDifficultyAt(angel.blockPosition()), MobSpawnType.STRUCTURE, null);
        level.addFreshEntityWithPassengers(angel);
    }

    private static void set(WorldGenLevel level, BoundingBox chunkBox, BlockPos p, BlockState state) {
        if (!chunkBox.isInside(p) || p.getY() < level.getMinBuildHeight()) return;
        level.setBlock(p, state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
    }
}
