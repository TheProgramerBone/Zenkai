package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.CrusherBlockEntity;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Triturador (Fase 5d de la 0.7.0): recetas de mena (2 + extras), cantidad de entrada (3 crudos → 4 polvos), reciclaje
 * que devuelve el material del nivel de la pieza, trabajo por ticks con FE y parada con las salidas llenas.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrusherGameTests {
    private CrusherGameTests() {}

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static CrusherBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.CRUSHER.get());
        return (CrusherBlockEntity) helper.getBlockEntity(POS);
    }

    private static int countOut(CrusherBlockEntity be, Item item) {
        int n = 0;
        for (int i = 0; i < CrusherBlockEntity.OUTPUT_SLOTS; i++) {
            ItemStack s = be.items().getStackInSlot(CrusherBlockEntity.SLOT_OUTPUT_FIRST + i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void oreGivesTwoDustPlusExtras(GameTestHelper helper) {
        CrusherBlockEntity be = place(helper);
        be.items().setStackInSlot(CrusherBlockEntity.SLOT_INPUT, new ItemStack(ModBlocks.KATCHIN_ORE.get(), 1));
        helper.assertTrue(be.crushNowForTests(), "La mena de katchin tiene receta");
        int dust = countOut(be, ModItems.KATCHIN_DUST.get());
        helper.assertTrue(dust == 2 || dust == 3, "Mena → 2 polvos + 25 % uno más; salieron " + dust);
        helper.assertTrue(be.items().getStackInSlot(CrusherBlockEntity.SLOT_INPUT).isEmpty(), "La mena se gasta");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void rawNeedsThreeAndGivesFour(GameTestHelper helper) {
        CrusherBlockEntity be = place(helper);
        be.items().setStackInSlot(CrusherBlockEntity.SLOT_INPUT, new ItemStack(ModItems.RAW_KATCHIN.get(), 2));
        helper.assertTrue(!be.crushNowForTests(), "Con 2 crudos no hay receta (pide 3, como Mekanism)");
        be.items().setStackInSlot(CrusherBlockEntity.SLOT_INPUT, new ItemStack(ModItems.RAW_KATCHIN.get(), 4));
        helper.assertTrue(be.crushNowForTests(), "Con 4 crudos sí");
        helper.assertTrue(countOut(be, ModItems.KATCHIN_DUST.get()) == 4, "3 crudos → 4 polvos");
        helper.assertTrue(be.items().getStackInSlot(CrusherBlockEntity.SLOT_INPUT).getCount() == 1, "Gasta 3, queda 1");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void infusedArmorReturnsItsLevel(GameTestHelper helper) {
        CrusherBlockEntity be = place(helper);
        ItemStack chest = KiInfusion.applyToArmor(new ItemStack(ModItems.SAIYAN_ARMOR_CHESTPLATE.get()), KiEnergyType.DIVINE);
        be.items().setStackInSlot(CrusherBlockEntity.SLOT_INPUT, chest);
        helper.assertTrue(be.crushNowForTests(), "El peto Saiyan se recicla");
        int divine = countOut(be, KiInfusion.ingot(KiEnergyType.DIVINE));
        helper.assertTrue(divine >= 3 && divine <= 4, "Peto (8) Divino → 3 lingotes Divinos + 20 % uno más; salieron " + divine);
        helper.assertTrue(countOut(be, ModItems.KATCHIN_INGOT.get()) == 0, "Nada de katchin normal");
        helper.succeed();
    }

    /** Por el tick real: con FE trabaja durante el tiempo de la receta; sin FE se para; con las salidas llenas, también. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tickUsesEnergyAndStopsWhenFull(GameTestHelper helper) {
        CrusherBlockEntity be = place(helper);
        BlockPos abs = helper.absolutePos(POS);
        Runnable tick = () -> CrusherBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
        be.items().setStackInSlot(CrusherBlockEntity.SLOT_INPUT, new ItemStack(ModItems.KATCHIN_INGOT.get(), 2));
        for (int i = 0; i < 120; i++) tick.run();
        helper.assertTrue(countOut(be, ModItems.KATCHIN_DUST.get()) == 0, "Sin FE no tritura");

        be.fillEnergyForTests(100_000);
        for (int i = 0; i < 100; i++) tick.run();   // el 101 ya empezaría el segundo lingote
        helper.assertTrue(countOut(be, ModItems.KATCHIN_DUST.get()) == 1, "Lingote → 1 polvo en 100 ticks");
        helper.assertTrue(be.energyStored() == 100_000 - 1_500, "Cobra los 1 500 FE de la receta; quedan " + be.energyStored());

        for (int i = 0; i < CrusherBlockEntity.OUTPUT_SLOTS; i++) {
            be.items().setStackInSlot(CrusherBlockEntity.SLOT_OUTPUT_FIRST + i, new ItemStack(Items.STONE, 64));
        }
        for (int i = 0; i < 120; i++) tick.run();
        helper.assertTrue(be.items().getStackInSlot(CrusherBlockEntity.SLOT_INPUT).getCount() == 1,
                "Con las salidas llenas no gasta la entrada");
        helper.succeed();
    }
}
