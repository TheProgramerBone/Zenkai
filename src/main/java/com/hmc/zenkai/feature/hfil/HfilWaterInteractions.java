package com.hmc.zenkai.feature.hfil;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry.InteractionInformation;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Qué pasa cuando el agua curativa de Namek o el agua hirviendo del HFIL tocan otros fluidos (0.7.0).
 * La tabla completa y su justificación: {@code .claude/docs/interacciones-de-fluidos.md}.
 *
 * Misma regla que {@code KiFluidInteractions}: el resultado NUNCA es un bloque exclusivo de otro
 * bioma o dimensión, ni un bloque propio del HFIL o de Namek (los dos fluidos tienen cubo: si dieran
 * piedras del HFIL, cualquiera las fabricaría desde casa).
 *
 *   hirviendo + agua vanilla   → el agua se EVAPORA (la poza sigue igual)
 *   hirviendo + lava           → la LAVA se solidifica: fuente o descendente → piedra lisa, fluyente → toba
 *   hirviendo + curativa       → barro (el fluido que se actualiza)
 *   hirviendo + ki             → el ki se evapora (ver KiFluidInteractions)
 *   curativa  + lava           → la LAVA se solidifica: fuente → tierra, fluyente → adoquín musgoso
 *   curativa  + ki             → arcilla (ver KiFluidInteractions)
 *   curativa  + agua vanilla   → sin reacción
 *
 * FluidInteractionRegistry transforma el bloque del fluido QUE SE ACTUALIZA; para consumir el OTRO
 * (la lava) se usa la variante con interacción propia, que escribe sobre la posición vecina.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class HfilWaterInteractions {
    private HfilWaterInteractions() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(HfilWaterInteractions::register);
    }

    private static void register() {
        FluidType boiling = ModFluids.HFIL_BOILING_WATER_TYPE.get();
        FluidType healing = ModFluids.HEALING_WATER_TYPE.get();
        FluidType water = NeoForgeMod.WATER_TYPE.value();
        FluidType lava = NeoForgeMod.LAVA_TYPE.value();

        // Hirviendo + agua: se quita el agua del vecino, la poza no cambia.
        FluidInteractionRegistry.addInteraction(boiling, new InteractionInformation(
                (level, cur, rel, state) -> level.getFluidState(rel).getFluidType() == water,
                (level, cur, rel, state) -> evaporate(level, rel)));
        // Hirviendo + lava: la lava se solidifica (como con el agua vanilla, es la lava la que cambia).
        FluidInteractionRegistry.addInteraction(boiling, new InteractionInformation(
                (level, cur, rel, state) -> level.getFluidState(rel).getFluidType() == lava,
                (level, cur, rel, state) -> solidify(level, rel, boilingLavaResult(level.getFluidState(rel)))));
        FluidInteractionRegistry.addInteraction(boiling,
                new InteractionInformation(healing, Blocks.MUD.defaultBlockState()));

        // Curativa + lava: la vida cubre la roca.
        FluidInteractionRegistry.addInteraction(healing, new InteractionInformation(
                (level, cur, rel, state) -> level.getFluidState(rel).getFluidType() == lava,
                (level, cur, rel, state) -> solidify(level, rel, healingLavaResult(level.getFluidState(rel)))));
        FluidInteractionRegistry.addInteraction(healing,
                new InteractionInformation(boiling, Blocks.MUD.defaultBlockState()));
    }

    private static boolean isFalling(FluidState fs) {
        return fs.hasProperty(FlowingFluid.FALLING) && fs.getValue(FlowingFluid.FALLING);
    }

    /** Fuente o descendente → piedra lisa; fluyente → toba. */
    private static BlockState boilingLavaResult(FluidState lava) {
        return (lava.isSource() || isFalling(lava) ? Blocks.SMOOTH_STONE : Blocks.TUFF).defaultBlockState();
    }

    /** Fuente → tierra («meteorización acelerada»); fluyente o descendente → adoquín musgoso. */
    private static BlockState healingLavaResult(FluidState lava) {
        return (lava.isSource() ? Blocks.DIRT : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
    }

    /** Sustituye la lava de {@code pos} por roca, con siseo y humo. Solo un LiquidBlock (no una losa con agua). */
    private static void solidify(Level level, BlockPos pos, BlockState result) {
        if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) return;
        level.setBlock(pos, result, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                2.2F + level.random.nextFloat() * 0.4F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    6, 0.3, 0.2, 0.3, 0.02);
        }
    }

    /** Quita el fluido de {@code pos} con siseo y una nube de vapor. */
    public static void evaporate(Level level, BlockPos pos) {
        // Un bloque con el fluido dentro (losa, cartel...) no es un LiquidBlock: se deja en paz.
        if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                2.2F + level.random.nextFloat() * 0.4F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    6, 0.3, 0.2, 0.3, 0.02);
        }
    }
}
