package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.blockentity.ArmorFabricatorBlockEntity;
import com.hmc.zenkai.content.blockentity.ImbuingStationBlockEntity;
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
 * Fase 5c de la 0.7.0 y rework del 2026-11-23: infusora (lingote normal o de nivel inferior + fluido → lingote infundido
 * propio, tolerancia en una ranura que absorbe a las de debajo), recetas por nivel sin mezcla, armadura con el nivel del
 * material, tope de puntos por pieza, pestaña Infundir, descuento al imbuir, plantillas y su duplicado.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KiInfusionGameTests {
    private KiInfusionGameTests() {}

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /** Estación de imbuido (separada de la fabricadora el 2026-11-23): imbuir, plantillas e infundir viven ahí. */
    private static ImbuingStationBlockEntity station(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.IMBUING_STATION.get());
        return (ImbuingStationBlockEntity) helper.getBlockEntity(POS);
    }
    private static final int TOL = KiContainmentChamberBlockEntity.SLOT_TOLERANCE;

    private static ItemStack ingot(KiEnergyType t, int n) {
        return new ItemStack(KiInfusion.ingot(t), n);
    }

    private static void fill(IFluidHandler tank, KiEnergyType t, int mb) {
        tank.fill(new FluidStack(t.fluid().source().get(), mb), IFluidHandler.FluidAction.EXECUTE);
    }

    private static void runInfuser(GameTestHelper helper, KiInfuserBlockEntity be, int ticks) {
        BlockPos abs = helper.absolutePos(POS);
        for (int i = 0; i < ticks; i++) KiInfuserBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void infuserNeedsToleranceAndInfuses(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.KI_INFUSER.get());
        KiInfuserBlockEntity be = (KiInfuserBlockEntity) helper.getBlockEntity(POS);
        be.fillEnergyForTests(50_000);
        be.items().setStackInSlot(KiInfuserBlockEntity.SLOT_INPUT, ingot(null, 1));   // uno: con dos el segundo arranca y gasta FE
        fill(be.tank(), KiEnergyType.DIVINE, 1000);
        helper.assertTrue(be.state() == KiInfuserBlockEntity.State.NO_TOLERANCE, "Ki Divino sin tolerancia no debe infundir");
        be.items().setStackInSlot(TOL, new ItemStack(ModItems.KI_TOLERANCE_SUPER.get()));
        helper.assertTrue(be.state() == KiInfuserBlockEntity.State.NO_TOLERANCE, "La tolerancia Súper no cubre el Divino");
        // La Legendaria absorbe a las de debajo.
        be.items().setStackInSlot(TOL, new ItemStack(ModItems.KI_TOLERANCE_LEGENDARY.get()));
        runInfuser(helper, be, KiInfusion.TICKS + 5);
        ItemStack out = be.items().getStackInSlot(KiInfuserBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(out.is(KiInfusion.ingot(KiEnergyType.DIVINE)), "Debería salir el lingote Divino, sale " + out);
        helper.assertTrue(be.tank().getFluidAmount() == 1000 - KiInfusion.MB_PER_ITEM,
                "Debe gastar " + KiInfusion.MB_PER_ITEM + " mB, quedan " + be.tank().getFluidAmount());
        helper.assertTrue(be.energyStored() == 50_000 - KiInfusion.FE_PER_ITEM, "FE gastada: " + (50_000 - be.energyStored()));
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void infuserRaisesInfusedIngotToFluidLevel(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.KI_INFUSER.get());
        KiInfuserBlockEntity be = (KiInfuserBlockEntity) helper.getBlockEntity(POS);
        be.fillEnergyForTests(50_000);
        be.items().setStackInSlot(TOL, new ItemStack(ModItems.KI_TOLERANCE_LEGENDARY.get()));
        be.items().setStackInSlot(KiInfuserBlockEntity.SLOT_INPUT, ingot(KiEnergyType.SUPER, 1));
        fill(be.tank(), KiEnergyType.SUPER, 1000);
        helper.assertTrue(be.state() == KiInfuserBlockEntity.State.NOT_HIGHER, "Súper con Ki Súper no sube");
        be.tank().setFluid(FluidStack.EMPTY);
        fill(be.tank(), KiEnergyType.LEGENDARY, 1000);
        runInfuser(helper, be, KiInfusion.TICKS + 5);
        helper.assertTrue(be.items().getStackInSlot(KiInfuserBlockEntity.SLOT_OUTPUT).is(KiInfusion.ingot(KiEnergyType.LEGENDARY)),
                "Súper + Ki Legendario → Legendario de una vez");
        helper.assertTrue(be.energyStored() == 50_000 - KiInfusion.FE_PER_ITEM, "Una sola infusión de coste, sin extra por escalón");
        helper.assertFalse(com.hmc.zenkai.feature.armor.KiInfusionRecipe.isInput(helper.getLevel().getRecipeManager(),
                ingot(KiEnergyType.LEGENDARY, 1)), "Un Legendario no tiene receta de infusión");
        helper.assertFalse(be.automationHandler().isItemValid(0, new ItemStack(Items.DIRT)), "Las tolvas no meten lo que no tiene receta");
        helper.assertTrue(helper.getLevel().getRecipeManager().getAllRecipesFor(
                com.hmc.zenkai.registry.ModRecipes.KI_INFUSION.get()).size() >= 10, "Faltan las 10 recetas de serie (datapack)");
        helper.succeed();
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String id) {
        RecipeHolder<?> h = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, id)).orElseThrow();
        return (CraftingRecipe) h.value();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void craftingPerLevelWithoutMixing(GameTestHelper helper) {
        var level = helper.getLevel();
        CraftingRecipe thread = recipe(helper, "ki_super_katchin_thread");
        ItemStack t = thread.assemble(CraftingInput.of(1, 1, List.of(ingot(KiEnergyType.SUPER, 1))), level.registryAccess());
        helper.assertTrue(t.is(KiInfusion.thread(KiEnergyType.SUPER)) && t.getCount() == 4, "Lingote Súper → 4 hilos Súper, sale " + t);

        CraftingRecipe cloth = recipe(helper, "ki_divine_katchin_cloth");
        ItemStack div = new ItemStack(KiInfusion.thread(KiEnergyType.DIVINE)), sup = new ItemStack(KiInfusion.thread(KiEnergyType.SUPER));
        helper.assertFalse(cloth.matches(CraftingInput.of(2, 2, List.of(div, div, sup, div)), level), "Sin mezcla de niveles en la tela");

        CraftingRecipe block = recipe(helper, "ki_base_katchin_block");
        helper.assertTrue(block.matches(CraftingInput.of(2, 2, Collections.nCopies(4, ingot(KiEnergyType.BASE, 1))), level),
                "Cuatro lingotes Normales hacen su bloque");
        helper.assertFalse(recipe(helper, "katchin_block").matches(
                CraftingInput.of(2, 2, Collections.nCopies(4, ingot(KiEnergyType.BASE, 1))), level), "No hacen el bloque normal");
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
    public static void fabricatedPieceTakesMaterialLevelWithoutMixing(GameTestHelper helper) {
        ArmorFabricatorBlockEntity be = fabricator(helper);
        be.fillEnergyForTests(100_000);
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_PATTERN, ArmorSets.pattern(ArmorSets.SAIYAN_ARMOR));
        // 7 Divinos delante y 3 normales detrás: con mezcla habría 10 ≥ 8, sin mezcla solo cuentan los 7 Divinos.
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST, ingot(KiEnergyType.DIVINE, 7));
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST + 1, ingot(null, 3));
        be.selectPiece(1);
        helper.assertTrue(be.materialCount(be.currentRecipe().orElseThrow().value().material()) == 7, "Solo cuentan los Divinos");
        run(helper, be, 250);
        helper.assertTrue(be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_OUTPUT).isEmpty(), "7 Divinos no bastan");
        be.items().setStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST, ingot(KiEnergyType.DIVINE, 8));
        run(helper, be, 250);
        ItemStack out = be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_OUTPUT);
        int base = new ItemStack(ModItems.SAIYAN_ARMOR_CHESTPLATE.get()).getMaxDamage();
        helper.assertTrue(KiInfusion.of(out) == KiEnergyType.DIVINE, "El peto debe salir Divino, sale " + out);
        helper.assertTrue(out.getMaxDamage() == base * 2, "Durabilidad ×2: " + base + " → " + out.getMaxDamage());
        helper.assertTrue(be.items().getStackInSlot(ArmorFabricatorBlockEntity.SLOT_MATERIAL_FIRST + 1).getCount() == 3,
                "Los lingotes normales no se gastan");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void pieceCapUsesLowerTierAndRejects(GameTestHelper helper) {
        // Hierro sin infundir: tope del escalón 0. Infundido en Legendario: su nivel propio (Normal) manda.
        ItemStack iron = new ItemStack(Items.IRON_CHESTPLATE);
        helper.assertTrue(KiInfusion.pieceCap(iron) == ServerConfig.imbuePieceCap(0), "Hierro sin infundir: tope 0");
        helper.assertTrue(KiInfusion.pieceCap(KiInfusion.applyToArmor(iron, KiEnergyType.LEGENDARY)) == ServerConfig.imbuePieceCap(1),
                "Hierro Legendario: manda su nivel propio (Normal)");
        // Set de Zenkai (tag sin techo): manda solo la infusión.
        ItemStack gi = new ItemStack(ModItems.TRAINING_GI_CHESTPLATE.get());
        helper.assertTrue(KiInfusion.pieceCap(KiInfusion.applyToArmor(gi, KiEnergyType.LEGENDARY)) == ServerConfig.imbuePieceCap(4),
                "Gi Legendario: tope Legendario");

        ImbuingStationBlockEntity be = station(helper);
        be.fillEnergyForTests(200_000);
        fill(be.tank(), KiEnergyType.BASE, 8000);
        ItemStack full = iron.copy();
        full.set(ModDataComponents.IMBUED.get(), new ImbuedAttributes(ServerConfig.imbuePieceCap(0), 0, 0, 0, 0));
        be.items().setStackInSlot(ImbuingStationBlockEntity.SLOT_ARMOR, full);
        be.items().setStackInSlot(ImbuingStationBlockEntity.SLOT_BLOOD, BloodItem.of(ModItems.STRENGTH_BLOOD.get(), 1, 1));
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.OVER_CAP, "Al tope, imbuir se rechaza");
        // Aumentar también.
        be.items().setStackInSlot(ImbuingStationBlockEntity.SLOT_TEMPLATE, new ItemStack(ModItems.AUGMENT_TEMPLATE.get()));
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.BAD_TEMPLATE, "Aumentar al tope se rechaza");
        helper.assertTrue(KiInfusion.usedPoints(be.items().getStackInSlot(ImbuingStationBlockEntity.SLOT_ARMOR))
                == ServerConfig.imbuePieceCap(0), "No se recorta nada");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void infuseTabInfusesAndRaises(GameTestHelper helper) {
        ImbuingStationBlockEntity be = station(helper);
        be.fillEnergyForTests(200_000);
        int P = ImbuingStationBlockEntity.SLOT_INFUSE_PIECE, I = ImbuingStationBlockEntity.SLOT_INFUSE_INGOTS;
        ItemStack piece = new ItemStack(Items.DIAMOND_CHESTPLATE);
        piece.set(ModDataComponents.IMBUED.get(), new ImbuedAttributes(5, 0, 0, 0, 0));
        be.items().setStackInSlot(P, piece);
        be.items().setStackInSlot(I, ingot(KiEnergyType.SUPER, 10));
        fill(be.tank(), KiEnergyType.SUPER, 4000);
        helper.assertTrue(be.infuse() == ImbuingStationBlockEntity.ImbueResult.NO_TOLERANCE, "Ki Súper sin tolerancia no infunde");
        be.items().setStackInSlot(TOL, new ItemStack(ModItems.KI_TOLERANCE_SUPER.get()));
        helper.assertTrue(be.infuse() == ImbuingStationBlockEntity.ImbueResult.OK, "Peto de diamante + 8 Súper infunde");
        ItemStack out = be.items().getStackInSlot(P);
        int base = new ItemStack(Items.DIAMOND_CHESTPLATE).getMaxDamage();
        helper.assertTrue(KiInfusion.of(out) == KiEnergyType.SUPER && out.getMaxDamage() == Math.round(base * 1.5),
                "Súper con durabilidad ×1,5: " + out);
        helper.assertTrue(be.items().getStackInSlot(I).getCount() == 2, "Gasta 8 lingotes (peto)");
        helper.assertTrue(KiInfusion.usedPoints(out) == 5, "Conserva lo imbuido");
        helper.assertTrue(be.infuse() == ImbuingStationBlockEntity.ImbueResult.NOT_HIGHER, "Súper sobre Súper no sube");

        // Subir a Divino: paga el nivel nuevo entero y la durabilidad se recalcula sobre la de fábrica.
        be.items().setStackInSlot(I, ingot(KiEnergyType.DIVINE, 8));
        be.items().setStackInSlot(TOL, new ItemStack(ModItems.KI_TOLERANCE_DIVINE.get()));
        be.tank().setFluid(FluidStack.EMPTY);
        fill(be.tank(), KiEnergyType.DIVINE, 4000);
        helper.assertTrue(be.infuse() == ImbuingStationBlockEntity.ImbueResult.OK, "Súper → Divino");
        out = be.items().getStackInSlot(P);
        helper.assertTrue(KiInfusion.of(out) == KiEnergyType.DIVINE && out.getMaxDamage() == base * 2, "Divino ×2 sobre la de fábrica");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void infusedPieceAsksItsTypeAndCostsLess(GameTestHelper helper) {
        ImbuingStationBlockEntity be = station(helper);
        ItemStack plain = new ItemStack(Items.IRON_CHESTPLATE);
        ItemStack divine = KiInfusion.applyToArmor(plain, KiEnergyType.DIVINE);
        helper.assertTrue(ImbuingStationBlockEntity.requiredKiType(helper.getLevel(), divine) == KiEnergyType.DIVINE,
                "Una pieza infundida pide el tipo de su infusión");
        ItemStack blood = BloodItem.of(ModItems.STRENGTH_BLOOD.get(), 2, 1);
        var p0 = ImbuingStationBlockEntity.preview(helper.getLevel(), plain, blood);
        var p1 = ImbuingStationBlockEntity.preview(helper.getLevel(), divine, blood);
        helper.assertTrue(p1.fe() == KiInfusion.discounted(p0.fe(), KiEnergyType.DIVINE) && p1.fe() < p0.fe(),
                "FE con descuento Divino: " + p0.fe() + " → " + p1.fe());
        helper.assertTrue(p1.mb() < p0.mb(), "Fluido con descuento: " + p0.mb() + " → " + p1.mb());

        be.fillEnergyForTests(100_000);
        be.items().setStackInSlot(ImbuingStationBlockEntity.SLOT_ARMOR, divine);
        be.items().setStackInSlot(ImbuingStationBlockEntity.SLOT_BLOOD, blood);
        be.items().setStackInSlot(TOL, new ItemStack(ModItems.KI_TOLERANCE_DIVINE.get()));
        fill(be.tank(), KiEnergyType.BASE, 1000);
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.NO_FLUID, "Ki Normal no vale para una pieza Divina");
        be.tank().setFluid(FluidStack.EMPTY);
        fill(be.tank(), KiEnergyType.DIVINE, 1000);
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.OK, "Con Ki Divino sí imbuye");
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
        ImbuingStationBlockEntity be = station(helper);
        be.fillEnergyForTests(200_000);
        fill(be.tank(), KiEnergyType.BASE, 8000);
        int S = ImbuingStationBlockEntity.SLOT_ARMOR, T = ImbuingStationBlockEntity.SLOT_TARGET,
                P = ImbuingStationBlockEntity.SLOT_TEMPLATE;

        be.items().setStackInSlot(S, imbued(new ItemStack(Items.IRON_HELMET), 5, 2));
        be.items().setStackInSlot(P, new ItemStack(ModItems.RESET_TEMPLATE.get(), 2));
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.OK, "Reiniciar debe aplicarse");
        helper.assertTrue(imb(be.items().getStackInSlot(S)).isEmpty(), "Tras reiniciar no queda nada imbuido");
        helper.assertTrue(be.items().getStackInSlot(P).getCount() == 1, "La plantilla se gasta de una en una");

        be.items().setStackInSlot(S, imbued(new ItemStack(Items.IRON_HELMET), 5, 2));
        be.items().setStackInSlot(T, new ItemStack(Items.IRON_CHESTPLATE));
        be.items().setStackInSlot(P, new ItemStack(ModItems.COPY_TEMPLATE.get()));
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.BAD_TEMPLATE, "Casco → peto no es el mismo tipo");
        be.items().setStackInSlot(T, new ItemStack(Items.DIAMOND_HELMET));
        int fluidBefore = be.tank().getFluidAmount();
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.NO_FLUID, "Destino de diamante pide Ki Súper");
        be.items().setStackInSlot(T, new ItemStack(Items.IRON_HELMET));
        int feBefore = be.energyStored();
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.OK, "Copiar debe aplicarse");
        helper.assertTrue(imb(be.items().getStackInSlot(T)).get(ZenkaiAttributes.STRENGTH) == 5
                && imb(be.items().getStackInSlot(S)).get(ZenkaiAttributes.STRENGTH) == 5, "Copiar deja 5 STR en ambas");
        helper.assertTrue(be.energyStored() < feBefore && be.tank().getFluidAmount() < fluidBefore, "Copiar cobra FE y fluido");

        be.items().setStackInSlot(P, new ItemStack(ModItems.TRANSFER_TEMPLATE.get()));
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.BAD_TEMPLATE, "Destino ya imbuido: no transfiere");
        be.items().setStackInSlot(T, new ItemStack(Items.GOLDEN_HELMET));
        feBefore = be.energyStored();
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.OK, "Transferir debe aplicarse");
        helper.assertTrue(imb(be.items().getStackInSlot(S)).isEmpty()
                && imb(be.items().getStackInSlot(T)).get(ZenkaiAttributes.DEXTERITY) == 2, "Transferir mueve los atributos");
        helper.assertTrue(be.energyStored() == feBefore, "Transferir no cobra FE");

        // Transferir más puntos de los que admite el destino: se rechaza.
        be.items().setStackInSlot(S, imbued(new ItemStack(Items.IRON_HELMET), ServerConfig.imbuePieceCap(0) + 1, 0));
        be.items().setStackInSlot(T, new ItemStack(Items.LEATHER_HELMET));
        be.items().setStackInSlot(P, new ItemStack(ModItems.TRANSFER_TEMPLATE.get()));   // la anterior se gastó
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.BAD_TEMPLATE, "Origen por encima del tope del destino");

        be.items().setStackInSlot(S, imbued(new ItemStack(Items.IRON_HELMET), 5, 0));
        be.items().setStackInSlot(P, new ItemStack(ModItems.AUGMENT_TEMPLATE.get()));
        helper.assertTrue(be.imbue() == ImbuingStationBlockEntity.ImbueResult.OK, "Aumentar debe aplicarse");
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

    /**
     * Cargar una infusora o una estación guardadas con OTRO número de ranuras (antes de la 5c y antes del rework del
     * 2026-11-23, 2026-11-25): vuelven al tamaño actual y lo que no encaja (sobra por el final o no vale en su ranura
     * de mejora) se SUELTA al mundo en el primer tick, sin perderse ni contar como otra mejora.
     */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void oldInfuserLayoutSpillsMisfits(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.KI_INFUSER.get());
        KiInfuserBlockEntity be = (KiInfuserBlockEntity) helper.getBlockEntity(POS);
        var reg = helper.getLevel().registryAccess();
        int oldSize = KiInfuserBlockEntity.SLOTS + 3;
        var old = new net.neoforged.neoforge.items.ItemStackHandler(oldSize);
        old.setStackInSlot(KiInfuserBlockEntity.SLOT_INPUT, ingot(null, 3));            // encaja: se queda
        old.setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));                          // no es mejora: se aparta
        old.setStackInSlot(oldSize - 1, new ItemStack(Items.GOLD_INGOT, 5));             // sobra por el final: se aparta
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("Items", old.serializeNBT(reg));
        tag.putInt("UpgradeLayout", 3);   // el desplazamiento de capas viejas ya lo cubre otro test
        be.loadWithComponents(tag, reg);
        helper.assertTrue(be.items().getSlots() == KiInfuserBlockEntity.SLOTS, "Debe cargar con las ranuras actuales");
        helper.assertTrue(be.items().getStackInSlot(KiInfuserBlockEntity.SLOT_INPUT).getCount() == 3, "Lo que encaja se queda");
        helper.assertTrue(be.items().getStackInSlot(0).isEmpty(), "Un diamante no se queda en una ranura de mejora");
        BlockPos abs = helper.absolutePos(POS);
        KiInfuserBlockEntity.serverTick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
        var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(abs).inflate(2));
        helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND) && e.getItem().getCount() == 2),
                "El diamante debe caer al mundo");
        helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.GOLD_INGOT) && e.getItem().getCount() == 5),
                "Lo que sobraba por el final debe caer al mundo");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void oldStationLayoutSpillsMisfits(GameTestHelper helper) {
        ImbuingStationBlockEntity be = station(helper);
        var reg = helper.getLevel().registryAccess();
        int oldSize = ImbuingStationBlockEntity.SLOTS + 3;
        var old = new net.neoforged.neoforge.items.ItemStackHandler(oldSize);
        old.setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));
        old.setStackInSlot(oldSize - 1, new ItemStack(Items.GOLD_INGOT, 5));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("Items", old.serializeNBT(reg));
        tag.putInt("UpgradeLayout", 3);
        be.loadWithComponents(tag, reg);
        helper.assertTrue(be.items().getSlots() == ImbuingStationBlockEntity.SLOTS, "Debe cargar con las ranuras actuales");
        helper.assertTrue(be.items().getStackInSlot(0).isEmpty(), "Un diamante no se queda en una ranura de mejora");
        be.dropContents(helper.getLevel(), helper.absolutePos(POS));
        var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(POS)).inflate(2));
        helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.GOLD_INGOT) && e.getItem().getCount() == 5),
                "Lo que sobraba por el final debe caer al mundo");
        helper.succeed();
    }
}
