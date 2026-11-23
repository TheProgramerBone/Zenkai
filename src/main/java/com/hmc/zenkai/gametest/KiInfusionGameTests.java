package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity;
import com.hmc.zenkai.content.blockentity.KiContainmentChamberBlockEntity;
import com.hmc.zenkai.content.blockentity.KiInfuserBlockEntity;
import com.hmc.zenkai.content.item.BloodItem;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.armor.ArmorSets;
import com.hmc.zenkai.feature.armor.ImbuedAttributes;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Fase 5c de la 0.7.0: infusora (lingote + fluido del tipo → lingote infundido, tolerancia), herencia de la infusión
 * (mesa y máquina de fabricación, el tipo más bajo), durabilidad y descuento al imbuir, plantillas de atributos y su
 * duplicado.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KiInfusionGameTests {
    private KiInfusionGameTests() {}

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static ItemStack ingot(KiEnergyType t, int n) {
        ItemStack s = new ItemStack(ModItems.KATCHIN_INGOT.get(), n);
        return t == null ? s : KiInfusion.infused(s, t);
    }

    private static void fill(IFluidHandler tank, KiEnergyType t, int mb) {
        tank.fill(new FluidStack(t.fluid().source().get(), mb), IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void infuserNeedsToleranceAndInfuses(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.KI_INFUSER.get());
        KiInfuserBlockEntity be = (KiInfuserBlockEntity) helper.getBlockEntity(POS);
        be.fillEnergyForTests(50_000);
        be.items().setStackInSlot(KiInfuserBlockEntity.SLOT_INPUT, ingot(null, 1));   // uno: con dos el segundo arranca y gasta FE
        fill(be.tank(), KiEnergyType.DIVINE, 1000);
        helper.assertTrue(be.state() == KiInfuserBlockEntity.State.NO_TOLERANCE, "Ki Divino sin tolerancia no debe infundir");

        be.items().setStackInSlot(KiContainmentChamberBlockEntity.SLOT_TOL_DIVINE, new ItemStack(ModItems.KI_TOLERANCE_DIVINE.get()));
        BlockPos abs = helper.absolutePos(POS);
        for (int i = 0; i < KiInfusion.TICKS + 5; i++) {
            KiInfuserBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
        }
        ItemStack out = be.items().getStackInSlot(KiInfuserBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(out.is(ModItems.KATCHIN_INGOT.get()) && KiInfusion.of(out) == KiEnergyType.DIVINE,
                "Debería salir un lingote infundido en Divino, sale " + out);
        helper.assertTrue(be.tank().getFluidAmount() == 1000 - KiInfusion.MB_PER_ITEM,
                "Debe gastar " + KiInfusion.MB_PER_ITEM + " mB, quedan " + be.tank().getFluidAmount());
        helper.assertTrue(be.energyStored() == 50_000 - KiInfusion.FE_PER_ITEM, "FE gastada: " + (50_000 - be.energyStored()));
        helper.assertTrue(!KiInfuserBlockEntity.canInfuse(out), "Un lingote infundido no se vuelve a infundir");
        helper.succeed();
    }

    /** Receta de mesa por id (las de katchin están envueltas en zenkai:infused_crafting). */
    private static CraftingRecipe recipe(GameTestHelper helper, String id) {
        RecipeHolder<?> h = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, id)).orElseThrow();
        return (CraftingRecipe) h.value();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void craftingInheritsLowestAndBlockRejects(GameTestHelper helper) {
        var level = helper.getLevel();
        CraftingRecipe thread = recipe(helper, "katchin_thread");
        ItemStack t = thread.assemble(CraftingInput.of(1, 1, List.of(ingot(KiEnergyType.SUPER, 1))), level.registryAccess());
        helper.assertTrue(t.is(ModItems.KATCHIN_THREAD.get()) && t.getCount() == 4 && KiInfusion.of(t) == KiEnergyType.SUPER,
                "Lingote Súper → 4 hilos Súper, sale " + t);

        CraftingRecipe cloth = recipe(helper, "katchin_cloth");
        ItemStack sup = KiInfusion.infused(new ItemStack(ModItems.KATCHIN_THREAD.get()), KiEnergyType.SUPER);
        ItemStack div = KiInfusion.infused(new ItemStack(ModItems.KATCHIN_THREAD.get()), KiEnergyType.DIVINE);
        ItemStack mixed = cloth.assemble(CraftingInput.of(2, 2, List.of(div, div, sup, div)), level.registryAccess());
        helper.assertTrue(KiInfusion.of(mixed) == KiEnergyType.SUPER, "Mezcla Divino+Súper → Súper (el más bajo)");
        ItemStack plain = cloth.assemble(CraftingInput.of(2, 2,
                List.of(div, div, div, new ItemStack(ModItems.KATCHIN_THREAD.get()))), level.registryAccess());
        helper.assertTrue(KiInfusion.of(plain) == null, "Con un hilo sin infundir sale sin infundir");

        CraftingRecipe block = recipe(helper, "katchin_block");
        CraftingInput four = CraftingInput.of(2, 2, Collections.nCopies(4, ingot(KiEnergyType.BASE, 1)));
        helper.assertFalse(block.matches(four, level), "Lingotes infundidos no deben hacer bloque");
        helper.assertTrue(block.matches(CraftingInput.of(2, 2, Collections.nCopies(4, ingot(null, 1))), level),
                "Lingotes normales sí hacen bloque");
        helper.succeed();
    }

    private static ArmorFabricatorBlockEntity fabricator(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.ARMOR_FABRICATOR.get());
        return (ArmorFabricatorBlockEntity) helper.getBlockEntity(POS);
    }

    private static void run(GameTestHelper helper, ArmorFabricatorBlockEntity be, int ticks) {
        BlockPos abs = helper.absolutePos(POS);
        for (int i = 0; i < ticks; i++) {
            ArmorFabricatorBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
        }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void fabricatedPieceInheritsInfusionAndDurability(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = fabricator(helper);
        be.fillEnergyForTests(100_000);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_PATTERN, ArmorSets.pattern(ArmorSets.SAIYAN_ARMOR));
        // Primero 8 Divinos (los que se gastan) y detrás uno sin infundir que NO entra en la cuenta.
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST, ingot(KiEnergyType.DIVINE, 8));
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST + 1, ingot(null, 1));
        be.selectPiece(1);
        run(helper, be, 250);
        ItemStack out = be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_OUTPUT);
        int base = new ItemStack(ModItems.SAIYAN_ARMOR_CHESTPLATE.get()).getMaxDamage();
        helper.assertTrue(KiInfusion.of(out) == KiEnergyType.DIVINE, "El peto debe salir Divino, sale " + out);
        helper.assertTrue(out.getMaxDamage() == base * 2, "Durabilidad ×2: " + base + " → " + out.getMaxDamage());
        helper.assertTrue(be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST + 1).getCount() == 1,
                "El lingote normal no se gasta");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void infusedPieceAsksItsTypeAndCostsLess(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = fabricator(helper);
        ItemStack plain = new ItemStack(Items.IRON_CHESTPLATE);
        ItemStack divine = KiInfusion.infused(plain, KiEnergyType.DIVINE);
        helper.assertTrue(ArmorFabricatorBlockEntity.requiredKiType(helper.getLevel(), divine) == KiEnergyType.DIVINE,
                "Una pieza infundida pide el tipo de su infusión");
        ItemStack blood = BloodItem.of(ModItems.STRENGTH_BLOOD.get(), 2, 1);
        var p0 = ArmorFabricatorBlockEntity.preview(helper.getLevel(), plain, blood);
        var p1 = ArmorFabricatorBlockEntity.preview(helper.getLevel(), divine, blood);
        helper.assertTrue(p1.fe() == KiInfusion.discounted(p0.fe(), KiEnergyType.DIVINE) && p1.fe() < p0.fe(),
                "FE con descuento Divino: " + p0.fe() + " → " + p1.fe());
        helper.assertTrue(p1.mb() < p0.mb(), "Fluido con descuento: " + p0.mb() + " → " + p1.mb());

        be.fillEnergyForTests(100_000);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_ARMOR, divine);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_BLOOD, blood);
        be.items().setStackInSlot(KiContainmentChamberBlockEntity.SLOT_TOL_DIVINE, new ItemStack(ModItems.KI_TOLERANCE_DIVINE.get()));
        fill(be.tank(), KiEnergyType.BASE, 1000);
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.NO_FLUID, "Ki Base no vale para una pieza Divina");
        be.tank().setFluid(FluidStack.EMPTY);
        fill(be.tank(), KiEnergyType.DIVINE, 1000);
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.OK, "Con Ki Divino sí imbuye");
        helper.succeed();
    }

    private static ItemStack imbued(ItemStack s, int str, int dex) {
        ItemStack c = s.copy();
        c.set(ModDataComponents.IMBUED.get(), new ImbuedAttributes(str, 0, dex, 0, 0));
        return c;
    }

    private static ImbuedAttributes imb(ItemStack s) {
        return s.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void templatesResetCopyTransferAugment(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = fabricator(helper);
        be.fillEnergyForTests(200_000);
        fill(be.tank(), KiEnergyType.BASE, 8000);
        int S = ArmorFabricatorBlockEntity.SLOT_ARMOR, T = ArmorFabricatorBlockEntity.SLOT_TARGET,
                P = ArmorFabricatorBlockEntity.SLOT_TEMPLATE;

        // Reiniciar: borra, gasta la plantilla.
        be.items().setStackInSlot(S, imbued(new ItemStack(Items.IRON_HELMET), 5, 2));
        be.items().setStackInSlot(P, new ItemStack(ModItems.RESET_TEMPLATE.get(), 2));
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.OK, "Reiniciar debe aplicarse");
        helper.assertTrue(imb(be.items().getStackInSlot(S)).isEmpty(), "Tras reiniciar no queda nada imbuido");
        helper.assertTrue(be.items().getStackInSlot(P).getCount() == 1, "La plantilla se gasta de una en una");

        // Copiar: el destino recibe lo mismo, el origen lo conserva, cobra FE y fluido.
        be.items().setStackInSlot(S, imbued(new ItemStack(Items.IRON_HELMET), 5, 2));
        be.items().setStackInSlot(T, new ItemStack(Items.IRON_CHESTPLATE));
        be.items().setStackInSlot(P, new ItemStack(ModItems.COPY_TEMPLATE.get()));
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.BAD_TEMPLATE, "Casco → peto no es el mismo tipo");
        be.items().setStackInSlot(T, new ItemStack(Items.DIAMOND_HELMET));
        int fluidBefore = be.tank().getFluidAmount();
        // El casco de diamante pide Súper: con Ki Base no vale.
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.NO_FLUID, "Destino de diamante pide Ki Súper");
        be.items().setStackInSlot(T, new ItemStack(Items.IRON_HELMET));
        int feBefore = be.energyStored();
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.OK, "Copiar debe aplicarse");
        helper.assertTrue(imb(be.items().getStackInSlot(T)).get(ZenkaiAttributes.STRENGTH) == 5
                && imb(be.items().getStackInSlot(S)).get(ZenkaiAttributes.STRENGTH) == 5, "Copiar deja 5 STR en ambas");
        helper.assertTrue(be.energyStored() < feBefore && be.tank().getFluidAmount() < fluidBefore, "Copiar cobra FE y fluido");

        // Transferir: el destino debe estar limpio; luego mueve.
        be.items().setStackInSlot(P, new ItemStack(ModItems.TRANSFER_TEMPLATE.get()));
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.BAD_TEMPLATE, "Destino ya imbuido: no transfiere");
        be.items().setStackInSlot(T, new ItemStack(Items.GOLDEN_HELMET));
        feBefore = be.energyStored();
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.OK, "Transferir debe aplicarse");
        helper.assertTrue(imb(be.items().getStackInSlot(S)).isEmpty()
                && imb(be.items().getStackInSlot(T)).get(ZenkaiAttributes.DEXTERITY) == 2, "Transferir mueve los atributos");
        helper.assertTrue(be.energyStored() == feBefore, "Transferir no cobra FE");

        // Aumentar: +1 a lo que ya hay.
        be.items().setStackInSlot(S, imbued(new ItemStack(Items.IRON_HELMET), 5, 0));
        be.items().setStackInSlot(P, new ItemStack(ModItems.AUGMENT_TEMPLATE.get()));
        helper.assertTrue(be.imbue() == ArmorFabricatorBlockEntity.ImbueResult.OK, "Aumentar debe aplicarse");
        ImbuedAttributes a = imb(be.items().getStackInSlot(S));
        helper.assertTrue(a.get(ZenkaiAttributes.STRENGTH) == 6 && a.get(ZenkaiAttributes.DEXTERITY) == 0,
                "Aumentar sube solo lo imbuido: " + a);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void duplicatesTemplate(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = fabricator(helper);
        be.fillEnergyForTests(100_000);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_PATTERN, new ItemStack(ModItems.COPY_TEMPLATE.get()));
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST, ingot(null, 3));
        run(helper, be, 250);
        ItemStack out = be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(out.is(ModItems.COPY_TEMPLATE.get()) && out.getCount() == 2, "Deben salir 2 plantillas, sale " + out);
        helper.assertTrue(be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_PATTERN).isEmpty(), "La plantilla se gasta");
        helper.assertTrue(be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST).getCount() == 1,
                "Gasta 2 lingotes");
        helper.succeed();
    }
}
