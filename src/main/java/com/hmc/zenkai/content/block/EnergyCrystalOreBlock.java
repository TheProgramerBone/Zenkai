package com.hmc.zenkai.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * Mena de cristal de energía. Misma lógica que la mena de redstone: se enciende al
 * golpearla, pisarla o hacer click derecho, y se apaga sola con el random tick.
 * Ojo con la visibilidad de los override: en 1.21 casi BlockBehaviour pasó a
 * protected, así que aquí se declaran public (ampliar visibilidad siempre es legal).
 */
public class EnergyCrystalOreBlock extends DropExperienceBlock {

    public static final MapCodec<EnergyCrystalOreBlock> CODEC = simpleCodec(EnergyCrystalOreBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Partícula cian, el equivalente al polvo rojo de la redstone. */
    private static final DustParticleOptions SPARK =
            new DustParticleOptions(new Vector3f(0.35f, 0.95f, 1.0f), 1.0f);

    public EnergyCrystalOreBlock(Properties props) {
        super(UniformInt.of(3, 7), props);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    public @NotNull MapCodec<? extends EnergyCrystalOreBlock> codec() {
        return CODEC;
    }

    @Override
    public void attack(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player) {
        interact(state, level, pos);
        super.attack(state, level, pos, player);
    }

    @Override
    public void stepOn(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Entity entity) {
        // Agachado no la enciende, igual que en vainilla: sirve para colarse a oscuras.
        if (!entity.isSteppingCarefully()) {
            interact(state, level, pos);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack, @NotNull BlockState state,
                                                       @NotNull Level level, @NotNull BlockPos pos,
                                                       @NotNull Player player, @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            spawnParticles(level, pos);
        } else {
            interact(state, level, pos);
        }
        // Si llevas un bloque en la mano, gana colocarlo: encender no debe bloquear la construcción.
        return stack.getItem() instanceof BlockItem && new BlockPlaceContext(player, hand, stack, hit).canPlace()
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.SUCCESS;
    }

    private static void interact(BlockState state, Level level, BlockPos pos) {
        spawnParticles(level, pos);
        if (!state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean isRandomlyTicking(@NotNull BlockState state) {
        return state.getValue(LIT);
    }

    @Override
    protected void randomTick(@NotNull BlockState state, @NotNull ServerLevel level,
                              @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        }
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level,
                            @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (state.getValue(LIT)) {
            spawnParticles(level, pos);
        }
    }

    private static void spawnParticles(Level level, BlockPos pos) {
        RandomSource random = level.random;
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            if (level.getBlockState(side).isSolidRender(level, side)) continue;

            Direction.Axis axis = dir.getAxis();
            double x = axis == Direction.Axis.X ? 0.5 + 0.5625 * dir.getStepX() : random.nextFloat();
            double y = axis == Direction.Axis.Y ? 0.5 + 0.5625 * dir.getStepY() : random.nextFloat();
            double z = axis == Direction.Axis.Z ? 0.5 + 0.5625 * dir.getStepZ() : random.nextFloat();
            level.addParticle(SPARK, pos.getX() + x, pos.getY() + y, pos.getZ() + z, 0, 0, 0);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}