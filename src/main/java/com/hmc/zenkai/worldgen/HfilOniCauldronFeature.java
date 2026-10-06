package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.content.entity.otherworld.OgreEntity;
import com.hmc.zenkai.registry.ModEntities;
import com.mojang.serialization.Codec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Caldero de los oni — propuesta G de .claude/pendiente/worldgen-pendiente.md. Referencia: el
 * kama-yude, el castigo de hervir en un caldero del Jigoku (el infierno budista japonés, del que
 * sale el HFIL de Dragon Ball: el Blood Pond es su Chi no Ike), donde los oni vigilan la olla.
 * Lo custodian un ogro rojo y uno azul (OgreEntity), generados con él. Exclusivo de blood_shore,
 * raro: es un landmark.
 *
 * La olla es un OBJETO fabricado, no terreno: hierro negro (piedra negra pulida), no el suelo del
 * bioma. Lo que la rodea sí es del bioma: se asienta sobre un cimiento del propio suelo. Dentro,
 * sangre (el agua del HFIL ya es roja) que HIERVE de verdad: el fondo es magma y encima columnas de
 * burbujas que arrastran hacia abajo (las de un bloque de magma bajo el agua). Se colocan ya
 * hechas porque en worldgen no corren las actualizaciones que las crearían solas. Debajo, hogueras
 * encendidas; al lado, un andamio para asomarse; y un remo (cerca) asomando de la olla.
 */
public class HfilOniCauldronFeature extends Feature<NoneFeatureConfiguration> {

    /** Radio exterior de la olla (pared incluida). */
    private static final int RADIUS = 3;
    /** Alto de la pared sobre el suelo. */
    private static final int HEIGHT = 4;
    private static final int GROUND_SEARCH_RADIUS = 4;
    private static final int MAX_STEP = 1;

    public HfilOniCauldronFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int g0 = LocalGroundProbe.findGroundY(level, origin.getX(), origin.getY(), origin.getZ(), GROUND_SEARCH_RADIUS);
        if (g0 == Integer.MIN_VALUE) return false;
        int pad = RADIUS + 2; // olla + hogueras + andamio
        for (int dx = -pad; dx <= pad; dx++) {
            for (int dz = -pad; dz <= pad; dz++) {
                int g = LocalGroundProbe.findGroundY(level, origin.getX() + dx, g0, origin.getZ() + dz, GROUND_SEARCH_RADIUS);
                if (g == Integer.MIN_VALUE || Math.abs(g - g0) > MAX_STEP) return false;
            }
        }
        BlockState ground = level.getBlockState(new BlockPos(origin.getX(), g0 - 1, origin.getZ()));
        BlockPos base = new BlockPos(origin.getX(), g0, origin.getZ()); // primer bloque sobre el suelo

        BlockState wall = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState boiling = Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true);
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > RADIUS * RADIUS) continue;
                boolean inside = d2 <= (RADIUS - 1) * (RADIUS - 1);
                int x = base.getX() + dx, z = base.getZ() + dz;
                // Cimiento del suelo del bioma bajo la olla, por si el terreno baja un bloque.
                set(level, x, g0 - 1, z, ground);
                set(level, x, g0 - 2, z, ground);
                // Fondo de magma (el fuego bajo la olla) y, encima, la sangre hirviendo.
                set(level, x, g0, z, inside ? magma : wall);
                for (int y = 1; y < HEIGHT; y++) set(level, x, g0 + y, z, inside ? boiling : wall);
                // El último bloque del interior queda vacío: la olla no rebosa.
                set(level, x, g0 + HEIGHT, z, inside ? air : wall);
                for (int y = HEIGHT + 1; y <= HEIGHT + 3; y++) set(level, x, g0 + y, z, air);
            }
        }

        // Hogueras encendidas pegadas a la olla, en los cuatro lados.
        BlockState campfire = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = base.relative(d, RADIUS + 1);
            if (level.getBlockState(p).isAir()) {
                set(level, p.getX(), p.getY(), p.getZ(), campfire.setValue(CampfireBlock.FACING, d));
            }
        }

        // Andamio para asomarse, en una esquina al azar, una altura por encima del borde.
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos tower = base.relative(side, RADIUS + 1).relative(side.getClockWise(), 2);
        for (int y = 0; y <= HEIGHT; y++) {
            BlockPos p = tower.above(y);
            if (!level.getBlockState(p).isAir()) break;
            set(level, p.getX(), p.getY(), p.getZ(), Blocks.SCAFFOLDING.defaultBlockState()
                    .setValue(ScaffoldingBlock.DISTANCE, 0).setValue(ScaffoldingBlock.BOTTOM, false));
        }

        // Remo: una cerca de roble oscuro que sale de la sangre y asoma sobre el borde.
        BlockPos paddle = base.relative(side.getOpposite(), 1);
        BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        for (int y = 1; y < HEIGHT; y++) {
            set(level, paddle.getX(), g0 + y, paddle.getZ(), fence.setValue(FenceBlock.WATERLOGGED, true));
        }
        for (int y = HEIGHT; y <= HEIGHT + 2; y++) set(level, paddle.getX(), g0 + y, paddle.getZ(), fence);
        // Bajo el remo, el fondo vuelve a ser magma; encima del remo no hay columna de burbujas.
        set(level, paddle.getX(), g0, paddle.getZ(), magma);

        // Sus guardias (propuesta I): Goz y Mez, un ogro rojo y uno azul a cada lado de la olla,
        // atados a ella (OgreEntity.guard, que se guarda en el NBT). Persistentes: el caldero es
        // un landmark, sus guardias no deben desaparecer al alejarse.
        spawnGuard(level, ModEntities.RED_OGRE.get(), base.relative(side.getClockWise(), RADIUS + 2), base);
        spawnGuard(level, ModEntities.BLUE_OGRE.get(), base.relative(side.getCounterClockWise(), RADIUS + 2), base);
        return true;
    }

    private static void spawnGuard(WorldGenLevel level, EntityType<OgreEntity> type, BlockPos at, BlockPos post) {
        int g = LocalGroundProbe.findGroundY(level, at.getX(), at.getY(), at.getZ(), GROUND_SEARCH_RADIUS);
        if (g == Integer.MIN_VALUE) return;
        OgreEntity ogre = type.create(level.getLevel());
        if (ogre == null) return;
        ogre.moveTo(at.getX() + 0.5, g, at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        ogre.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null);
        ogre.setPersistenceRequired();
        ogre.guard(post);
        level.addFreshEntityWithPassengers(ogre);
    }

    private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
        if (y < level.getMinBuildHeight()) return;
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
    }
}
