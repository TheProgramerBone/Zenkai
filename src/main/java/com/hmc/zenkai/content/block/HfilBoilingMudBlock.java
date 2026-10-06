package com.hmc.zenkai.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.MudBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Barro gris hirviendo de las pozas "Oniishibozu Jigoku" del HFIL (Beppu: las burbujas grises que
 * revientan en el barro recuerdan cabezas rapadas de monje, de ahí el nombre real).
 *
 * Hereda de MudBlock para conservar su hundimiento (forma de colisión 2 px más baja) — el barro
 * se "nota" al pisarlo. Quema al pisarlo como el magma (mismo hot_floor, ver
 * HfilBoilingWaterBlock para por qué ese daño escala bien con cualquier PL) salvo agachado,
 * exactamente la regla vanilla de MagmaBlock.stepOn: quien lo conoce, cruza con cuidado.
 */
public class HfilBoilingMudBlock extends MudBlock {

    public HfilBoilingMudBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully() && entity instanceof LivingEntity) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
        }
        super.stepOn(level, pos, state, entity);
    }

    /** Burbujas que revientan con su "plop": la seña del barro hirviendo. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir() || random.nextInt(5) != 0) return;
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        double y = pos.getY() + 1.0;
        level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.02, 0.0);
        if (random.nextInt(6) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS,
                    0.4F, 0.6F + random.nextFloat() * 0.3F, false);
        }
    }
}
