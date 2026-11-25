package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Diagnóstico del bug «el jugador no sale de un foso de 1×1 con agua curativa» (2026-11-25): mete al mismo jugador de
 * prueba en el mismo foso con agua vanilla y con agua curativa, mantiene el salto y avanza hacia el borde, y compara
 * hasta dónde sube cada uno. Reproduce la parte del servidor de {@code LivingEntity.aiStep} (salto en fluido) y llama a
 * {@code travel} a mano, porque el servidor no lo ejecuta para un jugador (lo hace el cliente).
 *
 * <p>Resultado (2026-11-25): con suelo de piedra el agua vanilla y la curativa suben IGUAL (máximo 4,25 con el borde
 * en 3). El atasco real tenía otra causa: la tierra del fondo se convertía en barro, el jugador quedaba hundido y el
 * bloque de sus pies (sin fluido) lo dejaba fuera de la física de natación de NeoForge; ver
 * {@code .claude/docs/interacciones-de-fluidos.md}, «Trampa». Este test NO prueba suelos bajos (el barro de verdad
 * hunde al jugador; aquí empieza sobre el bloque): se queda como guarda de las propiedades del fluido.</p>
 *
 * <p>El foso mide 1×1 de ancho y 1 de fondo: el fluido ocupa el bloque (3,2,3), rodeado por un anillo de piedra a la
 * misma altura; el borde queda en y = 3.</p>
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidPhysicsGameTests {
    private FluidPhysicsGameTests() {}

    private static final int TICKS = 80;
    private static final double RIM_Y = 3.0;

    private record Result(double maxY, double finalY, boolean touchedFluidAtEnd) {}

    /** Construye el foso con el fluido y el suelo dados y devuelve lo que hace el jugador manteniendo salto + adelante. */
    private static Result simulate(GameTestHelper helper, ServerPlayer sp, BlockState fluid, Block floor, boolean fullTick) {
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE.defaultBlockState());
            for (int y = 2; y <= 6; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        helper.setBlock(new BlockPos(3, 1, 3), floor.defaultBlockState());
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
            if (x == 3 && z == 3) continue;
            helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE.defaultBlockState());
        }
        helper.setBlock(new BlockPos(3, 2, 3), fluid);

        Vec3 start = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        sp.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);   // mira hacia +z
        sp.setDeltaMovement(Vec3.ZERO);
        sp.resetFallDistance();
        sp.setNoGravity(false);

        double maxY = sp.getY();
        for (int i = 0; i < TICKS; i++) {
            sp.setJumping(true);
            sp.zza = 1.0F;
            if (fullTick) {
                sp.tick();   // con todos los sistemas del mod (PlayerTickEvent)
            } else {
                sp.baseTick();
                sp.aiStep();
            }
            sp.travel(new Vec3(0.0, 0.0, 1.0));
            maxY = Math.max(maxY, sp.getY());
        }
        sp.setJumping(false);
        double rel = sp.getY() - helper.absoluteVec(new Vec3(0, 0, 0)).y;
        return new Result(maxY - helper.absoluteVec(new Vec3(0, 0, 0)).y, rel, sp.isInWater() || sp.isInFluidType());
    }

    private static void compare(GameTestHelper helper, Block floor, boolean fullTick) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        Result water = simulate(helper, sp, Blocks.WATER.defaultBlockState(), floor, fullTick);
        Result healing = simulate(helper, sp, ModBlocks.HEALING_WATER_BLOCK.get().defaultBlockState(), floor, fullTick);
        String report = String.format("suelo=%s fullTick=%s | agua: max=%.2f final=%.2f enFluido=%s | curativa: max=%.2f final=%.2f enFluido=%s",
                floor.getDescriptionId(), fullTick, water.maxY(), water.finalY(), water.touchedFluidAtEnd(),
                healing.maxY(), healing.finalY(), healing.touchedFluidAtEnd());
        System.out.println("[FluidPhysics] " + report);
        // Se compara lo que SUBE cada fluido (el jugador acaba cayendo del tablero de 5x5, así que la altura final no vale).
        helper.assertTrue(Math.abs(water.maxY() - healing.maxY()) < 0.3,
                "El foso se comporta distinto entre agua y agua curativa: " + report);
        helper.assertTrue(healing.maxY() >= RIM_Y + 0.5, "Con agua curativa no sube por encima del borde: " + report);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void pitStoneFloorPhysicsOnly(GameTestHelper helper) {
        compare(helper, Blocks.STONE, false);
    }
}
