package com.hmc.zenkai.content.block;

import com.hmc.zenkai.feature.fluidreact.FluidReactions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.Optional;
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

    /** Cada cuántos ticks se cuece un objeto tirado en la poza (5 s). */
    private static final int COOK_INTERVAL = 100;

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide() && entity instanceof LivingEntity living
                && living.tickCount % BURN_INTERVAL == 0) {
            living.hurt(level.damageSources().hotFloor(), BURN_DAMAGE);
        }
        if (!level.isClientSide() && entity instanceof ItemEntity item && item.tickCount > 0
                && item.tickCount % COOK_INTERVAL == 0) {
            cook(level, item);
        }
        super.entityInside(state, level, pos, entity);
    }

    /**
     * Un objeto tirado en la poza se cuece, UNO cada 5 s, solo con recetas de hoguera (comida): es el
     * mismo resultado que cocinarlo en una hoguera, así que no regala nada nuevo, solo comodidad.
     */
    private static void cook(Level level, ItemEntity item) {
        ItemStack stack = item.getItem();
        SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
        Optional<RecipeHolder<CampfireCookingRecipe>> recipe =
                level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, level);
        if (recipe.isEmpty()) return;
        ItemStack out = recipe.get().value().assemble(input, level.registryAccess());
        if (out.isEmpty()) return;
        stack.shrink(1);
        if (stack.isEmpty()) item.discard(); else item.setItem(stack);
        ItemEntity cooked = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), out);
        cooked.setDeltaMovement(0.0, 0.1, 0.0);
        level.addFreshEntity(cooked);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, item.getX(), item.getY() + 0.3, item.getZ(), 4, 0.15, 0.1, 0.15, 0.01);
        }
    }

    // ── Transformación de bloques sólidos vecinos (feature.fluidreact.FluidReactions) ──
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        FluidReactions.wake(level, pos, this);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        FluidReactions.wake(level, pos, this);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) { return true; }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FluidReactions.wake(level, pos, this);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FluidReactions.tick(level, pos, this, FluidReactions.Family.BOILING);
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
