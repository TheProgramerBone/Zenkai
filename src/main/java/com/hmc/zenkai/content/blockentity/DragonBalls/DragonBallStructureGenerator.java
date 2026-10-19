package com.hmc.zenkai.content.blockentity.DragonBalls;

import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class DragonBallStructureGenerator {

    private static final Set<Block> EARTH_BALLS = Set.of(
            ModBlocks.DRAGON_BALL_1.get(), ModBlocks.DRAGON_BALL_2.get(), ModBlocks.DRAGON_BALL_3.get(),
            ModBlocks.DRAGON_BALL_4.get(), ModBlocks.DRAGON_BALL_5.get(), ModBlocks.DRAGON_BALL_6.get(),
            ModBlocks.DRAGON_BALL_7.get());

    private static final Set<Block> NAMEK_BALLS = Set.of(
            ModBlocks.NAMEK_DRAGON_BALL_1.get(), ModBlocks.NAMEK_DRAGON_BALL_2.get(), ModBlocks.NAMEK_DRAGON_BALL_3.get(),
            ModBlocks.NAMEK_DRAGON_BALL_4.get(), ModBlocks.NAMEK_DRAGON_BALL_5.get(), ModBlocks.NAMEK_DRAGON_BALL_6.get(),
            ModBlocks.NAMEK_DRAGON_BALL_7.get());

    private static final List<BlockPos> H_PATTERN_1 = List.of(
            new BlockPos(-1, 0, -1),
            new BlockPos( 1, 0, -1),
            new BlockPos(-1, 0,  0),
            new BlockPos( 0, 0,  0),
            new BlockPos( 1, 0,  0),
            new BlockPos(-1, 0,  1),
            new BlockPos( 1, 0,  1)
    );

    private static final List<BlockPos> H_PATTERN_2 = List.of(
            new BlockPos(-1, 0,  1),
            new BlockPos( 0, 0,  1),
            new BlockPos( 1, 0,  1),
            new BlockPos( 0, 0,  0),
            new BlockPos(-1, 0, -1),
            new BlockPos( 0, 0, -1),
            new BlockPos( 1, 0, -1)
    );

    public static boolean tryFormStructure(Level level, BlockPos origin) {
        return tryFormStructure(level, origin, null);
    }

    /** Con {@code placer}, si la fusión ocurre se le otorga el logro de la H (Tierra o Namek). */
    public static boolean tryFormStructure(Level level, BlockPos origin, @Nullable LivingEntity placer) {
        if (level.isClientSide()) return false;

        // El juego lo decide la esfera recién colocada (el centro de la H): las de la Tierra y las de
        // Namek NO se mezclan, y cada juego se funde en su propio bloque. Antes solo se miraba el juego
        // de la Tierra, así que las de Namek llamaban aquí y nunca fundían.
        Block placed = level.getBlockState(origin).getBlock();
        Set<Block> required = EARTH_BALLS.contains(placed) ? EARTH_BALLS : NAMEK_BALLS.contains(placed) ? NAMEK_BALLS : null;
        if (required == null) return false;

        Map<BlockPos, Block> foundBlocks = new HashMap<>();

        for (BlockPos offset : H_PATTERN_1) {
            BlockPos pos = origin.offset(offset);
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();

            if (required.contains(block)) {
                foundBlocks.put(pos, block);
            }
        }

        for (BlockPos offset : H_PATTERN_2) {
            BlockPos pos = origin.offset(offset);
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();

            if (required.contains(block)) {
                foundBlocks.put(pos, block);
            }
        }

        if (foundBlocks.size() == 7 && new HashSet<>(foundBlocks.values()).containsAll(required)) {
            for (BlockPos pos : foundBlocks.keySet()) {
                level.removeBlock(pos, false);
            }

            level.setBlockAndUpdate(origin, (required == EARTH_BALLS ? ModBlocks.ALL_DRAGON_BALLS : ModBlocks.NAMEK_ALL_DRAGON_BALLS)
                    .get().defaultBlockState());

            if (placer instanceof ServerPlayer sp) {
                ZenkaiTriggers.MILESTONE.get().trigger(sp, required == EARTH_BALLS
                        ? ZenkaiTriggers.Kinds.DRAGON_BALLS_FUSED : ZenkaiTriggers.Kinds.NAMEK_DRAGON_BALLS_FUSED);
            }
            return true;
        }

        return false;
    }
}
