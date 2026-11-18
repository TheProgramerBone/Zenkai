package com.hmc.zenkai.gametest;

import com.hmc.zenkai.compat.create.KiMotorBlock;
import com.hmc.zenkai.compat.create.KiMotorBlockEntity;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.feature.transport.SideMode;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Cuerpos de los tests del Motor de Ki. Viven APARTE de {@link KiMotorGameTests} porque nombran
 * KiMotorBlockEntity (hija de una clase de Create): NeoForge recorre con getDeclaredMethods las
 * clases con @GameTestHolder al arrancar, y una firma con ese tipo (el helper motor()) obligaba a
 * cargar Create aunque no estuviera instalado: NoClassDefFoundError en la pantalla de carga. Esta
 * clase no lleva anotaciones, nadie la reflexiona y solo se carga al ejecutar un test con Create.
 */
final class KiMotorTestCases {
    private KiMotorTestCases() {}

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static KiMotorBlockEntity motor(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.KI_MOTOR.get().defaultBlockState().setValue(KiMotorBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(POS);
    }

    static void motorConvertsFluidAndStarts(GameTestHelper helper) {
        KiMotorBlockEntity be = motor(helper);
        helper.assertTrue(!be.active() && be.getGeneratedSpeed() == 0f, "Sin energía no gira");
        int mb = 500;
        int filled = be.fluidAt(Direction.EAST).fill(new FluidStack(KiEnergyType.BASE.fluid().source().get(), mb),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(filled == mb, "Por una cara Normal entra el fluido de Ki; entró " + filled);
        be.tick();
        int expected = mb * KiEnergyType.BASE.fePerMb();
        helper.assertTrue(be.tank().getFluidAmount() == 0, "El fluido se convierte entero; quedan " + be.tank().getFluidAmount());
        helper.assertTrue(be.fe() >= expected - ServerConfig.kiMotorRatedSu(), "El FE sale de la tabla de la cámara; hay " + be.fe());
        helper.assertTrue(be.active(), "Con FE para pagar un tick a plena carga arranca");
        helper.assertTrue(Math.abs(be.getGeneratedSpeed()) == be.rpm(), "Gira a la velocidad elegida; gira a " + be.getGeneratedSpeed());
        float capacity = be.calculateAddedStressCapacity();
        helper.assertTrue(Math.abs(capacity * be.rpm() - ServerConfig.kiMotorRatedSu()) < 0.5f,
                "Capacidad x RPM = SU nominales; da " + capacity * be.rpm());
        helper.succeed();
    }

    static void motorDoesNotStartWithoutEnergy(GameTestHelper helper) {
        KiMotorBlockEntity be = motor(helper);
        be.tick();
        helper.assertTrue(!be.active() && be.getGeneratedSpeed() == 0f, "Sin FE no arranca");
        be.energyAt(null).receiveEnergy(ServerConfig.kiMotorRatedSu(), false);
        be.tick();
        helper.assertTrue(be.active(), "Con FE arranca");
        // Conmutar el modo lo apaga: pasa a ser consumidor y deja de ofrecer velocidad.
        be.setMode(KiMotorBlockEntity.Mode.GENERATOR);
        helper.assertTrue(!be.active() && be.getGeneratedSpeed() == 0f, "En modo generador no ofrece rotación");
        helper.succeed();
    }

    static void portsFollowModeAndFace(GameTestHelper helper) {
        KiMotorBlockEntity be = motor(helper);
        helper.assertTrue(be.energyAt(Direction.NORTH) == null && be.fluidAt(Direction.NORTH) == null,
                "La cara del eje no tiene puertos");
        IEnergyStorage side = be.energyAt(Direction.EAST);
        helper.assertTrue(side.receiveEnergy(100, false) == 100, "Modo motor: la energía entra");
        helper.assertTrue(side.extractEnergy(100, true) == 0, "Modo motor: la energía no sale");

        be.cyclePort(Direction.EAST, false);   // Normal -> Empujar
        helper.assertTrue(be.energyMode(Direction.EAST) == SideMode.PUSH, "Cicla a Empujar");
        helper.assertTrue(be.energyAt(Direction.EAST).receiveEnergy(100, true) == 0, "Empujar no recibe");
        be.cyclePort(Direction.EAST, false);   // Empujar -> Tirar
        be.cyclePort(Direction.EAST, false);   // Tirar -> Nada
        helper.assertTrue(be.energyAt(Direction.EAST) == null, "Nada desconecta la cara");

        be.setMode(KiMotorBlockEntity.Mode.GENERATOR);
        IEnergyStorage west = be.energyAt(Direction.WEST);
        helper.assertTrue(west.receiveEnergy(100, true) == 0, "Modo generador: no recibe");
        helper.assertTrue(west.extractEnergy(50, true) == 50, "Modo generador: la energía sale");
        helper.assertTrue(be.fluidAt(Direction.WEST).fill(new FluidStack(KiEnergyType.BASE.fluid().source().get(), 100),
                IFluidHandler.FluidAction.SIMULATE) == 0, "Modo generador: el fluido no entra");
        helper.succeed();
    }

    static void tankRejectsMixedKiAndOtherFluids(GameTestHelper helper) {
        KiMotorBlockEntity be = motor(helper);
        IFluidHandler h = be.fluidAt(Direction.UP);
        helper.assertTrue(h.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 100),
                IFluidHandler.FluidAction.SIMULATE) == 0, "El agua no entra");
        // Se llena con Super (sin tick, para que no se convierta) y el Base ya no cabe encima.
        h.fill(new FluidStack(KiEnergyType.SUPER.fluid().source().get(), 100), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(h.fill(new FluidStack(KiEnergyType.BASE.fluid().source().get(), 100),
                IFluidHandler.FluidAction.SIMULATE) == 0, "No se mezclan tipos de ki en el tanque");
        helper.assertTrue(h.drain(10, IFluidHandler.FluidAction.SIMULATE).isEmpty(), "Nadie saca el fluido: solo se convierte");
        helper.succeed();
    }

    static void settingsSurviveSaveAndLoad(GameTestHelper helper) {
        KiMotorBlockEntity be = motor(helper);
        be.setMode(KiMotorBlockEntity.Mode.GENERATOR);
        be.setSpeedStep(3);
        be.toggleReversed();
        be.cyclePort(Direction.SOUTH, true);
        be.energyAt(null).receiveEnergy(777, false);
        CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());

        helper.setBlock(POS.east(2), ModBlocks.KI_MOTOR.get().defaultBlockState().setValue(KiMotorBlock.FACING, Direction.NORTH));
        KiMotorBlockEntity copy = helper.getBlockEntity(POS.east(2));
        copy.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(copy.mode() == KiMotorBlockEntity.Mode.GENERATOR, "Conserva el modo");
        helper.assertTrue(copy.speedStep() == 3 && copy.rpm() == 256, "Conserva la velocidad");
        helper.assertTrue(copy.reversed(), "Conserva el sentido");
        helper.assertTrue(copy.fluidMode(Direction.SOUTH) == SideMode.PUSH, "Conserva los puertos de fluido");
        helper.assertTrue(copy.fe() == 777, "Conserva la energía; tiene " + copy.fe());
        helper.succeed();
    }

    /** Regresión 2026-11-17: el tanque se quedaba en 1000 mB (FluidTank.fill lee el campo, no getCapacity()). */
    static void tankHoldsConfiguredCapacity(GameTestHelper helper) {
        KiMotorBlockEntity be = motor(helper);
        int want = Math.min(3000, ServerConfig.kiMotorTankCapacity());
        // Energía llena antes: así el tick no convierte el fluido y se mide solo el tanque.
        be.energyAt(null).receiveEnergy(Integer.MAX_VALUE, false);
        int filled = be.fluidAt(Direction.EAST).fill(new FluidStack(KiEnergyType.BASE.fluid().source().get(), want),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(filled == want, "Entra lo que dice la config; entraron " + filled + " de " + want);
        helper.assertTrue(be.tank().getCapacity() == ServerConfig.kiMotorTankCapacity(), "La capacidad es la de la config");
        helper.succeed();
    }
}
