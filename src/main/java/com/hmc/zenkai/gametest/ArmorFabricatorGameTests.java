package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity;
import com.hmc.zenkai.content.item.BloodItem;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.feature.armor.ImbuedAttributes;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModDataMaps;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fase 5b de la 0.7.0: la máquina de fabricación fabrica una pieza con patrón + material + FE (el patrón no se gasta),
 * la pestaña de atributos imbuye con FE + fluido de Ki + sangre según la curva de ImbueCost, y el data map de sangre
 * carga del datapack.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmorFabricatorGameTests {
    private ArmorFabricatorGameTests() {}

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static ArmorFabricatorBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.ARMOR_FABRICATOR.get());
        return (ArmorFabricatorBlockEntity) helper.getBlockEntity(POS);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void fabricatesPieceAndKeepsPattern(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = place(helper);
        be.fillEnergyForTests(100_000);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_PATTERN, ArmorSets.pattern(ArmorSets.SAIYAN_ARMOR));
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST, new ItemStack(ModItems.KATCHIN_INGOT.get(), 5));
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST + 4, new ItemStack(ModItems.KATCHIN_INGOT.get(), 5));
        be.selectPiece(1);   // peto: 8 lingotes, 40 000 FE, 200 ticks

        BlockPos abs = helper.absolutePos(POS);
        for (int i = 0; i < 250; i++) {
            ArmorFabricatorBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
        }
        ItemStack out = be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(out.is(ModItems.SAIYAN_ARMOR_CHESTPLATE.get()), "Debería salir el peto Saiyan, sale " + out);
        int left = be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST).getCount()
                + be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST + 4).getCount();
        helper.assertTrue(left == 2, "De 10 lingotes deberían quedar 2, quedan " + left);
        helper.assertTrue(be.energyStored() == 60_000, "Deberían quedar 60 000 FE, quedan " + be.energyStored());
        helper.assertTrue(!be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_PATTERN).isEmpty(),
                "El patrón no debe gastarse");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void imbueSpendsBloodEnergyAndFluid(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = place(helper);
        be.fillEnergyForTests(100_000);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_ARMOR, new ItemStack(Items.IRON_CHESTPLATE));
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_BLOOD, BloodItem.of(ModItems.STRENGTH_BLOOD.get(), 2, 1));

        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.NO_FLUID, "Sin fluido de Ki no debe imbuir");
        be.tank().fill(new FluidStack(KiEnergyType.BASE.fluid().source().get(), 1000), IFluidHandler.FluidAction.EXECUTE);

        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.OK, "Con FE, fluido y sangre debe imbuir");
        ItemStack piece = be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_ARMOR);
        int str = piece.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE).get(ZenkaiAttributes.STRENGTH);
        helper.assertTrue(str == 2, "Sangre Saiyan concentrada = +2 STR, da " + str);
        helper.assertTrue(be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_BLOOD).isEmpty(), "La sangre se gasta");
        helper.assertTrue(be.energyStored() == 98_000, "2 puntos bajo el umbral = 2 000 FE; quedan " + be.energyStored());
        helper.assertTrue(be.tank().getFluidAmount() == 800, "2 puntos = 200 mB; quedan " + be.tank().getFluidAmount());
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.NO_INPUT, "Sin sangre no debe imbuir");
        helper.succeed();
    }

    /**
     * Tipo de Ki (2026-11-22): el peto de diamante pide Ki Súper (automático); con Base no vale, con Súper cuesta lo de
     * siempre y con Divino (un tipo más) la mitad.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void imbueRequiresKiTypeAndHigherCostsHalf(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = place(helper);
        be.fillEnergyForTests(100_000);
        ItemStack diamond = new ItemStack(Items.DIAMOND_CHESTPLATE);
        helper.assertTrue(ArmorFabricatorBlockEntity.requiredKiType(helper.getLevel(), diamond) == KiEnergyType.SUPER,
                "El peto de diamante debería pedir Ki Súper (tipo automático: armadura + dureza)");
        helper.assertTrue(ArmorFabricatorBlockEntity.requiredKiType(helper.getLevel(), new ItemStack(Items.IRON_CHESTPLATE))
                == KiEnergyType.BASE, "Una pieza sin receta pide Ki Base");

        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_ARMOR, diamond);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_BLOOD, BloodItem.of(ModItems.STRENGTH_BLOOD.get(), 2, 2));
        be.tank().fill(new FluidStack(KiEnergyType.BASE.fluid().source().get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.NO_FLUID, "Ki Base no vale si pide Súper");

        be.tank().setFluid(new FluidStack(KiEnergyType.DIVINE.fluid().source().get(), 1000));
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.OK, "Ki Divino (superior) debe valer");
        helper.assertTrue(be.tank().getFluidAmount() == 900, "2 puntos = 200 mB de Súper = 100 mB de Divino; quedan "
                + be.tank().getFluidAmount());
        helper.succeed();
    }

    /** Carga de imbuir (2026-11-22): no imbuye al pulsar, sino tras IMBUE_TICKS; quitar la sangre a mitad cancela sin gastar. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void imbueChargesBeforeApplying(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = place(helper);
        be.fillEnergyForTests(100_000);
        be.tank().fill(new FluidStack(KiEnergyType.BASE.fluid().source().get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_ARMOR, new ItemStack(Items.IRON_CHESTPLATE));
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_BLOOD, BloodItem.of(ModItems.STRENGTH_BLOOD.get(), 1, 2));
        BlockPos abs = helper.absolutePos(POS);
        Runnable tick = () -> ArmorFabricatorBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);

        helper.assertTrue(be.startImbue() == ArmorFabricatorBlockEntity.ImbueResult.OK, "Debe arrancar la carga");
        for (int i = 0; i < ArmorFabricatorBlockEntity.IMBUE_TICKS - 2; i++) tick.run();
        ItemStack piece = be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_ARMOR);
        helper.assertTrue(piece.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE).get(ZenkaiAttributes.STRENGTH) == 0,
                "Antes de acabar la carga no debe imbuir");
        for (int i = 0; i < 3; i++) tick.run();
        piece = be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_ARMOR);
        helper.assertTrue(piece.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE).get(ZenkaiAttributes.STRENGTH) == 1,
                "Al acabar la carga debe dar +1 STR");

        be.startImbue();
        tick.run();
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_BLOOD, ItemStack.EMPTY);
        tick.run();
        helper.assertTrue(!be.imbuing(), "Quitar la sangre a mitad cancela la carga");
        helper.assertTrue(be.tank().getFluidAmount() == 900, "La carga cancelada no gasta fluido; quedan " + be.tank().getFluidAmount());
        helper.succeed();
    }

    /** Tipo automático (2026-11-22): armadura + dureza frente a la vanilla del mismo tipo; las recetas mandan, también para bajar. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void autoKiTierByArmorAndToughness(GameTestHelper helper) {
        var level = helper.getLevel();
        record Case(net.minecraft.world.item.Item item, KiEnergyType want) {}
        for (Case c : new Case[]{
                new Case(Items.LEATHER_HELMET, KiEnergyType.BASE), new Case(Items.IRON_CHESTPLATE, KiEnergyType.BASE),
                new Case(Items.GOLDEN_LEGGINGS, KiEnergyType.BASE), new Case(Items.DIAMOND_BOOTS, KiEnergyType.SUPER),
                new Case(Items.DIAMOND_CHESTPLATE, KiEnergyType.SUPER), new Case(Items.NETHERITE_HELMET, KiEnergyType.DIVINE),
                new Case(ModItems.SAIYAN_ARMOR_CHESTPLATE.get(), KiEnergyType.SUPER),
                new Case(ModItems.NAMEK_CLOTHES_CHESTPLATE.get(), KiEnergyType.BASE),   // receta: la tela baja a Base
                new Case(ModItems.TRAINING_GI_HELMET.get(), KiEnergyType.BASE)}) {
            KiEnergyType got = ArmorFabricatorBlockEntity.requiredKiType(level, new ItemStack(c.item()));
            helper.assertTrue(got == c.want(), c.item() + " debería pedir " + c.want() + ", pide " + got);
        }
        helper.succeed();
    }

    /** Ids viejos de las sangres por raza: cargan como la sangre del mismo atributo (alias del registro). */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void legacyBloodIdsAreAliased(GameTestHelper helper) {
        var reg = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        helper.assertTrue(reg.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "saiyan_blood"))
                == ModItems.STRENGTH_BLOOD.get(), "saiyan_blood debería cargar como strength_blood");
        helper.assertTrue(reg.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "majin_blood"))
                == ModItems.CONSTITUTION_BLOOD.get(), "majin_blood debería cargar como constitution_blood");
        helper.succeed();
    }

    /** Interruptor de la fabricación (2026-11-22): en pausa no gasta material ni FE. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void pausedFabricatorSpendsNothing(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = place(helper);
        be.fillEnergyForTests(100_000);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_PATTERN, ArmorSets.pattern(ArmorSets.SAIYAN_ARMOR));
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST, new ItemStack(ModItems.KATCHIN_INGOT.get(), 10));
        be.selectPiece(1);
        be.toggleCraft();
        BlockPos abs = helper.absolutePos(POS);
        for (int i = 0; i < 250; i++) {
            ArmorFabricatorBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
        }
        helper.assertTrue(be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_OUTPUT).isEmpty(), "En pausa no fabrica");
        helper.assertTrue(be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST).getCount() == 10,
                "En pausa no gasta material");
        helper.assertTrue(be.energyStored() == 100_000, "En pausa no gasta FE; quedan " + be.energyStored());
        helper.succeed();
    }

    /** Regresión 2026-11-22: un ciclo de inicialización dejaba las 12 piezas con material nulo y el creativo crasheaba. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void dragonBallArmorHasMaterial(GameTestHelper helper) {
        for (var holder : ModItems.ITEMS.getEntries()) {
            if (holder.get() instanceof net.minecraft.world.item.ArmorItem armor) {
                helper.assertTrue(armor.getMaterial() != null, "Material nulo en " + holder.getId());
            }
        }
        helper.assertTrue(ModItems.SAIYAN_ARMOR_CHESTPLATE.get().getDefense() == 8, "El peto Saiyan debería dar 8 de armadura");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void bloodSourcesLoadFromDatapack(GameTestHelper helper) {
        // 2026-11-22: varias sangres por mob; el zombi suelta Constitución y Fuerza.
        var zombie = EntityType.ZOMBIE.builtInRegistryHolder().getData(ModDataMaps.BLOOD_SOURCES);
        helper.assertTrue(zombie != null && zombie.size() == 2
                        && zombie.stream().anyMatch(s -> s.blood() == ModItems.CONSTITUTION_BLOOD.get() && s.quality() == 1)
                        && zombie.stream().anyMatch(s -> s.blood() == ModItems.STRENGTH_BLOOD.get()),
                "El zombi debería soltar Sangre de Constitución y de Fuerza +1, suelta " + zombie);
        var ogre = com.hmc.zenkai.registry.ModEntities.RED_OGRE.get().builtInRegistryHolder()
                .getData(ModDataMaps.BLOOD_SOURCES);
        helper.assertTrue(ogre != null && ogre.stream().allMatch(s -> s.quality() == 2), "El ogro debería soltar sangre +2");
        helper.succeed();
    }
}
