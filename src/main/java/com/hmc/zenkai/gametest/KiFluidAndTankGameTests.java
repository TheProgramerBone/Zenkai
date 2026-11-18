package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.FluidTransportBlockEntity;
import com.hmc.zenkai.feature.player.PlayerResourcePools;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tanda de tecnología 0.7.0: la sobrecarga del ki Legendario (PlayerResourcePools) y las ranuras del
 * tanque de fluidos. El segundo cubre la regresión que vieron las capturas del 2026-11-17: la salida
 * rechazaba el cubo lleno porque el handler filtra las salidas para el jugador.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KiFluidAndTankGameTests {
    private KiFluidAndTankGameTests() {}

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void legendaryOverchargeFillsAboveAndFades(GameTestHelper helper) {
        PlayerResourcePools pools = new PlayerResourcePools();
        pools.setEnergyMax(100);
        pools.addKi(100);
        pools.addKi(50);
        helper.assertTrue(pools.getKiCurrent() == 100, "Sin sobrecarga el ki no pasa del pool");
        pools.holdOvercharge(25, 30);
        pools.addKi(50);
        helper.assertTrue(pools.getKiCurrent() == 125, "Con sobrecarga llega al 125 %; hay " + pools.getKiCurrent());
        pools.tickOvercharge();
        helper.assertTrue(pools.getKiCurrent() > 100, "Al salir no se pierde de golpe");
        for (int i = 0; i < 40; i++) pools.tickOvercharge();
        helper.assertTrue(pools.getOverchargeRoom() == 0 && pools.getKiCurrent() == 100,
                "Pasado el desvanecimiento vuelve al pool; hay " + pools.getKiCurrent());
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void fluidTankSlotFillsBucket(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FLUID_TANK.get().defaultBlockState());
        FluidTransportBlockEntity be = helper.getBlockEntity(pos);
        int filled = be.fluidAt(null).fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(filled > 0, "El tanque acepta agua; entró " + filled);
        be.items().setStackInSlot(FluidTransportBlockEntity.SLOT_FILL_IN, new ItemStack(Items.BUCKET, 2));
        helper.runAfterDelay(10, () -> {
            ItemStack out = be.items().getStackInSlot(FluidTransportBlockEntity.SLOT_FILL_OUT);
            helper.assertTrue(out.is(Items.WATER_BUCKET), "Sale un cubo de agua; sale " + out);
            helper.assertTrue(be.items().getStackInSlot(FluidTransportBlockEntity.SLOT_FILL_IN).getCount() == 1,
                    "Se consume un cubo vacío y el otro espera (el cubo lleno no se apila)");
            helper.succeed();
        });
    }
}
