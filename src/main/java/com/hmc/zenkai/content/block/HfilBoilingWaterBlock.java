package com.hmc.zenkai.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Agua hirviendo de las pozas "Umi Jigoku" del HFIL (ver ModFluids.HFIL_BOILING_WATER).
 *
 * QUEMA con {@code damageSources().hotFloor()}, el mismo daño que el bloque de magma. Elegido a
 * propósito: hot_floor está en la etiqueta IS_FIRE, así que CombatZenkaiHooks lo trata como
 * peligro AMBIENTAL y lo traduce al mismo % de vida vanilla aplicado sobre el bodyMax — quema
 * igual con PL 300 que con PL 3.000.000, en vez de quedar aplastado por la DEF. También respeta
 * Resistencia al fuego sin código extra (IS_FIRE).
 */
public class HfilBoilingWaterBlock extends LiquidBlock {

    /** Daño vanilla por golpe (1 = medio corazón, como el magma). */
    private static final float BURN_DAMAGE = 1.0F;
    /** Cada cuántos ticks quema. entityInside corre CADA tick que la entidad solapa el fluido. */
    private static final int BURN_INTERVAL = 10;

    public HfilBoilingWaterBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide() && entity instanceof LivingEntity living
                && living.tickCount % BURN_INTERVAL == 0) {
            living.hurt(level.damageSources().hotFloor(), BURN_DAMAGE);
        }
        super.entityInside(state, level, pos, entity);
    }

    /** Vapor y burbujas que revientan en la superficie: se lee "hirviendo" antes de tocarla. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (!level.getBlockState(pos.above()).isAir()) return; // solo la lámina de arriba
        double x = pos.getX() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        double y = pos.getY() + 0.9;
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.04, 0.0);
        }
        if (random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.CLOUD, x, y + 0.1, z, 0.0, 0.03, 0.0);
        }
    }
}
