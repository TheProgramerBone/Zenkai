package com.hmc.zenkai.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Azufre de los campos de Osorezan (propuesta E de .claude/docs/worldgen.md): el
 * monte volcánico de Japón considerado la puerta del inframundo, con costras de azufre amarillo y
 * olor a huevo podrido. Dos usos de la misma clase:
 * <ul>
 *   <li>Costra ({@code fumarole = false}): pisarla levanta gas — náusea corta.</li>
 *   <li>Fumarola ({@code fumarole = true}): echa humo sin parar y quien se para encima respira el
 *   gas concentrado — náusea y un veneno leve.</li>
 * </ul>
 * Efectos y no daño directo: el peligro es la desorientación, como en un campo de gases real.
 */
public class HfilSulfurBlock extends Block {

    /** Cada cuántos ticks se reaplica el efecto mientras se está encima. */
    private static final int GAS_INTERVAL = 20;

    private final boolean fumarole;

    public HfilSulfurBlock(Properties properties, boolean fumarole) {
        super(properties);
        this.fumarole = fumarole;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide() && entity instanceof LivingEntity living && living.tickCount % GAS_INTERVAL == 0) {
            living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, fumarole ? 160 : 100, 0, false, true));
            if (fumarole) living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, true));
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) return;
        if (fumarole) {
            // Columna de humo alta y continua: se ve de lejos, como las de Osorezan.
            if (random.nextInt(2) == 0) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                        pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.0,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.05, 0.0);
            }
        } else if (random.nextInt(25) == 0) {
            // Costra: alguna voluta baja de vez en cuando, el gas que se escapa del suelo.
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.0,
                    pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
        }
    }
}
