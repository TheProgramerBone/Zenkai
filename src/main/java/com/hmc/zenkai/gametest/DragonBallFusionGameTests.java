package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.DragonBalls.DragonBallStructureGenerator;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Supplier;

/**
 * Fusión de las siete esferas en una H (DragonBallStructureGenerator): cada juego se funde en SU
 * bloque y no se mezclan. Las esferas de Namek no fundían nunca (el generador solo miraba las de la
 * Tierra); regresión de 2026-10-18.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DragonBallFusionGameTests {
    private DragonBallFusionGameTests() {}

    /** Celdas de la H (rejilla 3x3 en el plano XZ); la 4ª es el centro, que se coloca la última. */
    private static final int[][] H = {{-1, -1}, {1, -1}, {-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {1, 1}};

    private static void place(ServerLevel level, BlockPos center, List<Supplier<Block>> balls, int skip) {
        for (int i = 0; i < H.length; i++) {
            if (i == skip) continue;
            level.setBlock(center.offset(H[i][0], 0, H[i][1]), balls.get(i).get().defaultBlockState(), 3);
        }
    }

    private static final List<Supplier<Block>> EARTH = List.of(
            ModBlocks.DRAGON_BALL_1, ModBlocks.DRAGON_BALL_2, ModBlocks.DRAGON_BALL_3, ModBlocks.DRAGON_BALL_4,
            ModBlocks.DRAGON_BALL_5, ModBlocks.DRAGON_BALL_6, ModBlocks.DRAGON_BALL_7);
    private static final List<Supplier<Block>> NAMEK = List.of(
            ModBlocks.NAMEK_DRAGON_BALL_1, ModBlocks.NAMEK_DRAGON_BALL_2, ModBlocks.NAMEK_DRAGON_BALL_3,
            ModBlocks.NAMEK_DRAGON_BALL_4, ModBlocks.NAMEK_DRAGON_BALL_5, ModBlocks.NAMEK_DRAGON_BALL_6,
            ModBlocks.NAMEK_DRAGON_BALL_7);

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void eachSetFusesIntoItsOwnBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(3, 2, 3));

        place(level, center, EARTH, 3);
        level.setBlock(center, ModBlocks.DRAGON_BALL_4.get().defaultBlockState(), 3);
        helper.assertTrue(DragonBallStructureGenerator.tryFormStructure(level, center)
                && level.getBlockState(center).is(ModBlocks.ALL_DRAGON_BALLS.get()), "la Tierra funde en ALL_DRAGON_BALLS");
        helper.assertTrue(level.getBlockState(center.offset(1, 0, 1)).isAir(), "las seis de fuera desaparecen");

        BlockPos nc = helper.absolutePos(new BlockPos(3, 2, 10));
        place(level, nc, NAMEK, 3);
        level.setBlock(nc, ModBlocks.NAMEK_DRAGON_BALL_4.get().defaultBlockState(), 3);
        helper.assertTrue(DragonBallStructureGenerator.tryFormStructure(level, nc)
                && level.getBlockState(nc).is(ModBlocks.NAMEK_ALL_DRAGON_BALLS.get()),
                "Namek funde en NAMEK_ALL_DRAGON_BALLS (antes no fundía nunca)");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void setsDoNotMixAndAllSevenAreNeeded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(3, 2, 3));

        // Seis de la Tierra y una de Namek: no se funde.
        place(level, center, EARTH, 3);
        level.setBlock(center.offset(1, 0, 1), ModBlocks.NAMEK_DRAGON_BALL_7.get().defaultBlockState(), 3);
        level.setBlock(center, ModBlocks.DRAGON_BALL_4.get().defaultBlockState(), 3);
        helper.assertTrue(!DragonBallStructureGenerator.tryFormStructure(level, center), "no se mezclan los juegos");

        // Seis de Namek (falta una): tampoco.
        BlockPos nc = helper.absolutePos(new BlockPos(3, 2, 10));
        place(level, nc, NAMEK, 3);
        level.setBlock(nc.offset(1, 0, 1), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(nc, ModBlocks.NAMEK_DRAGON_BALL_4.get().defaultBlockState(), 3);
        helper.assertTrue(!DragonBallStructureGenerator.tryFormStructure(level, nc), "con seis no se funde");
        helper.succeed();
    }
}
